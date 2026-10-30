# CareBuddy Troubleshooting & Tuning Guide

## 1. TTP223 Capacitive Touch Sensitivity Tuning

### Issue: Sensor triggers when finger is near without touching
The TTP223 module is sensitive to electromagnetic fields. CareBuddy solves this through a multi-tiered approach:

1. **Software Debouncing & State Machine (Built into `CareBuddy.ino`)**:
   - `TOUCH_DEBOUNCE_MS (50ms)`: Ignores micro-spikes and EMI noise.
   - `MIN_TOUCH_MS (80ms)`: Ensures accidental fly-by proximity trigger is ignored.
   - `DOUBLE_TAP_WINDOW_MS (450ms)`: Prevents false rapid double clicks.
   - **Release Guarantee**: The touch manager forces sensor pin to return LOW before allowing a new tap event.

2. **Hardware Sensitivity Adjustment (TTP223 Module)**:
   - On the TTP223 PCB, locate capacitor slot **C3** (normally empty or 0-50pF).
   - Adding a small capacitor (e.g., **22pF - 50pF**) across C3 reduces physical capacitive sensitivity, requiring firmer physical contact.
   - Ensure the touch wire / pad is not running parallel to unshielded high-current wires or antenna traces.

---

## 2. OLED Display Issues

* **Screen remains completely dark**:
  1. Verify SDA is connected to GPIO 21 and SCL to GPIO 22.
  2. Verify I2C address in code (`0x3C`). Run an I2C scanner sketch if using a non-standard 0.96" display module (some use `0x3D`).
  3. Ensure 3.3V and GND wires are properly powered.

---

## 3. BLE Connection Issues

* **App cannot discover `CareBuddy-XXXX`**:
  1. Ensure Location and Bluetooth permissions are granted on the smartphone.
  2. Ensure phone's Location / GPS toggle is ON (required by Android BLE scanner APIs).
  3. Verify ESP32 Serial Monitor shows `BLE Device Advertising as: CareBuddy-XXXX`.
  4. Press ESP32 EN/RESET button to restart advertising.

---

## 4. Emergency SMS / Phone Call Behavior

* **Automated SMS not sending**:
  1. Android 10+ restricts background automated SMS sending without explicit user interaction or `SEND_SMS` permission.
  2. CareBuddy attempts direct transmission via `SmsManager`. If blocked by OS permissions, it automatically falls back to launching the Android native Messaging app pre-filled with the contact number and emergency alert body.
  3. Ensure SIM card has active cellular service.
