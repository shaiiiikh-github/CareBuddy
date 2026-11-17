# CareBuddy Dead UI & Functional Integrity Audit

**Audit Date**: 17 November 2026  
**Auditor**: Senior Android Engineer & QA Lead  
**Scope**: Full scan of all UI screens, view models, repositories, permissions, manifest declarations, resources, and navigation routes.

---

## 1. Audit Summary & Counts

* **Total Components Audited**: 53 (9 audit candidates + 44 live features)
* **Items Removed**: **4** (Dead hardware touch debounce stepper, dead SOS hold stepper, unused `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` permission, redundant activity label)
* **Items Wired / Fixed**: **5** (`BuildConfig` dynamic versioning, telephony `<uses-feature>`, receiver export flag, obsolete SDK check removal, static context leak annotation)
* **Items Relabeled**: **0**
* **Items Kept (Verified Alive)**: **44**
* **Android Lint Result**:
  * **Errors**: **0** (Reduced from 3 to 0)
  * **Warnings**: **31** (Reduced from 35 to 31)

---

## 2. Audit Findings Matrix

| Item | Screen / File:Line | Kind | Why Dead or Fake (Evidence) | Verdict | Reason & Action Taken |
|:---|:---|:---:|:---|:---:|:---|
| **Minimum Touch Press Stepper** | `SettingsScreen.kt:163`<br>`MainViewModel.kt:49,278`<br>`CareBuddyRepository.kt:126` | Setting & Control | Value persisted locally in `key_touch_min_ms`, but NEVER transmitted to ESP32. Firmware defines hardcoded `TOUCH_DEBOUNCE_MS 80` (`CareBuddy.ino:23`). No BLE protocol message exists to modify debounce. | **REMOVE** | Misleading device control. Removed from UI, removed unused ViewModel methods, cleaned persisted keys, and documented in `docs/future_work.md` with proposed `SET_CONFIG` BLE command. |
| **SOS Hold Countdown Stepper** | `SettingsScreen.kt:198`<br>`MainViewModel.kt:50,283`<br>`CareBuddyRepository.kt:129` | Setting & Control | Value persisted locally in `key_sos_hold_ms`, but NEVER transmitted to ESP32. Firmware defines hardcoded `HOLD_TIME 3000` (`CareBuddy.ino:24`). No BLE protocol message exists to modify hold duration. | **REMOVE** | Misleading device control. Removed from UI, removed unused ViewModel methods, cleaned persisted keys, and documented in `docs/future_work.md` with proposed `SET_CONFIG` BLE command. |
| **Application Version String** | `SettingsScreen.kt:473,479` | Value | Hardcoded string literals `"1.1.0 (Build 2) • Release"` and `"v1.1.0"`. | **WIRE** | Wired dynamically to `BuildConfig.VERSION_NAME` and `BuildConfig.VERSION_CODE`. |
| **Battery Optimization Permission** | `AndroidManifest.xml:24` | Permission | `<uses-permission android:name="android.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS" />` declared but never used. App uses standard `ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS` intent and `isIgnoringBatteryOptimizations` query, neither of which requires this permission. | **REMOVE** | Dead permission triggering Google Play review warnings. Removed from manifest. |
| **Redundant Activity Label** | `AndroidManifest.xml:55` | Code / Manifest | `android:label="CareBuddy"` duplicated on `MainActivity` while `<application>` already declares the identical label. | **REMOVE** | Redundant manifest attribute. Removed to resolve Lint warning. |
| **Missing Telephony Feature Tags** | `AndroidManifest.xml:14,15` | Code / Manifest | `CALL_PHONE` and `SEND_SMS` permissions imply telephony hardware requirement on Chromebooks/tablets without explicit `<uses-feature>` flags. | **WIRE** | Added `<uses-feature android:name="android.hardware.telephony" android:required="false" />` to fix Lint Error. |
| **Unspecified Broadcast Receiver Export Flag** | `SosDispatcher.kt:223` | Code | `context.registerReceiver(smsReceiver, filter)` registered without `RECEIVER_NOT_EXPORTED` flag on API 34, causing Lint Error and runtime exception on Android 14. | **WIRE** | Switched to `ContextCompat.registerReceiver(context, smsReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)`. |
| **Obsolete SDK Check in BleManager** | `CareBuddyBleManager.kt:368` | Code | `if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)` redundant because project `minSdk` is 26 (Android 8.0 Oreo). | **REMOVE** | Simplified to direct `connectGatt` call with `TRANSPORT_LE`. |
| **Static Context Reference Warning** | `CareBuddyBleManager.kt:81` | Code | Lint flags companion `INSTANCE` holding context reference. Manager safely stores `context.applicationContext`. | **WIRE** | Annotated with `@SuppressLint("StaticFieldLeak")` and verified `applicationContext` usage. |

---

## 2. KEEP List & Proof Chains (Verified Alive)

Every control, value, and action below has a verified, end-to-end chain from UI to repository, phone hardware, or BLE GATT peripheral:

### 2.1 Today / Dashboard Screen
1. **Live Connection Pill** (`DashboardScreen.kt:100`):
   - *Proof*: Observes `MainViewModel.bleStatus` -> bound to `CareBuddyBleManager.status`. Tap executes `viewModel.connectDevice()` or `viewModel.disconnectDevice()`. Pulses on transitional states (`Connecting`, `SettingUp`, `Scanning`).
2. **Ringing Reminder Banner** (`DashboardScreen.kt:115`):
   - *Proof*: Only displays when `MainViewModel.ringingReminder` is non-null, fed by incoming `REMINDER_TRIGGERED` BLE notification from ESP32. Tap navigates to `Screen.Reminders`.
3. **Missing Emergency Contact Warning** (`DashboardScreen.kt:156`):
   - *Proof*: Conditionally shown when `contactsCount == 0`. Tap navigates to `Screen.Contacts`.
4. **Hero Glass Card (Next Reminder & Daily Progress)** (`DashboardScreen.kt:197`):
   - *Proof*: Dynamically computes next pending reminder and progress ring (`acknowledgedCount / totalCount`). Tap navigates to `Screen.Reminders`.
5. **Assistant Overview List** (`DashboardScreen.kt:276,291`):
   - *Proof*: "Emergency contact" row displays real primary contact name and count; "Scheduled reminders" row displays active schedules and pending count. Tap navigates to respective destinations.
6. **Sync Clock Quick Action** (`DashboardScreen.kt:308`):
   - *Proof*: Calls `MainViewModel.syncTimeToDevice()`, constructing `{"type":"SYNC_TIME","minutes":...,"epoch":...}` and writing to Nordic UART RX characteristic.
7. **Test SOS Quick Action** (`DashboardScreen.kt:316`):
   - *Proof*: Calls `MainViewModel.testSosFromApp()`, triggering test mode in `SosDispatcher` (no SMS/calls) and transmitting `{"type":"TEST_SOS"}` to ESP32.

### 2.2 Reminders Screen
8. **Add Reminder Header Button** (`RemindersScreen.kt:56`):
   - *Proof*: Navigates to `add_edit_reminder?id=new`.
9. **Empty State Action** (`RemindersScreen.kt:70`):
   - *Proof*: Displayed when reminder list is empty. Tap navigates to `add_edit_reminder?id=new`.
10. **Reminder Row Item Tap** (`RemindersScreen.kt:129`):
    - *Proof*: Navigates to `add_edit_reminder?id={reminder.id}` in edit mode.
11. **Reminder Enable/Disable Switch** (`RemindersScreen.kt:201`):
    - *Proof*: Calls `MainViewModel.toggleReminderEnabled()`, which updates `CareBuddyRepository` and sends updated `ADD_REMINDER` JSON with `enabled` state over BLE.
12. **Reminder Delete Button** (`RemindersScreen.kt:186`):
    - *Proof*: Prompts confirmation dialog, then calls `MainViewModel.deleteReminder()`, removing from SharedPreferences and sending `DELETE_REMINDER` JSON over BLE.

### 2.3 Add / Edit Reminder Screen
13. **Hour & Minute Steppers** (`AddEditReminderScreen.kt:104,111`):
    - *Proof*: Modifies local state `hour` (0..23) and `minute` (0..59 in 5m steps).
14. **AM/PM Period Toggle** (`AddEditReminderScreen.kt:133`):
    - *Proof*: Adjusts hour between 12-hour periods.
15. **Title Text Input** (`AddEditReminderScreen.kt:167`):
    - *Proof*: Validates non-blank string with inline error message.
16. **Category Capsule Chips** (`AddEditReminderScreen.kt:184`):
    - *Proof*: Updates category string (`Medication`, `Water`, `Activity`, `Custom`).
17. **Repeat Schedule Segmented Control** (`AddEditReminderScreen.kt:209`):
    - *Proof*: Updates `RepeatOption` enum (`DAILY`, `WEEKDAYS`, `CUSTOM`).
18. **Cancel Button** (`AddEditReminderScreen.kt:66`):
    - *Proof*: Pops Compose navigation back-stack.
19. **Save / Create Reminder Button** (`AddEditReminderScreen.kt:220`):
    - *Proof*: Constructs `Reminder` model, persists via `CareBuddyRepository.saveReminders()`, and dispatches `ADD_REMINDER` JSON payload over BLE.

### 2.4 Emergency Contacts Screen
20. **Add Contact Header Button** (`EmergencyContactScreen.kt:54`):
    - *Proof*: Opens modal sheet in creation mode.
21. **Empty State Action** (`EmergencyContactScreen.kt:74`):
    - *Proof*: Displayed when contacts list is empty. Opens modal sheet.
22. **Contact Row Tap** (`EmergencyContactScreen.kt:248`):
    - *Proof*: Opens modal sheet in edit mode populated with contact fields.
23. **Avatar Primary Toggle** (`EmergencyContactScreen.kt:258`):
    - *Proof*: Promotes tapped contact to `isPrimary = true` and demotes others.
24. **Contact Enabled Switch** (`EmergencyContactScreen.kt:318`):
    - *Proof*: Toggles `enabled` boolean in `CareBuddyRepository`. Excluded from SMS queue when false.
25. **Contact Delete Button** (`EmergencyContactScreen.kt:303`):
    - *Proof*: Prompts confirmation dialog, then deletes contact and auto-promotes first contact to primary if needed.
26. **Modal Sheet Name & Phone Input** (`EmergencyContactScreen.kt:144,154`):
    - *Proof*: Validates phone number strictly for 7–15 digits.
27. **Modal Sheet Primary Switch** (`EmergencyContactScreen.kt:174`):
    - *Proof*: Sets contact as primary recipient.
28. **Modal Sheet Save Button** (`EmergencyContactScreen.kt:185`):
    - *Proof*: Commits contact to SharedPreferences via `CareBuddyRepository.saveEmergencyContacts()`.

### 2.5 SOS Alert Modal Screen
29. **Trigger Guard** (`SosAlertScreen.kt:37`):
    - *Proof*: Modal only renders when `sosDispatchState.isTriggered` is true.
30. **Per-Contact Live Delivery Status** (`SosAlertScreen.kt:139`):
    - *Proof*: Bound to `sosDispatchState.contactsStatus`, rendering real-time states (`Queued`, `Sending`, `Sent`, `Delivered`, `Failed`) with error descriptions.
31. **Direct Phone Call Button** (`SosAlertScreen.kt:215`):
    - *Proof*: Calls `MainViewModel.initiatePhoneCall()`, executing system `ACTION_CALL` intent with `ACTION_DIAL` fallback.
32. **Manual Messaging App Fallback** (`SosAlertScreen.kt:197`):
    - *Proof*: Appears if any SMS fails; opens system SMS chooser via `ACTION_SENDTO` prepopulated with emergency message.
33. **Dismiss / Cancel Button** (`SosAlertScreen.kt:226`):
    - *Proof*: Calls `MainViewModel.dismissSosAlert()`, terminating alarm and resetting dispatch pipeline.

### 2.6 Settings Screen
34. **BLE Link Status Card** (`SettingsScreen.kt:77`):
    - *Proof*: Bound to `MainViewModel.bleStatus`.
35. **Connect / Disconnect Action** (`SettingsScreen.kt:112`):
    - *Proof*: Resolves current state: disconnects active link, or launches Bluetooth Settings / Location Settings / App Permissions if prerequisites are missing.
36. **Emergency SMS Custom Text** (`SettingsScreen.kt:149`):
    - *Proof*: Persists in SharedPreferences (`key_default_sos_msg`) and is used as message body in `SosDispatcher.kt`.
37. **Bluetooth Radio Diagnostic Row** (`SettingsScreen.kt:238`):
    - *Proof*: Displays radio state from `BluetoothAdapter`.
38. **Telephony Permissions Diagnostic Row** (`SettingsScreen.kt:251`):
    - *Proof*: Checks runtime permissions for `CALL_PHONE` and `SEND_SMS`.
39. **Battery Optimization Diagnostic Row** (`SettingsScreen.kt:265`):
    - *Proof*: Checks `PowerManager.isIgnoringBatteryOptimizations()`. Tap opens `Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS`.
40. **Last SOS Result Diagnostic Row** (`SettingsScreen.kt:285`):
    - *Proof*: Displays timestamp and outcome of most recent SOS trigger.
41. **Test SMS Action** (`SettingsScreen.kt:298`):
    - *Proof*: Dispatches single test SMS to primary contact and records result.
42. **Scan Test Diagnostic Tool** (`SettingsScreen.kt:316`):
    - *Proof*: Runs 10s BLE scan and renders discovered devices with RSSI and CareBuddy match pills.
43. **BLE Event Log Viewer** (`SettingsScreen.kt:387`):
    - *Proof*: Displays up to 200 circular buffer log entries with timestamp, severity, and clipboard copy action.
44. **About App Version** (`SettingsScreen.kt:471`):
    - *Proof*: Displays actual dynamic version from `BuildConfig.VERSION_NAME` and `BuildConfig.VERSION_CODE`.
