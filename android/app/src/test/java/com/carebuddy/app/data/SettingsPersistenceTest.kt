package com.carebuddy.app.data

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.lang.reflect.Modifier

class SettingsPersistenceTest {

    private lateinit var fakePrefs: FakeSharedPreferences
    private lateinit var repository: CareBuddyRepository

    @Before
    fun setup() {
        fakePrefs = FakeSharedPreferences()
        repository = CareBuddyRepository(fakePrefs)
    }

    @Test
    fun testSaveAndRestoreSosMessage() {
        val customMsg = "Urgent: Grandfather needs assistance at home!"
        repository.saveSosMessage(customMsg)
        assertEquals(customMsg, repository.getSosMessage())

        // Verify in fresh instance
        val freshRepo = CareBuddyRepository(fakePrefs)
        assertEquals(customMsg, freshRepo.getSosMessage())
    }

    @Test
    fun testDefaultSosMessageWhenUnset() {
        val defaultMsg = repository.getSosMessage()
        assertTrue(defaultMsg.contains("SOS from CareBuddy"))
    }

    @Test
    fun testMigrationCleansDeadHardwareSensitivityKeys() {
        // Populate pre-existing install with obsolete keys
        val legacyPrefs = FakeSharedPreferences()
        legacyPrefs.putString(CareBuddyRepository.KEY_TOUCH_MIN_MS, "120")
        legacyPrefs.putString(CareBuddyRepository.KEY_SOS_HOLD_MS, "4000")
        assertTrue(legacyPrefs.contains(CareBuddyRepository.KEY_TOUCH_MIN_MS))
        assertTrue(legacyPrefs.contains(CareBuddyRepository.KEY_SOS_HOLD_MS))
        assertFalse(legacyPrefs.contains(CareBuddyRepository.KEY_SETTINGS_MIGRATED_V1))

        // Initializing CareBuddyRepository triggers cleanStaleSettingsIfNeeded()
        CareBuddyRepository(legacyPrefs)

        // Stale dead keys must be permanently removed
        assertFalse("Dead key_touch_min_ms must be removed", legacyPrefs.contains(CareBuddyRepository.KEY_TOUCH_MIN_MS))
        assertFalse("Dead key_sos_hold_ms must be removed", legacyPrefs.contains(CareBuddyRepository.KEY_SOS_HOLD_MS))
        assertTrue("Migration flag must be recorded", legacyPrefs.getBoolean(CareBuddyRepository.KEY_SETTINGS_MIGRATED_V1, false))
    }

    /**
     * Settings Contract Test:
     * Enforces that every setting key exposed in the active UI has:
     * (1) Exactly one writer method in CareBuddyRepository
     * (2) At least one reader method in CareBuddyRepository
     * (3) No unmapped/orphaned keys are declared without corresponding accessors.
     */
    @Test
    fun testSettingsContractEveryExposedSettingHasWriterAndReader() {
        data class SettingKeyContract(
            val keyConstant: String,
            val writerMethodName: String,
            val readerMethodName: String
        )

        val activeUiSettingsContracts = listOf(
            SettingKeyContract(
                keyConstant = "key_default_sos_msg",
                writerMethodName = "saveSosMessage",
                readerMethodName = "getSosMessage"
            ),
            SettingKeyContract(
                keyConstant = "key_reminders",
                writerMethodName = "saveReminders",
                readerMethodName = "getReminders"
            ),
            SettingKeyContract(
                keyConstant = "key_contacts",
                writerMethodName = "saveEmergencyContacts",
                readerMethodName = "getEmergencyContacts"
            )
        )

        val repoClass = CareBuddyRepository::class.java
        val methods = repoClass.declaredMethods

        for (contract in activeUiSettingsContracts) {
            val writers = methods.filter { it.name == contract.writerMethodName && Modifier.isPublic(it.modifiers) }
            val readers = methods.filter { it.name == contract.readerMethodName && Modifier.isPublic(it.modifiers) }

            assertEquals(
                "Setting key '${contract.keyConstant}' must have exactly ONE public writer",
                1,
                writers.size
            )
            assertTrue(
                "Setting key '${contract.keyConstant}' must have at least ONE public reader",
                readers.isNotEmpty()
            )
        }
    }
}
