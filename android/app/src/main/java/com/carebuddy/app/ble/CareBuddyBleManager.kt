package com.carebuddy.app.ble

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.*
import android.bluetooth.le.*
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.ParcelUuid
import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonObject
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.*

enum class BleConnectionState {
    DISCONNECTED, CONNECTING, CONNECTED, RECONNECTING
}

/**
 * Rich, deterministic status model exposing exact pipeline states and actionable labels.
 */
sealed class BleStatus(val label: String, val actionLabel: String? = null) {
    object Idle : BleStatus("Idle", "Connect")
    object BluetoothOff : BleStatus("Bluetooth is off", "Turn on Bluetooth")
    data class PermissionNeeded(val missingPermission: String) : BleStatus("Bluetooth permission required", "Grant Permission")
    object LocationOff : BleStatus("Location services required", "Enable Location")
    object Scanning : BleStatus("Searching for CareBuddy…", "Stop")
    object DeviceNotFound : BleStatus("CareBuddy not found. Is it nearby?", "Retry Scan")
    object Connecting : BleStatus("Connecting to CareBuddy…", "Cancel")
    data class SettingUp(val stage: String) : BleStatus("Setting up ($stage)…", "Cancel")
    object Connected : BleStatus("Connected", "Disconnect")
    data class Disconnected(val reason: String?) : BleStatus(reason ?: "Disconnected", "Connect")
}

data class BleScanTestResult(
    val name: String,
    val maskedAddress: String,
    val rssi: Int,
    val matchesCareBuddy: Boolean
)

class CareBuddyBleManager private constructor(private val context: Context) {

    companion object {
        const val TAG = "CareBuddyBle"
        val SERVICE_UUID: UUID = UUID.fromString("6E400001-B5A3-F393-E0A9-E50E24DCCA9E")
        val RX_CHAR_UUID: UUID = UUID.fromString("6E400002-B5A3-F393-E0A9-E50E24DCCA9E") // App -> ESP32 Write
        val TX_CHAR_UUID: UUID = UUID.fromString("6E400003-B5A3-F393-E0A9-E50E24DCCA9E") // ESP32 -> App Notify
        val CCCD_UUID: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

        private const val REQUESTED_MTU = 185
        private const val DEFAULT_MTU = 23

        // Per-stage watchdog timeouts
        const val TIMEOUT_SCAN_MS = 12_000L
        const val TIMEOUT_CONNECT_MS = 15_000L
        const val TIMEOUT_MTU_MS = 2_000L
        const val TIMEOUT_SERVICE_DISCOVERY_MS = 10_000L
        const val TIMEOUT_CCCD_WRITE_MS = 5_000L

        internal val BACKOFF_STEPS = longArrayOf(2_000L, 4_000L, 8_000L, 16_000L, 30_000L)
        private const val MAX_LOG_ENTRIES = 200

        fun getBackoffDelay(attempt: Int): Long {
            return BACKOFF_STEPS[attempt.coerceIn(0, BACKOFF_STEPS.size - 1)]
        }

        @SuppressLint("StaticFieldLeak")
        @Volatile
        private var INSTANCE: CareBuddyBleManager? = null

        fun getInstance(context: Context): CareBuddyBleManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: CareBuddyBleManager(context.applicationContext).also { INSTANCE = it }
            }
        }

        internal fun resetInstanceForTesting() {
            INSTANCE = null
        }
    }

    private val bluetoothAdapter: BluetoothAdapter? by lazy {
        val manager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        manager?.adapter
    }

    private var bluetoothGatt: BluetoothGatt? = null
    private var rxCharacteristic: BluetoothGattCharacteristic? = null

    private val _status = MutableStateFlow<BleStatus>(BleStatus.Idle)
    val status: StateFlow<BleStatus> = _status.asStateFlow()

    private val _connectionState = MutableStateFlow(BleConnectionState.DISCONNECTED)
    val connectionState: StateFlow<BleConnectionState> = _connectionState.asStateFlow()

    private val _deviceName = MutableStateFlow<String?>("CareBuddy")
    val deviceName: StateFlow<String?> = _deviceName.asStateFlow()

    private val _incomingMessage = MutableSharedFlow<JsonObject>(extraBufferCapacity = 64)
    val incomingMessage: SharedFlow<JsonObject> = _incomingMessage

    private val handler = Handler(Looper.getMainLooper())
    private val gson = Gson()
    private val logDateFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault())

    private var autoReconnect = true
    private var isScanning = false
    private var scanCallback: ScanCallback? = null
    private var reconnectAttempt = 0

    @Volatile private var mtu = DEFAULT_MTU
    private val writeQueue: Queue<ByteArray> = LinkedList()
    private var writeInProgress = false

    // Thread-safe fixed-size ring buffer for diagnostics logging
    private val eventLogs = LinkedList<String>()

    // Watchdog Runnables
    private val scanWatchdog = Runnable {
        if (isScanning) {
            log(TAG, "[WATCHDOG] Scan timed out after ${TIMEOUT_SCAN_MS}ms")
            stopScan()
            transitionTo(BleStatus.DeviceNotFound)
            scheduleReconnectWithBackoff()
        }
    }

    private val connectWatchdog = Runnable {
        log(TAG, "[WATCHDOG] GATT connect timed out after ${TIMEOUT_CONNECT_MS}ms")
        handleDisconnect(bluetoothGatt, "Connection timed out")
    }

    private val mtuWatchdog = Runnable {
        log(TAG, "[WATCHDOG] MTU callback timed out after ${TIMEOUT_MTU_MS}ms. Continuing with default MTU.")
        val gatt = bluetoothGatt
        if (gatt != null) {
            transitionTo(BleStatus.SettingUp("Discovering services (MTU fallback)"))
            discoverServicesWithWatchdog(gatt)
        }
    }

    private val serviceWatchdog = Runnable {
        log(TAG, "[WATCHDOG] Service discovery timed out after ${TIMEOUT_SERVICE_DISCOVERY_MS}ms")
        handleDisconnect(bluetoothGatt, "Service discovery timed out")
    }

    private val cccdWatchdog = Runnable {
        log(TAG, "[WATCHDOG] CCCD write callback timed out after ${TIMEOUT_CCCD_WRITE_MS}ms. Marking link ready.")
        markConnected()
    }

    private val reconnectRunnable = Runnable {
        if (autoReconnect && bluetoothGatt == null && !isScanning) {
            log(TAG, "[RECONNECT] Triggering scheduled reconnect attempt #$reconnectAttempt")
            startScanAndConnect()
        }
    }

    // Bluetooth Radio State Monitor Receiver
    private val bluetoothReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context?, intent: Intent?) {
            if (intent?.action == BluetoothAdapter.ACTION_STATE_CHANGED) {
                val state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)
                when (state) {
                    BluetoothAdapter.STATE_ON -> {
                        log(TAG, "[RADIO] Bluetooth turned ON")
                        if (autoReconnect && bluetoothGatt == null && !isScanning) {
                            startScanAndConnect()
                        }
                    }
                    BluetoothAdapter.STATE_OFF, BluetoothAdapter.STATE_TURNING_OFF -> {
                        log(TAG, "[RADIO] Bluetooth turned OFF")
                        stopScan()
                        disconnectInternal("Bluetooth disabled")
                        transitionTo(BleStatus.BluetoothOff)
                    }
                }
            }
        }
    }

    init {
        try {
            val filter = IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED)
            context.registerReceiver(bluetoothReceiver, filter)
        } catch (e: Exception) {
            log(TAG, "Failed registering bluetoothReceiver: ${e.message}")
        }
        checkInitialState()
    }

    private fun checkInitialState() {
        val adapter = bluetoothAdapter
        if (adapter == null || !adapter.isEnabled) {
            transitionTo(BleStatus.BluetoothOff)
        } else {
            transitionTo(BleStatus.Idle)
        }
    }

    fun log(tag: String, message: String) {
        val time = logDateFormat.format(Date())
        val entry = "$time [$tag] $message"
        Log.d(tag, message)
        synchronized(eventLogs) {
            if (eventLogs.size >= MAX_LOG_ENTRIES) {
                eventLogs.removeFirst()
            }
            eventLogs.add(entry)
        }
    }

    fun getEventLogs(): List<String> = synchronized(eventLogs) { ArrayList(eventLogs) }

    fun clearEventLogs() = synchronized(eventLogs) { eventLogs.clear() }

    private fun transitionTo(newStatus: BleStatus) {
        _status.value = newStatus
        val mappedConnectionState = when (newStatus) {
            is BleStatus.Connected -> BleConnectionState.CONNECTED
            is BleStatus.Connecting, is BleStatus.SettingUp -> BleConnectionState.CONNECTING
            is BleStatus.Scanning -> BleConnectionState.CONNECTING
            is BleStatus.DeviceNotFound -> BleConnectionState.RECONNECTING
            else -> BleConnectionState.DISCONNECTED
        }
        _connectionState.value = mappedConnectionState
        log(TAG, "[STATE] ${newStatus.label} (mapped: $mappedConnectionState)")
    }

    private fun checkPermissions(): String? {
        fun granted(p: String) = context.checkSelfPermission(p) == PackageManager.PERMISSION_GRANTED

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (!granted(Manifest.permission.BLUETOOTH_SCAN)) return Manifest.permission.BLUETOOTH_SCAN
            if (!granted(Manifest.permission.BLUETOOTH_CONNECT)) return Manifest.permission.BLUETOOTH_CONNECT
        } else {
            if (!granted(Manifest.permission.ACCESS_FINE_LOCATION)) return Manifest.permission.ACCESS_FINE_LOCATION
        }
        return null
    }

    private fun isLocationServiceEnabled(): Boolean {
        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.R) return true
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return false
        return lm.isProviderEnabled(LocationManager.GPS_PROVIDER) || lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
    }

    @SuppressLint("MissingPermission")
    private fun stopScan() {
        isScanning = false
        handler.removeCallbacks(scanWatchdog)
        try {
            scanCallback?.let { bluetoothAdapter?.bluetoothLeScanner?.stopScan(it) }
        } catch (e: Exception) {
            log(TAG, "Exception stopping scan: ${e.message}")
        }
        scanCallback = null
    }

    @SuppressLint("MissingPermission")
    fun startScanAndConnect() {
        val adapter = bluetoothAdapter
        if (adapter == null || !adapter.isEnabled) {
            log(TAG, "Cannot start scan: Bluetooth is disabled")
            transitionTo(BleStatus.BluetoothOff)
            return
        }

        val missingPerm = checkPermissions()
        if (missingPerm != null) {
            log(TAG, "Cannot start scan: Missing permission $missingPerm")
            transitionTo(BleStatus.PermissionNeeded(missingPerm))
            return
        }

        if (!isLocationServiceEnabled()) {
            log(TAG, "Cannot start scan: Location services disabled on Android <= 11")
            transitionTo(BleStatus.LocationOff)
            return
        }

        if (isScanning || bluetoothGatt != null) {
            log(TAG, "Scan/Connect request ignored: Already scanning ($isScanning) or connected ($bluetoothGatt)")
            return
        }

        val scanner = adapter.bluetoothLeScanner
        if (scanner == null) {
            log(TAG, "bluetoothLeScanner is null")
            transitionTo(BleStatus.Disconnected("Bluetooth scanner unavailable"))
            return
        }

        autoReconnect = true
        transitionTo(BleStatus.Scanning)

        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        val callback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                if (!isScanning) return

                val device = result.device
                val name = device.name ?: result.scanRecord?.deviceName ?: ""
                val serviceUuids = result.scanRecord?.serviceUuids
                val rawBytes = result.scanRecord?.bytes

                val matchesName = name.contains("CareBuddy", ignoreCase = true)
                val matchesUuid = serviceUuids?.contains(ParcelUuid(SERVICE_UUID)) == true
                val matchesRaw = rawBytes != null &&
                        String(rawBytes, Charsets.ISO_8859_1).contains("CareBuddy", ignoreCase = true)

                if (matchesName || matchesUuid || matchesRaw) {
                    log(TAG, "[SCAN] MATCH FOUND: '$name' (${device.address}) RSSI=${result.rssi}dBm")
                    stopScan()
                    _deviceName.value = if (name.isNotEmpty()) name else "CareBuddy-BLE"
                    handler.postDelayed({ connectToDevice(device) }, 200)
                }
            }

            override fun onScanFailed(errorCode: Int) {
                log(TAG, "[SCAN] Scan failed with error code: $errorCode")
                stopScan()
                transitionTo(BleStatus.DeviceNotFound)
                scheduleReconnectWithBackoff()
            }
        }

        try {
            scanCallback = callback
            isScanning = true
            scanner.startScan(null, settings, callback)
            handler.postDelayed(scanWatchdog, TIMEOUT_SCAN_MS)
            log(TAG, "[SCAN] Started BLE scan (watchdog ${TIMEOUT_SCAN_MS}ms)")
        } catch (e: Exception) {
            log(TAG, "[SCAN] Error invoking startScan: ${e.message}")
            stopScan()
            transitionTo(BleStatus.Disconnected("Scan initialization failed"))
            scheduleReconnectWithBackoff()
        }
    }

    @SuppressLint("MissingPermission")
    private fun connectToDevice(device: BluetoothDevice) {
        if (bluetoothGatt != null) return
        log(TAG, "[GATT] Initiating connectGatt to ${device.address}")
        autoReconnect = true
        mtu = DEFAULT_MTU
        transitionTo(BleStatus.Connecting)

        handler.postDelayed(connectWatchdog, TIMEOUT_CONNECT_MS)

        bluetoothGatt = device.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
    }

    private fun scheduleReconnectWithBackoff() {
        if (!autoReconnect) return
        handler.removeCallbacks(reconnectRunnable)
        val delay = BACKOFF_STEPS[reconnectAttempt.coerceAtMost(BACKOFF_STEPS.size - 1)]
        reconnectAttempt++
        log(TAG, "[BACKOFF] Scheduling reconnect in ${delay}ms (attempt #$reconnectAttempt)")
        handler.postDelayed(reconnectRunnable, delay)
    }

    @SuppressLint("MissingPermission")
    private fun handleDisconnect(gatt: BluetoothGatt?, reason: String?) {
        handler.removeCallbacks(connectWatchdog)
        handler.removeCallbacks(mtuWatchdog)
        handler.removeCallbacks(serviceWatchdog)
        handler.removeCallbacks(cccdWatchdog)

        rxCharacteristic = null
        synchronized(writeQueue) {
            writeQueue.clear()
            writeInProgress = false
        }

        try {
            gatt?.disconnect()
            gatt?.close()
        } catch (e: Exception) {
            log(TAG, "Error closing GATT: ${e.message}")
        }

        if (bluetoothGatt === gatt || gatt == null) {
            bluetoothGatt = null
        }

        transitionTo(BleStatus.Disconnected(reason))
        scheduleReconnectWithBackoff()
    }

    fun disconnect() {
        autoReconnect = false
        reconnectAttempt = 0
        disconnectInternal("User disconnected")
    }

    @SuppressLint("MissingPermission")
    private fun disconnectInternal(reason: String) {
        stopScan()
        handler.removeCallbacks(reconnectRunnable)
        handler.removeCallbacks(connectWatchdog)
        handler.removeCallbacks(mtuWatchdog)
        handler.removeCallbacks(serviceWatchdog)
        handler.removeCallbacks(cccdWatchdog)

        synchronized(writeQueue) {
            writeQueue.clear()
            writeInProgress = false
        }

        bluetoothGatt?.let {
            try {
                it.disconnect()
                it.close()
            } catch (e: Exception) {
                log(TAG, "Error on disconnect: ${e.message}")
            }
        }
        bluetoothGatt = null
        rxCharacteristic = null
        transitionTo(BleStatus.Disconnected(reason))
    }

    private fun markConnected() {
        handler.removeCallbacks(cccdWatchdog)
        handler.removeCallbacks(connectWatchdog)
        reconnectAttempt = 0
        transitionTo(BleStatus.Connected)
        log(TAG, "[READY] CareBuddy link active! MTU=$mtu, notifications enabled.")
    }

    @SuppressLint("MissingPermission")
    private fun discoverServicesWithWatchdog(gatt: BluetoothGatt) {
        handler.removeCallbacks(serviceWatchdog)
        handler.postDelayed(serviceWatchdog, TIMEOUT_SERVICE_DISCOVERY_MS)
        val started = gatt.discoverServices()
        log(TAG, "[SERVICES] discoverServices() started=$started (watchdog ${TIMEOUT_SERVICE_DISCOVERY_MS}ms)")
        if (!started) {
            handleDisconnect(gatt, "Failed starting service discovery")
        }
    }

    private val gattCallback = object : BluetoothGattCallback() {

        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            log(TAG, "[GATT] onConnectionStateChange: status=$status newState=$newState")
            handler.removeCallbacks(connectWatchdog)

            if (newState == BluetoothProfile.STATE_CONNECTED && status == BluetoothGatt.GATT_SUCCESS) {
                transitionTo(BleStatus.SettingUp("Negotiating MTU"))
                handler.postDelayed(mtuWatchdog, TIMEOUT_MTU_MS)
                if (!gatt.requestMtu(REQUESTED_MTU)) {
                    log(TAG, "[MTU] requestMtu() failed immediately; proceeding to service discovery")
                    handler.removeCallbacks(mtuWatchdog)
                    transitionTo(BleStatus.SettingUp("Discovering services"))
                    discoverServicesWithWatchdog(gatt)
                }
            } else {
                handleDisconnect(gatt, "GATT disconnect (status $status)")
            }
        }

        @SuppressLint("MissingPermission")
        override fun onMtuChanged(gatt: BluetoothGatt, newMtu: Int, status: Int) {
            handler.removeCallbacks(mtuWatchdog)
            mtu = if (status == BluetoothGatt.GATT_SUCCESS) newMtu else DEFAULT_MTU
            log(TAG, "[MTU] Handshake complete: MTU=$mtu (status=$status)")
            transitionTo(BleStatus.SettingUp("Discovering services"))
            discoverServicesWithWatchdog(gatt)
        }

        @SuppressLint("MissingPermission")
        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            handler.removeCallbacks(serviceWatchdog)
            if (status != BluetoothGatt.GATT_SUCCESS) {
                log(TAG, "[SERVICES] Discovery failed with status $status")
                handleDisconnect(gatt, "Service discovery failed")
                return
            }

            val service = gatt.getService(SERVICE_UUID)
            if (service == null) {
                log(TAG, "[SERVICES] CareBuddy NUS Service ($SERVICE_UUID) not found on device")
                handleDisconnect(gatt, "CareBuddy service not found")
                return
            }

            rxCharacteristic = service.getCharacteristic(RX_CHAR_UUID)
            val txChar = service.getCharacteristic(TX_CHAR_UUID)

            if (rxCharacteristic == null || txChar == null) {
                log(TAG, "[SERVICES] RX or TX characteristic missing from service")
                handleDisconnect(gatt, "Required characteristics missing")
                return
            }

            transitionTo(BleStatus.SettingUp("Enabling notifications"))
            handler.postDelayed(cccdWatchdog, TIMEOUT_CCCD_WRITE_MS)

            handler.postDelayed({
                try {
                    if (!enableNotifications(gatt, txChar)) {
                        log(TAG, "[CCCD] enableNotifications failed")
                        markConnected() // Fallback rather than hang
                    }
                } catch (e: Exception) {
                    log(TAG, "[CCCD] Exception enabling notifications: ${e.message}")
                    markConnected()
                }
            }, 250)
        }

        @SuppressLint("MissingPermission")
        override fun onDescriptorWrite(gatt: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {
            handler.removeCallbacks(cccdWatchdog)
            log(TAG, "[CCCD] onDescriptorWrite finished with status: $status")
            if (descriptor.uuid == CCCD_UUID) {
                markConnected()
            }
        }

        override fun onCharacteristicWrite(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                log(TAG, "[WRITE] Characteristic write failed with status $status")
            }
            synchronized(writeQueue) { writeInProgress = false }
            drainWriteQueue()
        }

        @Deprecated("Deprecated in API 33")
        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            @Suppress("DEPRECATION")
            handleNotification(characteristic.value)
        }

        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, value: ByteArray) {
            handleNotification(value)
        }
    }

    @SuppressLint("MissingPermission")
    private fun enableNotifications(gatt: BluetoothGatt, txChar: BluetoothGattCharacteristic): Boolean {
        if (!gatt.setCharacteristicNotification(txChar, true)) return false
        val descriptor = txChar.getDescriptor(CCCD_UUID) ?: run {
            log(TAG, "[CCCD] TX characteristic has no CCCD descriptor")
            return false
        }
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            gatt.writeDescriptor(descriptor, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE) == BluetoothStatusCodes.SUCCESS
        } else {
            @Suppress("DEPRECATION")
            descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
            @Suppress("DEPRECATION")
            gatt.writeDescriptor(descriptor)
        }
    }

    fun sendJson(jsonObject: JsonObject): Boolean {
        if (_status.value !is BleStatus.Connected || bluetoothGatt == null || rxCharacteristic == null) {
            log(TAG, "[WRITE] sendJson rejected: not in Connected state (${_status.value.label})")
            return false
        }

        val jsonString = gson.toJson(jsonObject)
        val bytes = jsonString.toByteArray(Charsets.UTF_8)
        val maxPayload = mtu - 3

        if (bytes.size > maxPayload) {
            log(TAG, "[WRITE] Payload ${bytes.size}B exceeds MTU limit ($maxPayload B): $jsonString")
            return false
        }

        log(TAG, "[WRITE] Queueing JSON (${bytes.size}B): $jsonString")
        synchronized(writeQueue) { writeQueue.add(bytes) }
        drainWriteQueue()
        return true
    }

    @SuppressLint("MissingPermission")
    private fun drainWriteQueue() {
        val gatt = bluetoothGatt ?: return
        val rx = rxCharacteristic ?: return

        val data = synchronized(writeQueue) {
            if (writeInProgress) null
            else writeQueue.poll()?.also { writeInProgress = true }
        } ?: return

        val writeType = if ((rx.properties and BluetoothGattCharacteristic.PROPERTY_WRITE) != 0)
            BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
        else
            BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE

        val started = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                gatt.writeCharacteristic(rx, data, writeType) == BluetoothStatusCodes.SUCCESS
            } else {
                @Suppress("DEPRECATION")
                rx.value = data
                rx.writeType = writeType
                @Suppress("DEPRECATION")
                gatt.writeCharacteristic(rx)
            }
        } catch (e: Exception) {
            log(TAG, "[WRITE] writeCharacteristic threw: ${e.message}")
            false
        }

        if (!started) {
            log(TAG, "[WRITE] writeCharacteristic failed to start")
            synchronized(writeQueue) { writeInProgress = false }
        }
    }

    private fun handleNotification(data: ByteArray?) {
        if (data == null) return
        val jsonStr = String(data, Charsets.UTF_8)
        log(TAG, "[NOTIFY] Received: $jsonStr")
        try {
            val jsonObject = gson.fromJson(jsonStr, JsonObject::class.java)
            _incomingMessage.tryEmit(jsonObject)
        } catch (e: Exception) {
            log(TAG, "[NOTIFY] Malformed JSON parse failure: ${e.message}")
        }
    }

    /**
     * Active 10-second Scan Test reporting all nearby BLE devices and RSSI signals.
     */
    @SuppressLint("MissingPermission")
    fun startScanTest(timeoutMs: Long = 10_000L, onUpdate: (List<BleScanTestResult>) -> Unit) {
        val adapter = bluetoothAdapter
        val scanner = adapter?.bluetoothLeScanner
        if (adapter == null || !adapter.isEnabled || scanner == null || checkPermissions() != null) {
            onUpdate(emptyList())
            return
        }

        val resultsMap = LinkedHashMap<String, BleScanTestResult>()
        val settings = ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build()

        val testCallback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                val device = result.device
                val name = device.name ?: result.scanRecord?.deviceName ?: "Unnamed"
                val rawAddr = device.address ?: "00:00:00:00:00:00"
                val maskedAddr = if (rawAddr.length >= 8) "XX:XX:XX:" + rawAddr.takeLast(8) else rawAddr
                val isCareBuddy = name.contains("CareBuddy", ignoreCase = true) ||
                        result.scanRecord?.serviceUuids?.contains(ParcelUuid(SERVICE_UUID)) == true

                resultsMap[rawAddr] = BleScanTestResult(name, maskedAddr, result.rssi, isCareBuddy)
                onUpdate(resultsMap.values.toList())
            }
        }

        try {
            scanner.startScan(null, settings, testCallback)
            log(TAG, "[SCAN_TEST] Started 10-second scan test")
            handler.postDelayed({
                try {
                    scanner.stopScan(testCallback)
                    log(TAG, "[SCAN_TEST] Scan test completed. Found ${resultsMap.size} devices.")
                } catch (_: Exception) {}
            }, timeoutMs)
        } catch (e: Exception) {
            log(TAG, "[SCAN_TEST] Error running scan test: ${e.message}")
        }
    }
}
