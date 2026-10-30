package com.carebuddy.app.ble

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.*
import android.bluetooth.le.*
import android.content.Context
import android.content.pm.PackageManager
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
import java.util.*

enum class BleConnectionState {
    DISCONNECTED, CONNECTING, CONNECTED, RECONNECTING
}

class CareBuddyBleManager(private val context: Context) {

    companion object {
        private const val TAG = "CareBuddyBle"
        val SERVICE_UUID: UUID = UUID.fromString("6E400001-B5A3-F393-E0A9-E50E24DCCA9E")
        val RX_CHAR_UUID: UUID = UUID.fromString("6E400002-B5A3-F393-E0A9-E50E24DCCA9E") // App -> ESP32 Write
        val TX_CHAR_UUID: UUID = UUID.fromString("6E400003-B5A3-F393-E0A9-E50E24DCCA9E") // ESP32 -> App Notify
        val CCCD_UUID: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

        private const val REQUESTED_MTU = 185          // ESP32 must also call BLEDevice::setMTU(185+)
        private const val DEFAULT_MTU = 23
        private const val SCAN_TIMEOUT_MS = 12_000L
        private const val RECONNECT_DELAY_MS = 3_000L
    }

    private val bluetoothAdapter: BluetoothAdapter? by lazy {
        val manager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        manager.adapter
    }

    private var bluetoothGatt: BluetoothGatt? = null
    private var rxCharacteristic: BluetoothGattCharacteristic? = null

    private val _connectionState = MutableStateFlow(BleConnectionState.DISCONNECTED)
    val connectionState: StateFlow<BleConnectionState> = _connectionState

    private val _deviceName = MutableStateFlow<String?>("CareBuddy")
    val deviceName: StateFlow<String?> = _deviceName

    // SharedFlow so every incoming BLE notification (e.g. SOS) is emitted without deduplication
    private val _incomingMessage = MutableSharedFlow<JsonObject>(extraBufferCapacity = 64)
    val incomingMessage: SharedFlow<JsonObject> = _incomingMessage

    private val handler = Handler(Looper.getMainLooper())
    private val gson = Gson()

    private var autoReconnect = true
    private var isScanning = false
    private var scanCallback: ScanCallback? = null

    @Volatile private var mtu = DEFAULT_MTU

    // Android allows only one GATT write at a time -> serialize them.
    private val writeQueue: Queue<ByteArray> = LinkedList()
    private var writeInProgress = false

    private val scanTimeoutRunnable = Runnable {
        if (isScanning) {
            Log.w(TAG, "Scan timed out, no CareBuddy found")
            stopScan()
            _connectionState.value = BleConnectionState.DISCONNECTED
            scheduleReconnect()   // previously the retry loop died here
        }
    }

    private val reconnectRunnable = Runnable {
        if (autoReconnect && bluetoothGatt == null && !isScanning) {
            _connectionState.value = BleConnectionState.RECONNECTING
            startScanAndConnect()
        }
    }

    private fun hasBlePermissions(): Boolean {
        fun granted(p: String) = context.checkSelfPermission(p) == PackageManager.PERMISSION_GRANTED
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            granted(Manifest.permission.BLUETOOTH_SCAN) && granted(Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            granted(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }

    private fun scheduleReconnect() {
        if (!autoReconnect) return
        handler.removeCallbacks(reconnectRunnable)
        handler.postDelayed(reconnectRunnable, RECONNECT_DELAY_MS)
    }

    @SuppressLint("MissingPermission")
    private fun stopScan() {
        isScanning = false
        handler.removeCallbacks(scanTimeoutRunnable)
        try {
            scanCallback?.let { bluetoothAdapter?.bluetoothLeScanner?.stopScan(it) }
        } catch (_: Exception) {}
        scanCallback = null
    }

    @SuppressLint("MissingPermission")
    fun startScanAndConnect() {
        val adapter = bluetoothAdapter
        if (adapter == null || !adapter.isEnabled) {
            Log.e(TAG, "Bluetooth disabled or unavailable")
            _connectionState.value = BleConnectionState.DISCONNECTED
            return
        }
        if (!hasBlePermissions()) {
            Log.e(TAG, "Missing BLE runtime permissions (BLUETOOTH_SCAN / BLUETOOTH_CONNECT or location)")
            _connectionState.value = BleConnectionState.DISCONNECTED
            return
        }
        // Don't start a second scan/connection on top of an existing one
        if (isScanning || bluetoothGatt != null) return

        val scanner = adapter.bluetoothLeScanner
        if (scanner == null) {
            Log.e(TAG, "bluetoothLeScanner is null")
            _connectionState.value = BleConnectionState.DISCONNECTED
            return
        }

        _connectionState.value = BleConnectionState.CONNECTING

        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        val callback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                // Late/duplicate results can arrive after stopScan(); ignore them,
                // otherwise we open several GATT connections to the same device.
                if (!isScanning) return

                val device = result.device
                val name = device.name ?: result.scanRecord?.deviceName ?: ""
                val serviceUuids = result.scanRecord?.serviceUuids

                val matchesName = name.contains("CareBuddy", ignoreCase = true)
                val matchesUuid = serviceUuids?.contains(ParcelUuid(SERVICE_UUID)) == true
                val rawBytes = result.scanRecord?.bytes
                val matchesRaw = rawBytes != null &&
                        String(rawBytes, Charsets.ISO_8859_1).contains("CareBuddy", ignoreCase = true)

                if (matchesName || matchesUuid || matchesRaw) {
                    Log.d(TAG, "FOUND MATCH: $name (${device.address})")
                    stopScan()
                    _deviceName.value = if (name.isNotEmpty()) name else "CareBuddy-BLE"
                    handler.postDelayed({ connectToDevice(device) }, 200)
                }
            }

            override fun onScanFailed(errorCode: Int) {
                Log.e(TAG, "BLE Scan failed: $errorCode")
                stopScan()
                _connectionState.value = BleConnectionState.DISCONNECTED
                scheduleReconnect()
            }
        }

        try {
            scanCallback = callback
            isScanning = true
            scanner.startScan(null, settings, callback)
            handler.postDelayed(scanTimeoutRunnable, SCAN_TIMEOUT_MS)
        } catch (e: Exception) {
            Log.e(TAG, "Error starting scan", e)
            stopScan()
            _connectionState.value = BleConnectionState.DISCONNECTED
            scheduleReconnect()
        }
    }

    @SuppressLint("MissingPermission")
    private fun connectToDevice(device: BluetoothDevice) {
        if (bluetoothGatt != null) return
        Log.d(TAG, "Connecting to GATT on ${device.address}")
        autoReconnect = true
        mtu = DEFAULT_MTU
        bluetoothGatt = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            device.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
        } else {
            device.connectGatt(context, false, gattCallback)
        }
    }

    @SuppressLint("MissingPermission")
    fun disconnect() {
        autoReconnect = false
        stopScan()
        handler.removeCallbacksAndMessages(null)
        synchronized(writeQueue) {
            writeQueue.clear()
            writeInProgress = false
        }
        bluetoothGatt?.let {
            it.disconnect()
            it.close()
        }
        bluetoothGatt = null
        rxCharacteristic = null
        _connectionState.value = BleConnectionState.DISCONNECTED
    }

    /**
     * Returns true only if the message was actually queued for sending.
     * The whole JSON must fit in one ATT write (MTU - 3 bytes) because the
     * ESP32 firmware treats each write as one complete message.
     */
    fun sendJson(jsonObject: JsonObject): Boolean {
        if (bluetoothGatt == null || rxCharacteristic == null ||
            _connectionState.value != BleConnectionState.CONNECTED
        ) {
            Log.w(TAG, "sendJson: not connected / not ready")
            return false
        }

        val jsonString = gson.toJson(jsonObject)
        val bytes = jsonString.toByteArray(Charsets.UTF_8)
        val maxPayload = mtu - 3
        if (bytes.size > maxPayload) {
            Log.e(TAG, "Payload ${bytes.size}B exceeds max $maxPayload B (MTU=$mtu): $jsonString")
            return false
        }

        Log.d(TAG, "Queueing BLE JSON (${bytes.size}B): $jsonString")
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

        // Use whichever write type the ESP32 characteristic actually advertises
        val writeType =
            if ((rx.properties and BluetoothGattCharacteristic.PROPERTY_WRITE) != 0)
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
            Log.e(TAG, "writeCharacteristic threw", e)
            false
        }

        if (!started) {
            Log.e(TAG, "writeCharacteristic failed to start")
            synchronized(writeQueue) { writeInProgress = false }
        }
    }

    @SuppressLint("MissingPermission")
    private fun enableNotifications(gatt: BluetoothGatt, txChar: BluetoothGattCharacteristic): Boolean {
        if (!gatt.setCharacteristicNotification(txChar, true)) return false
        val descriptor = txChar.getDescriptor(CCCD_UUID) ?: run {
            Log.e(TAG, "TX characteristic has no CCCD (ESP32 needs BLE2902 on TX)")
            return false
        }
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            gatt.writeDescriptor(descriptor, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE) ==
                    BluetoothStatusCodes.SUCCESS
        } else {
            @Suppress("DEPRECATION")
            descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
            @Suppress("DEPRECATION")
            gatt.writeDescriptor(descriptor)
        }
    }

    @SuppressLint("MissingPermission")
    private fun handleDisconnect(gatt: BluetoothGatt) {
        rxCharacteristic = null
        synchronized(writeQueue) {
            writeQueue.clear()
            writeInProgress = false
        }
        try { gatt.close() } catch (_: Exception) {}
        if (bluetoothGatt === gatt) bluetoothGatt = null
        _connectionState.value = BleConnectionState.DISCONNECTED
        scheduleReconnect()
    }

    private val gattCallback = object : BluetoothGattCallback() {

        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED && status == BluetoothGatt.GATT_SUCCESS) {
                Log.d(TAG, "GATT connected, requesting MTU $REQUESTED_MTU")
                // Stay in CONNECTING until MTU + services + CCCD are all done.
                // Services are discovered from onMtuChanged.
                if (!gatt.requestMtu(REQUESTED_MTU)) {
                    Log.w(TAG, "requestMtu failed to start, discovering services with default MTU")
                    gatt.discoverServices()
                }
            } else {
                Log.w(TAG, "GATT disconnected/failed, status=$status newState=$newState")
                handleDisconnect(gatt)
            }
        }

        @SuppressLint("MissingPermission")
        override fun onMtuChanged(gatt: BluetoothGatt, newMtu: Int, status: Int) {
            mtu = if (status == BluetoothGatt.GATT_SUCCESS) newMtu else DEFAULT_MTU
            Log.d(TAG, "MTU = $mtu (status=$status) -> max payload ${mtu - 3}B")
            gatt.discoverServices()
        }

        @SuppressLint("MissingPermission")
        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                Log.e(TAG, "onServicesDiscovered failed with status: $status")
                gatt.disconnect()
                return
            }
            val service = gatt.getService(SERVICE_UUID)
            if (service == null) {
                Log.e(TAG, "CareBuddy Service NOT FOUND")
                gatt.disconnect()
                return
            }
            rxCharacteristic = service.getCharacteristic(RX_CHAR_UUID)
            val txChar = service.getCharacteristic(TX_CHAR_UUID)
            if (rxCharacteristic == null || txChar == null) {
                Log.e(TAG, "RX/TX characteristic missing")
                gatt.disconnect()
                return
            }

            handler.postDelayed({
                try {
                    if (!enableNotifications(gatt, txChar)) {
                        Log.e(TAG, "Failed to enable TX notifications")
                        gatt.disconnect()
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error enabling notifications", e)
                    gatt.disconnect()
                }
            }, 300)
        }

        @SuppressLint("MissingPermission")
        override fun onDescriptorWrite(gatt: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {
            Log.d(TAG, "CCCD write finished. status=$status")
            if (descriptor.uuid != CCCD_UUID) return
            if (status == BluetoothGatt.GATT_SUCCESS) {
                // Only NOW is the link actually usable in both directions
                _connectionState.value = BleConnectionState.CONNECTED
                Log.d(TAG, "BLE ready: MTU=$mtu, notifications enabled")
            } else {
                Log.e(TAG, "CCCD write failed, status=$status")
                gatt.disconnect()
            }
        }

        override fun onCharacteristicWrite(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int
        ) {
            if (status != BluetoothGatt.GATT_SUCCESS) Log.e(TAG, "Write failed, status=$status")
            synchronized(writeQueue) { writeInProgress = false }
            drainWriteQueue()
        }

        @Deprecated("Deprecated in API 33")
        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            @Suppress("DEPRECATION")
            handleNotification(characteristic.value)
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray
        ) {
            handleNotification(value)
        }
    }

    private fun handleNotification(data: ByteArray?) {
        if (data == null) return
        val jsonStr = String(data, Charsets.UTF_8)
        Log.d(TAG, "Received BLE Notification: $jsonStr")
        try {
            val jsonObject = gson.fromJson(jsonStr, JsonObject::class.java)
            _incomingMessage.tryEmit(jsonObject)
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing incoming BLE JSON (truncated by MTU?): $jsonStr", e)
        }
    }
}
