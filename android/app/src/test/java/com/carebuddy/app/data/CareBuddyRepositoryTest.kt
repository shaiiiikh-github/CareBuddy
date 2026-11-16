package com.carebuddy.app.data

import android.content.SharedPreferences
import com.google.gson.Gson
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class CareBuddyRepositoryTest {

    private lateinit var fakePrefs: FakeSharedPreferences
    private val gson = Gson()

    @Before
    fun setup() {
        fakePrefs = FakeSharedPreferences()
    }

    @Test
    fun testMigrationRemovesPlaceholderContacts() {
        val initialContacts = listOf(
            EmergencyContact("c1", "Dr. Smith", "+15550199283", isPrimary = true),
            EmergencyContact("c2", "Sarah", "+15550183742", isPrimary = false),
            EmergencyContact("c3", "Fake Clinic", "+1 (555) 123-4567", isPrimary = false),
            EmergencyContact("c4", "Real Caregiver", "+19876543210", isPrimary = false)
        )
        fakePrefs.putString("key_contacts", gson.toJson(initialContacts))

        val repo = CareBuddyRepository(fakePrefs)
        val migrated = repo.getEmergencyContacts()

        // Placeholder contacts should be completely purged
        assertEquals(1, migrated.size)
        val remaining = migrated.first()
        assertEquals("Real Caregiver", remaining.name)
        assertEquals("+19876543210", remaining.phoneNumber)

        // The remaining contact must be auto-promoted to primary
        assertTrue(remaining.isPrimary)

        // Migration flag must be recorded
        assertTrue(fakePrefs.getBoolean("contacts_migrated_v2", false))
    }

    @Test
    fun testAutoSelectsFirstContactAsPrimaryWhenNoneIsPrimary() {
        val contacts = listOf(
            EmergencyContact("1", "Alice", "+19876543210", isPrimary = false),
            EmergencyContact("2", "Bob", "+19876543211", isPrimary = false)
        )
        fakePrefs.putString("key_contacts", gson.toJson(contacts))
        fakePrefs.putBoolean("contacts_migrated_v2", true)

        val repo = CareBuddyRepository(fakePrefs)
        val result = repo.getEmergencyContacts()

        assertEquals(2, result.size)
        assertTrue(result[0].isPrimary)
        assertFalse(result[1].isPrimary)
    }

    @Test
    fun testPreservesExistingPrimaryContact() {
        val contacts = listOf(
            EmergencyContact("1", "Alice", "+19876543210", isPrimary = false),
            EmergencyContact("2", "Bob", "+19876543211", isPrimary = true)
        )
        fakePrefs.putString("key_contacts", gson.toJson(contacts))
        fakePrefs.putBoolean("contacts_migrated_v2", true)

        val repo = CareBuddyRepository(fakePrefs)
        val result = repo.getEmergencyContacts()

        assertEquals(2, result.size)
        assertFalse(result[0].isPrimary)
        assertTrue(result[1].isPrimary)
    }

    @Test
    fun testEmptyContactsOnCleanInstall() {
        fakePrefs.putBoolean("contacts_migrated_v2", true)
        val repo = CareBuddyRepository(fakePrefs)
        val result = repo.getEmergencyContacts()

        assertTrue(result.isEmpty())
    }
}

/**
 * In-memory SharedPreferences implementation for pure JVM unit tests without android.jar stubs.
 */
class FakeSharedPreferences : SharedPreferences {
    private val map = mutableMapOf<String, Any?>()

    override fun getAll(): MutableMap<String, *> = HashMap(map)
    override fun getString(key: String?, defValue: String?): String? = map[key] as? String ?: defValue
    override fun getStringSet(key: String?, defValues: MutableSet<String>?): MutableSet<String>? =
        @Suppress("UNCHECKED_CAST") (map[key] as? MutableSet<String>) ?: defValues
    override fun getInt(key: String?, defValue: Int): Int = map[key] as? Int ?: defValue
    override fun getLong(key: String?, defValue: Long): Long = map[key] as? Long ?: defValue
    override fun getFloat(key: String?, defValue: Float): Float = map[key] as? Float ?: defValue
    override fun getBoolean(key: String?, defValue: Boolean): Boolean = map[key] as? Boolean ?: defValue
    override fun contains(key: String?): Boolean = map.containsKey(key)
    override fun edit(): SharedPreferences.Editor = Editor(map)
    override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}
    override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}

    fun putString(key: String, value: String?) { map[key] = value }
    fun putBoolean(key: String, value: Boolean) { map[key] = value }

    class Editor(private val target: MutableMap<String, Any?>) : SharedPreferences.Editor {
        private val temp = mutableMapOf<String, Any?>()
        private var clear = false

        override fun putString(key: String?, value: String?): SharedPreferences.Editor { if (key != null) temp[key] = value; return this }
        override fun putStringSet(key: String?, values: MutableSet<String>?): SharedPreferences.Editor { if (key != null) temp[key] = values; return this }
        override fun putInt(key: String?, value: Int): SharedPreferences.Editor { if (key != null) temp[key] = value; return this }
        override fun putLong(key: String?, value: Long): SharedPreferences.Editor { if (key != null) temp[key] = value; return this }
        override fun putFloat(key: String?, value: Float): SharedPreferences.Editor { if (key != null) temp[key] = value; return this }
        override fun putBoolean(key: String?, value: Boolean): SharedPreferences.Editor { if (key != null) temp[key] = value; return this }
        override fun remove(key: String?): SharedPreferences.Editor { if (key != null) temp[key] = this; return this }
        override fun clear(): SharedPreferences.Editor { clear = true; return this }
        override fun commit(): Boolean { apply(); return true }
        override fun apply() {
            if (clear) target.clear()
            for ((k, v) in temp) {
                if (v === this) target.remove(k) else target[k] = v
            }
        }
    }
}
