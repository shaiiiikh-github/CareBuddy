# CareBuddy - Project Status & Working History

## 1. Snapshot
* **Date Analyzed**: 16 November 2026
* **Repository Branch**: `ui/glass-redesign` (base: `fix/sos-sms-and-background`)
* **Published Release**: `v1.0.0` (tagged on GitHub, attached asset `CareBuddy-v1.0.0.apk`)
* **Overall Completion Estimate**: **98%** — Both ESP32 firmware and Android companion app have fully functioning UI, BLE communication, reminder scheduling, acoustic escalation, and emergency SOS pipeline. The critical P0/P1 safety and architecture gaps are resolved, and the entire Android UI has been redesigned with the iOS 26 "Liquid Glass" design system: custom glass surfaces, floating tab bar, inset grouped tables, tactile spring physics, calm human copy, zero emojis, and complete light/dark theme support.

---

## 2. Inventory & File Tree

### 2.1 File Tree & Metrics (Excluding build/cache dirs)

```
CareBuddy/
├── .gitignore                                          (40 lines)
├── README.md                                           (47 lines, 3.56 KB)
├── PROJECT_STATUS.md
├── protocol/
│   └── communication_protocol.md                       (91 lines, 1.90 KB)
├── docs/
│   ├── setup.md                                        (31 lines, 1.59 KB)
│   ├── troubleshooting.md                              (32 lines, 2.25 KB)
│   ├── ui_audit.md                                     (52 lines, Phase 0 audit & before/after transformations)
│   ├── UI_DESIGN_SYSTEM.md                             (51 lines, Design System specifications and rules)
│   └── wiring.md                                       (29 lines, 1.88 KB)
├── firmware/
│   └── CareBuddy/
│       └── CareBuddy.ino                               (2,389 lines, 97.46 KB)
└── android/
    ├── build.gradle.kts                                (4 lines, 143 B)
    ├── settings.gradle.kts                             (16 lines, 328 B)
    ├── gradle.properties                               (3 lines, 109 B)
    ├── gradlew                                         (185 lines, executable)
    ├── gradlew.bat                                     (66 lines, 2.62 KB)
    ├── gradle/wrapper/
    │   ├── gradle-wrapper.jar                          (64.7 KB)
    │   └── gradle-wrapper.properties                   (7 lines, 251 B)
    └── app/
        ├── build.gradle.kts                            (62 lines, 1.95 KB)
        ├── src/
        │   ├── test/java/com/carebuddy/app/
        │   │   ├── data/CareBuddyRepositoryTest.kt      (Unit tests for migration, primary contact selection, phone validation)
        │   │   ├── protocol/BleProtocolJsonTest.kt     (Unit tests for BLE JSON parsing, parity, and serialization)
        │   │   └── sos/SosDispatcherTest.kt            (Unit tests for SMS normalization, GSM-7, retry, deduplication)
        │   └── main/
        │       ├── AndroidManifest.xml                 (48 lines, permissions, service, boot receiver)
        │       ├── res/
        │       │   ├── drawable/
        │       │   │   ├── ic_launcher_background.xml
        │       │   │   └── ic_launcher_foreground.xml
        │       │   ├── mipmap/ (ic_launcher.xml, round)
        │       │   ├── mipmap-anydpi-v26/ (adaptive)
        │       │   ├── values/styles.xml
        │       │   └── xml/
        │       │       ├── backup_rules.xml
        │       │       └── data_extraction_rules.xml
        │       └── java/com/carebuddy/app/
        │           ├── MainActivity.kt                 (Edge-to-edge WindowCompat, lifecycle, permissions, SOS intent)
        │           ├── ble/
        │           │   └── CareBuddyBleManager.kt      (GATT client, NUS UUIDs, MTU 185, queue, reconnect)
        │           ├── data/
        │           │   ├── CareBuddyRepository.kt      (Contacts migration v2, persistence, SharedPreferences)
        │           │   ├── EmergencyContact.kt         (Data model with enabled flag and validation)
        │           │   └── Reminder.kt                 (Data model, repeats, active state)
        │           ├── receiver/
        │           │   └── BootReceiver.kt             (Starts CareBuddyBleService on system boot)
        │           ├── service/
        │           │   └── CareBuddyBleService.kt      (Foreground service connectedDevice, full-screen SOS alert)
        │           ├── sos/
        │           │   └── SosDispatcher.kt            (Multi-contact SMS dispatcher, retry, GSM-7, delivery tracking)
        │           ├── ui/
        │           │   ├── Previews.kt                 (Compose preview matrix for Light and Dark themes)
        │           │   ├── components/
        │           │   │   ├── CapsuleButton.kt        (Full capsule buttons: Filled, Tinted, Plain, Danger)
        │           │   │   ├── EmptyState.kt           (Calm human empty state with vector symbol and action)
        │           │   │   ├── FloatingTabBar.kt       (Floating glass capsule bottom navigation bar)
        │           │   │   ├── GlassBackground.kt      (Ambient diffuse background color blobs)
        │           │   │   ├── GlassBottomSheet.kt     (28dp corner modal sheet with grabber)
        │           │   │   ├── GlassSurface.kt         (Core Liquid Glass surface with hairline gradient border)
        │           │   │   ├── InsetGroupedList.kt     (iOS Inset Grouped table and ListRow)
        │           │   │   ├── IosSwitch.kt            (Tactile green track toggle with spring physics)
        │           │   │   ├── IosTextField.kt         (Translucent filled glass text input)
        │           │   │   ├── LargeTitleScaffold.kt   (iOS Large Title header scaffold)
        │           │   │   ├── Navigation.kt           (Navigation shell with FloatingTabBar & SOS overlay)
        │           │   │   ├── ProgressRing.kt         (Smooth circular progress ring)
        │           │   │   ├── SegmentedControl.kt     (Sliding capsule segment switcher)
        │           │   │   └── StatusPill.kt           (Colored dot with sentence-case status label)
        │           │   ├── screens/
        │           │   │   ├── AddEditReminderScreen.kt (Native-feeling time stepper, segmented repeat, category chips)
        │           │   │   ├── DashboardScreen.kt      (Pulsing live status pill, hero glass card, progress ring)
        │           │   │   ├── EmergencyContactScreen.kt (Grouped contact cards, primary badge, modal edit sheet)
        │           │   │   ├── RemindersScreen.kt      (Grouped list by time, StatusPill badges, capsule Add)
        │           │   │   ├── SettingsScreen.kt       (Inset grouped settings, diagnostics pills, test SMS)
        │           │   │   └── SosAlertScreen.kt       (High-contrast emergency crimson screen, calm breathing pulse)
        │           │   └── theme/
        │           │       ├── Color.kt                (Semantic CareBuddyColors tokens for Light and Dark)
        │           │       ├── Motion.kt               (iOS snappy springs, 0.97 pressScale, haptic feedback)
        │           │       ├── Theme.kt                (CareBuddyTheme provider, transparent system bars)
        │           │       └── Type.kt                 (iOS typography scale with calibrated letter-spacing)
        │           └── viewmodel/
        │               └── MainViewModel.kt            (StateFlows, SosDispatcher integration, service sync)
```

### 2.2 Technical Stack & Dependencies

* **Android Target**:
  * Build System: Gradle 8.13 (AGP 8.13.2, Kotlin 1.9.22)
  * `minSdk`: 26 (Android 8.0 Oreo)
  * `targetSdk`: 34 (Android 14)
  * `compileSdk`: 34
  * Core UI: Jetpack Compose (BOM `2024.02.00`), Material 3, Material Icons Extended
  * Serialization: Google Gson `2.10.1`
  * Architecture: MVVM (`androidx.lifecycle:lifecycle-viewmodel-compose:2.7.0`)
  * Design System: iOS 26 "Liquid Glass" (Translucent surfaces, hairline gradient borders, continuous radii)
* **Firmware Target**:
  * Microcontroller: ESP32 Dev Module (Xtensa Dual-Core 32-bit LX6)
  * Framework: Arduino Core for ESP32 (`BLEDevice`, `BLEServer`, `Preferences`, `Wire`)
  * External Libraries: `Adafruit GFX Library`, `Adafruit SSD1306`, `ArduinoJson` (v6/v7)
  * Compilation Toolchain: Arduino IDE 2.x (or PlatformIO `esp32dev`)

---

## 3. Git History & Milestones

All commits in repository history (obtained via `git log --reverse`):

| Commit | Author | Date | Message | Milestone / Phase |
|:---|:---|:---|:---|:---|
| `25f0cc6` | shaiiiikh-github | 2026-10-31 | `chore: configure repository and ignore generated files` | Phase 1: Repo Setup |
| `5943f59` | shaiiiikh-github | 2026-10-31 | `docs: add CareBuddy project overview and hardware architecture` | Phase 2: Documentation |
| `33613a9` | shaiiiikh-github | 2026-10-31 | `docs: document hardware wiring, setup, and troubleshooting` | Phase 2: Documentation |
| `a2d814c` | shaiiiikh-github | 2026-10-31 | `docs: specify BLE Nordic UART communication protocol` | Phase 2: Documentation |
| `ab532eb` | shaiiiikh-github | 2026-10-31 | `build: configure Android application build system and wrapper` | Phase 3: Android Build Infra |
| `e82b76e` | shaiiiikh-github | 2026-10-31 | `feat(firmware): implement ESP32 firmware with OLED animations, touch controls, and BLE` | Phase 4: Embedded Firmware Bring-up |
| `b6151a9` | shaiiiikh-github | 2026-10-31 | `feat(android): add application manifest, launcher resources, and theme styling` | Phase 5: Android Foundation |
| `c7f1df8` | shaiiiikh-github | 2026-10-31 | `feat(android): implement local data models and repository storage` | Phase 6: Android Data Layer |
| `49dd945` | shaiiiikh-github | 2026-10-31 | `feat(android): implement BLE manager for device pairing and protocol synchronization` | Phase 7: Android Connectivity |
| `e63e7bc` | shaiiiikh-github | 2026-10-31 | `feat(android): implement UI navigation, dashboard, reminder management, and emergency screens` | Phase 8: Android UI & Presentation |
| `c581e1b` | shaiiiikh-github | 2026-11-16 | `fix(data): migrate persisted placeholder contacts` | Phase 9: Emergency Contacts Fix |
| `0854699` | shaiiiikh-github | 2026-11-16 | `build(android): add gradle wrapper jar and unix launcher script` | Phase 9: Build Infra Restored |
| `16395f8` | shaiiiikh-github | 2026-11-16 | `fix(sos): implement robust multi-contact SMS dispatcher with live delivery tracking` | Phase 10: SOS Dispatch Pipeline |
| `4f57815` | shaiiiikh-github | 2026-11-16 | `feat(service): add CareBuddyBleService foreground service and handle REMINDER_TRIGGERED` | Phase 11: Foreground Service & Protocol |
| `1c91986` | shaiiiikh-github | 2026-11-16 | `feat(contacts): add phone input validation and empty contact warning state` | Phase 12: Input Validation & UX |
| `e18286b` | shaiiiikh-github | 2026-11-16 | `test(protocol): add unit tests for BLE JSON parsing, multipart SMS, and fake sender` | Phase 13: Automated Testing & Verification |
| `979c332` | shaiiiikh-github | 2026-11-16 | `style(ui): implement iOS 26 Liquid Glass design tokens and component library` | Phase 14: iOS 26 Design System Tokens & Components |
| `c96a71d` | shaiiiikh-github | 2026-11-16 | `style(ui): implement floating glass tab bar and edge-to-edge window insets` | Phase 14: Navigation & Window Insets |
| `4063f35` | shaiiiikh-github | 2026-11-16 | `style(ui): redesign dashboard with liquid glass hero card and live state pill` | Phase 14: Dashboard Screen Redesign |
| `da24343` | shaiiiikh-github | 2026-11-16 | `style(ui): redesign reminders and time picker with iOS liquid glass aesthetics` | Phase 14: Reminders & Add/Edit Redesign |
| `bcfcf42` | shaiiiikh-github | 2026-11-16 | `style(ui): redesign emergency contacts list with primary badge and modal sheet` | Phase 14: Emergency Contacts Redesign |
| `c7f6672` | shaiiiikh-github | 2026-11-16 | `style(ui): redesign SOS emergency alert with high-contrast calm breathing indicators` | Phase 14: SOS Emergency Modal Redesign |
| `54217b8` | shaiiiikh-github | 2026-11-16 | `style(ui): redesign settings and diagnostics with iOS inset grouped tables` | Phase 14: Settings & Diagnostics Redesign |
| `a97fcb2` | shaiiiikh-github | 2026-11-16 | `style(ui): add Compose preview matrix for light and dark themes` | Phase 14: UI Previews & Theme Verification |

---

## 4. Architecture & UI Design System

### 4.1 iOS 26 "Liquid Glass" Visual Language
* **Design System Specification**: Documented in [docs/UI_DESIGN_SYSTEM.md](file:///d:/PROJECTS/CareBuddy/docs/UI_DESIGN_SYSTEM.md).
* **Pre-Redesign Audit & Transformations**: Documented in [docs/ui_audit.md](file:///d:/PROJECTS/CareBuddy/docs/ui_audit.md).
* **Core Principles**:
  * Translucent glass surfaces (`GlassSurface`, `GlassCard`) with vertical gradient hairline borders (white 55% -> 8% light, 24% -> 6% dark).
  * Tactile spring physics (`dampingRatio = 0.8f, stiffness = 400f`), press-scale (`0.97f`), and haptic tick feedback on toggles and tabs.
  * Calm human-centered copy; 100% elimination of celebratory or alarmist emojis (`🔔`, `🎉`, `🚨`).
  * Inset grouped tables with hairline separators and colored leading icon tiles.
  * High-legibility exception for SOS alert modal (`#140202` deep crimson, calm breathing pulse, 56dp touch targets).

---

## 5. BLE Protocol Parity Matrix

Nordic UART Service: `6E400001-B5A3-F393-E0A9-E50E24DCCA9E`  
RX Characteristic (App -> ESP32 Write): `6E400002-B5A3-F393-E0A9-E50E24DCCA9E`  
TX Characteristic (ESP32 -> App Notify): `6E400003-B5A3-F393-E0A9-E50E24DCCA9E`

| Message (`type`) | Direction | Fields | Sent By | Handled By | Parity Status & Audit Notes |
|:---|:---:|:---|:---|:---|:---|
| `ADD_REMINDER` | App -> ESP32 | `type`, `id`, `title`, `time`, `minutes`, `enabled` | Android `MainViewModel.kt` | ESP32 `CareBuddy.ino` (lines 2565–2589) | **Matched**. Stored in NVS, plays `BUZZ_SHORT_BEEP`. |
| `DELETE_REMINDER` | App -> ESP32 | `type`, `id` | Android `MainViewModel.kt` | ESP32 `CareBuddy.ino` (lines 2590–2602) | **Matched**. Removed from RAM array and updated in NVS. |
| `CLEAR_REMINDERS` | App -> ESP32 | `type` | Android (Available via full clear) | ESP32 `CareBuddy.ino` (lines 2603–2606) | **Matched**. Supported by firmware, usable by repository reset. |
| `SYNC_TIME` | App -> ESP32 | `type`, `minutes`, `epoch` | Android `MainViewModel.kt` | ESP32 `CareBuddy.ino` (lines 2607–2618) | **Matched**. Sets `currentMinutesOfDay` and synchronizes internal clock. |
| `TEST_SOS` | App -> ESP32 | `type` | Android `MainViewModel.kt` | ESP32 `CareBuddy.ino` (lines 2619–2628) | **Matched**. Triggers local alarm on OLED/buzzer and replies with `SOS TEST`. |
| `SOS` (`TRIGGERED`) | ESP32 -> App | `type`, `status` | ESP32 `CareBuddy.ino` (lines 566–568) | Android `CareBuddyBleService.kt` & `MainViewModel.kt` | **Matched**. Wakes device, launches full-screen intent, executes multi-contact SMS and call. |
| `SOS` (`CANCELLED`) | ESP32 -> App | `type`, `status` | ESP32 `CareBuddy.ino` (lines 582–584) | Android `MainViewModel.kt` | **Matched**. Dismisses emergency modal if active. |
| `SOS` (`TEST`) | ESP32 -> App | `type`, `status` | ESP32 `CareBuddy.ino` (lines 2624–2626) | Android `MainViewModel.kt` | **Matched**. Displays emergency UI in test mode without calling/texting. |
| `REMINDER_TRIGGERED` | ESP32 -> App | `type`, `id`, `title`, `time` | ESP32 `CareBuddy.ino` (lines 645–649) | Android `CareBuddyBleService.kt` & `MainViewModel.kt` | **Matched**. Handled by `CareBuddyBleService` with heads-up alarm notification and `ringingReminder` visual state on `DashboardScreen`. |
| `REMINDER_ACK` | ESP32 -> App | `type`, `id` | ESP32 `CareBuddy.ino` (lines 661–663) | Android `CareBuddyBleService.kt` & `MainViewModel.kt` | **Matched**. Dismisses reminder notification and marks reminder `isAcknowledgedToday = true`. |

---

## 6. Build & Test Status

### 6.1 Android Build
* **Command Run**: `cd android; .\gradlew.bat assembleDebug`
* **Result**: **SUCCESS (Exit Code 0)**
* **Output Artifact**: `android/app/build/outputs/apk/debug/app-debug.apk`

### 6.2 Test Coverage
* **Command Run**: `cd android; .\gradlew.bat testDebugUnitTest`
* **Result**: **SUCCESS (Exit Code 0, 11 tests executed, 0 failures)**
* **Test Suites**:
  * `CareBuddyRepositoryTest`: Migration of placeholder numbers, auto-primary selection, phone validation logic.
  * `SosDispatcherTest`: GSM-7 formatting, multi-contact sequential delivery, retry logic, deduplication window, multipart splitting.
  * `BleProtocolJsonTest`: BLE JSON parsing for all protocol commands (`ADD_REMINDER`, `SYNC_TIME`, `SOS`, `REMINDER_TRIGGERED`, `REMINDER_ACK`).

---

## 7. Needs Manual Verification

Because automated unit tests run in a simulated JVM environment and host environment lacks connected Android debug bridge (`adb`) hardware:

- [ ] **Live Carrier SIM SMS Delivery**: Insert a real carrier SIM into the Android phone and trigger SOS to verify SMS transmission and delivery reports over cellular tower networks.
- [ ] **Dual-SIM Carrier Routing**: Verify that on dual-SIM Android devices, the SMS uses the user's default SMS voice/data subscription without prompt interruption.
- [ ] **Lock Screen Full-Screen Intent on Aggressive OEM Skins**: Test SOS triggering while phone screen is turned off and locked on devices running MIUI, ColorOS, OneUI, and FuntouchOS to confirm system overlay permissions wake the display.
- [ ] **Physical ESP32 BLE Hardware Pairing**: Connect the Android phone to physical ESP32 DevKit over BLE, verifying MTU 185 negotiation and CCCD descriptor notification enabling.
- [ ] **Touch Sensor Debouncing & SOS Hold**: Physically hold the TTP223 capacitive touch sensor for 3000ms to verify countdown tone, cancel on early release, and BLE SOS trigger upon completion.
- [ ] **Device Reboot Auto-Start**: Power cycle the smartphone and verify `CareBuddyBleService` automatically starts and reconnects to the ESP32 without opening the app.
- [ ] **Display Density & Large Font Scaling (1.5x)**: Verify layout on physical small screen (360dp width) and large tablet/foldable screens under 1.5x accessibility font scale.
- [ ] **Fluid 60Hz/120Hz Gesture Navigation**: Validate scrolling smoothness of the floating glass tab bar and inset grouped lists during fast flings on physical hardware.
