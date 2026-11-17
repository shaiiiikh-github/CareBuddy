package com.carebuddy.app.protocol

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonSyntaxException
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class FakeEsp32Test {

    private lateinit var gson: Gson

    @Before
    fun setup() {
        gson = Gson()
    }

    /**
     * Simulated parser matching the message parsing pipeline in CareBuddy.
     */
    private fun parseEsp32Message(payload: String): JsonObject? {
        return try {
            val jsonObject = gson.fromJson(payload, JsonObject::class.java)
            if (jsonObject != null && jsonObject.has("type")) {
                jsonObject
            } else {
                null
            }
        } catch (e: JsonSyntaxException) {
            null
        } catch (e: Exception) {
            null
        }
    }

    @Test
    fun testParseSosTriggeredPayload() {
        val payload = """{"type":"SOS","status":"TRIGGERED"}"""
        val parsed = parseEsp32Message(payload)

        assertNotNull(parsed)
        assertEquals("SOS", parsed?.get("type")?.asString)
        assertEquals("TRIGGERED", parsed?.get("status")?.asString)
    }

    @Test
    fun testParseSosCancelledPayload() {
        val payload = """{"type":"SOS","status":"CANCELLED"}"""
        val parsed = parseEsp32Message(payload)

        assertNotNull(parsed)
        assertEquals("SOS", parsed?.get("type")?.asString)
        assertEquals("CANCELLED", parsed?.get("status")?.asString)
    }

    @Test
    fun testParseSosTestPayload() {
        val payload = """{"type":"SOS","status":"TEST"}"""
        val parsed = parseEsp32Message(payload)

        assertNotNull(parsed)
        assertEquals("SOS", parsed?.get("type")?.asString)
        assertEquals("TEST", parsed?.get("status")?.asString)
    }

    @Test
    fun testParseReminderTriggeredPayload() {
        val payload = """{"type":"REMINDER_TRIGGERED","id":"rem_42","title":"Evening Medication","time":"08:00 PM"}"""
        val parsed = parseEsp32Message(payload)

        assertNotNull(parsed)
        assertEquals("REMINDER_TRIGGERED", parsed?.get("type")?.asString)
        assertEquals("rem_42", parsed?.get("id")?.asString)
        assertEquals("Evening Medication", parsed?.get("title")?.asString)
        assertEquals("08:00 PM", parsed?.get("time")?.asString)
    }

    @Test
    fun testParseReminderAckPayload() {
        val payload = """{"type":"REMINDER_ACK","id":"rem_42"}"""
        val parsed = parseEsp32Message(payload)

        assertNotNull(parsed)
        assertEquals("REMINDER_ACK", parsed?.get("type")?.asString)
        assertEquals("rem_42", parsed?.get("id")?.asString)
    }

    @Test
    fun testMalformedJsonDoesNotCrash() {
        val malformedStrings = listOf(
            "{not a valid json",
            "",
            "   ",
            "SOS_TRIGGERED",
            "{\"type\":",
            "{\"type\":\"SOS\",\"status\":",
            "12345",
            "null",
            "{\"incomplete\": true"
        )

        for (badPayload in malformedStrings) {
            val result = parseEsp32Message(badPayload)
            assertNull("Expected null for payload: '$badPayload'", result)
        }
    }

    @Test
    fun testMissingTypeFieldReturnsNull() {
        val payloadWithoutType = """{"status":"TRIGGERED","device":"CareBuddy"}"""
        val result = parseEsp32Message(payloadWithoutType)
        assertNull(result)
    }

    @Test
    fun testFakeEsp32MessageStreamSimulation() {
        // Simulates a sequence of packets arriving from the ESP32 Nordic UART TX characteristic
        val fakeTransmissionStream = listOf(
            """{"type":"PONG","uptime":42}""",
            """{"type":"REMINDER_TRIGGERED","id":"med_01","title":"Blood Pressure","time":"09:00 AM"}""",
            """{"type":"REMINDER_ACK","id":"med_01"}""",
            """{"type":"SOS","status":"TRIGGERED"}""",
            """corrupted_ble_packet_#123""",
            """{"type":"SOS","status":"CANCELLED"}"""
        )

        val processedEvents = mutableListOf<String>()

        for (packet in fakeTransmissionStream) {
            val parsed = parseEsp32Message(packet)
            if (parsed != null) {
                val type = parsed.get("type")?.asString
                val status = parsed.get("status")?.asString
                val id = parsed.get("id")?.asString
                val summary = when (type) {
                    "SOS" -> "SOS:$status"
                    "REMINDER_TRIGGERED" -> "REM_TRIG:$id"
                    "REMINDER_ACK" -> "REM_ACK:$id"
                    else -> "OTHER:$type"
                }
                processedEvents.add(summary)
            } else {
                processedEvents.add("DROPPED_INVALID")
            }
        }

        assertEquals(6, processedEvents.size)
        assertEquals("OTHER:PONG", processedEvents[0])
        assertEquals("REM_TRIG:med_01", processedEvents[1])
        assertEquals("REM_ACK:med_01", processedEvents[2])
        assertEquals("SOS:TRIGGERED", processedEvents[3])
        assertEquals("DROPPED_INVALID", processedEvents[4])
        assertEquals("SOS:CANCELLED", processedEvents[5])
    }
}
