package com.carebuddy.app.ble

import org.junit.Assert.*
import org.junit.Test

class CareBuddyBleStatusTest {

    @Test
    fun testBleStatusLabelAndActionMapping() {
        // Idle
        assertEquals("Idle", BleStatus.Idle.label)
        assertEquals("Connect", BleStatus.Idle.actionLabel)

        // BluetoothOff
        assertEquals("Bluetooth is off", BleStatus.BluetoothOff.label)
        assertEquals("Turn on Bluetooth", BleStatus.BluetoothOff.actionLabel)

        // PermissionNeeded
        val permStatus = BleStatus.PermissionNeeded("android.permission.BLUETOOTH_CONNECT")
        assertEquals("Bluetooth permission required", permStatus.label)
        assertEquals("Grant Permission", permStatus.actionLabel)
        assertEquals("android.permission.BLUETOOTH_CONNECT", permStatus.missingPermission)

        // LocationOff
        assertEquals("Location services required", BleStatus.LocationOff.label)
        assertEquals("Enable Location", BleStatus.LocationOff.actionLabel)

        // Scanning
        assertEquals("Searching for CareBuddy…", BleStatus.Scanning.label)
        assertEquals("Stop", BleStatus.Scanning.actionLabel)

        // DeviceNotFound
        assertEquals("CareBuddy not found. Is it nearby?", BleStatus.DeviceNotFound.label)
        assertEquals("Retry Scan", BleStatus.DeviceNotFound.actionLabel)

        // Connecting
        assertEquals("Connecting to CareBuddy…", BleStatus.Connecting.label)
        assertEquals("Cancel", BleStatus.Connecting.actionLabel)

        // SettingUp
        val setupMtu = BleStatus.SettingUp("negotiating MTU")
        assertEquals("Setting up (negotiating MTU)…", setupMtu.label)
        assertEquals("Cancel", setupMtu.actionLabel)
        assertEquals("negotiating MTU", setupMtu.stage)

        val setupDiscovery = BleStatus.SettingUp("discovering services")
        assertEquals("Setting up (discovering services)…", setupDiscovery.label)

        val setupCccd = BleStatus.SettingUp("enabling notifications")
        assertEquals("Setting up (enabling notifications)…", setupCccd.label)

        // Connected
        assertEquals("Connected", BleStatus.Connected.label)
        assertEquals("Disconnect", BleStatus.Connected.actionLabel)

        // Disconnected
        val discWithReason = BleStatus.Disconnected("Connection timed out")
        assertEquals("Connection timed out", discWithReason.label)
        assertEquals("Connect", discWithReason.actionLabel)

        val discDefault = BleStatus.Disconnected(null)
        assertEquals("Disconnected", discDefault.label)
        assertEquals("Connect", discDefault.actionLabel)
    }

    @Test
    fun testWatchdogTimeouts() {
        assertEquals(12_000L, CareBuddyBleManager.TIMEOUT_SCAN_MS)
        assertEquals(15_000L, CareBuddyBleManager.TIMEOUT_CONNECT_MS)
        assertEquals(2_000L, CareBuddyBleManager.TIMEOUT_MTU_MS)
        assertEquals(10_000L, CareBuddyBleManager.TIMEOUT_SERVICE_DISCOVERY_MS)
        assertEquals(5_000L, CareBuddyBleManager.TIMEOUT_CCCD_WRITE_MS)
    }

    @Test
    fun testExponentialBackoffDelays() {
        // Attempt 0: 2s
        assertEquals(2_000L, CareBuddyBleManager.getBackoffDelay(0))
        // Attempt 1: 4s
        assertEquals(4_000L, CareBuddyBleManager.getBackoffDelay(1))
        // Attempt 2: 8s
        assertEquals(8_000L, CareBuddyBleManager.getBackoffDelay(2))
        // Attempt 3: 16s
        assertEquals(16_000L, CareBuddyBleManager.getBackoffDelay(3))
        // Attempt 4: 30s (capped)
        assertEquals(30_000L, CareBuddyBleManager.getBackoffDelay(4))
        // Attempt 5+: still capped at 30s
        assertEquals(30_000L, CareBuddyBleManager.getBackoffDelay(5))
        assertEquals(30_000L, CareBuddyBleManager.getBackoffDelay(10))
    }

    @Test
    fun testSingletonReset() {
        CareBuddyBleManager.resetInstanceForTesting()
        // Ensure reset does not throw and allows clean state
        assertTrue(true)
    }
}
