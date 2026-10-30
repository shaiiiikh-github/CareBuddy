package com.carebuddy.app.data

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class CareBuddyRepository(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("carebuddy_prefs", Context.MODE_PRIVATE)
    private val gson = Gson()

    companion object {
        private const val KEY_REMINDERS = "key_reminders"
        private const val KEY_CONTACTS = "key_contacts"
        private const val KEY_DEFAULT_SOS_MSG = "key_default_sos_msg"
        private const val KEY_TOUCH_MIN_MS = "key_touch_min_ms"
        private const val KEY_SOS_HOLD_MS = "key_sos_hold_ms"
    }

    fun getReminders(): List<Reminder> {
        val json = prefs.getString(KEY_REMINDERS, null) ?: return defaultReminders()
        val type = object : TypeToken<List<Reminder>>() {}.type
        return try {
            gson.fromJson(json, type)
        } catch (e: Exception) {
            defaultReminders()
        }
    }

    fun saveReminders(reminders: List<Reminder>) {
        val json = gson.toJson(reminders)
        prefs.edit().putString(KEY_REMINDERS, json).apply()
    }

    fun getEmergencyContacts(): List<EmergencyContact> {
        val json = prefs.getString(KEY_CONTACTS, null) ?: return defaultContacts()
        val type = object : TypeToken<List<EmergencyContact>>() {}.type
        return try {
            gson.fromJson(json, type)
        } catch (e: Exception) {
            defaultContacts()
        }
    }

    fun saveEmergencyContacts(contacts: List<EmergencyContact>) {
        val json = gson.toJson(contacts)
        prefs.edit().putString(KEY_CONTACTS, json).apply()
    }

    fun getSosMessage(): String {
        return prefs.getString(
            KEY_DEFAULT_SOS_MSG,
            "🚨 CareBuddy Emergency Alert: The user has activated the SOS button! Please check on them immediately."
        ) ?: ""
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
        return listOf(
            EmergencyContact("c1", "Dr. Smith (Family Doctor)", "+15550199283", isPrimary = true),
            EmergencyContact("c2", "Sarah (Caregiver / Daughter)", "+15550183742", isPrimary = false)
        )
    }
}
