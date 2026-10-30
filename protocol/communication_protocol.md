# CareBuddy BLE Communication Protocol

## Overview
CareBuddy uses **Bluetooth Low Energy (BLE)** with the industry-standard **Nordic UART Service (NUS)** to achieve fast, local, zero-cloud communication between the smartphone Android App and the ESP32 Companion Device.

---

## 1. BLE Service & Characteristic Specification

* **Device Advertising Name**: `CareBuddy-XXXX` (where `XXXX` is derived from the ESP32 MAC address)
* **Service UUID**: `6E400001-B5A3-F393-E0A9-E50E24DCCA9E`
* **RX Characteristic (App -> ESP32 Write)**: `6E400002-B5A3-F393-E0A9-E50E24DCCA9E`
* **TX Characteristic (ESP32 -> App Notify)**: `6E400003-B5A3-F393-E0A9-E50E24DCCA9E`

---

## 2. Payload Format
All payloads are encoded as UTF-8 JSON strings over BLE packets.

---

## 3. Smartphone App -> ESP32 Commands

### A. Add / Update Reminder
```json
{
  "type": "ADD_REMINDER",
  "id": "med_01",
  "title": "Morning Medicine",
  "time": "10:00 AM",
  "minutes": 600,
  "enabled": true
}
```

### B. Delete Reminder
```json
{
  "type": "DELETE_REMINDER",
  "id": "med_01"
}
```

### C. Clear All Reminders
```json
{
  "type": "CLEAR_REMINDERS"
}
```

### D. Synchronize Internal Clock
```json
{
  "type": "SYNC_TIME",
  "minutes": 630,
  "epoch": 1700000000
}
```

### E. Trigger Test SOS (Safe Test Mode)
```json
{
  "type": "TEST_SOS"
}
```

---

## 4. ESP32 Firmware -> Smartphone App Notifications

### A. Emergency SOS Alert
```json
{
  "type": "SOS",
  "status": "TRIGGERED"
}
```

### B. Emergency SOS Cancelled
```json
{
  "type": "SOS",
  "status": "CANCELLED"
}
```

### C. Emergency SOS Test Mode
```json
{
  "type": "SOS",
  "status": "TEST"
}
```

### D. Active Reminder Triggered
```json
{
  "type": "REMINDER_TRIGGERED",
  "id": "med_01",
  "title": "Morning Medicine",
  "time": "10:00 AM"
}
```

### E. Reminder Acknowledged (Touch Confirmed)
```json
{
  "type": "REMINDER_ACK",
  "id": "med_01"
}
```
