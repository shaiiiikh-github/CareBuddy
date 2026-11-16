package com.carebuddy.app.protocol

import com.google.gson.Gson
import com.google.gson.JsonObject
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class BleProtocolJsonTest {

    private lateinit var gson: Gson

    @Before
    fun setup() {
        gson = Gson()
    }

    @Test
    fun testParseSosTriggered() {
        val json = """{"type":"SOS","status":"TRIGGERED"}"""
        val obj = gson.fromJson(json, JsonObject::class.java)

        assertEquals("SOS", obj.get("type")?.asString)
        assertEquals("TRIGGERED", obj.get("status")?.asString)
    }

    @Test
    fun testParseSosTest() {
        val json = """{"type":"SOS","status":"TEST"}"""
        val obj = gson.fromJson(json, JsonObject::class.java)

        assertEquals("SOS", obj.get("type")?.asString)
        assertEquals("TEST", obj.get("status")?.asString)
    }

    @Test
    fun testParseSosCancelled() {
        val json = """{"type":"SOS","status":"CANCELLED"}"""
        val obj = gson.fromJson(json, JsonObject::class.java)

        assertEquals("SOS", obj.get("type")?.asString)
        assertEquals("CANCELLED", obj.get("status")?.asString)
    }

    @Test
    fun testParseReminderTriggered() {
        val json = """{"type":"REMINDER_TRIGGERED","id":"med_01","title":"Morning Medicine","time":"10:00 AM"}"""
        val obj = gson.fromJson(json, JsonObject::class.java)

        assertEquals("REMINDER_TRIGGERED", obj.get("type")?.asString)
        assertEquals("med_01", obj.get("id")?.asString)
        assertEquals("Morning Medicine", obj.get("title")?.asString)
        assertEquals("10:00 AM", obj.get("time")?.asString)
    }

    @Test
    fun testParseReminderAck() {
        val json = """{"type":"REMINDER_ACK","id":"med_01"}"""
        val obj = gson.fromJson(json, JsonObject::class.java)

        assertEquals("REMINDER_ACK", obj.get("type")?.asString)
        assertEquals("med_01", obj.get("id")?.asString)
    }

    @Test
    fun testAppToEsp32AddReminderJson() {
        val doc = JsonObject().apply {
            addProperty("type", "ADD_REMINDER")
            addProperty("id", "med_01")
            addProperty("title", "Morning Medicine")
            addProperty("time", "10:00 AM")
            addProperty("minutes", 600)
            addProperty("enabled", true)
        }
        val serialized = gson.toJson(doc)

        assertTrue(serialized.contains("\"type\":\"ADD_REMINDER\""))
        assertTrue(serialized.contains("\"minutes\":600"))
        assertTrue(serialized.contains("\"enabled\":true"))
    }

    @Test
    fun testAppToEsp32SyncTimeJson() {
        val doc = JsonObject().apply {
            addProperty("type", "SYNC_TIME")
            addProperty("minutes", 630)
            addProperty("epoch", 1700000000L)
        }
        val serialized = gson.toJson(doc)

        assertTrue(serialized.contains("\"type\":\"SYNC_TIME\""))
        assertTrue(serialized.contains("\"minutes\":630"))
        assertTrue(serialized.contains("\"epoch\":1700000000"))
    }
}
