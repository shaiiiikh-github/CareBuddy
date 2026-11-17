package com.carebuddy.app.sos

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.telephony.SmsManager
import android.telephony.SubscriptionManager
import android.util.Log
import androidx.core.content.ContextCompat
import com.carebuddy.app.data.CareBuddyRepository
import com.carebuddy.app.data.EmergencyContact
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class SmsDeliveryStatus {
    IDLE,
    SENDING,
    SENT,
    DELIVERED,
    FAILED
}

data class ContactSosStatus(
    val contactId: String,
    val contactName: String,
    val phoneNumber: String,
    val isPrimary: Boolean,
    val status: SmsDeliveryStatus = SmsDeliveryStatus.IDLE,
    val failureReason: String? = null,
    val attemptCount: Int = 0
)

data class SosDispatchState(
    val isTriggered: Boolean = false,
    val isTestMode: Boolean = false,
    val timestampMs: Long = 0L,
    val contactsStatus: List<ContactSosStatus> = emptyList(),
    val fallbackRequired: Boolean = false,
    val generalError: String? = null,
    val callLaunched: Boolean = false,
    val callTargetNumber: String? = null
)

data class DiagnosticsInfo(
    val sendSmsGranted: Boolean,
    val callPhoneGranted: Boolean,
    val postNotificationsGranted: Boolean,
    val bluetoothGranted: Boolean,
    val batteryOptimizationExempt: Boolean,
    val fullScreenIntentAllowed: Boolean,
    val bleState: String,
    val lastSosResult: String
)

interface SmsSender {
    fun sendMultipart(
        destinationAddress: String,
        parts: List<String>,
        sentIntents: List<PendingIntent>?,
        deliveryIntents: List<PendingIntent>?
    )
    fun divideMessage(text: String): List<String>
}

class AndroidSmsSender(private val context: Context) : SmsSender {
    @SuppressLint("MissingPermission")
    private fun getSmsManager(): SmsManager {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val base = context.getSystemService(SmsManager::class.java)
            try {
                val defaultSubId = SubscriptionManager.getDefaultSmsSubscriptionId()
                if (defaultSubId != SubscriptionManager.INVALID_SUBSCRIPTION_ID && defaultSubId != -1) {
                    base.createForSubscriptionId(defaultSubId)
                } else {
                    base
                }
            } catch (e: Exception) {
                Log.w(SosDispatcher.TAG, "Failed resolving subscription SmsManager, using base", e)
                base
            }
        } else {
            @Suppress("DEPRECATION")
            SmsManager.getDefault()
        }
    }

    override fun divideMessage(text: String): List<String> {
        return try {
            getSmsManager().divideMessage(text) ?: listOf(text)
        } catch (e: Exception) {
            Log.e(SosDispatcher.TAG, "divideMessage threw exception", e)
            listOf(text)
        }
    }

    override fun sendMultipart(
        destinationAddress: String,
        parts: List<String>,
        sentIntents: List<PendingIntent>?,
        deliveryIntents: List<PendingIntent>?
    ) {
        val manager = getSmsManager()
        val sIntents = sentIntents?.let { ArrayList(it) }
        val dIntents = deliveryIntents?.let { ArrayList(it) }
        manager.sendMultipartTextMessage(
            destinationAddress,
            null,
            ArrayList(parts),
            sIntents,
            dIntents
        )
    }
}

class SosDispatcher(
    private val context: Context,
    private val repository: CareBuddyRepository,
    private val smsSender: SmsSender = AndroidSmsSender(context)
) {
    companion object {
        const val TAG = "CareBuddySOS"
        private const val ACTION_SMS_SENT = "com.carebuddy.app.ACTION_SMS_SENT"
        private const val ACTION_SMS_DELIVERED = "com.carebuddy.app.ACTION_SMS_DELIVERED"
        private const val EXTRA_CONTACT_ID = "contact_id"
        private const val EXTRA_ATTEMPT = "attempt"
        private const val DEDUPE_WINDOW_MS = 60_000L
        private const val RETRY_DELAY_MS = 5_000L

        fun normalizePhoneNumber(raw: String, defaultCountryCode: String = "+1"): String {
            val trimmed = raw.trim()
            if (trimmed.isBlank()) return ""

            val cleaned = trimmed.replace(Regex("[^0-9+]"), "")
            return when {
                cleaned.startsWith("+") -> cleaned
                cleaned.startsWith("00") -> "+" + cleaned.removePrefix("00")
                cleaned.length == 10 -> "$defaultCountryCode$cleaned"
                cleaned.length == 11 && cleaned.startsWith("1") && defaultCountryCode == "+1" -> "+$cleaned"
                else -> "$defaultCountryCode$cleaned"
            }
        }

        fun maskPhoneNumber(number: String): String {
            if (number.length <= 4) return "***"
            val visibleStart = if (number.startsWith("+")) 3 else 2
            val visibleEnd = 3
            if (number.length <= visibleStart + visibleEnd) return "***"
            return number.substring(0, visibleStart) + "****" + number.substring(number.length - visibleEnd)
        }

        fun sanitizeToGsm7(text: String): String {
            val sb = java.lang.StringBuilder()
            for (ch in text) {
                when (ch) {
                    '’', '‘' -> sb.append('\'')
                    '“', '”' -> sb.append('"')
                    '–', '—' -> sb.append('-')
                    in '\u0020'..'\u007E', '\n', '\r' -> sb.append(ch)
                    else -> {} // Strip unsupported unicode/emojis to keep pure GSM-7
                }
            }
            return sb.toString().trim()
        }

        fun buildSosMessage(userName: String, timestampMs: Long = System.currentTimeMillis()): String {
            val timeFormat = SimpleDateFormat("HH:mm dd/MM", Locale.US)
            val formatted = timeFormat.format(Date(timestampMs))
            val raw = "SOS from CareBuddy: $userName needs help. Triggered at $formatted."
            return sanitizeToGsm7(raw)
        }
    }

    private val _dispatchState = MutableStateFlow(SosDispatchState())
    val dispatchState: StateFlow<SosDispatchState> = _dispatchState.asStateFlow()

    private val handler = Handler(Looper.getMainLooper())
    private var lastTriggerTimestamp = 0L
    private var lastSosSummary = "None"

    private val smsReceiver = object : BroadcastReceiver() {
        override fun onReceive(recvContext: Context?, intent: Intent?) {
            intent ?: return
            val contactId = intent.getStringExtra(EXTRA_CONTACT_ID) ?: return
            val attempt = intent.getIntExtra(EXTRA_ATTEMPT, 1)

            when (intent.action) {
                ACTION_SMS_SENT -> {
                    val code = resultCode
                    handleSmsSentResult(contactId, attempt, code)
                }
                ACTION_SMS_DELIVERED -> {
                    val code = resultCode
                    handleSmsDeliveredResult(contactId, code)
                }
            }
        }
    }

    init {
        val filter = IntentFilter().apply {
            addAction(ACTION_SMS_SENT)
            addAction(ACTION_SMS_DELIVERED)
        }
        try {
            ContextCompat.registerReceiver(
                context,
                smsReceiver,
                filter,
                ContextCompat.RECEIVER_NOT_EXPORTED
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed registering SMS broadcast receiver", e)
        }
    }

    fun dispatchSos(isTest: Boolean = false) {
        val now = System.currentTimeMillis()
        Log.i(TAG, "[1] BLE msg received (type=SOS, status=${if (isTest) "TEST" else "TRIGGERED"})")

        if (!isTest && (now - lastTriggerTimestamp < DEDUPE_WINDOW_MS)) {
            Log.w(TAG, "[Dedupe] Duplicate SOS event within 60s suppressed")
            return
        }
        lastTriggerTimestamp = now

        val allContacts = repository.getEmergencyContacts()
        val enabledContacts = allContacts.filter { it.enabled }.sortedByDescending { it.isPrimary }

        val maskedList = enabledContacts.map { maskPhoneNumber(it.phoneNumber) }
        Log.i(TAG, "[2] contacts resolved (count=${enabledContacts.size}, numbers=$maskedList)")

        val hasSendSms = ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED
        val hasCallPhone = ContextCompat.checkSelfPermission(context, Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED
        Log.i(TAG, "[3] permission state (SEND_SMS=$hasSendSms, CALL_PHONE=$hasCallPhone)")

        if (enabledContacts.isEmpty()) {
            val errorMsg = "No emergency contacts configured! Please add contacts in Settings."
            Log.e(TAG, "SOS dispatch failed: $errorMsg")
            lastSosSummary = "FAILED: No contacts"
            _dispatchState.value = SosDispatchState(
                isTriggered = true,
                isTestMode = isTest,
                timestampMs = now,
                contactsStatus = emptyList(),
                fallbackRequired = true,
                generalError = errorMsg
            )
            return
        }

        val initialStatuses = enabledContacts.map { c ->
            ContactSosStatus(
                contactId = c.id,
                contactName = c.name,
                phoneNumber = normalizePhoneNumber(c.phoneNumber),
                isPrimary = c.isPrimary,
                status = if (isTest) SmsDeliveryStatus.IDLE else SmsDeliveryStatus.SENDING,
                attemptCount = 0
            )
        }

        _dispatchState.value = SosDispatchState(
            isTriggered = true,
            isTestMode = isTest,
            timestampMs = now,
            contactsStatus = initialStatuses,
            fallbackRequired = !hasSendSms,
            generalError = if (!hasSendSms) "SEND_SMS permission not granted" else null
        )

        if (isTest) {
            lastSosSummary = "TEST: Simulated alert shown (no SMS/call sent)"
            Log.i(TAG, "SOS Test Mode: Skipping SMS and phone call")
            return
        }

        // Send SMS to ALL enabled emergency contacts (primary first)
        val messageText = buildSosMessage("CareBuddy User", now)
        if (hasSendSms) {
            for (contact in enabledContacts) {
                sendSmsToContact(contact, messageText, attempt = 1)
            }
        } else {
            // Update all to FAILED due to permission denial
            val updated = _dispatchState.value.contactsStatus.map {
                it.copy(status = SmsDeliveryStatus.FAILED, failureReason = "Permission SEND_SMS denied")
            }
            _dispatchState.value = _dispatchState.value.copy(
                contactsStatus = updated,
                fallbackRequired = true
            )
            lastSosSummary = "FAILED: Permission denied"
        }

        // Launch phone call to Primary Contact AFTER SMS dispatch has begun
        val primary = enabledContacts.firstOrNull { it.isPrimary } ?: enabledContacts.first()
        val normalizedPrimary = normalizePhoneNumber(primary.phoneNumber)
        Log.i(TAG, "[7] call launched to ${maskPhoneNumber(normalizedPrimary)}")

        _dispatchState.value = _dispatchState.value.copy(
            callLaunched = true,
            callTargetNumber = normalizedPrimary
        )

        if (hasCallPhone && normalizedPrimary.isNotBlank()) {
            initiateCallIntent(normalizedPrimary)
        }
    }

    private fun sendSmsToContact(contact: EmergencyContact, message: String, attempt: Int) {
        val normalized = normalizePhoneNumber(contact.phoneNumber)
        if (normalized.isBlank()) {
            updateContactStatus(contact.id, SmsDeliveryStatus.FAILED, "Invalid/empty phone number")
            return
        }

        Log.i(TAG, "[4] SMS send start to ${maskPhoneNumber(normalized)} (attempt=$attempt, isPrimary=${contact.isPrimary})")

        try {
            val parts = smsSender.divideMessage(message)
            val sentIntents = parts.indices.map { partIdx ->
                val intent = Intent(ACTION_SMS_SENT).apply {
                    setPackage(context.packageName)
                    putExtra(EXTRA_CONTACT_ID, contact.id)
                    putExtra(EXTRA_ATTEMPT, attempt)
                }
                PendingIntent.getBroadcast(
                    context,
                    (contact.id.hashCode() * 31 + attempt * 7 + partIdx),
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            }

            val deliveryIntents = parts.indices.map { partIdx ->
                val intent = Intent(ACTION_SMS_DELIVERED).apply {
                    setPackage(context.packageName)
                    putExtra(EXTRA_CONTACT_ID, contact.id)
                }
                PendingIntent.getBroadcast(
                    context,
                    (contact.id.hashCode() * 47 + partIdx),
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            }

            smsSender.sendMultipart(normalized, parts, sentIntents, deliveryIntents)
            updateContactStatus(contact.id, SmsDeliveryStatus.SENDING, null, attempt)
        } catch (e: Exception) {
            Log.e(TAG, "SmsManager send thrown for ${maskPhoneNumber(normalized)}", e)
            if (attempt == 1) {
                scheduleRetry(contact, message)
            } else {
                updateContactStatus(contact.id, SmsDeliveryStatus.FAILED, e.message ?: "SMS Manager exception")
                _dispatchState.value = _dispatchState.value.copy(fallbackRequired = true)
            }
        }
    }

    private fun handleSmsSentResult(contactId: String, attempt: Int, resultCode: Int) {
        val current = _dispatchState.value.contactsStatus.firstOrNull { it.contactId == contactId }
        val numberMasked = current?.let { maskPhoneNumber(it.phoneNumber) } ?: contactId

        if (resultCode == Activity.RESULT_OK) {
            Log.i(TAG, "[5] sent callback SUCCESS for $numberMasked (attempt=$attempt)")
            updateContactStatus(contactId, SmsDeliveryStatus.SENT, null)
            lastSosSummary = "SENT to $numberMasked"
        } else {
            val reason = when (resultCode) {
                SmsManager.RESULT_ERROR_GENERIC_FAILURE -> "Generic failure"
                SmsManager.RESULT_ERROR_NO_SERVICE -> "No cellular service"
                SmsManager.RESULT_ERROR_NULL_PDU -> "Null PDU"
                SmsManager.RESULT_ERROR_RADIO_OFF -> "Airplane mode / Radio OFF"
                else -> "Error code $resultCode"
            }
            Log.e(TAG, "[5] sent callback FAILED ($reason) for $numberMasked (attempt=$attempt)")

            if (attempt == 1) {
                val contact = repository.getEmergencyContacts().firstOrNull { it.id == contactId }
                if (contact != null) {
                    val message = buildSosMessage("CareBuddy User", _dispatchState.value.timestampMs)
                    scheduleRetry(contact, message)
                    return
                }
            }

            updateContactStatus(contactId, SmsDeliveryStatus.FAILED, reason)
            _dispatchState.value = _dispatchState.value.copy(fallbackRequired = true)
            lastSosSummary = "FAILED: $reason for $numberMasked"
        }
    }

    private fun handleSmsDeliveredResult(contactId: String, resultCode: Int) {
        val current = _dispatchState.value.contactsStatus.firstOrNull { it.contactId == contactId }
        val numberMasked = current?.let { maskPhoneNumber(it.phoneNumber) } ?: contactId

        if (resultCode == Activity.RESULT_OK) {
            Log.i(TAG, "[6] delivered callback SUCCESS for $numberMasked")
            updateContactStatus(contactId, SmsDeliveryStatus.DELIVERED, null)
            lastSosSummary = "DELIVERED to $numberMasked"
        } else {
            Log.w(TAG, "[6] delivered callback reported delivery failure: $resultCode for $numberMasked")
        }
    }

    private fun scheduleRetry(contact: EmergencyContact, message: String) {
        Log.w(TAG, "Scheduling retry for ${maskPhoneNumber(contact.phoneNumber)} in 5s...")
        updateContactStatus(contact.id, SmsDeliveryStatus.SENDING, "Retrying in 5s...", attemptCount = 2)
        handler.postDelayed({
            sendSmsToContact(contact, message, attempt = 2)
        }, RETRY_DELAY_MS)
    }

    private fun updateContactStatus(
        contactId: String,
        status: SmsDeliveryStatus,
        reason: String?,
        attemptCount: Int? = null
    ) {
        val list = _dispatchState.value.contactsStatus.map { item ->
            if (item.contactId == contactId) {
                item.copy(
                    status = status,
                    failureReason = reason,
                    attemptCount = attemptCount ?: item.attemptCount
                )
            } else {
                item
            }
        }
        _dispatchState.value = _dispatchState.value.copy(contactsStatus = list)
    }

    private fun initiateCallIntent(phoneNumber: String) {
        try {
            val callIntent = Intent(Intent.ACTION_CALL).apply {
                data = Uri.parse("tel:$phoneNumber")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(callIntent)
        } catch (e: Exception) {
            Log.w(TAG, "Direct ACTION_CALL threw, falling back to ACTION_DIAL", e)
            try {
                val dialIntent = Intent(Intent.ACTION_DIAL).apply {
                    data = Uri.parse("tel:$phoneNumber")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(dialIntent)
            } catch (ex: Exception) {
                Log.e(TAG, "ACTION_DIAL failed to launch", ex)
            }
        }
    }

    fun sendTestSmsToContact(contact: EmergencyContact, onResult: (Boolean, String) -> Unit) {
        val hasPermission = ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED
        if (!hasPermission) {
            onResult(false, "SEND_SMS permission not granted")
            return
        }

        val normalized = normalizePhoneNumber(contact.phoneNumber)
        if (normalized.isBlank()) {
            onResult(false, "Invalid phone number")
            return
        }

        val testMessage = "TEST from CareBuddy: System test SMS to ${contact.name}. Hardware link OK."
        try {
            val parts = smsSender.divideMessage(testMessage)
            smsSender.sendMultipart(normalized, parts, null, null)
            Log.i(TAG, "Explicit test SMS sent to ${maskPhoneNumber(normalized)}")
            onResult(true, "Test SMS sent successfully!")
        } catch (e: Exception) {
            Log.e(TAG, "Error sending test SMS to ${maskPhoneNumber(normalized)}", e)
            onResult(false, "Send failed: ${e.message}")
        }
    }

    fun dismissSosAlert() {
        _dispatchState.value = SosDispatchState(isTriggered = false)
    }

    fun getDiagnosticsInfo(bleState: String): DiagnosticsInfo {
        val sendSms = ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED
        val callPhone = ContextCompat.checkSelfPermission(context, Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED
        val postNotif = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
        val bluetooth = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED &&
                    ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        }

        val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        val batteryExempt = powerManager?.isIgnoringBatteryOptimizations(context.packageName) ?: false

        val fullScreenIntentAllowed = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? android.app.NotificationManager
            nm?.canUseFullScreenIntent() ?: false
        } else {
            true
        }

        return DiagnosticsInfo(
            sendSmsGranted = sendSms,
            callPhoneGranted = callPhone,
            postNotificationsGranted = postNotif,
            bluetoothGranted = bluetooth,
            batteryOptimizationExempt = batteryExempt,
            fullScreenIntentAllowed = fullScreenIntentAllowed,
            bleState = bleState,
            lastSosResult = lastSosSummary
        )
    }
}
