# CareBuddy Future Work & Firmware Protocol Proposals

## 1. Remote Hardware Debounce & SOS Hold Configuration

### Problem Statement
The Android companion application previously allowed configuring:
1. Minimum Touch Press debounce window (`touchMinMs`, default 80ms).
2. SOS Long-Press Hold countdown duration (`sosHoldMs`, default 3000ms).

These settings were persisted in Android SharedPreferences, but could not affect device behavior because the ESP32 firmware currently uses compile-time `#define` constants in `CareBuddy.ino`:
```cpp
#define TOUCH_DEBOUNCE_MS 80
#define HOLD_TIME 3000
```
In accordance with CareBuddy integrity rules, this non-functional UI was removed from the app to avoid misleading users into believing device hardware timing was altered.

### Proposed Firmware Support & Protocol Command
To re-enable hardware sensitivity adjustments in future releases, the firmware should support a new command: `SET_CONFIG`.

#### App -> ESP32 JSON Payload:
```json
{
  "type": "SET_CONFIG",
  "touchMinMs": 80,
  "sosHoldMs": 3000
}
```

#### ESP32 -> App Confirmation Payload:
```json
{
  "type": "CONFIG_ACK",
  "touchMinMs": 80,
  "sosHoldMs": 3000
}
```

#### Firmware Implementation Requirements:
1. Parse `touchMinMs` (valid range: 40ms to 300ms) and store in NVS `Preferences`.
2. Parse `sosHoldMs` (valid range: 1000ms to 10000ms) and store in NVS `Preferences`.
3. Load sensitivity variables on ESP32 boot with fallbacks to defaults.
4. Replace compile-time `TOUCH_DEBOUNCE_MS` and `HOLD_TIME` with dynamic variables.
