package com.carebuddy.app.data

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class CareBuddyRepository(private val prefs: SharedPreferences) {

    constructor(context: Context) : this(
        context.getSharedPreferences("carebuddy_prefs", Context.MODE_PRIVATE)
    )

    private val gson = Gson()

    companion object {
        private const val TAG = "CareBuddyRepo"
        private const val KEY_REMINDERS = "key_reminders"
        private const val KEY_CONTACTS = "key_contacts"
        private const val KEY_CONTACTS_MIGRATED_V2 = "contacts_migrated_v2"
        private const val KEY_DEFAULT_SOS_MSG = "key_default_sos_msg"
        internal const val KEY_TOUCH_MIN_MS = "key_touch_min_ms"
        internal const val KEY_SOS_HOLD_MS = "key_sos_hold_ms"
    }

    init {
        migrateContactsIfNeeded()
    }

    fun migrateContactsIfNeeded() {
        val alreadyMigrated = prefs.getBoolean(KEY_CONTACTS_MIGRATED_V2, false)
        if (alreadyMigrated) return

        val json = prefs.getString(KEY_CONTACTS, null)
        val list: List<EmergencyContact> = if (!json.isNullOrBlank()) {
            try {
                val type = object : TypeToken<List<EmergencyContact>>() {}.type
                gson.fromJson(json, type) ?: emptyList()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to parse contacts for migration", e)
                emptyList()
            }
        } else {
            emptyList()
        }

        // Delete any saved contact matching +15550199283, +15550183742 or starting with +1555
        val filtered = list.filterNot { contact ->
            val norm = contact.phoneNumber.replace("[\\s\\-\\(\\)]".toRegex(), "")
            norm == "+15550199283" || norm == "+15550183742" || norm.startsWith("+1555") || norm.startsWith("1555")
        }

        // If, after migration, contacts exist but none is primary, mark the first one primary
        val finalized = if (filtered.isNotEmpty() && !filtered.any { it.isPrimary }) {
            filtered.mapIndexed { index, contact ->
                if (index == 0) contact.copy(isPrimary = true) else contact
            }
        } else {
            filtered
        }

        prefs.edit()
            .putString(KEY_CONTACTS, gson.toJson(finalized))
            .putBoolean(KEY_CONTACTS_MIGRATED_V2, true)
            .apply()
    }

    fun getReminders(): List<Reminder> {
        val json = prefs.getString(KEY_REMINDERS, null) ?: return defaultReminders()
        val type = object : TypeToken<List<Reminder>>() {}.type
        return try {
            gson.fromJson(json, type) ?: defaultReminders()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse reminders", e)
            defaultReminders()
        }
    }

    fun saveReminders(reminders: List<Reminder>) {
        val json = gson.toJson(reminders)
        prefs.edit().putString(KEY_REMINDERS, json).apply()
    }

    fun getEmergencyContacts(): List<EmergencyContact> {
        migrateContactsIfNeeded()
        val json = prefs.getString(KEY_CONTACTS, null) ?: return defaultContacts()
        val type = object : TypeToken<List<EmergencyContact>>() {}.type
        val contacts: List<EmergencyContact> = try {
            gson.fromJson(json, type) ?: defaultContacts()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse emergency contacts", e)
            defaultContacts()
        }

        return if (contacts.isNotEmpty() && !contacts.any { it.isPrimary }) {
            val updated = contacts.mapIndexed { index, c -> if (index == 0) c.copy(isPrimary = true) else c }
            saveEmergencyContacts(updated)
            updated
        } else {
            contacts
        }
    }

    fun saveEmergencyContacts(contacts: List<EmergencyContact>) {
        val normalized = if (contacts.isNotEmpty() && !contacts.any { it.isPrimary }) {
            contacts.mapIndexed { index, c -> if (index == 0) c.copy(isPrimary = true) else c }
        } else {
            contacts
        }
        val json = gson.toJson(normalized)
        prefs.edit().putString(KEY_CONTACTS, json).apply()
    }

    fun getSosMessage(): String {
        return prefs.getString(
            KEY_DEFAULT_SOS_MSG,
            "SOS from CareBuddy: Emergency alert triggered! Please check immediately."
        ) ?: "SOS from CareBuddy: Emergency alert triggered! Please check immediately."
    }

    fun saveSosMessage(message: String) {
        prefs.edit().putString(KEY_DEFAULT_SOS_MSG, message).apply()
    }

    fun getTouchMinMs(): Int = prefs.getInt(KEY_TOUCH_MIN_MS, 80)
    fun saveTouchMinMs(ms: Int) = prefs.edit().putInt(KEY_TOUCH_MIN_MS, ms).apply()

    fun getSosHoldMs(): Int = prefs.getInt(KEY_SOS_HOLD_MS, 3000)
    fun saveSosHoldMs(ms: Int) = prefs.edit().putInt(KEY_SOS_HOLD_MS, ms).apply()

    private fun defaultReminders(): List<Reminder> {
        return listOf(
            Reminder(
                id = "med_01",
                title = "Morning Medicine",
                time = "10:00 AM",
                minutesOfDay = 600,
                repeat = RepeatOption.DAILY,
                category = "Medication"
            ),
            Reminder(
                id = "water_01",
                title = "Drink Water",
                time = "11:30 AM",
                minutesOfDay = 690,
                repeat = RepeatOption.DAILY,
                category = "Water"
            ),
            Reminder(
                id = "walk_01",
                title = "Evening Walk",
                time = "05:00 PM",
                minutesOfDay = 1020,
                repeat = RepeatOption.DAILY,
                category = "Activity"
            )
        )
    }

    private fun defaultContacts(): List<EmergencyContact> {
        // No dummy contacts on clean install
        return emptyList()
    }
}
