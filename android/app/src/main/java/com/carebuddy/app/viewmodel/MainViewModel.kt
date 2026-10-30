package com.carebuddy.app.viewmodel

import android.app.Application
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.telephony.SmsManager
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.carebuddy.app.ble.BleConnectionState
import com.carebuddy.app.ble.CareBuddyBleManager
import com.carebuddy.app.data.CareBuddyRepository
import com.carebuddy.app.data.EmergencyContact
import com.carebuddy.app.data.Reminder
import com.google.gson.JsonObject
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.util.Calendar

data class SosAlertState(
    val isTriggered: Boolean = false,
    val isTestMode: Boolean = false,
    val contactName: String = "",
    val contactPhone: String = "",
    val smsStatus: String = "IDLE", // IDLE, SENT, FAILED, INITIATED
    val callStatus: String = "IDLE" // IDLE, INITIATED, FAILED
)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = CareBuddyRepository(application)
    val bleManager = CareBuddyBleManager(application)

    val connectionState: StateFlow<BleConnectionState> = bleManager.connectionState
    val deviceName: StateFlow<String?> = bleManager.deviceName

    private val _reminders = MutableStateFlow<List<Reminder>>(emptyList())
    val reminders: StateFlow<List<Reminder>> = _reminders

    private val _emergencyContacts = MutableStateFlow<List<EmergencyContact>>(emptyList())
    val emergencyContacts: StateFlow<List<EmergencyContact>> = _emergencyContacts

    private val _sosAlertState = MutableStateFlow(SosAlertState())
    val sosAlertState: StateFlow<SosAlertState> = _sosAlertState

    private val _sosCustomMessage = MutableStateFlow("")
    val sosCustomMessage: StateFlow<String> = _sosCustomMessage

    private val mainHandler = Handler(Looper.getMainLooper())

    init {
        loadData()
        observeBleConnectionState()
        observeBleMessages()
    }

    private fun loadData() {
        _reminders.value = repository.getReminders()
        _emergencyContacts.value = repository.getEmergencyContacts()
        _sosCustomMessage.value = repository.getSosMessage()
    }

    private fun observeBleConnectionState() {
        viewModelScope.launch {
            connectionState.collect { state ->
                if (state == BleConnectionState.CONNECTED) {
                    delay(200)
                    syncTimeToDevice()
                    delay(150)
                    syncAllRemindersToDevice()
                }
            }
        }
    }

    private fun observeBleMessages() {
        viewModelScope.launch {
            bleManager.incomingMessage.collect { msg ->
                val type = msg.get("type")?.asString ?: ""
                when (type) {
                    "SOS" -> {
                        val status = msg.get("status")?.asString ?: ""
                        if (status == "TRIGGERED") {
                            triggerSosAlert(isTest = false)
                        } else if (status == "TEST") {
                            triggerSosAlert(isTest = true)
                        } else if (status == "CANCELLED") {
                            dismissSosAlert()
                        }
                    }
                    "REMINDER_ACK" -> {
                        val id = msg.get("id")?.asString
                        if (id != null) {
                            markReminderAcknowledged(id)
                        }
                    }
                }
            }
        }
    }

    fun connectDevice() {
        bleManager.startScanAndConnect()
    }

    fun disconnectDevice() {
        bleManager.disconnect()
    }

    fun addReminder(reminder: Reminder) {
        val updated = _reminders.value.toMutableList()
        updated.add(reminder)
        _reminders.value = updated
        repository.saveReminders(updated)

        viewModelScope.launch {
            syncTimeToDevice()
            delay(120)
            sendReminderToDevice(reminder)
        }
    }

    fun updateReminder(reminder: Reminder) {
        val updated = _reminders.value.map { if (it.id == reminder.id) reminder else it }
        _reminders.value = updated
        repository.saveReminders(updated)

        viewModelScope.launch {
            syncTimeToDevice()
            delay(120)
            sendReminderToDevice(reminder)
        }
    }

    fun deleteReminder(id: String) {
        val updated = _reminders.value.filter { it.id != id }
        _reminders.value = updated
        repository.saveReminders(updated)

        val json = JsonObject().apply {
            addProperty("type", "DELETE_REMINDER")
            addProperty("id", id)
        }
        bleManager.sendJson(json)
    }

    fun toggleReminderEnabled(id: String, enabled: Boolean) {
        val reminder = _reminders.value.find { it.id == id } ?: return
        updateReminder(reminder.copy(enabled = enabled))
    }

    private fun sendReminderToDevice(reminder: Reminder) {
        val json = JsonObject().apply {
            addProperty("type", "ADD_REMINDER")
            addProperty("id", reminder.id)
            addProperty("title", reminder.title)
            addProperty("time", reminder.time)
            addProperty("minutes", reminder.minutesOfDay)
            addProperty("enabled", reminder.enabled)
        }
        bleManager.sendJson(json)
    }

    fun syncAllRemindersToDevice() {
        viewModelScope.launch {
            _reminders.value.forEach { reminder ->
                sendReminderToDevice(reminder)
                delay(120)
            }
        }
    }

    private fun markReminderAcknowledged(id: String) {
        val updated = _reminders.value.map {
            if (it.id == id) it.copy(isAcknowledgedToday = true) else it
        }
        _reminders.value = updated
        repository.saveReminders(updated)
    }

    fun updateEmergencyContacts(contacts: List<EmergencyContact>) {
        _emergencyContacts.value = contacts
        repository.saveEmergencyContacts(contacts)
    }

    fun saveSosCustomMessage(msg: String) {
        _sosCustomMessage.value = msg
        repository.saveSosMessage(msg)
    }

    fun triggerSosAlert(isTest: Boolean) {
        val primaryContact = _emergencyContacts.value.firstOrNull { it.isPrimary }
            ?: _emergencyContacts.value.firstOrNull()
            ?: EmergencyContact("def", "Primary Contact", "911")

        _sosAlertState.value = SosAlertState(
            isTriggered = true,
            isTestMode = isTest,
            contactName = primaryContact.name,
            contactPhone = primaryContact.phoneNumber,
            smsStatus = "IDLE",
            callStatus = "IDLE"
        )

        // Automatically send SMS and dial call when hardware SOS is triggered in real life
        if (!isTest && primaryContact.phoneNumber.isNotBlank()) {
            sendSosSmsDirectly()
            initiatePhoneCall(primaryContact.phoneNumber)
        }
    }

    fun testSosFromApp() {
        triggerSosAlert(isTest = true)
        val json = JsonObject().apply {
            addProperty("type", "TEST_SOS")
        }
        bleManager.sendJson(json)
    }

    fun dismissSosAlert() {
        _sosAlertState.value = SosAlertState(isTriggered = false)
    }

    fun sendSosSmsDirectly() {
        val state = _sosAlertState.value
        if (state.contactPhone.isEmpty()) return

        try {
            val smsManager: SmsManager = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                getApplication<Application>().getSystemService(SmsManager::class.java)
            } else {
                @Suppress("DEPRECATION")
                SmsManager.getDefault()
            }
            smsManager.sendTextMessage(state.contactPhone, null, _sosCustomMessage.value, null, null)
            _sosAlertState.value = state.copy(smsStatus = "SENT")
            Toast.makeText(getApplication(), "SOS SMS Sent successfully!", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            _sosAlertState.value = state.copy(smsStatus = "FAILED")
            // Fallback to SMS Intent chooser if direct sending fails
            openSmsAppFallback(state.contactPhone, _sosCustomMessage.value)
        }
    }

    fun openSmsAppFallback(phoneNumber: String, text: String) {
        val intent = Intent(Intent.ACTION_SENDTO).apply {
            data = Uri.parse("smsto:$phoneNumber")
            putExtra("sms_body", text)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            getApplication<Application>().startActivity(intent)
            _sosAlertState.value = _sosAlertState.value.copy(smsStatus = "INITIATED")
        } catch (e: Exception) {
            _sosAlertState.value = _sosAlertState.value.copy(smsStatus = "FAILED")
        }
    }

    fun initiatePhoneCall(phoneNumber: String) {
        val intent = Intent(Intent.ACTION_CALL).apply {
            data = Uri.parse("tel:$phoneNumber")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            getApplication<Application>().startActivity(intent)
            _sosAlertState.value = _sosAlertState.value.copy(callStatus = "INITIATED")
        } catch (e: Exception) {
            val dialIntent = Intent(Intent.ACTION_DIAL).apply {
                data = Uri.parse("tel:$phoneNumber")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            try {
                getApplication<Application>().startActivity(dialIntent)
                _sosAlertState.value = _sosAlertState.value.copy(callStatus = "INITIATED")
            } catch (ex: Exception) {
                _sosAlertState.value = _sosAlertState.value.copy(callStatus = "FAILED")
            }
        }
    }

    fun syncTimeToDevice() {
        val calendar = Calendar.getInstance()
        val minutesOfDay = calendar.get(Calendar.HOUR_OF_DAY) * 60 + calendar.get(Calendar.MINUTE)
        val epochSeconds = System.currentTimeMillis() / 1000

        val json = JsonObject().apply {
            addProperty("type", "SYNC_TIME")
            addProperty("minutes", minutesOfDay)
            addProperty("epoch", epochSeconds)
        }
        bleManager.sendJson(json)
    }
}
