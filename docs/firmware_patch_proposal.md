# Proposed Firmware Patch for CareBuddy ESP32

## Summary
These proposed non-breaking improvements to `firmware/CareBuddy/CareBuddy.ino` increase BLE reconnect reliability and discovery speed with Android companion devices. Per project constraints, these changes are documented here as a proposal and **not applied to `firmware/`**.

---

## 1. Immediate Advertising Restart on Disconnect

### Issue
In `CareBuddy.ino:276–286`, `onDisconnect` only sets `deviceConnected = false`. Advertising restart is deferred to the non-blocking `loop()` at line 378 (`if (!deviceConnected && oldDeviceConnected)`). If the ESP32 is busy executing long spring calculations or display flushes, advertising restart may lag by up to several hundred milliseconds.

### Proposed Patch
```diff
--- a/firmware/CareBuddy/CareBuddy.ino
+++ b/firmware/CareBuddy/CareBuddy.ino
@@ -282,6 +282,8 @@
     void onDisconnect(BLEServer* pServer) override {
       deviceConnected = false;
       Serial.println(F("BLE: Client Disconnected"));
+      // Immediately restart advertising on GATT disconnect
+      pServer->startAdvertising();
     }
   };
```

---

## 2. Primary Advertising Service UUID Flag

### Issue
In `CareBuddy.ino:318–327`, `deviceBleName` is placed in `oAdvertisementData`, while `SERVICE_UUID` is placed in `oScanResponseData`. Some Android Bluetooth controllers perform passive scans that discard scan responses, failing to match filters expecting `SERVICE_UUID`.

### Proposed Patch
```diff
--- a/firmware/CareBuddy/CareBuddy.ino
+++ b/firmware/CareBuddy/CareBuddy.ino
@@ -320,6 +320,7 @@
   oAdvertisementData.setFlags(0x06); // LE General Discoverable Mode
   oAdvertisementData.setName(deviceBleName);
+  oAdvertisementData.setCompleteServices(BLEUUID(SERVICE_UUID));
   pAdvertising->setAdvertisementData(oAdvertisementData);
```
