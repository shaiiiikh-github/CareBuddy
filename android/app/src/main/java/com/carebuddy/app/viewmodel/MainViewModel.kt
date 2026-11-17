package com.carebuddy.app.viewmodel

import android.app.Application
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.carebuddy.app.ble.BleConnectionState
import com.carebuddy.app.ble.BleStatus
import com.carebuddy.app.ble.BleScanTestResult
import com.carebuddy.app.ble.CareBuddyBleManager
import com.carebuddy.app.data.CareBuddyRepository
import com.carebuddy.app.data.EmergencyContact
import com.carebuddy.app.data.Reminder
import com.carebuddy.app.service.CareBuddyBleService
import com.carebuddy.app.sos.DiagnosticsInfo
import com.carebuddy.app.sos.SosDispatchState
import com.carebuddy.app.sos.SosDispatcher
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
    val bleManager = CareBuddyBleManager.getInstance(application)
    val sosDispatcher = SosDispatcher(application, repository)

    val bleStatus: StateFlow<BleStatus> = bleManager.status
    val connectionState: StateFlow<BleConnectionState> = bleManager.connectionState
    val deviceName: StateFlow<String?> = bleManager.deviceName
    val sosDispatchState: StateFlow<SosDispatchState> = sosDispatcher.dispatchState

    val ringingReminder: StateFlow<Reminder?> = CareBuddyBleService.instance?.ringingReminder
        ?: MutableStateFlow(null)

    private val _reminders = MutableStateFlow<List<Reminder>>(emptyList())
    val reminders: StateFlow<List<Reminder>> = _reminders

    private val _emergencyContacts = MutableStateFlow<List<EmergencyContact>>(emptyList())
    val emergencyContacts: StateFlow<List<EmergencyContact>> = _emergencyContacts

    private val _sosCustomMessage = MutableStateFlow("")
    val sosCustomMessage: StateFlow<String> = _sosCustomMessage

    private val mainHandler = Handler(Looper.getMainLooper())

    companion object {
        private const val TAG = "MainViewModel"
    }

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
                    mainHandler.postDelayed({
                        syncTimeToDevice()
                        syncAllRemindersToDevice()
                    }, 1000)
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
                        when (status) {
                            "TRIGGERED" -> triggerSosAlert(isTest = false)
                            "TEST" -> triggerSosAlert(isTest = true)
                            "CANCELLED" -> dismissSosAlert()
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
        val index = updated.indexOfFirst { it.id == reminder.id }
        if (index != -1) {
            updated[index] = reminder
        } else {
            updated.add(reminder)
        }
        _reminders.value = updated
        repository.saveReminders(updated)
        sendReminderToDevice(reminder)
    }

    fun updateReminder(reminder: Reminder) {
        addReminder(reminder)
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

    fun toggleReminderEnabled(id: String, enabled: Boolean? = null) {
        val reminder = _reminders.value.firstOrNull { it.id == id } ?: return
        val newEnabled = enabled ?: !reminder.enabled
        val toggled = reminder.copy(enabled = newEnabled)
        addReminder(toggled)
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
        val normalized = if (contacts.isNotEmpty() && !contacts.any { it.isPrimary }) {
            contacts.mapIndexed { index, c -> if (index == 0) c.copy(isPrimary = true) else c }
        } else {
            contacts
        }
        _emergencyContacts.value = normalized
        repository.saveEmergencyContacts(normalized)
    }

    fun saveSosCustomMessage(msg: String) {
        _sosCustomMessage.value = msg
        repository.saveSosMessage(msg)
    }

    fun triggerSosAlert(isTest: Boolean) {
        sosDispatcher.dispatchSos(isTest)
    }

    fun testSosFromApp() {
        triggerSosAlert(isTest = true)
        val json = JsonObject().apply {
            addProperty("type", "TEST_SOS")
        }
        bleManager.sendJson(json)
    }

    fun dismissSosAlert() {
        sosDispatcher.dismissSosAlert()
    }

    fun openSmsAppFallback(phoneNumber: String, text: String) {
        val intent = Intent(Intent.ACTION_SENDTO).apply {
            data = Uri.parse("smsto:$phoneNumber")
            putExtra("sms_body", text)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            getApplication<Application>().startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to launch SMS intent chooser", e)
        }
    }

    fun initiatePhoneCall(phoneNumber: String) {
        val intent = Intent(Intent.ACTION_CALL).apply {
            data = Uri.parse("tel:$phoneNumber")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            getApplication<Application>().startActivity(intent)
        } catch (e: Exception) {
            Log.w(TAG, "ACTION_CALL failed, falling back to ACTION_DIAL", e)
            val dialIntent = Intent(Intent.ACTION_DIAL).apply {
                data = Uri.parse("tel:$phoneNumber")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            try {
                getApplication<Application>().startActivity(dialIntent)
            } catch (ex: Exception) {
                Log.e(TAG, "ACTION_DIAL failed to launch", ex)
            }
        }
    }

    fun sendTestSmsToContact(contact: EmergencyContact, onResult: (Boolean, String) -> Unit) {
        sosDispatcher.sendTestSmsToContact(contact, onResult)
    }

    fun getDiagnosticsInfo(): DiagnosticsInfo {
        return sosDispatcher.getDiagnosticsInfo(connectionState.value.name)
    }

    fun syncTimeToDevice() {
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

    fun getBleEventLogs(): List<String> {
        return bleManager.getEventLogs()
    }

    fun clearBleEventLogs() {
        bleManager.clearEventLogs()
    }

    fun runBleScanTest(onResult: (List<BleScanTestResult>) -> Unit) {
        bleManager.startScanTest(10_000L, onResult)
    }
}
