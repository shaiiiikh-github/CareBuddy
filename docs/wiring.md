# CareBuddy Hardware Wiring Guide

This document specifies the exact physical wiring connections between the ESP32 Development Board, the SSD1306 OLED display, the TTP223 capacitive touch sensor, and the buzzer.

---

## 1. Wiring Summary Table

| Component | Pin Label | ESP32 GPIO | Electrical Connection Notes |
| :--- | :--- | :--- | :--- |
| **0.96" OLED Display** | VCC | **3.3V** | Connect to 3.3V power pin |
| **0.96" OLED Display** | GND | **GND** | Connect to Common Ground |
| **0.96" OLED Display** | SDA | **GPIO 21 / D21** | Hardware I2C SDA (`OLED_SDA`) |
| **0.96" OLED Display** | SCL | **GPIO 22 / D22** | Hardware I2C SCL (`OLED_SCL`) |
| **TTP223 Touch Sensor** | VCC | **3.3V** | Connect to 3.3V power pin |
| **TTP223 Touch Sensor** | GND | **GND** | Connect to Common Ground |
| **TTP223 Touch Sensor** | OUT | **GPIO 18 / D18** | Dedicated Touch Pin (`TOUCH_PIN`) |
| **Buzzer** | Positive (+) | **GPIO 19 / D19** | Dedicated Buzzer Pin (`BUZZER_PIN`) |
| **Buzzer** | Negative (-) | **GND** | Connect to Common Ground |

---

## 2. Hardware Configuration Constants in Code
All GPIO pins are defined at the top of [`CareBuddy.ino`](file:///d:/PROJECTS/CareBuddy/firmware/CareBuddy/CareBuddy.ino):

```cpp
#define OLED_SDA       21     // Hardware I2C Data Pin (D21)
#define OLED_SCL       22     // Hardware I2C Clock Pin (D22)
#define OLED_ADDRESS   0x3C   // Standard SSD1306 I2C Address

#define TOUCH_PIN      18     // TTP223 OUT pin connected to GPIO 18 ONLY
#define BUZZER_PIN     19     // Buzzer positive lead connected to GPIO 19 ONLY
```

---

## 3. Buzzer Electrical Safety Note
* **Active Buzzer**: Can be driven directly from GPIO 19.
* **Passive Buzzer / High Current Speaker**: If using a buzzer requiring more than 20mA, connect a standard NPN transistor (e.g., 2N2222) with a 1kΩ base resistor between GPIO 19 and transistor base.
