# CareBuddy Setup & Build Guide

## 1. Prerequisites & Required Libraries

### Firmware (ESP32)
* **Arduino IDE 2.x** (or PlatformIO)
* **ESP32 Board Package**: Install via Arduino Board Manager (`esp32` by Espressif Systems)
* **Required Libraries** (Install via Arduino Library Manager):
  1. `Adafruit GFX Library` (by Adafruit)
  2. `Adafruit SSD1306` (by Adafruit)
  3. `ArduinoJson` (v6 or v7 by Benoit Blanchon)
  4. `Preferences` (Built into ESP32 core)
  5. `BLEDevice` (Built into ESP32 core)

### Android Application
* **Android Studio Hedgehog / Iguana / Jellyfish** or newer
* **Android SDK**: API level 34 (Android 14) compiled, minimum API level 26 (Android 8.0)
* **JDK**: Version 17

---

## 2. ESP32 Firmware Upload Instructions
1. Open `firmware/CareBuddy/CareBuddy.ino` in Arduino IDE.
2. Select target board: `ESP32 Dev Module` (or your specific ESP32 board).
3. Select the correct COM port.
4. Verify pins in `CareBuddy.ino` match your hardware connections (`OLED_SDA=21`, `OLED_SCL=22`, `TOUCH_PIN=4`, `BUZZER_PIN=18`).
5. Click **Upload**.
6. Open Serial Monitor at **115200 baud** to observe startup diagnostics and BLE device name (`CareBuddy-XXXX`).

---

## 3. Android Application Build & Run Instructions
1. Open Android Studio.
2. Select **Open** and choose the `android/` directory inside `CareBuddy`.
3. Wait for Gradle Sync to complete.
4. Connect your Android smartphone via USB (with Developer Options & USB Debugging enabled).
5. Click **Run 'app'** (Shift + F10).
6. On first launch, grant Bluetooth, Location, Call, and SMS permissions when prompted.
