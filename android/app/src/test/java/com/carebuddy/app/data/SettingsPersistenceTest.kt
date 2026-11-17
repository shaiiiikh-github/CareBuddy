package com.carebuddy.app.data

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class SettingsPersistenceTest {

    private lateinit var fakePrefs: FakeSharedPreferences
    private lateinit var repository: CareBuddyRepository

    @Before
    fun setup() {
        fakePrefs = FakeSharedPreferences()
        repository = CareBuddyRepository(fakePrefs)
    }

    @Test
    fun testDefaultHardwareSensitivitySettings() {
        // Defaults should match firmware defaults: 80ms touch debounce, 3000ms SOS long press
        assertEquals(80, repository.getTouchMinMs())
        assertEquals(3000, repository.getSosHoldMs())
    }

    @Test
    fun testSaveAndRestoreTouchSensitivity() {
        repository.saveTouchMinMs(120)
        assertEquals(120, repository.getTouchMinMs())

        // Verify direct SharedPreferences content
        assertEquals(120, fakePrefs.getInt(CareBuddyRepository.KEY_TOUCH_MIN_MS, -1))

        // Create a new repository instance pointing to the same SharedPreferences
        val freshRepo = CareBuddyRepository(fakePrefs)
        assertEquals(120, freshRepo.getTouchMinMs())
    }

    @Test
    fun testSaveAndRestoreSosHoldDuration() {
        repository.saveSosHoldMs(4000)
        assertEquals(4000, repository.getSosHoldMs())

        // Verify direct SharedPreferences content
        assertEquals(4000, fakePrefs.getInt(CareBuddyRepository.KEY_SOS_HOLD_MS, -1))

        // Create a new repository instance pointing to the same SharedPreferences
        val freshRepo = CareBuddyRepository(fakePrefs)
        assertEquals(4000, freshRepo.getSosHoldMs())
    }

    @Test
    fun testUpdateBothSettingsPreservesOtherPreferences() {
        repository.saveTouchMinMs(200)
        repository.saveSosHoldMs(5000)
        repository.saveSosMessage("Custom emergency message")

        val freshRepo = CareBuddyRepository(fakePrefs)
        assertEquals(200, freshRepo.getTouchMinMs())
        assertEquals(5000, freshRepo.getSosHoldMs())
        assertEquals("Custom emergency message", freshRepo.getSosMessage())
    }
}
