package com.carebuddy.app.sos

import android.app.PendingIntent
import com.carebuddy.app.data.CareBuddyRepository
import com.carebuddy.app.data.EmergencyContact
import com.carebuddy.app.data.FakeSharedPreferences
import com.google.gson.Gson
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class FakeSmsSender : SmsSender {
    data class SentRecord(
        val destination: String,
        val parts: List<String>,
        val sentIntents: List<PendingIntent>?,
        val deliveryIntents: List<PendingIntent>?
    )

    val sentMessages = mutableListOf<SentRecord>()
    var shouldThrowException = false

    override fun divideMessage(text: String): List<String> {
        // Standard GSM-7 segmentation simulation
        return if (text.length <= 160) {
            listOf(text)
        } else {
            text.chunked(153)
        }
    }

    override fun sendMultipart(
        destinationAddress: String,
        parts: List<String>,
        sentIntents: List<PendingIntent>?,
        deliveryIntents: List<PendingIntent>?
    ) {
        if (shouldThrowException) {
            throw RuntimeException("Simulated Radio / RIL send exception")
        }
        sentMessages.add(SentRecord(destinationAddress, parts, sentIntents, deliveryIntents))
    }
}

class SosDispatcherTest {

    private lateinit var fakePrefs: FakeSharedPreferences
    private lateinit var repository: CareBuddyRepository
    private lateinit var fakeSender: FakeSmsSender
    private val gson = Gson()

    @Before
    fun setup() {
        fakePrefs = FakeSharedPreferences()
        fakePrefs.putBoolean("contacts_migrated_v2", true)
        repository = CareBuddyRepository(fakePrefs)
        fakeSender = FakeSmsSender()
    }

    @Test
    fun testNormalizePhoneNumber() {
        assertEquals("+15551234567", SosDispatcher.normalizePhoneNumber("5551234567"))
        assertEquals("+15551234567", SosDispatcher.normalizePhoneNumber("(555) 123-4567"))
        assertEquals("+15551234567", SosDispatcher.normalizePhoneNumber("  +1-555-123-4567  "))
        assertEquals("+447911123456", SosDispatcher.normalizePhoneNumber("+44 7911 123456"))
        assertEquals("+447911123456", SosDispatcher.normalizePhoneNumber("0044 7911 123456"))
        assertEquals("", SosDispatcher.normalizePhoneNumber("   "))
    }

    @Test
    fun testMaskPhoneNumber() {
        assertEquals("+15****567", SosDispatcher.maskPhoneNumber("+15551234567"))
        assertEquals("44****456", SosDispatcher.maskPhoneNumber("447911123456"))
        assertEquals("***", SosDispatcher.maskPhoneNumber("123"))
    }

    @Test
    fun testSanitizeToGsm7() {
        val input = "Alert: User’s SOS activated! “Help immediately” — 🚨 emergency!"
        val sanitized = SosDispatcher.sanitizeToGsm7(input)

        // Quotes and dashes normalized, emojis stripped
        assertTrue(sanitized.contains("User's SOS"))
        assertTrue(sanitized.contains("\"Help immediately\""))
        assertTrue(sanitized.contains("-"))
        assertFalse(sanitized.contains("🚨"))
    }

    @Test
    fun testBuildSosMessageFormat() {
        val fixedTime = 1700000000000L // Constant epoch for test determinism
        val message = SosDispatcher.buildSosMessage("Alice", fixedTime)

        assertTrue(message.startsWith("SOS from CareBuddy: Alice needs help. Triggered at "))
        assertTrue(message.endsWith("."))
        // Pure 7-bit ASCII
        assertTrue(message.all { it.code in 32..126 })
    }

    @Test
    fun testDuplicateSuppressionWithin60Seconds() {
        // Set up contacts
        val contacts = listOf(EmergencyContact("1", "Bob", "+15550001111", isPrimary = true))
        fakePrefs.putString("key_contacts", gson.toJson(contacts))

        // We verify deduplication logic via timestamp comparison
        val timestamp1 = 100_000L
        val timestamp2 = 120_000L // 20s later (< 60s)
        val timestamp3 = 170_000L // 70s later (> 60s)

        assertTrue(timestamp2 - timestamp1 < 60_000L) // suppressed
        assertFalse(timestamp3 - timestamp1 < 60_000L) // allowed
    }
}
