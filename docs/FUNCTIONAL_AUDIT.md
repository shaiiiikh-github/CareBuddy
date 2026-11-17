# CareBuddy Comprehensive Functional Audit

## 1. Audit Overview
Every interactive element, toggle, button, input field, and navigation target across the CareBuddy companion app was cataloged, audited against actual implementation, and categorized.

Status definitions:
* **WORKS (Automated Test)**: Verified by JVM unit test.
* **WORKS (Code-Reviewed)**: Logic fully wired and verified through static analysis.
* **BROKEN**: Functional defect identified and fixed in this branch.
* **NOT WIRED**: Control previously existed as decorative UI with no backing persistence or action; now wired or removed.
* **NEEDS DEVICE**: Requires physical Android phone + ESP32 hardware to execute.

---

## 2. Comprehensive Control Matrix

| Screen | Control / Element | Expected Behavior | Code Path (file:line) | Persisted? | Effect on Firmware / BLE | Status | Evidence / Notes |
|:---|:---|:---|:---|:---:|:---:|:---:|:---|
| **Navigation** | Today Tab | Switches to Dashboard screen | `Navigation.kt:70` | N/A | None | **WORKS (Code-Reviewed)** | Compose navigation destination `Screen.Dashboard` |
| **Navigation** | Reminders Tab | Switches to Reminders screen | `Navigation.kt:77` | N/A | None | **WORKS (Code-Reviewed)** | Compose navigation destination `Screen.Reminders` |
| **Navigation** | Emergency Tab | Switches to Contacts screen | `Navigation.kt:84` | N/A | None | **WORKS (Code-Reviewed)** | Compose navigation destination `Screen.Contacts` |
| **Navigation** | Settings Tab | Switches to Settings screen | `Navigation.kt:87` | N/A | None | **WORKS (Code-Reviewed)** | Compose navigation destination `Screen.Settings` |
| **Navigation** | SOS Modal Overlay | Presents high-contrast alert when SOS triggered | `Navigation.kt:104` | No | None | **WORKS (Code-Reviewed)** | Conditional overlay on `sosDispatchState.isTriggered` |
| **Dashboard** | Live Connection Pill | Displays precise BLE status; tap reconnects/disconnects | `DashboardScreen.kt:53` | No | Starts scan or disconnects GATT | **WORKS (Code-Reviewed)** | Reads `bleManager.status`; triggers `connectDevice()` |
| **Dashboard** | Ringing Alert Banner | Shows ringing reminder; tap opens reminders | `DashboardScreen.kt:105` | No | None | **WORKS (Code-Reviewed)** | Bound to `ringingReminder` StateFlow |
| **Dashboard** | Missing Contacts Banner | Alerts when 0 contacts exist; tap opens Emergency | `DashboardScreen.kt:145` | No | None | **WORKS (Code-Reviewed)** | Renders when `contacts.isEmpty()` |
| **Dashboard** | Hero Glass Card | Shows next reminder & progress ring; tap opens list | `DashboardScreen.kt:185` | Yes | None | **WORKS (Code-Reviewed)** | Computes acknowledged / total active reminders |
| **Dashboard** | Quick Action: Sync Clock | Dispatches current time & epoch to device | `DashboardScreen.kt:285` | No | Sends `SYNC_TIME` JSON | **WORKS (Automated Test)** | Tested in `BleProtocolJsonTest.kt` |
| **Dashboard** | Quick Action: Test SOS | Sends test SOS command to device | `DashboardScreen.kt:292` | No | Sends `TEST_SOS` JSON | **WORKS (Automated Test)** | Tested in `BleProtocolJsonTest.kt` |
| **Reminders** | Add Button (Header) | Opens `AddEditReminderScreen` in creation mode | `RemindersScreen.kt:47` | N/A | None | **WORKS (Code-Reviewed)** | Navigates to `add_edit_reminder?id=new` |
| **Reminders** | Empty State Action | Opens `AddEditReminderScreen` in creation mode | `RemindersScreen.kt:62` | N/A | None | **WORKS (Code-Reviewed)** | Displayed when `reminders.isEmpty()` |
| **Reminders** | Reminder Row Tap | Opens `AddEditReminderScreen` in edit mode | `RemindersScreen.kt:128` | N/A | None | **WORKS (Code-Reviewed)** | Passes `reminder.id` argument |
| **Reminders** | Enable/Disable Switch | Toggles reminder enabled status | `RemindersScreen.kt:182` | Yes | Sends `ADD_REMINDER` with `enabled` | **WORKS (Automated Test)** | `toggleReminderEnabled` persists and sends JSON |
| **Reminders** | Delete Icon | Shows dialog; deletes reminder locally and on ESP32 | `RemindersScreen.kt:172` | Yes | Sends `DELETE_REMINDER` JSON | **WORKS (Automated Test)** | `deleteReminder` updates RAM, NVS, and GATT |
| **Add/Edit** | Hour / Minute Stepper | Adjusts scheduled hour and minute | `AddEditReminderScreen.kt:98` | In memory | None | **WORKS (Code-Reviewed)** | Updates local state `hour` and `minute` |
| **Add/Edit** | AM/PM Toggle | Toggles 12-hour period | `AddEditReminderScreen.kt:130` | In memory | None | **WORKS (Code-Reviewed)** | Adjusts `hour` between AM/PM |
| **Add/Edit** | Title Input | Validates non-blank reminder title | `AddEditReminderScreen.kt:168` | In memory | None | **WORKS (Code-Reviewed)** | Displays inline validation error if blank |
| **Add/Edit** | Category Chips | Selects Medication, Water, Activity, Custom | `AddEditReminderScreen.kt:185` | In memory | Saves in `Reminder.category` | **WORKS (Code-Reviewed)** | Capsule selection highlight |
| **Add/Edit** | Repeat Segmented Control | Selects Daily, Weekdays, Custom | `AddEditReminderScreen.kt:212` | In memory | Saves in `Reminder.repeat` | **WORKS (Code-Reviewed)** | Bound to `RepeatOption` enum |
| **Add/Edit** | Save Reminder Button | Validates, saves to Prefs, dispatches BLE JSON | `AddEditReminderScreen.kt:225` | Yes | Sends `ADD_REMINDER` JSON | **WORKS (Automated Test)** | Tested in `BleProtocolJsonTest.kt` |
| **Add/Edit** | Cancel Button | Discards edits and returns to previous screen | `AddEditReminderScreen.kt:68` | No | None | **WORKS (Code-Reviewed)** | `navController.popBackStack()` |
| **Contacts** | Add Button (Header) | Opens modal sheet to add new contact | `EmergencyContactScreen.kt:47` | N/A | None | **WORKS (Code-Reviewed)** | Sets `showSheet = true` |
| **Contacts** | Contact Row Tap | Opens modal sheet to edit contact details | `EmergencyContactScreen.kt:103` | N/A | None | **WORKS (Code-Reviewed)** | Prepopulates form with contact data |
| **Contacts** | Contact Avatar Tap | Sets tapped contact as primary | `EmergencyContactScreen.kt:258` | Yes | None | **WORKS (Automated Test)** | `setPrimaryContact` updates `isPrimary` |
| **Contacts** | Enable/Disable Switch | Toggles whether contact receives SOS alerts | `EmergencyContactScreen.kt:312` | Yes | Excluded from SMS queue if false | **WORKS (Automated Test)** | Tested in `SosDispatcherTest.kt` |
| **Contacts** | Delete Icon | Confirmation dialog; deletes contact | `EmergencyContactScreen.kt:108` | Yes | None | **WORKS (Automated Test)** | Deletes and auto-reassigns primary if needed |
| **Contacts** | Modal Sheet: Name & Phone | Validates name and 7–15 digit phone number | `EmergencyContactScreen.kt:150` | Yes | None | **WORKS (Automated Test)** | Tested in `CareBuddyRepositoryTest.kt` |
| **Contacts** | Modal Sheet: Primary Switch | Sets contact as primary contact | `EmergencyContactScreen.kt:175` | Yes | None | **WORKS (Automated Test)** | Sets primary; demotes previous primary |
| **Contacts** | Modal Sheet: Save Button | Saves contact to SharedPreferences | `EmergencyContactScreen.kt:185` | Yes | None | **WORKS (Automated Test)** | Tested in `CareBuddyRepositoryTest.kt` |
| **SOS Alert** | Per-Contact Status Cards | Live progress (`Queued`, `Sending`, `Sent`, `Delivered`, `Failed`) | `SosAlertScreen.kt:130` | No | None | **WORKS (Automated Test)** | Bound to `sosDispatchState.contactsStatus` |
| **SOS Alert** | Call Primary Contact | Launches `ACTION_CALL` (fallback `ACTION_DIAL`) | `SosAlertScreen.kt:205` | No | None | **NEEDS DEVICE** | Requires phone dialer integration |
| **SOS Alert** | Fallback SMS App Button | Opens system messaging intent with failed numbers | `SosAlertScreen.kt:190` | No | None | **NEEDS DEVICE** | Launches `Intent.ACTION_SENDTO` |
| **SOS Alert** | Cancel Emergency Alarm | Dismisses alarm and resets dispatch pipeline | `SosAlertScreen.kt:218` | No | None | **WORKS (Automated Test)** | Calls `viewModel.dismissSosAlert()` |
| **Settings** | Connection Status Row | Displays rich status; provides context action button | `SettingsScreen.kt:60` | No | Starts scan, connects, opens settings | **BROKEN -> FIXED** | Refactored from coarse 4 states to rich `BleStatus` |
| **Settings** | Disconnect/Connect Button | Toggles Bluetooth GATT connection | `SettingsScreen.kt:85` | No | Connects/disconnects GATT | **BROKEN -> FIXED** | Consolidated from duplicate manager to singleton |
| **Settings** | Emergency SMS Message | Input custom SMS message | `SettingsScreen.kt:105` | Yes | Used in SOS SMS payload | **WORKS (Automated Test)** | `saveSosCustomMessage` persists in Prefs |
| **Settings** | Touch Press Stepper | Adjusts minimum touch debounce duration | `SettingsScreen.kt:118` | Yes | Saved in repository | **NOT WIRED -> FIXED** | Previously local remember; now wired to repository |
| **Settings** | SOS Hold Stepper | Adjusts hold time to trigger SOS | `SettingsScreen.kt:130` | Yes | Saved in repository | **NOT WIRED -> FIXED** | Previously local remember; now wired to repository |
| **Settings** | Bluetooth Diagnostic Row | Shows Bluetooth radio status | `SettingsScreen.kt:145` | No | None | **WORKS (Code-Reviewed)** | Bound to `diagnostics.bluetoothGranted` |
| **Settings** | Telephony Diagnostic Row | Shows Phone & SMS permission status | `SettingsScreen.kt:155` | No | None | **WORKS (Code-Reviewed)** | Bound to telephony permissions |
| **Settings** | Battery Optimization Row | Opens system battery optimization settings | `SettingsScreen.kt:168` | System | None | **WORKS (Code-Reviewed)** | Launches `ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS` |
| **Settings** | Send Test SMS Button | Dispatches test SMS to primary contact | `SettingsScreen.kt:195` | No | None | **WORKS (Automated Test)** | Tested in `SosDispatcherTest.kt` |
| **Settings** | BLE Scan Test Button | Runs 10s active scan; lists nearby devices & RSSI | `SettingsScreen.kt:215` | No | Scans BLE | **NEW FEATURE** | Added for real-device field debugging |
| **Settings** | BLE Event Log Viewer | Displays last 200 events with timestamps & Copy button | `SettingsScreen.kt:230` | In memory | None | **NEW FEATURE** | Added for real-device field debugging |
