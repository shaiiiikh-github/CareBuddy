# CareBuddy 🤖
### An Interactive Elderly Safety & Reminder Assistant

CareBuddy is a low-cost, companion-oriented assistive device and smartphone companion application designed to help elderly individuals living independently with daily medication schedules, routine reminders, and an intuitive emergency SOS call system.

Rather than presenting a cold, generic gadget interface, CareBuddy features an interactive OLED visual face with expressive animated eyes, friendly auditory cues, and non-intrusive capacitive touch interaction.

---

> [!NOTE]
> **Societal Purpose & Disclaimer**: CareBuddy is designed as an assistive companion tool for daily routine organization and emergency notification assistance. It is NOT a certified medical device and does not guarantee emergency response.

---

## Hardware Architecture & Components

CareBuddy is built strictly with accessible prototype components:

* **ESP32 Development Board** (Dual-Core, BLE Built-in)
* **0.96" SSD1306 OLED Display** (I2C 128x64)
* **TTP223 Capacitive Touch Sensor**
* **Active/Passive Buzzer**

```
 [ Android Smartphone App ]
             │
             │ BLE (Nordic UART Service)
             ▼
      [ ESP32 Board ]
       ├── 0.96" OLED Display (Animated Eyes & UI)
       ├── TTP223 Touch Sensor (State Machine Input)
       └── Buzzer (Auditory Notifications & Alarms)
```

---

## Demonstration Sequence for Presentation / Evaluation

1. **Power On**: Turn on CareBuddy. The device shows splash text, plays a startup tone, and wakes up its eyes.
2. **Idle Animation**: Eyes naturally blink, glance left and right, and return to center.
3. **Single Touch**: Tap the TTP223 sensor. Eyes become happy `^_^` and display `"Hello!"`.
4. **Double Touch**: Double-tap the sensor within 450ms. Eyes display playful irritated expression `> <` and display `"Hey! Stop poking!"`.
5. **Inactivity Sleep**: Leave the device untouched for 25 seconds. Eyes enter sleepy state `Zzz`, then enter low-power sleep mode with animated drifting `Z z z`.
6. **Wake Up**: Touch the sensor while sleeping. Eyes perform smooth wake-up animation.
7. **App Pairing**: Open the CareBuddy Android App. Tap "Connect". App connects to `CareBuddy-XXXX` via BLE and syncs time.
8. **Create Reminder**: Create a new medication reminder (e.g. "Take Blood Pressure Pill" at current minute).
9. **Reminder Trigger**: When scheduled time hits, ESP32 plays reminder beep pattern, eyes display wide alert, and OLED shows `"REMINDER: Take Blood Pressure Pill"`.
10. **Acknowledge Reminder**: Tap the sensor. CareBuddy displays `"DONE ✓"`, plays acknowledgment tone, and notifies the app.
11. **Emergency SOS Countdown**: Touch and hold the sensor for 3 seconds. The OLED displays `SOS 3... 2... 1...` with ticking buzzer.
12. **SOS Cancellation**: Release sensor before 3 seconds to cancel.
13. **SOS Activation**: Hold for full 3 seconds. OLED shows `SOS SENT!`, emergency alarm sounds, and BLE notification dispatches to smartphone.
14. **App SOS Screen**: The Android App immediately triggers a prominent emergency modal with contact details, direct **Call** and **Send SMS** buttons.
15. **Test Mode**: Trigger "Test SOS" from App dashboard to safely test hardware link without sending real SMS.

---

## Documentation Links

* 🔌 [Hardware Wiring & Pinout Guide](docs/wiring.md)
* ⚙️ [Setup & Compilation Instructions](docs/setup.md)
* 📡 [BLE JSON Communication Protocol](protocol/communication_protocol.md)
* 🛠️ [Troubleshooting & Sensitivity Tuning](docs/troubleshooting.md)
