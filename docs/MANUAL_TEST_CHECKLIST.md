# CareBuddy Manual QA & Verification Checklist

This manual verification protocol must be executed on a physical Android device paired with an active CareBuddy ESP32 companion device prior to production deployment.

---

## 1. Prerequisites
* **Hardware**: Physical Android phone (Android 8.0+ / API 26+) with active carrier SIM card.
* **Peripheral**: ESP32 DevKit running `firmware/CareBuddy/CareBuddy.ino` with SSD1306 OLED, TTP223 touch sensor, and buzzer connected per `docs/wiring.md`.
* **Tooling**: Android Studio Logcat or terminal capturing tags `CareBuddyBle`, `CareBuddyService`, and `CareBuddySOS`.

---

## 2. Test Cases

### TC-01: Fresh Install & Permission Flow
1. Install `CareBuddy-v1.1.0.apk` cleanly (`adb uninstall com.carebuddy.app; adb install CareBuddy-v1.1.0.apk`).
2. Launch the app for the first time.
3. **Expected Result**: System prompts for Bluetooth, Location (Android <= 11), Notifications (Android 13+), Phone, and SMS permissions.
4. Grant all requested permissions.
5. **Expected Result**: Foreground service notification appears in the notification drawer: *"CareBuddy Service • Active"*.

### TC-02: Bluetooth Radio State Transitions (Off -> On)
1. In Android Quick Settings, turn Bluetooth OFF.
2. Open CareBuddy Settings screen.
3. **Expected Result**: Status pill displays *"Bluetooth is off"* with an action button *"Turn on Bluetooth"*.
4. Turn Bluetooth back ON.
5. **Expected Result**: The app detects `ACTION_STATE_CHANGED` immediately, transitions to *"Searching for CareBuddy…"*, finds the device, performs MTU 185 handshake, and transitions to *"Connected"*.

### TC-03: Initial Connection & Handshake Synchronization
1. Power on the ESP32 while the app is running.
2. Observe Logcat (`tag:CareBuddyBle`).
3. **Expected Result**:
   * Scan result matched: `CareBuddy-XXXX`.
   * GATT connected -> MTU 185 negotiated.
   * `CareBuddy Service` discovered -> CCCD descriptor write succeeds.
   * App automatically dispatches `SYNC_TIME` followed by all active `ADD_REMINDER` schedules.
   * ESP32 OLED displays startup animation and plays `BUZZ_STARTUP`.

### TC-04: Hardware Power Cycle & Auto-Reconnect Backoff
1. With the app connected, unplug the ESP32 USB cable.
2. **Expected Result**: App status transitions to *"Device disconnected"*. Auto-reconnect timer initiates with exponential backoff (2s, 4s, 8s, 16s, max 30s).
3. Re-plug the ESP32 USB cable.
4. **Expected Result**: App automatically reconnects within one backoff interval and restores *"Connected"* state without user intervention.

### TC-05: Background Service Survival & App Swipe-Away
1. Connect the app to CareBuddy.
2. Press Home and swipe CareBuddy out of Android Recent Apps (force task-kill).
3. **Expected Result**: Persistent notification remains in the notification shade: *"CareBuddy Service • Connected to CareBuddy-XXXX"*.
4. Trigger an alert on the ESP32.
5. **Expected Result**: Phone receives the BLE notification and rings in the background.

### TC-06: Reminder Add, Trigger, and Acknowledgment Round-Trip
1. In the app, create a reminder set for 1 minute in the future.
2. Tap *"Save Reminder"*.
3. **Expected Result**: Logcat confirms `ADD_REMINDER` sent. ESP32 beeps confirmation.
4. Wait for the clock minute to hit.
5. **Expected Result**:
   * ESP32 OLED switches to `STATE_REMINDER`, buzzer sounds escalating alert.
   * Android notification appears: *"CareBuddy Reminder • [Title]"*.
   * App dashboard displays *"Reminder ringing on CareBuddy"*.
6. Tap the TTP223 capacitive touch sensor on the ESP32.
7. **Expected Result**:
   * ESP32 face transitions to `STATE_ACKNOWLEDGED`, chime plays.
   * ESP32 transmits `{"type":"REMINDER_ACK","id":"..."}`.
   * Android notification dismisses, and the reminder row marks *"Done today"*.

### TC-07: Emergency SOS Multi-Contact Delivery
1. Configure two emergency contacts in the app (both enabled, one primary).
2. On the ESP32, hold the TTP223 sensor for 3 seconds until countdown finishes.
3. **Expected Result**:
   * ESP32 begins repeating Morse code alarm and displays `STATE_SOS_SENT`.
   * ESP32 sends `{"type":"SOS","status":"TRIGGERED"}`.
   * Android phone wakes display (full-screen intent) showing crimson `SosAlertScreen`.
   * First SMS dispatches to primary contact; second SMS dispatches to secondary contact.
   * Live cards show *"Delivered"*.
   * Phone initiates phone call to primary contact.

### TC-08: Settings Persistence Across App Kill
1. In Settings, modify:
   * Minimum touch press: `120 ms`
   * SOS hold countdown: `4 s`
   * Custom emergency SMS text.
2. Force-stop the app via Android Settings -> Apps -> CareBuddy -> Force Stop.
3. Re-open CareBuddy.
4. Navigate to Settings.
5. **Expected Result**: All modified values (`120 ms`, `4 s`, custom SMS text) are restored from `SharedPreferences`.

### TC-09: Diagnostic Log Capture
1. In Settings, scroll to the Diagnostics section.
2. Tap *"Run BLE Scan Test (10s)"*.
3. **Expected Result**: Displays list of detected BLE devices with masked MACs and signal strength (RSSI).
4. Tap *"Copy Event Log"*.
5. Paste into notes or chat.
6. **Expected Result**: Formatted log lines with timestamps showing exact state transitions and GATT status codes.
