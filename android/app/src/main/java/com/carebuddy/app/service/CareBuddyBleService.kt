package com.carebuddy.app.service

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Binder
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.carebuddy.app.MainActivity
import com.carebuddy.app.R
import com.carebuddy.app.ble.BleConnectionState
import com.carebuddy.app.ble.CareBuddyBleManager
import com.carebuddy.app.data.CareBuddyRepository
import com.carebuddy.app.data.Reminder
import com.carebuddy.app.sos.SosDispatcher
import com.google.gson.JsonObject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Calendar

class CareBuddyBleService : Service() {

    companion object {
        const val TAG = "CareBuddyService"
        const val CHANNEL_SERVICE = "carebuddy_service_channel"
        const val CHANNEL_SOS = "carebuddy_sos_channel"
        const val CHANNEL_REMINDERS = "carebuddy_reminders_channel"

        const val NOTIFICATION_ID_SERVICE = 1001
        const val NOTIFICATION_ID_SOS = 911
        const val NOTIFICATION_ID_REMINDER_BASE = 2000

        const val ACTION_START_SERVICE = "com.carebuddy.app.START_SERVICE"
        const val ACTION_STOP_SERVICE = "com.carebuddy.app.STOP_SERVICE"
        const val EXTRA_NAVIGATE_TO_SOS = "navigate_to_sos"

        @Volatile
        var instance: CareBuddyBleService? = null
            private set
    }

    private val binder = LocalBinder()
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val mainHandler = Handler(Looper.getMainLooper())

    lateinit var repository: CareBuddyRepository
        private set
    lateinit var bleManager: CareBuddyBleManager
        private set
    lateinit var sosDispatcher: SosDispatcher
        private set

    private val _ringingReminder = MutableStateFlow<Reminder?>(null)
    val ringingReminder: StateFlow<Reminder?> = _ringingReminder.asStateFlow()

    inner class LocalBinder : Binder() {
        fun getService(): CareBuddyBleService = this@CareBuddyBleService
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onCreate() {
        super.onCreate()
        instance = this
        Log.i(TAG, "CareBuddyBleService created")

        repository = CareBuddyRepository(applicationContext)
        bleManager = CareBuddyBleManager(applicationContext)
        sosDispatcher = SosDispatcher(applicationContext, repository)

        createNotificationChannels()
        startForeground(NOTIFICATION_ID_SERVICE, buildServiceNotification(BleConnectionState.DISCONNECTED))

        observeBleState()
        observeBleMessages()

        // Start scanning automatically if BLE permissions are granted
        if (hasBlePermissions()) {
            bleManager.startScanAndConnect()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP_SERVICE) {
            Log.i(TAG, "Stopping CareBuddyBleService via intent")
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }

        return START_STICKY
    }

    private fun hasBlePermissions(): Boolean {
        fun granted(p: String) = ContextCompat.checkSelfPermission(this, p) == PackageManager.PERMISSION_GRANTED
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            granted(Manifest.permission.BLUETOOTH_SCAN) && granted(Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            granted(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }

    private fun observeBleState() {
        serviceScope.launch {
            bleManager.connectionState.collect { state ->
                updateServiceNotification(state)
                if (state == BleConnectionState.CONNECTED) {
                    mainHandler.postDelayed({
                        syncTime()
                        syncAllReminders()
                    }, 1000)
                }
            }
        }
    }

    private fun observeBleMessages() {
        serviceScope.launch {
            bleManager.incomingMessage.collect { msg ->
                val type = msg.get("type")?.asString ?: ""
                when (type) {
                    "SOS" -> {
                        val status = msg.get("status")?.asString ?: ""
                        when (status) {
                            "TRIGGERED" -> {
                                Log.i(TAG, "Incoming SOS TRIGGERED from hardware!")
                                sosDispatcher.dispatchSos(isTest = false)
                                postSosEmergencyNotification(isTest = false)
                            }
                            "TEST" -> {
                                Log.i(TAG, "Incoming SOS TEST from hardware")
                                sosDispatcher.dispatchSos(isTest = true)
                                postSosEmergencyNotification(isTest = true)
                            }
                            "CANCELLED" -> {
                                Log.i(TAG, "Incoming SOS CANCELLED from hardware")
                                sosDispatcher.dismissSosAlert()
                                cancelSosNotification()
                            }
                        }
                    }
                    "REMINDER_TRIGGERED" -> {
                        val id = msg.get("id")?.asString ?: ""
                        val title = msg.get("title")?.asString ?: "Medicine"
                        val time = msg.get("time")?.asString ?: ""
                        Log.i(TAG, "Incoming REMINDER_TRIGGERED: $title ($id) at $time")

                        val reminder = repository.getReminders().firstOrNull { it.id == id }
                            ?: Reminder(id = id, title = title, time = time, minutesOfDay = 0)

                        _ringingReminder.value = reminder
                        postReminderNotification(reminder)
                    }
                    "REMINDER_ACK" -> {
                        val id = msg.get("id")?.asString ?: ""
                        Log.i(TAG, "Incoming REMINDER_ACK: $id")
                        _ringingReminder.value = null
                        cancelReminderNotification(id)

                        // Mark acknowledged in repository
                        val updated = repository.getReminders().map {
                            if (it.id == id) it.copy(isAcknowledgedToday = true) else it
                        }
                        repository.saveReminders(updated)
                    }
                }
            }
        }
    }

    private fun syncTime() {
        val cal = Calendar.getInstance()
        val minutesOfDay = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
        val epochSeconds = System.currentTimeMillis() / 1000

        val json = JsonObject().apply {
            addProperty("type", "SYNC_TIME")
            addProperty("minutes", minutesOfDay)
            addProperty("epoch", epochSeconds)
        }
        bleManager.sendJson(json)
    }

    private fun syncAllReminders() {
        serviceScope.launch {
            val list = repository.getReminders()
            list.forEach { reminder ->
                val json = JsonObject().apply {
                    addProperty("type", "ADD_REMINDER")
                    addProperty("id", reminder.id)
                    addProperty("title", reminder.title)
                    addProperty("time", reminder.time)
                    addProperty("minutes", reminder.minutesOfDay)
                    addProperty("enabled", reminder.enabled)
                }
                bleManager.sendJson(json)
                kotlinx.coroutines.delay(120)
            }
        }
    }

    private fun createNotificationChannels() {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // 1. Persistent Low-Priority Service Channel
        val serviceChannel = NotificationChannel(
            CHANNEL_SERVICE,
            "CareBuddy Connection",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Maintains persistent BLE connection with CareBuddy device"
            setShowBadge(false)
        }
        manager.createNotificationChannel(serviceChannel)

        // 2. High-Priority Alarm SOS Channel
        val alarmSound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
        val audioAttr = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ALARM)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()

        val sosChannel = NotificationChannel(
            CHANNEL_SOS,
            "CareBuddy Emergency SOS",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Full-screen alert when hardware emergency SOS button is activated"
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 500, 200, 500, 200, 800)
            setSound(alarmSound, audioAttr)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }
        manager.createNotificationChannel(sosChannel)

        // 3. High-Priority Reminder Channel
        val reminderSound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        val reminderChannel = NotificationChannel(
            CHANNEL_REMINDERS,
            "CareBuddy Reminders",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Alerts when a scheduled medication or routine reminder sounds"
            enableVibration(true)
            setSound(reminderSound, audioAttr)
        }
        manager.createNotificationChannel(reminderChannel)
    }

    private fun buildServiceNotification(state: BleConnectionState): Notification {
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val statusText = when (state) {
            BleConnectionState.CONNECTED -> "Connected to ${bleManager.deviceName.value ?: "CareBuddy"}"
            BleConnectionState.CONNECTING -> "Connecting to CareBuddy..."
            BleConnectionState.RECONNECTING -> "Searching / Reconnecting..."
            BleConnectionState.DISCONNECTED -> "Device disconnected. Tap to connect."
        }

        return NotificationCompat.Builder(this, CHANNEL_SERVICE)
            .setContentTitle("CareBuddy Safety Monitor")
            .setContentText(statusText)
            .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun updateServiceNotification(state: BleConnectionState) {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID_SERVICE, buildServiceNotification(state))
    }

    @SuppressLint("FullScreenIntentPolicy")
    private fun postSosEmergencyNotification(isTest: Boolean) {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val fullScreenIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(EXTRA_NAVIGATE_TO_SOS, true)
        }

        val fullScreenPendingIntent = PendingIntent.getActivity(
            this,
            911,
            fullScreenIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val title = if (isTest) "🧪 CareBuddy Test SOS" else "🚨 EMERGENCY: CareBuddy SOS Activated!"
        val content = if (isTest) {
            "Simulated alert from device. SMS/call bypassed."
        } else {
            "Hardware SOS button held! Emergency SMS dispatching..."
        }

        val builder = NotificationCompat.Builder(this, CHANNEL_SOS)
            .setContentTitle(title)
            .setContentText(content)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setOngoing(true)
            .setAutoCancel(false)
            .setContentIntent(fullScreenPendingIntent)
            .setFullScreenIntent(fullScreenPendingIntent, true)

        manager.notify(NOTIFICATION_ID_SOS, builder.build())
    }

    private fun cancelSosNotification() {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.cancel(NOTIFICATION_ID_SOS)
    }

    private fun postReminderNotification(reminder: Reminder) {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            reminder.id.hashCode(),
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_REMINDERS)
            .setContentTitle("🔔 Reminder: ${reminder.title}")
            .setContentText("Scheduled for ${reminder.time}. Tap touch sensor to acknowledge.")
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        val notifId = NOTIFICATION_ID_REMINDER_BASE + (reminder.id.hashCode() % 1000)
        manager.notify(notifId, notification)
    }

    private fun cancelReminderNotification(id: String) {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val notifId = NOTIFICATION_ID_REMINDER_BASE + (id.hashCode() % 1000)
        manager.cancel(notifId)
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
        Log.i(TAG, "CareBuddyBleService destroyed")
        serviceScope.cancel()
    }
}
