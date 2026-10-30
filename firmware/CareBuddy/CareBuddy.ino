/*
 CAREBUDDY HARDWARE - MOCHI DESK BUDDY EDITION

 ESP32 Development Board

 OLED SSD1306:
 SDA  -> GPIO 21 / D21
 SCL  -> GPIO 22 / D22
 VCC  -> 3.3V
 GND  -> GND

 TP223 TOUCH SENSOR:
 OUT  -> GPIO 18 / D18
 VCC  -> 3.3V
 GND  -> GND

 BUZZER:
 +    -> GPIO 19 / D19
 -    -> GND

 IMPORTANT:
 GPIO 18 = TOUCH ONLY
 GPIO 19 = BUZZER ONLY
 GPIO 21 = OLED SDA
 GPIO 22 = OLED SCL
*/

#include <Wire.h>
#include <Adafruit_GFX.h>
#include <Adafruit_SSD1306.h>
#include <BLEDevice.h>
#include <BLEServer.h>
#include <BLEUtils.h>
#include <BLE2902.h>
#include <Preferences.h>
#include <ArduinoJson.h>
#include <math.h>

// ==========================================
// 1. HARDWARE CONFIGURATION CONSTANTS
// ==========================================
#define OLED_SDA           21     // Hardware I2C Data Pin (D21)
#define OLED_SCL           22     // Hardware I2C Clock Pin (D22)
#define OLED_ADDRESS       0x3C   // Standard SSD1306 I2C Address
#define SCREEN_WIDTH       128    // OLED display width in pixels
#define SCREEN_HEIGHT      64     // OLED display height in pixels
#define OLED_RESET         -1     // Reset pin # (or -1 if sharing reset)

#define TOUCH_PIN          18     // TTP223 OUT pin connected to GPIO 18 ONLY
#define BUZZER_PIN         19     // Buzzer positive lead connected to GPIO 19 ONLY

// ==========================================
// 2. TIMING & THRESHOLD CONSTANTS
// ==========================================
#define DISPLAY_UPDATE_INTERVAL_MS 33   // ~30 FPS OLED refresh rate target
#define TOUCH_DEBOUNCE_MS          50   // Ignore contact bounce below 50ms
#define MIN_TOUCH_MS               80   // Minimum valid press duration to reject noise
#define DOUBLE_TAP_WINDOW_MS       450  // Max gap between taps for double-tap
#define LONG_PRESS_MS              1200 // Hold duration for status screen
#define SOS_HOLD_MS                3000 // Hold duration to initiate SOS countdown
#define TOUCH_COOLDOWN_MS          300  // Mandatory cooldown after valid event

#define SLEEP_TIMEOUT_MS           25000 // Inactivity timeout before sleep (25s)
#define MAX_REMINDERS              10    // Maximum stored active reminders

// Reminder alert escalation (buzzer + animation): the alert never gives up, it just gets more insistent
#define REMINDER_ESCALATE_1_MS     30000 // after 30 s unanswered: faster beeps, "!" marks, pleading face
#define REMINDER_ESCALATE_2_MS     90000 // after 90 s unanswered: most insistent
#define REMINDER_NAG_MS            600000 // after 10 min unanswered: slow persistent nag (buzzer only)
#define FX_NIGHT_START_MIN         (22 * 60) // drowsy face + dimmed OLED from 22:00 ...
#define FX_NIGHT_END_MIN           (6 * 60)  // ... until 06:00 (uses the clock synced by the app)

// ==========================================
// 3. BLE NORDIC UART SERVICE UUIDs
// ==========================================
#define SERVICE_UUID           "6E400001-B5A3-F393-E0A9-E50E24DCCA9E"
#define CHARACTERISTIC_UUID_RX "6E400002-B5A3-F393-E0A9-E50E24DCCA9E"
#define CHARACTERISTIC_UUID_TX "6E400003-B5A3-F393-E0A9-E50E24DCCA9E"

// ==========================================
// 4. DATA STRUCTURES & ENUMS
// ==========================================
enum EyeState {
  STATE_STARTUP,
  STATE_IDLE,
  STATE_BLINKING,
  STATE_LOOK_LEFT,
  STATE_LOOK_RIGHT,
  STATE_HAPPY,
  STATE_CONFUSED,
  STATE_IRRITATED,
  STATE_SLEEPY,
  STATE_SLEEPING,
  STATE_WAKING,
  STATE_REMINDER,
  STATE_ACKNOWLEDGED,
  STATE_STATUS,
  STATE_SOS_COUNTDOWN,
  STATE_SOS_SENT
};

enum BuzzerPattern {
  BUZZ_NONE,
  BUZZ_STARTUP,
  BUZZ_SHORT_BEEP,
  BUZZ_DOUBLE_BEEP,
  BUZZ_REMINDER,
  BUZZ_SOS_TICK,
  BUZZ_SOS_ALARM,
  BUZZ_ACK,
  // expression sounds
  BUZZ_CHEER,       // first reminder of the day
  BUZZ_CONNECT,     // app connected
  BUZZ_DISCONNECT,  // app disconnected
  BUZZ_SAD,
  BUZZ_WINK,
  BUZZ_PURR,
  BUZZ_GIGGLE,
  BUZZ_SIGH,        // SOS cancelled
  BUZZ_SHY,
  BUZZ_DIZZY,
  BUZZ_BORED,
  BUZZ_DAYDREAM,
  BUZZ_BLIP,
  BUZZ_STARTLE,
  BUZZ_HICCUP,
  BUZZ_SMUG,
  BUZZ_KISS          // 4th tap in a petting streak
};

struct Reminder {
  char id[16];
  char title[24];
  char timeStr[10];     // e.g., "10:00 AM"
  uint16_t timeMinutes; // minutes since midnight (0 - 1439)
  bool enabled;
  bool acknowledgedToday;
};

// ==========================================
// 4b. ANIMATION / BUZZER TYPES (kept above setup() so the Arduino IDE's
//     auto-generated prototypes can see them)
// ==========================================
enum FxEmo : uint8_t {
  FXE_NONE = 0, FXE_CONNECT, FXE_WORRIED, FXE_WINK_NOD, FXE_SHY, FXE_DIZZY,
  FXE_BORED, FXE_DAYDREAM, FXE_CROSSEYED, FXE_HICCUP, FXE_STARTLED, FXE_SMUG
};
enum FxHappyVar : uint8_t { FXH_NORMAL = 0, FXH_WINK, FXH_PET, FXH_TICKLE, FXH_RELIEF, FXH_KISS };

// target pose written every frame, then eased by the springs
struct FxPose {
  float eyeW, eyeH, lookX, lookY, faceY, lidTop, slope, lidBot;
  float arc, bend, asym, cross, wink;
  float mCurve, mWidth, mOpen, blush, shine, blink, sway;
  int   mStyle, eyeMode;   // mStyle: 0 curve/open 1 teeth 2 wave 3 'o' 4 hidden 5 smirk
};                         // eyeMode: 0 solid 1 heart 2 star 3 spiral

struct BuzzSeq { const uint16_t* steps; uint8_t count; };

// ==========================================
// 5. GLOBAL OBJECTS & STATE VARIABLES
// ==========================================
Adafruit_SSD1306 display(SCREEN_WIDTH, SCREEN_HEIGHT, &Wire, OLED_RESET);
Preferences preferences;

// BLE Variables
BLEServer* pServer = nullptr;
BLECharacteristic* pTxCharacteristic = nullptr;
bool deviceConnected = false;
bool oldDeviceConnected = false;
std::vector<String> rxMessageQueue;
portMUX_TYPE rxMux = portMUX_INITIALIZER_UNLOCKED;
char deviceBleName[30];

// State Engines
EyeState currentEyeState = STATE_STARTUP;
EyeState previousEyeState = STATE_IDLE;

// Refresh Timer for 30 FPS OLED Rendering
uint32_t lastDisplayUpdate = 0;

// Reminder Storage & Clock
Reminder reminders[MAX_REMINDERS];
int reminderCount = 0;
uint32_t currentEpochSeconds = 1700000000; // Synced via BLE
uint32_t lastMillisClock = 0;
uint16_t currentMinutesOfDay = (10 * 60);  // Default 10:00 AM if un-synced
int activeReminderIndex = -1;
int lastTriggeredMinute = -1;

// Timers
uint32_t lastActivityMillis = 0;
uint32_t stateTimer = 0;
uint32_t nextBlinkMillis = 0;
uint32_t nextGlanceMillis = 0;
uint32_t reactionEndMillis = 0;

// SOS Engine
int sosCountdownSeconds = 3;
uint32_t sosTimer = 0;
bool sosSentDispatched = false;

// Buzzer Engine
BuzzerPattern currentBuzzerPattern = BUZZ_NONE;
uint32_t buzzerTimer = 0;
int buzzerStep = 0;

// Touch State Machine Variables
enum RawTouchState { T_IDLE, T_DEBOUNCING, T_PRESSED, T_WAIT_RELEASE, T_WAIT_SECOND, T_COOLDOWN };
RawTouchState tState = T_IDLE;
uint32_t touchStartMs = 0;
uint32_t touchReleaseMs = 0;
uint32_t touchCooldownEndMs = 0;
bool sosHoldThresholdReached = false;
bool longPressTriggeredThisPress = false;

// Forward Declarations
void updateTouchManager();
void updateEyeEngine();
void updateBuzzerEngine();
void updateReminderEngine();
void updateSleepEngine();
void updateSosEngine();
void processBleMessages(const String& rawJson);
void sendBleJson(const JsonDocument& doc);
void playBuzzerPattern(BuzzerPattern pattern);
void triggerReminder(int index);
void acknowledgeActiveReminder();
void cancelSosCountdown();
void saveRemindersToNVS();
void loadRemindersFromNVS();
void fxBootAnimation();

// ==========================================
// 6. SETUP & INITIALIZATION
// ==========================================
void setup() {
  Serial.begin(115200);
  Serial.println(F("\nCareBuddy Firmware Starting"));
  Serial.println(F("OLED: SDA = 21, SCL = 22"));
  Serial.println(F("Touch: GPIO = 18"));
  Serial.println(F("Buzzer: GPIO = 19"));

  // Pin Modes
  pinMode(TOUCH_PIN, INPUT);
  pinMode(BUZZER_PIN, OUTPUT);
  digitalWrite(BUZZER_PIN, LOW);

  // Initialize I2C OLED Display
  Wire.begin(OLED_SDA, OLED_SCL);
  if (!display.begin(SSD1306_SWITCHCAPVCC, OLED_ADDRESS)) {
    Serial.println(F("ERROR: OLED SSD1306 allocation failed!"));
    for (;;) {
      digitalWrite(BUZZER_PIN, HIGH);
      delay(100);
      digitalWrite(BUZZER_PIN, LOW);
      delay(100);
    }
  }

  display.clearDisplay();
  display.setTextColor(SSD1306_WHITE);
  display.display();

  // Generate unique BLE device name from MAC address
  uint64_t chipid = ESP.getEfuseMac();
  uint16_t macSuffix = (uint16_t)(chipid >> 32);
  snprintf(deviceBleName, sizeof(deviceBleName), "CareBuddy-%04X", macSuffix);

  // Initialize BLE with 512 MTU for long JSON packets
  BLEDevice::init(deviceBleName);
  BLEDevice::setMTU(512);

  pServer = BLEDevice::createServer();
  
  class ServerCallbacks: public BLEServerCallbacks {
    void onConnect(BLEServer* pServer) override {
      deviceConnected = true;
      Serial.println(F("BLE: Client Connected"));
    }
    void onDisconnect(BLEServer* pServer) override {
      deviceConnected = false;
      Serial.println(F("BLE: Client Disconnected"));
    }
  };
  pServer->setCallbacks(new ServerCallbacks());

  BLEService* pService = pServer->createService(SERVICE_UUID);
  pTxCharacteristic = pService->createCharacteristic(
    CHARACTERISTIC_UUID_TX,
    BLECharacteristic::PROPERTY_NOTIFY
  );
  pTxCharacteristic->addDescriptor(new BLE2902());

  BLECharacteristic* pRxCharacteristic = pService->createCharacteristic(
    CHARACTERISTIC_UUID_RX,
    BLECharacteristic::PROPERTY_WRITE | BLECharacteristic::PROPERTY_WRITE_NR
  );

  class RxCallbacks: public BLECharacteristicCallbacks {
    void onWrite(BLECharacteristic* pCharacteristic) override {
      String val = String(pCharacteristic->getValue().c_str());
      if (val.length() > 0) {
        portENTER_CRITICAL(&rxMux);
        rxMessageQueue.push_back(val);
        portEXIT_CRITICAL(&rxMux);
      }
    }
  };
  pRxCharacteristic->setCallbacks(new RxCallbacks());

  pService->start();
  
  // High-Reliability BLE Advertising payload structure (< 31 bytes per packet)
  BLEAdvertising* pAdvertising = BLEDevice::getAdvertising();
  pAdvertising->addServiceUUID(SERVICE_UUID);

  // Primary Packet: Name + Flags (fits in 31 bytes)
  BLEAdvertisementData oAdvertisementData = BLEAdvertisementData();
  oAdvertisementData.setFlags(0x06); // LE General Discoverable Mode
  oAdvertisementData.setName(deviceBleName);
  pAdvertising->setAdvertisementData(oAdvertisementData);

  // Scan Response Packet: Complete 128-bit Service UUID
  BLEAdvertisementData oScanResponseData = BLEAdvertisementData();
  oScanResponseData.setCompleteServices(BLEUUID(SERVICE_UUID));
  pAdvertising->setScanResponseData(oScanResponseData);

  pAdvertising->setScanResponse(true);
  pAdvertising->setMinPreferred(0x06);
  pAdvertising->setMinPreferred(0x12);
  BLEDevice::startAdvertising();

  Serial.print(F("BLE Advertising as: "));
  Serial.println(deviceBleName);

  // Load Reminders from NVS
  loadRemindersFromNVS();

  // Startup sequence
  lastActivityMillis = millis();
  lastMillisClock = millis();
  playBuzzerPattern(BUZZ_STARTUP);

  // Premium animated boot sequence (non-feature visual; plays the startup jingle)
  fxBootAnimation();

  currentEyeState = STATE_IDLE;
  nextBlinkMillis = millis() + random(3000, 6000);
  nextGlanceMillis = millis() + random(4000, 8000);
}

// ==========================================
// 7. MAIN EVENT LOOP (100% NON-BLOCKING)
// ==========================================
void loop() {
  uint32_t currentMillis = millis();

  // 1. Maintain Virtual Internal Clock
  if (currentMillis - lastMillisClock >= 1000) {
    uint32_t elapsedSec = (currentMillis - lastMillisClock) / 1000;
    lastMillisClock = currentMillis;
    currentEpochSeconds += elapsedSec;

    // Advance minute counter when 60 seconds pass
    if (currentEpochSeconds % 60 == 0) {
      currentMinutesOfDay = (currentMinutesOfDay + 1) % 1440;
      // Reset daily acknowledgements at midnight
      if (currentMinutesOfDay == 0) {
        for (int i = 0; i < reminderCount; i++) {
          reminders[i].acknowledgedToday = false;
        }
      }
    }
  }

  // 2. BLE Connection Management
  if (!deviceConnected && oldDeviceConnected) {
    pServer->startAdvertising();
    Serial.println(F("BLE: Restarted Advertising"));
    oldDeviceConnected = deviceConnected;
  }
  if (deviceConnected && !oldDeviceConnected) {
    oldDeviceConnected = deviceConnected;
  }

  // 3. Process incoming BLE Commands from Queue
  String currentBleMsg = "";
  portENTER_CRITICAL(&rxMux);
  if (!rxMessageQueue.empty()) {
    currentBleMsg = rxMessageQueue.front();
    rxMessageQueue.erase(rxMessageQueue.begin());
  }
  portEXIT_CRITICAL(&rxMux);

  if (currentBleMsg.length() > 0) {
    processBleMessages(currentBleMsg);
  }

  // 4. Update Sub-System State Machines
  updateTouchManager();
  updateSleepEngine();
  updateReminderEngine();
  updateSosEngine();
  updateEyeEngine();
  updateBuzzerEngine();
}

// ==========================================
// 8. ROBUST TOUCH MANAGER STATE MACHINE
// ==========================================
void updateTouchManager() {
  uint32_t now = millis();
  bool pinRaw = digitalRead(TOUCH_PIN) == HIGH;

  switch (tState) {
    case T_COOLDOWN:
      // Mandatory lockout: wait until pin is LOW AND cooldown duration passed
      if (!pinRaw && now >= touchCooldownEndMs) {
        tState = T_IDLE;
      }
      break;

    case T_IDLE:
      if (pinRaw) {
        tState = T_DEBOUNCING;
        touchStartMs = now;
      }
      break;

    case T_DEBOUNCING:
      if (pinRaw) {
        if (now - touchStartMs >= TOUCH_DEBOUNCE_MS) {
          tState = T_PRESSED;
          sosHoldThresholdReached = false;
          longPressTriggeredThisPress = false;
          lastActivityMillis = now; // Reset sleep timer

          // If SOS Countdown is actively running, any touch cancels it!
          if (currentEyeState == STATE_SOS_COUNTDOWN) {
            cancelSosCountdown();
            tState = T_COOLDOWN;
            touchCooldownEndMs = now + TOUCH_COOLDOWN_MS;
          }
        }
      } else {
        tState = T_IDLE; // False noise bounce rejected
      }
      break;

    case T_PRESSED:
      if (pinRaw) {
        uint32_t holdDuration = now - touchStartMs;

        // SOS Hold Threshold (~3 seconds)
        if (holdDuration >= SOS_HOLD_MS && !sosHoldThresholdReached) {
          sosHoldThresholdReached = true;
          longPressTriggeredThisPress = true;
          currentEyeState = STATE_SOS_COUNTDOWN;
          sosCountdownSeconds = 3;
          sosTimer = now;
          sosSentDispatched = false;
          playBuzzerPattern(BUZZ_SOS_TICK);
          Serial.println(F("TOUCH: SOS START (Countdown initiated)"));
          tState = T_WAIT_RELEASE;
        }
        // Long Press Detection (~1.2 seconds)
        else if (holdDuration >= LONG_PRESS_MS && !longPressTriggeredThisPress && !sosHoldThresholdReached) {
          longPressTriggeredThisPress = true;
          if (activeReminderIndex != -1) {
            acknowledgeActiveReminder();
          } else if (currentEyeState != STATE_SLEEPING && currentEyeState != STATE_SLEEPY) {
            currentEyeState = STATE_STATUS;
            reactionEndMillis = now + 4000;
            playBuzzerPattern(BUZZ_SHORT_BEEP);
            Serial.println(F("TOUCH: LONG PRESS"));
          }
        }
      } else {
        // Touch released before hold thresholds
        uint32_t pressDuration = now - touchStartMs;
        touchReleaseMs = now;

        if (longPressTriggeredThisPress) {
          tState = T_COOLDOWN;
          touchCooldownEndMs = now + TOUCH_COOLDOWN_MS;
        } else if (pressDuration >= MIN_TOUCH_MS) {
          tState = T_WAIT_SECOND;
        } else {
          tState = T_IDLE;
        }
      }
      break;

    case T_WAIT_SECOND:
      if (pinRaw) {
        // Second tap detected within window -> DOUBLE TAP!
        tState = T_WAIT_RELEASE;
        Serial.println(F("TOUCH: DOUBLE TAP"));

        if (currentEyeState == STATE_SLEEPING || currentEyeState == STATE_SLEEPY) {
          currentEyeState = STATE_WAKING;
          stateTimer = now;
          playBuzzerPattern(BUZZ_SHORT_BEEP);
        } else if (activeReminderIndex != -1) {
          acknowledgeActiveReminder();
        } else {
          currentEyeState = STATE_IRRITATED;
          reactionEndMillis = now + 2500;
          playBuzzerPattern(BUZZ_DOUBLE_BEEP);
        }
      } else if (now - touchReleaseMs > DOUBLE_TAP_WINDOW_MS) {
        // Double tap window expired -> SINGLE SHORT TAP!
        Serial.println(F("TOUCH: SHORT TAP"));

        if (currentEyeState == STATE_SLEEPING || currentEyeState == STATE_SLEEPY) {
          currentEyeState = STATE_WAKING;
          stateTimer = now;
          playBuzzerPattern(BUZZ_SHORT_BEEP);
        } else if (activeReminderIndex != -1) {
          acknowledgeActiveReminder();
        } else {
          currentEyeState = STATE_HAPPY;
          reactionEndMillis = now + 2500;
          playBuzzerPattern(BUZZ_SHORT_BEEP);
        }

        tState = T_COOLDOWN;
        touchCooldownEndMs = now + TOUCH_COOLDOWN_MS;
      }
      break;

    case T_WAIT_RELEASE:
      if (!pinRaw) {
        tState = T_COOLDOWN;
        touchCooldownEndMs = now + TOUCH_COOLDOWN_MS;
      }
      break;
  }
}

// ==========================================
// 9. INDEPENDENT SOS COUNTDOWN ENGINE
// ==========================================
void updateSosEngine() {
  if (currentEyeState != STATE_SOS_COUNTDOWN) return;

  uint32_t now = millis();
  if (now - sosTimer >= 1000) {
    sosTimer = now;
    sosCountdownSeconds--;

    if (sosCountdownSeconds > 0) {
      playBuzzerPattern(BUZZ_SOS_TICK);
    } else {
      // Countdown reached 0 -> SOS CONFIRMED!
      currentEyeState = STATE_SOS_SENT;
      playBuzzerPattern(BUZZ_SOS_ALARM);
      reactionEndMillis = now + 8000;

      if (!sosSentDispatched) {
        sosSentDispatched = true;
        Serial.println(F("TOUCH: SOS CONFIRMED"));

        StaticJsonDocument<128> doc;
        doc["type"] = "SOS";
        doc["status"] = "TRIGGERED";
        sendBleJson(doc);
      }
    }
  }
}

void cancelSosCountdown() {
  if (currentEyeState == STATE_SOS_COUNTDOWN) {
    currentEyeState = STATE_HAPPY;
    reactionEndMillis = millis() + 2500;
    playBuzzerPattern(BUZZ_SHORT_BEEP);
    Serial.println(F("TOUCH: SOS CANCELLED"));

    StaticJsonDocument<128> doc;
    doc["type"] = "SOS";
    doc["status"] = "CANCELLED";
    sendBleJson(doc);
  }
}

// ==========================================
// 10. SLEEP & INACTIVITY ENGINE
// ==========================================
void updateSleepEngine() {
  uint32_t now = millis();

  // Do not enter sleep during active reminder, SOS countdown, or SOS sent state
  if (activeReminderIndex != -1 || currentEyeState == STATE_SOS_COUNTDOWN || currentEyeState == STATE_SOS_SENT) {
    lastActivityMillis = now;
    return;
  }

  // Reaction state timeouts -> return to IDLE
  if (reactionEndMillis > 0 && now >= reactionEndMillis) {
    reactionEndMillis = 0;
    if (currentEyeState != STATE_SLEEPING && currentEyeState != STATE_SLEEPY) {
      currentEyeState = STATE_IDLE;
    }
  }

  // Check Inactivity timeout
  if (currentEyeState == STATE_IDLE && (now - lastActivityMillis > SLEEP_TIMEOUT_MS)) {
    currentEyeState = STATE_SLEEPY;
    stateTimer = now;
  }

  if (currentEyeState == STATE_SLEEPY && (now - stateTimer > 3000)) {
    currentEyeState = STATE_SLEEPING;
  }
}

// ==========================================
// 11. REMINDER ENGINE
// ==========================================
void updateReminderEngine() {
  if (activeReminderIndex != -1) return; // Already displaying an active reminder

  for (int i = 0; i < reminderCount; i++) {
    if (reminders[i].enabled && !reminders[i].acknowledgedToday) {
      if (reminders[i].timeMinutes == currentMinutesOfDay && currentMinutesOfDay != lastTriggeredMinute) {
        lastTriggeredMinute = currentMinutesOfDay;
        triggerReminder(i);
        break;
      }
    }
  }
}

void triggerReminder(int index) {
  activeReminderIndex = index;
  currentEyeState = STATE_REMINDER;
  lastActivityMillis = millis();
  playBuzzerPattern(BUZZ_REMINDER);
  Serial.print(F("REMINDER TRIGGERED: "));
  Serial.println(reminders[index].title);

  StaticJsonDocument<256> doc;
  doc["type"] = "REMINDER_TRIGGERED";
  doc["id"] = reminders[index].id;
  doc["title"] = reminders[index].title;
  doc["time"] = reminders[index].timeStr;
  sendBleJson(doc);
}

void acknowledgeActiveReminder() {
  if (activeReminderIndex != -1) {
    reminders[activeReminderIndex].acknowledgedToday = true;
    playBuzzerPattern(BUZZ_ACK);

    Serial.print(F("REMINDER ACKNOWLEDGED: "));
    Serial.println(reminders[activeReminderIndex].id);

    StaticJsonDocument<256> doc;
    doc["type"] = "REMINDER_ACK";
    doc["id"] = reminders[activeReminderIndex].id;
    sendBleJson(doc);

    activeReminderIndex = -1;
    currentEyeState = STATE_ACKNOWLEDGED;
    reactionEndMillis = millis() + 3000;
  }
}

// ==========================================
// 12. DESK BUDDY FACE / EYE ANIMATION ENGINE (30 FPS)
//     Premium "Mochi" rig v2 - spring-driven, fully non-blocking.
//
//     CORE   : squash & stretch eyes, gaze parallax, micro-saccades, blinks,
//              breathing, eyelids, arc-eyes, expressive mouth, shutter cuts.
//     EVENTS : app connects / disconnects, reminder added, SOS cancelled,
//              first reminder of the day, repeated taps (wink / purr / tickle),
//              being poked too much (dizzy), finger-hold progress feedback.
//     IDLE   : bored eye-roll, daydream bubble, cross-eyed, hiccup, startled,
//              smug, shy.  Late-night drowsy face + OLED dimming.
//     ALERTS : reminder card escalates (faster rings, "!" marks, pleading face).
//
//     This layer only READS the firmware state (touch, BLE, reminders, SOS)
//     and DRAWS.  It never changes what the device does.
// ==========================================

#define FX_ON            SSD1306_WHITE
#define FX_OFF           SSD1306_BLACK
#define FX_EYE_L_X       38
#define FX_EYE_R_X       90
#define FX_EYE_Y         26
#define FX_EYE_W         28.0f
#define FX_EYE_H         32.0f
#define FX_EYE_CORNER    11
#define FX_MOUTH_X       64
#define FX_MOUTH_Y       49
#define FX_HOLD_SHOW_MS  350      // finger held this long -> "determined" face + progress line
#define FX_TAP_WINDOW_MS 4500     // taps closer than this count as petting
#define FX_PLEAD_PERIOD  8000     // escalated reminder: pleading face every 8 s ...
#define FX_PLEAD_LEN     1600     // ... for 1.6 s

// (FxEmo / FxHappyVar / FxPose live in section 4b, above setup(), so the Arduino IDE's auto-prototypes can see them)

// ---------- math helpers ----------
static inline float easeInOutQuad(float t) {
  if (t < 0.5f) return 2.0f * t * t;
  float v = -2.0f * t + 2.0f;
  return 1.0f - (v * v) / 2.0f;
}
static inline float easeOutBack(float t) {
  const float c1 = 1.70158f;
  const float c3 = c1 + 1.0f;
  float u = t - 1.0f;
  return 1.0f + c3 * u * u * u + c1 * u * u;
}
static inline float lerpf(float a, float b, float t) { return a + (b - a) * t; }
static inline int clampi(int v, int lo, int hi) { return v < lo ? lo : (v > hi ? hi : v); }
static inline float fxClamp(float v, float lo, float hi) { return v < lo ? lo : (v > hi ? hi : v); }
static inline float easeOutCubic(float t) {
  t = 1.0f - fxClamp(t, 0.0f, 1.0f);
  return 1.0f - t * t * t;
}
static inline int fxRound(float v) { return (int)lroundf(v); }

static inline bool fxIsCard(EyeState s) {
  return s == STATE_STATUS || s == STATE_REMINDER ||
         s == STATE_SOS_COUNTDOWN || s == STATE_SOS_SENT;
}
// the "calm face" states on which small emotions may play
static inline bool fxIdleLike() {
  return currentEyeState == STATE_IDLE || currentEyeState == STATE_BLINKING ||
         currentEyeState == STATE_LOOK_LEFT || currentEyeState == STATE_LOOK_RIGHT;
}
static inline bool fxIsNight() {
  return currentMinutesOfDay >= FX_NIGHT_START_MIN || currentMinutesOfDay < FX_NIGHT_END_MIN;
}
// shared by animation + buzzer so look and sound escalate together
static inline int reminderLevelFromMs(uint32_t ms) {
  return ms >= REMINDER_ESCALATE_2_MS ? 2 : (ms >= REMINDER_ESCALATE_1_MS ? 1 : 0);
}

// ---------- spring (gives the face its "life") ----------
struct Spring {
  float x, v, target, stiff, damp;
  void snap(float value) { x = value; v = 0.0f; target = value; }
  void kick(float impulse) { v += impulse; }
  void step(float dt) {
    float h = dt * 0.5f;                       // 2 sub-steps = stable at 30 FPS
    for (int i = 0; i < 2; i++) {
      float a = stiff * (target - x) - damp * v;
      v += a * h;
      x += v * h;
    }
  }
};

//                      x      v     target     stiffness  damping
Spring fxEyeW   = { FX_EYE_W, 0.0f, FX_EYE_W, 320.0f, 19.0f };
Spring fxEyeH   = { 3.0f,     0.0f, FX_EYE_H, 320.0f, 19.0f };  // starts closed -> opens
Spring fxLookX  = { 0.0f,     0.0f, 0.0f,     240.0f, 26.0f };
Spring fxLookY  = { 0.0f,     0.0f, 0.0f,     240.0f, 26.0f };
Spring fxFaceY  = { 0.0f,     0.0f, 0.0f,     200.0f, 22.0f };
Spring fxLidTop = { 0.0f,     0.0f, 0.0f,     300.0f, 30.0f };
Spring fxSlope  = { 0.0f,     0.0f, 0.0f,     300.0f, 30.0f };
Spring fxLidBot = { 0.0f,     0.0f, 0.0f,     300.0f, 30.0f };
Spring fxArc    = { 0.0f,     0.0f, 0.0f,     480.0f, 42.0f };
Spring fxBend   = { 0.0f,     0.0f, 0.0f,     220.0f, 24.0f };
Spring fxAsym   = { 0.0f,     0.0f, 0.0f,     260.0f, 24.0f };
Spring fxCross  = { 0.0f,     0.0f, 0.0f,     260.0f, 26.0f };  // cross-eyed
Spring fxWink   = { 0.0f,     0.0f, 0.0f,     520.0f, 44.0f };  // right eye closes alone
Spring fxMCurve = { 0.75f,    0.0f, 0.75f,    240.0f, 22.0f };
Spring fxMWidth = { 12.0f,    0.0f, 12.0f,    240.0f, 22.0f };
Spring fxMOpen  = { 0.0f,     0.0f, 0.0f,     260.0f, 22.0f };
Spring fxBlush  = { 0.0f,     0.0f, 0.0f,     160.0f, 20.0f };

// ---------- animation bookkeeping ----------
float    fxShine = 1.0f, fxShineT = 1.0f;
int      fxMouthStyle = 0;
int      fxEyeMode = 0;
float    fxPoseBlink = 0.0f, fxPoseSway = 0.0f;
EyeState fxLastState = STATE_STARTUP;
bool     fxBooted = false;
uint32_t fxEnterMs = 0;
uint32_t fxPopMs = 0;
uint32_t fxShutterMs = 0;
uint32_t fxLastReactionEnd = 0;
uint32_t fxNextSaccadeMs = 0;
uint32_t fxNextMoodMs = 0;
uint32_t fxMoodUntilMs = 0;
uint32_t fxSosPopMs = 0;
float    fxSaccX = 0.0f, fxSaccY = 0.0f, fxGlanceY = 0.0f;
bool     fxDoubleBlink = false;
bool     fxWakePopped = false;
int      fxLastSosSecond = -1;

// emotion / event layer
FxEmo    fxEmo = FXE_NONE;
uint32_t fxEmoStart = 0, fxEmoDur = 0;
uint8_t  fxEmoFlags = 0;
uint8_t  fxDreamIcon = 0;
FxEmo    fxLastIdleEmo = FXE_NONE;
uint8_t  fxHappyVar = FXH_NORMAL;
int      fxTapCount = 0;
uint32_t fxLastTapMs = 0;
int      fxIrrCount = 0;
uint32_t fxLastIrrMs = 0;
bool     fxDizzyAfter = false;
bool     fxAckStar = false;
bool     fxPleadActive = false;
bool     fxEventsInit = false;
bool     fxLastConn = false;
int      fxLastReminderCount = 0;
uint32_t fxNextIdleEmoMs = 0;
uint32_t fxBoredStamp = 0xFFFFFFFFUL;
bool     fxDetermined = false;
float    fxHoldFrac = 0.0f;
bool     fxDimmed = false;

// ==========================================
// 12a. DRAWING PRIMITIVES
// ==========================================

// Quadratic bezier "pen stroke" with round caps (thick 1..5).
void fxStrokeBezier(float x0, float y0, float cx, float cy, float x2, float y2, int thick) {
  float dx = x2 - x0, dy = y2 - y0;
  float len = sqrtf(dx * dx + dy * dy) + fabsf(cy - y0) * 0.8f;
  int steps = (int)fxClamp(len * 1.2f, 8.0f, 56.0f);
  int rad = thick / 2;
  int pxi = fxRound(x0), pyi = fxRound(y0);
  for (int i = 0; i <= steps; i++) {
    float t = (float)i / (float)steps;
    float mt = 1.0f - t;
    int xi = fxRound(mt * mt * x0 + 2.0f * mt * t * cx + t * t * x2);
    int yi = fxRound(mt * mt * y0 + 2.0f * mt * t * cy + t * t * y2);
    if (thick >= 3) {
      display.fillCircle(xi, yi, rad, FX_ON);
    } else {
      if (i > 0) {
        display.drawLine(pxi, pyi, xi, yi, FX_ON);
        if (thick == 2) display.drawLine(pxi, pyi + 1, xi, yi + 1, FX_ON);
      } else {
        display.drawPixel(xi, yi, FX_ON);
      }
    }
    pxi = xi; pyi = yi;
  }
}

// Partial circle outline, 2px thick. Angles in degrees, 0 = 3 o'clock, clockwise.
void fxArcPolar(int cx, int cy, int r, float a0, float a1) {
  for (float a = a0; a <= a1; a += 3.0f) {
    float rad = a * 0.0174533f;
    float c = cosf(rad), s = sinf(rad);
    display.drawPixel(cx + fxRound(c * r), cy + fxRound(s * r), FX_ON);
    display.drawPixel(cx + fxRound(c * (r - 1)), cy + fxRound(s * (r - 1)), FX_ON);
  }
}

void fxSparkle(int x, int y, int s) {
  if (s <= 0) { display.drawPixel(x, y, FX_ON); return; }
  display.drawFastHLine(x - s, y, 2 * s + 1, FX_ON);
  display.drawFastVLine(x, y - s, 2 * s + 1, FX_ON);
  if (s >= 3) {
    display.drawPixel(x - 1, y - 1, FX_ON); display.drawPixel(x + 1, y - 1, FX_ON);
    display.drawPixel(x - 1, y + 1, FX_ON); display.drawPixel(x + 1, y + 1, FX_ON);
  }
}

// tiny 5x4 heart, top-left at (x,y)
void fxMiniHeart(int x, int y) {
  display.drawPixel(x + 1, y, FX_ON);
  display.drawPixel(x + 3, y, FX_ON);
  display.drawFastHLine(x, y + 1, 5, FX_ON);
  display.drawFastHLine(x + 1, y + 2, 3, FX_ON);
  display.drawPixel(x + 2, y + 3, FX_ON);
}

// big glossy heart (heart-eyes). half = half width in px
void fxDrawHeart(int cx, int cy, int half) {
  int rr = half / 2;
  if (rr < 1) rr = 1;
  display.fillCircle(cx - rr, cy - rr / 2, rr, FX_ON);
  display.fillCircle(cx + rr, cy - rr / 2, rr, FX_ON);
  display.fillTriangle(cx - 2 * rr, cy - rr / 2 + 1, cx + 2 * rr, cy - rr / 2 + 1, cx, cy + 2 * rr + 1, FX_ON);
  if (rr >= 4) display.fillCircle(cx - rr, cy - rr + 1, rr / 3 > 0 ? rr / 3 : 1, FX_OFF);   // gloss
}

// 4-point twinkle star (star-eyes), R = tip radius, rot in radians
void fxDrawStar4(int cx, int cy, int R, float rot) {
  float r2 = (float)R * 0.38f;
  int px[8], py[8];
  for (int i = 0; i < 8; i++) {
    float a = rot + (float)i * 0.785398f;
    float rad = (i % 2 == 0) ? (float)R : r2;
    px[i] = cx + fxRound(cosf(a) * rad);
    py[i] = cy + fxRound(sinf(a) * rad);
  }
  for (int i = 0; i < 8; i++) {
    int j = (i + 1) & 7;
    display.fillTriangle(cx, cy, px[i], py[i], px[j], py[j], FX_ON);
  }
}

// hypnotic spiral eye (dizzy)
void fxDrawSpiral(int cx, int cy, int R, float phase) {
  display.drawCircle(cx, cy, R, FX_ON);
  int px = cx, py = cy;
  for (float a = 0.0f; a <= 12.0f; a += 0.3f) {
    float r = (float)(R - 2) * a / 12.0f;
    float th = a + phase;
    int x = cx + fxRound(cosf(th) * r);
    int y = cy + fxRound(sinf(th) * r);
    if (a > 0.0f) {
      display.drawLine(px, py, x, y, FX_ON);
      display.drawLine(px + 1, py, x + 1, y, FX_ON);
    }
    px = x; py = y;
  }
}

// teardrop, tip at (x,y)  (sweat / tear)
void fxDrawDrop(int x, int y, bool big) {
  int r = big ? 3 : 2;
  display.fillTriangle(x, y, x - r, y + r + 1, x + r, y + r + 1, FX_ON);
  display.fillCircle(x, y + r + 2, r, FX_ON);
}

// Bluetooth rune centred on (x,y)
void fxDrawBT(int x, int y) {
  display.drawLine(x, y - 6, x, y + 6, FX_ON);
  display.drawLine(x, y - 6, x + 4, y - 2, FX_ON);
  display.drawLine(x + 4, y - 2, x - 3, y + 4, FX_ON);
  display.drawLine(x, y + 6, x + 4, y + 2, FX_ON);
  display.drawLine(x + 4, y + 2, x - 3, y - 4, FX_ON);
}

// y position of a line of text that slides up from below the screen
int fxSlideY(int finalY, uint32_t t, uint32_t delayMs) {
  if (t < delayMs) return SCREEN_HEIGHT + 2;
  float p = easeOutCubic((float)(t - delayMs) / 260.0f);
  return fxRound(lerpf((float)(SCREEN_HEIGHT + 2), (float)finalY, p));
}

// centred text line that slides up
void fxTextSlide(const char* s, int finalY, uint32_t t, uint32_t delayMs) {
  int w = (int)strlen(s) * 6;
  display.setTextSize(1);
  display.setCursor((SCREEN_WIDTH - w) / 2, fxSlideY(finalY, t, delayMs));
  display.print(s);
}

// Ping-pong marquee for text wider than the screen
void fxPrintMarquee(const char* s, int y, uint32_t now) {
  int w = (int)strlen(s) * 6;
  display.setTextSize(1);
  int off = 0;
  if (w > SCREEN_WIDTH) {
    int span = w - SCREEN_WIDTH;
    uint32_t run = (uint32_t)span * 45U;
    uint32_t p = now % (run + 1800U);
    if (p < 900U) off = 0;
    else if (p < 900U + run) off = (int)((p - 900U) / 45U);
    else off = span;
  }
  display.setCursor(-off, y);
  display.print(s);
}

// ---------- eyes ----------
// Solid rounded eye with eyelid system.
//   lidTop : 0..1 portion of the eye covered from the top
//   slope  : +1 angry (inner corner lower)  ...  -1 droopy/sad
//   side   : -1 left eye, +1 right eye
//   lidBot : 0..1 cheek-lift (smiling squint)
//   shine  : 1 glossy, 2 big "pleading" gloss, <-0.5 tiny shocked pupil
void fxDrawSolidEye(int cx, int cy, int w, int h, int r, float lidTop, float slope,
                    int side, float lidBot, float shDx, float shDy, float shine) {
  if (w < 4) w = 4;
  if (h < 3) h = 3;
  int x0 = cx - w / 2;
  int y0 = cy - h / 2;
  int rmax = (w < h ? w : h) / 2;
  if (r > rmax) r = rmax;
  if (r < 1) r = 1;
  display.fillRoundRect(x0, y0, w, h, r, FX_ON);

  int sx = cx + fxRound(shDx);
  int sy = cy + fxRound(shDy);
  if (shine < -0.5f) {
    display.fillCircle(sx, sy, 2, FX_OFF);                       // shocked: tiny pupil
  } else if (shine > 1.5f && h >= 14) {
    display.fillCircle(sx - w / 5, sy - h / 5, 5, FX_OFF);       // pleading: huge gloss
    display.fillCircle(sx + w / 5, sy + h / 6, 2, FX_OFF);
    display.fillCircle(sx - w / 5 + 4, sy - h / 5 + 6, 1, FX_OFF);
  } else if (shine > 0.25f && h >= 14) {
    display.fillCircle(sx - w / 6 - 1, sy - h / 5, 3, FX_OFF);
    display.fillCircle(sx + w / 6 + 1, sy + h / 6, 1, FX_OFF);
  }

  // upper lid (angry / sleepy / sad)
  if (lidTop > 0.01f) {
    float cover = lidTop * (float)h;
    float tilt = slope * (float)w * 0.5f;
    float cOut = fxClamp(cover - tilt * 0.5f, 0.0f, (float)h);
    float cIn  = fxClamp(cover + tilt * 0.5f, 0.0f, (float)h);
    int cL = fxRound(side < 0 ? cOut : cIn);
    int cR = fxRound(side < 0 ? cIn : cOut);
    int xl = x0 - 1, xr = x0 + w;
    display.fillTriangle(xl, y0 - 1, xr, y0 - 1, xr, y0 + cR, FX_OFF);
    display.fillTriangle(xl, y0 - 1, xr, y0 + cR, xl, y0 + cL, FX_OFF);
  }

  // cheek lift (smiling squint)
  if (lidBot > 0.02f) {
    int cut = fxRound(lidBot * (float)h);
    int R = (int)((float)w * 0.80f) + 2;
    display.fillCircle(cx, (y0 + h) - cut + R, R, FX_OFF);
  }
}

// Closed / smiling eye drawn as a thick curved stroke.
// bend > 0 : happy arch (^)    bend < 0 : sleeping curve (u)
void fxDrawArcEye(int cx, int cy, int w, float bend, int thick) {
  float half = (float)w * 0.5f - (float)thick * 0.5f;
  const float A = 9.0f;
  float yEnd = (float)cy + bend * A * 0.5f;
  float yCtl = (float)cy - bend * A * 1.5f;
  fxStrokeBezier((float)cx - half, yEnd, (float)cx, yCtl, (float)cx + half, yEnd, thick);
}

// ---------- mouth ----------
void fxDrawMouth(int cx, int cy, float curve, float mw, float open, int style, uint32_t now) {
  float half = mw * 0.5f;
  switch (style) {
    case 1: {   // gritted teeth (irritated)
      int tw = 18, th = 8;
      int x = cx - tw / 2, y = cy - th / 2 + 1;
      display.drawRoundRect(x, y, tw, th, 2, FX_ON);
      display.drawFastHLine(x + 1, y + th / 2, tw - 2, FX_ON);
      for (int i = 1; i < 5; i++) display.drawFastVLine(x + (i * tw) / 5, y + 1, th - 2, FX_ON);
      break;
    }
    case 2: {   // wobble (confused / dizzy / pleading)
      int px = 0, py = 0;
      for (int i = -7; i <= 7; i++) {
        int y = cy + fxRound(sinf((float)i * 0.75f + (float)now * 0.009f) * 1.6f);
        if (i > -7) display.drawLine(cx + px, py, cx + i, y, FX_ON);
        px = i; py = y;
      }
      break;
    }
    case 3: {   // 'o' (yawn / surprise / exhale)
      int rr = 1 + (int)(open * 4.0f);
      display.fillCircle(cx, cy + 1, rr, FX_ON);
      if (rr >= 3) display.fillCircle(cx, cy + 1, rr - 2, FX_OFF);
      break;
    }
    case 4:     // hidden
      break;
    case 5:     // smirk (smug)
      fxStrokeBezier((float)(cx - 6), (float)(cy + 2), (float)(cx + 1), (float)(cy + 4),
                     (float)(cx + 8), (float)(cy - 2), 2);
      break;
    default: {  // smile / frown / open laugh
      float c = curve * 5.0f;
      float yEnd = (float)cy - c * 0.5f;
      float yCtl = (float)cy + c * 1.5f;
      if (open > 0.06f && curve > 0.2f) {
        int mh = (int)(open * 9.0f);
        int top = fxRound(yEnd);
        for (int dy = 0; dy <= mh; dy++) {
          float u = (float)dy / ((float)mh + 0.6f);
          int hw = fxRound(half * sqrtf(fxClamp(1.0f - u * u, 0.0f, 1.0f)));
          display.drawFastHLine(cx - hw, top + dy, 2 * hw + 1, FX_ON);
        }
        if (mh >= 6) display.fillCircle(cx, top + mh - 1, 2, FX_OFF);   // tongue
      } else {
        fxStrokeBezier((float)cx - half, yEnd, (float)cx, yCtl, (float)cx + half, yEnd, 2);
      }
      break;
    }
  }
}

void fxDrawBlush(int cx, int cy, float amount) {
  if (amount < 0.15f) return;
  int n = amount > 0.75f ? 3 : (amount > 0.45f ? 2 : 1);
  for (int i = 0; i < n; i++) {
    int x = cx - 3 + i * 3;
    display.drawLine(x, cy + 3, x + 2, cy, FX_ON);
  }
}

// check-mark that draws itself (p = 0..1)
void fxDrawCheckProgress(int cx, int cy, float p) {
  float d = fxClamp(p, 0.0f, 1.0f) * 24.2f;
  float a = fxClamp(d / 7.8f, 0.0f, 1.0f);
  for (int t = 0; t < 2; t++) {
    display.drawLine(cx - 7, cy + t, cx - 7 + fxRound(5.0f * a), cy + t + fxRound(6.0f * a), FX_ON);
  }
  if (d > 7.8f) {
    float b = fxClamp((d - 7.8f) / 16.4f, 0.0f, 1.0f);
    for (int t = 0; t < 2; t++) {
      display.drawLine(cx - 2, cy + 6 + t, cx - 2 + fxRound(10.0f * b), cy + 6 + t - fxRound(13.0f * b), FX_ON);
    }
  }
}

// swinging bell (pivot at the top). ang in radians
void fxDrawBell(int cx, int top, float ang) {
  float c = cosf(ang), s = sinf(ang);
  #define FX_RX(px, py) (cx + fxRound((px) * c - (py) * s))
  #define FX_RY(px, py) (top + fxRound((px) * s + (py) * c))
  display.fillCircle(FX_RX(0.0f, 5.0f), FX_RY(0.0f, 5.0f), 4, FX_ON);
  display.fillTriangle(FX_RX(-4.0f, 6.0f), FX_RY(-4.0f, 6.0f),
                       FX_RX(4.0f, 6.0f),  FX_RY(4.0f, 6.0f),
                       FX_RX(6.5f, 12.0f), FX_RY(6.5f, 12.0f), FX_ON);
  display.fillTriangle(FX_RX(-4.0f, 6.0f), FX_RY(-4.0f, 6.0f),
                       FX_RX(6.5f, 12.0f), FX_RY(6.5f, 12.0f),
                       FX_RX(-6.5f, 12.0f), FX_RY(-6.5f, 12.0f), FX_ON);
  display.drawLine(FX_RX(-7.0f, 12.5f), FX_RY(-7.0f, 12.5f), FX_RX(7.0f, 12.5f), FX_RY(7.0f, 12.5f), FX_ON);
  display.fillCircle(FX_RX(0.0f, -1.0f), FX_RY(0.0f, -1.0f), 1, FX_ON);
  display.fillCircle(cx + fxRound(-ang * 8.0f), top + 15, 1, FX_ON);
  #undef FX_RX
  #undef FX_RY
}

// ==========================================
// 12b. EVENTS, EMOTIONS & STATE LOGIC
// ==========================================

float fxBlinkAmount(uint32_t e) {
  const float CLOSE = 80.0f, HOLD = 25.0f, OPEN = 105.0f;
  float f = (float)e;
  if (f < CLOSE) { float t = f / CLOSE; return t * t; }
  if (f < CLOSE + HOLD) return 1.0f;
  if (f < CLOSE + HOLD + OPEN) { float t = (f - CLOSE - HOLD) / OPEN; float u = 1.0f - t; return u * u * u; }
  return 0.0f;
}

// Start a small emotion (only while the face is calm). Each carries its own sound.
void fxStartEmo(FxEmo e, uint32_t now) {
  if (!fxIdleLike() || fxDetermined) return;
  fxEmo = e;
  fxEmoStart = now;
  fxEmoFlags = 0;
  switch (e) {
    case FXE_CONNECT:   fxEmoDur = 2300; playBuzzerPattern(BUZZ_CONNECT);    fxFaceY.kick(-70.0f); fxEyeH.kick(120.0f); break;
    case FXE_WORRIED:   fxEmoDur = 4800; playBuzzerPattern(BUZZ_DISCONNECT); fxFaceY.kick(30.0f); break;
    case FXE_WINK_NOD:  fxEmoDur = 1400; playBuzzerPattern(BUZZ_WINK);       fxFaceY.kick(60.0f); break;
    case FXE_SHY:       fxEmoDur = 1900; playBuzzerPattern(BUZZ_SHY);        break;
    case FXE_DIZZY:     fxEmoDur = 2900; playBuzzerPattern(BUZZ_DIZZY);      break;
    case FXE_BORED:     fxEmoDur = 1700; playBuzzerPattern(BUZZ_BORED);      break;
    case FXE_DAYDREAM:  fxEmoDur = 3400; playBuzzerPattern(BUZZ_DAYDREAM);   fxDreamIcon = (uint8_t)random(0, 3); break;
    case FXE_CROSSEYED: fxEmoDur = 1900; playBuzzerPattern(BUZZ_BLIP);       break;
    case FXE_HICCUP:    fxEmoDur = 1900; break;                                   // sounds on each jolt
    case FXE_STARTLED:  fxEmoDur = 1500; playBuzzerPattern(BUZZ_STARTLE);    fxFaceY.kick(-150.0f); fxEyeH.kick(140.0f); break;
    case FXE_SMUG:      fxEmoDur = 2100; playBuzzerPattern(BUZZ_SMUG);       break;
    default: fxEmo = FXE_NONE; break;
  }
}

// One-shot actions + expiry of the running emotion
void fxEmoTick(uint32_t now) {
  if (fxEmo == FXE_NONE) return;
  if (!fxIdleLike() || fxDetermined) { fxEmo = FXE_NONE; return; }
  uint32_t te = now - fxEmoStart;
  if (te >= fxEmoDur) { fxEmo = FXE_NONE; return; }
  switch (fxEmo) {
    case FXE_WORRIED:
      if (te >= 3200 && !(fxEmoFlags & 1)) { fxEmoFlags |= 1; playBuzzerPattern(BUZZ_SAD); fxFaceY.kick(40.0f); }
      break;
    case FXE_HICCUP:
      if (te >= 300 && !(fxEmoFlags & 1)) { fxEmoFlags |= 1; playBuzzerPattern(BUZZ_HICCUP); fxFaceY.kick(-140.0f); fxEyeH.kick(-90.0f); }
      if (te >= 1100 && !(fxEmoFlags & 2)) { fxEmoFlags |= 2; playBuzzerPattern(BUZZ_HICCUP); fxFaceY.kick(-140.0f); fxEyeH.kick(-90.0f); }
      break;
    case FXE_CROSSEYED:
      if (te >= 1400 && !(fxEmoFlags & 1)) { fxEmoFlags |= 1; fxEyeH.kick(160.0f); fxFaceY.kick(-60.0f); }
      break;
    default: break;
  }
}

// Short taps: normal -> sometimes a wink -> petting (3) -> tickle (5)
void fxHappyTap(uint32_t now, bool again) {
  if (now - fxLastTapMs > FX_TAP_WINDOW_MS) fxTapCount = 0;
  fxTapCount++;
  fxLastTapMs = now;
  if (fxTapCount >= 5)      fxHappyVar = FXH_TICKLE;
  else if (fxTapCount == 4) fxHappyVar = FXH_KISS;
  else if (fxTapCount >= 3) fxHappyVar = FXH_PET;
  else                      fxHappyVar = (random(0, 100) < 28) ? FXH_WINK : FXH_NORMAL;
  fxEnterMs = now;
  fxPopMs = now;
  switch (fxHappyVar) {
    case FXH_WINK:   playBuzzerPattern(BUZZ_WINK);   fxFaceY.kick(-90.0f); break;
    case FXH_PET:    playBuzzerPattern(BUZZ_PURR);   fxFaceY.kick(-60.0f); fxEyeW.kick(30.0f); break;
    case FXH_TICKLE: playBuzzerPattern(BUZZ_GIGGLE); fxFaceY.kick(-120.0f); break;
    case FXH_KISS:   playBuzzerPattern(BUZZ_KISS);   fxFaceY.kick(-70.0f); break;
    default:         fxFaceY.kick(again ? -90.0f : -110.0f); if (again) fxEyeW.kick(30.0f); break;
  }
}

// Poked repeatedly -> dizzy afterwards
void fxIrritatedTap(uint32_t now) {
  if (now - fxLastIrrMs > 15000UL) fxIrrCount = 0;
  fxIrrCount++;
  fxLastIrrMs = now;
  if (fxIrrCount >= 3) fxDizzyAfter = true;
}

void fxOnStateEnter(EyeState prev, EyeState next, uint32_t now) {
  fxEnterMs = now;
  fxPopMs = now;
  fxShutterMs = 0;
  if (!fxBooted) {
    fxBooted = true;
    fxShutterMs = 260;                                         // first frame after boot: eyes "open"
  } else if (fxIsCard(next) || fxIsCard(prev)) {
    fxShutterMs = (next == STATE_SOS_COUNTDOWN || next == STATE_SOS_SENT) ? 130 : 220;
  }

  if (prev == STATE_HAPPY && next == STATE_IDLE &&
      (fxHappyVar == FXH_PET || fxHappyVar == FXH_TICKLE || fxHappyVar == FXH_KISS)) {
    fxHappyVar = FXH_NORMAL;
    fxStartEmo(FXE_SHY, now);                                  // petted -> a little shy afterwards
  }
  if (prev == STATE_IRRITATED && next == STATE_IDLE && fxDizzyAfter) {
    fxDizzyAfter = false;
    fxIrrCount = 0;
    fxStartEmo(FXE_DIZZY, now);                                // poked too much -> dizzy
  }

  switch (next) {
    case STATE_LOOK_LEFT:
    case STATE_LOOK_RIGHT:
      fxGlanceY = (random(0, 100) < 30) ? -3.0f : 0.0f;
      break;
    case STATE_HAPPY:
      if (prev == STATE_SOS_COUNTDOWN) {                       // SOS cancelled -> relief
        fxHappyVar = FXH_RELIEF;
        playBuzzerPattern(BUZZ_SIGH);
        fxFaceY.kick(40.0f);
      } else {
        fxHappyTap(now, false);
      }
      break;
    case STATE_ACKNOWLEDGED: {
      int ackCount = 0;
      for (int i = 0; i < reminderCount; i++) if (reminders[i].acknowledgedToday) ackCount++;
      fxAckStar = (ackCount <= 1);                             // first confirmation of the day -> star eyes
      if (fxAckStar) playBuzzerPattern(BUZZ_CHEER);
      fxFaceY.kick(-110.0f);
      break;
    }
    case STATE_IRRITATED:
      fxFaceY.kick(-60.0f);
      fxIrritatedTap(now);
      break;
    case STATE_WAKING:
      fxWakePopped = false;
      break;
    case STATE_SOS_COUNTDOWN:
      fxLastSosSecond = sosCountdownSeconds;
      fxSosPopMs = now;
      break;
    default:
      break;
  }
}

// Detects state changes made by the touch / reminder / SOS code.
void fxDetectStateChange(uint32_t now) {
  if (currentEyeState != fxLastState) {
    EyeState prev = fxLastState;
    fxLastState = currentEyeState;
    fxOnStateEnter(prev, currentEyeState, now);
  } else if (reactionEndMillis > fxLastReactionEnd && fxLastReactionEnd != 0) {
    // same reaction triggered again (tapped again while it was still showing)
    if (currentEyeState == STATE_HAPPY) {
      fxHappyTap(now, true);
    } else if (currentEyeState == STATE_IRRITATED) {
      fxPopMs = now;
      fxFaceY.kick(-50.0f);
      fxIrritatedTap(now);
    }
  }
  fxLastReactionEnd = reactionEndMillis;
}

// finger-hold feedback: determined face + progress line toward the SOS hold
void fxComputeHold(uint32_t now) {
  fxDetermined = false;
  fxHoldFrac = 0.0f;
  if (tState == T_PRESSED && fxIdleLike()) {
    uint32_t hold = now - touchStartMs;
    if (hold >= FX_HOLD_SHOW_MS) {
      fxDetermined = true;
      fxHoldFrac = fxClamp((float)hold / (float)SOS_HOLD_MS, 0.0f, 1.0f);
    }
  }
}

// Watches connection / reminder list / inactivity and fires emotions
void fxPollEvents(uint32_t now) {
  if (!fxEventsInit) {
    fxEventsInit = true;
    fxLastConn = deviceConnected;
    fxLastReminderCount = reminderCount;
    fxNextIdleEmoMs = now + (uint32_t)random(12000, 22000);
  }

  if (deviceConnected != fxLastConn) {
    fxLastConn = deviceConnected;
    if (fxIdleLike()) fxStartEmo(deviceConnected ? FXE_CONNECT : FXE_WORRIED, now);
  }

  if (reminderCount != fxLastReminderCount) {
    bool added = reminderCount > fxLastReminderCount;
    fxLastReminderCount = reminderCount;
    if (added && fxEmo == FXE_NONE && fxIdleLike()) fxStartEmo(FXE_WINK_NOD, now);
  }

  if (fxEmo == FXE_NONE && fxIdleLike() && !fxDetermined) {
    uint32_t inactive = now - lastActivityMillis;
    if (inactive > 10000UL && inactive < (uint32_t)SLEEP_TIMEOUT_MS - 4000UL &&
        fxBoredStamp != lastActivityMillis) {
      fxBoredStamp = lastActivityMillis;                       // once per quiet spell, before it dozes off
      fxStartEmo(FXE_BORED, now);
    } else if (now >= fxNextIdleEmoMs) {
      static const FxEmo POOL[6] = { FXE_DAYDREAM, FXE_CROSSEYED, FXE_HICCUP,
                                     FXE_STARTLED, FXE_SMUG, FXE_DAYDREAM };
      FxEmo pick = POOL[random(0, 6)];
      if (pick == fxLastIdleEmo) pick = POOL[random(0, 6)];
      fxLastIdleEmo = pick;
      fxStartEmo(pick, now);
      fxNextIdleEmoMs = now + (uint32_t)random(14000, 30000);
    }
  }
}

// Idle behaviours: blinks, glances, saccades, moods. State timeouts for
// reactions are still owned by updateSleepEngine().
void fxAdvanceStateLogic(uint32_t now) {
  if (now >= fxNextSaccadeMs) {
    fxSaccX = (float)random(-20, 21) / 10.0f;
    fxSaccY = (float)random(-8, 9) / 10.0f;
    fxNextSaccadeMs = now + (uint32_t)random(650, 2300);
  }

  if (fxNextMoodMs == 0) {
    fxNextMoodMs = now + (uint32_t)random(15000, 30000);
  } else if (currentEyeState == STATE_IDLE && fxEmo == FXE_NONE && now >= fxNextMoodMs) {
    fxMoodUntilMs = now + 1800;
    fxNextMoodMs = now + (uint32_t)random(18000, 40000);
  }

  switch (currentEyeState) {
    case STATE_IDLE:
      if (fxEmo != FXE_NONE || fxDetermined) break;            // an emotion / a held finger owns the face
      if (now >= nextBlinkMillis) {
        currentEyeState = STATE_BLINKING;
        stateTimer = now;
        nextBlinkMillis = now + (uint32_t)random(3200, 6800);
        fxDoubleBlink = (random(0, 100) < 18);
      } else if (now >= nextGlanceMillis) {
        currentEyeState = (random(0, 2) == 0) ? STATE_LOOK_LEFT : STATE_LOOK_RIGHT;
        stateTimer = now;
        nextGlanceMillis = now + (uint32_t)random(5000, 10000);
      }
      break;

    case STATE_BLINKING:
      if (now - stateTimer >= 210) {
        currentEyeState = STATE_IDLE;
        if (fxDoubleBlink) {
          fxDoubleBlink = false;
          nextBlinkMillis = now + 110;
        }
      }
      break;

    case STATE_LOOK_LEFT:
    case STATE_LOOK_RIGHT:
      if (now - stateTimer > 1500) currentEyeState = STATE_IDLE;
      break;

    case STATE_WAKING:
      if (now - stateTimer >= 640) currentEyeState = STATE_IDLE;
      break;

    default:
      break;
  }
}

// ---------- emotion poses ----------
void fxPoseEmotion(FxPose& p, uint32_t now) {
  uint32_t te = now - fxEmoStart;
  float t = (float)te;
  p.lookX = 0.0f; p.lookY = 0.0f;

  switch (fxEmo) {
    case FXE_CONNECT:                                          // wide-eyed hello, happy blink, content smile
      p.lookY = -1.0f;
      if (te < 780) { p.eyeW = 31.0f; p.eyeH = 38.0f; p.mCurve = 0.9f; p.mOpen = 0.7f; p.blush = 0.6f; }
      else {
        if (te < 990) p.blink = fxBlinkAmount(te - 780);
        p.eyeW = 30.0f; p.eyeH = 36.0f;
        p.lidBot = (te >= 990) ? 0.22f : 0.0f;
        p.mCurve = 1.0f; p.mWidth = 14.0f; p.blush = 1.0f; p.mOpen = (te < 990) ? 0.7f : 0.0f;
      }
      break;

    case FXE_WORRIED:                                          // searching... then sad with a tear
      p.blush = 0.0f; p.mWidth = 9.0f;
      if (te < 3200) {
        p.lidTop = 0.22f; p.slope = -0.6f; p.eyeH = 34.0f; p.faceY = 1.0f;
        p.lookX = sinf(t * 0.0075f) * 8.0f; p.mCurve = -0.5f;
      } else {
        p.lidTop = 0.30f; p.slope = -0.7f; p.eyeH = 32.0f; p.faceY = 2.0f;
        p.lookY = 3.0f; p.mCurve = -0.9f;
      }
      break;

    case FXE_WINK_NOD:                                         // "got it" - wink + nod
      p.mCurve = 1.0f; p.mWidth = 14.0f; p.blush = 1.0f; p.faceY = -1.0f;
      if (te >= 100 && te < 900) { p.wink = 1.0f; p.bend = 1.0f; p.lidBot = 0.12f; }
      break;

    case FXE_SHY:                                              // looks away, blushes, peeks back
      p.lookX = (te < 1100) ? -7.0f : 3.0f; p.lookY = 3.0f;
      p.eyeW = 26.0f; p.eyeH = 30.0f; p.mCurve = 0.3f; p.mWidth = 6.0f; p.blush = 1.0f; p.faceY = 1.0f;
      break;

    case FXE_DIZZY: {                                          // spiral eyes, wobble
      float fade = 1.0f - 0.6f * ((float)te / (float)fxEmoDur);
      p.eyeMode = 3; p.eyeW = 26.0f; p.eyeH = 26.0f; p.mStyle = 2; p.blush = 0.0f;
      p.sway = sinf(t * 0.012f) * 3.0f * fade;
      p.faceY = sinf(t * 0.02f) * 1.0f;
      break;
    }

    case FXE_BORED:                                            // eye-roll, then unimpressed
      if (te < 900) {
        float a = (t / 900.0f) * 3.14159f;
        p.lookX = cosf(a) * 8.0f; p.lookY = -sinf(a) * 6.0f;
        p.mCurve = 0.2f; p.mWidth = 8.0f; p.blush = 0.0f;
      } else {
        p.lookX = -3.0f; p.lookY = 1.0f; p.lidTop = 0.35f;
        p.mCurve = 0.0f; p.mWidth = 8.0f; p.blush = 0.0f;
      }
      break;

    case FXE_DAYDREAM:                                         // gazes up, thought bubble
      p.lookX = 4.0f; p.lookY = -3.0f; p.lidBot = 0.10f;
      p.mCurve = 0.85f; p.mWidth = 10.0f; p.blush = 0.6f;
      break;

    case FXE_CROSSEYED:                                        // silly cross-eyes, snaps back
      if (te < 1400) { p.cross = 1.0f; p.eyeH = 30.0f; p.mCurve = 0.4f; p.mWidth = 8.0f; }
      else { p.eyeH = 36.0f; p.mCurve = 0.9f; }
      break;

    case FXE_HICCUP:
      p.mCurve = 0.3f; p.mWidth = 8.0f; p.blush = 0.6f;
      break;

    case FXE_STARTLED:                                         // jump scare
      p.eyeW = 32.0f; p.eyeH = 40.0f; p.shine = -1.0f; p.lookY = -1.0f;
      p.mStyle = 3; p.mOpen = 1.0f; p.blush = 0.0f;
      break;

    case FXE_SMUG:                                             // half-lids, side glance, smirk
      p.lidTop = 0.38f; p.lookX = 6.0f; p.mStyle = 5; p.blush = 0.4f; p.eyeH = 32.0f;
      break;

    default: break;
  }
}

// Writes the target pose for the current state into the springs.
void fxApplyPose(uint32_t now, uint32_t inState) {
  FxPose p;
  p.eyeW = FX_EYE_W; p.eyeH = FX_EYE_H; p.lookX = fxSaccX; p.lookY = fxSaccY;
  p.faceY = 0.0f; p.lidTop = 0.0f; p.slope = 0.0f; p.lidBot = 0.0f;
  p.arc = 0.0f; p.bend = 0.0f; p.asym = 0.0f; p.cross = 0.0f; p.wink = 0.0f;
  p.mCurve = 0.75f; p.mWidth = 12.0f; p.mOpen = 0.0f; p.blush = 0.35f;
  p.shine = 1.0f; p.blink = 0.0f; p.sway = 0.0f;
  p.mStyle = 0; p.eyeMode = 0;

  switch (currentEyeState) {
    case STATE_LOOK_LEFT:
    case STATE_LOOK_RIGHT: {
      float dir = (currentEyeState == STATE_LOOK_LEFT) ? -1.0f : 1.0f;
      p.lookX = dir * 7.0f + fxSaccX * 0.3f;
      p.lookY = fxGlanceY;
      p.mCurve = 0.6f;
      break;
    }

    case STATE_HAPPY: {
      p.lookX = 0.0f; p.lookY = 0.0f; p.shine = 0.0f;
      switch (fxHappyVar) {
        case FXH_WINK:                                         // wink (right eye)
          p.eyeW = 29.0f; p.eyeH = 32.0f; p.wink = 1.0f; p.bend = 1.0f; p.shine = 1.0f;
          p.lidBot = 0.12f; p.faceY = -2.0f;
          p.mCurve = 1.0f; p.mWidth = 14.0f; p.blush = 1.0f;
          break;
        case FXH_PET:                                          // heart eyes + purr tremble
          p.eyeMode = 1; p.eyeW = 28.0f; p.eyeH = 26.0f; p.faceY = -2.0f;
          p.mCurve = 1.0f; p.mWidth = 12.0f; p.blush = 1.0f;
          p.sway = sinf((float)now * 0.09f) * 0.9f;
          break;
        case FXH_KISS:                                         // eyes shut, pursed lips, blowing a kiss
          p.arc = 1.0f; p.bend = 1.0f; p.eyeW = 26.0f; p.eyeH = 6.0f; p.faceY = -2.0f;
          p.mStyle = 3; p.mOpen = 0.3f; p.blush = 1.0f;
          break;
        case FXH_TICKLE:                                       // squeezed laughing eyes + squirm
          p.arc = 1.0f; p.bend = 1.0f; p.eyeW = 30.0f; p.eyeH = 6.0f;
          p.mCurve = 1.0f; p.mWidth = 15.0f; p.mOpen = 1.0f; p.blush = 1.0f;
          p.sway = sinf((float)now * 0.06f) * 3.0f;
          p.faceY = -3.0f + sinf((float)now * 0.045f) * 2.0f;
          break;
        case FXH_RELIEF:                                       // phew - exhale, then a soft smile
          p.arc = 1.0f; p.bend = -0.6f; p.eyeW = 30.0f; p.eyeH = 6.0f; p.blush = 0.6f;
          if (inState < 900) { p.mStyle = 3; p.mOpen = 0.8f; p.faceY = 2.0f; }
          else { p.mCurve = 0.9f; p.mWidth = 12.0f; p.faceY = -1.0f; }
          break;
        default:                                               // classic "Hello!"
          p.arc = 1.0f; p.bend = 1.0f; p.eyeW = 30.0f; p.eyeH = 6.0f;
          p.faceY = -3.0f;
          p.mCurve = 1.0f; p.mWidth = 15.0f; p.blush = 1.0f;
          p.mOpen = (inState < 900) ? 0.80f + 0.20f * sinf((float)inState * 0.022f) : 0.75f;
          break;
      }
      break;
    }

    case STATE_CONFUSED: {
      p.asym = 1.0f;
      p.lookX = sinf((float)now * 0.0035f) * 3.0f;
      p.lookY = -1.0f;
      p.mStyle = 2; p.blush = 0.0f;
      break;
    }

    case STATE_IRRITATED: {
      p.eyeW = 29.0f; p.eyeH = 27.0f; p.faceY = -3.0f;
      p.lidTop = 0.40f; p.slope = 1.0f;
      p.lookX = 0.0f; p.lookY = 1.0f;
      p.mStyle = 1; p.blush = 0.0f; p.shine = 0.5f;
      break;
    }

    case STATE_SLEEPY: {
      float k = fxClamp((float)inState / 2400.0f, 0.0f, 1.0f);
      p.eyeH = 30.0f;
      p.lidTop = lerpf(0.08f, 0.62f, k);
      p.slope = -0.35f;
      p.faceY = 2.0f + sinf((float)now * 0.0012f) * 1.5f * k;
      p.lookX = 0.0f; p.lookY = 1.0f;
      p.mCurve = 0.15f; p.mWidth = 8.0f; p.blush = 0.0f;
      break;
    }

    case STATE_SLEEPING: {
      p.arc = 1.0f; p.bend = -1.0f; p.eyeH = 6.0f;
      p.faceY = 4.0f; p.lookX = 0.0f; p.lookY = 0.0f;
      p.mCurve = 0.10f; p.mWidth = 6.0f; p.blush = 0.5f; p.shine = 0.0f;
      break;
    }

    case STATE_WAKING: {
      float e = (float)(now - stateTimer);
      p.lookX = 0.0f;
      if (e < 260.0f) {                                        // yawn + stretch
        p.arc = 1.0f; p.bend = 0.25f; p.eyeH = 6.0f;
        p.mStyle = 3; p.mOpen = easeOutCubic(e / 260.0f);
        p.faceY = 3.0f - 4.0f * (e / 260.0f);
        p.shine = 0.0f; p.blush = 0.4f;
      } else {                                                 // eyes pop open
        p.eyeH = FX_EYE_H + 2.0f; p.mCurve = 0.9f; p.lookY = -1.0f;
        if (!fxWakePopped) {
          fxWakePopped = true;
          fxEyeH.kick(240.0f);
          fxFaceY.kick(-70.0f);
        }
      }
      break;
    }

    case STATE_ACKNOWLEDGED: {
      p.lookX = 0.0f; p.lookY = 0.0f; p.faceY = -3.0f;
      p.mStyle = 4; p.blush = 1.0f; p.shine = 0.0f;
      if (fxAckStar) { p.eyeMode = 2; p.eyeW = 26.0f; p.eyeH = 26.0f; }     // first of the day: star eyes
      else           { p.arc = 1.0f; p.bend = 1.0f; p.eyeW = 30.0f; p.eyeH = 6.0f; }
      break;
    }

    case STATE_REMINDER:
      p.eyeH = FX_EYE_H + 2.0f; p.lookX = 0.0f; p.lookY = 0.0f;
      if (fxPleadActive) {                                     // big shiny pleading eyes
        p.eyeW = 30.0f; p.eyeH = 38.0f; p.lookY = -1.0f;
        p.lidTop = 0.12f; p.slope = -0.5f; p.shine = 2.0f;
        p.mStyle = 2; p.blush = 0.0f;
        p.sway = sinf((float)now * 0.05f) * 1.0f;
      }
      break;

    default: {   // IDLE, BLINKING, STARTUP, status / SOS cards
      if (fxIsNight()) { p.lidTop = 0.12f; p.mCurve = 0.5f; p.blush = 0.2f; }   // late-night drowsy face
      if (now < fxMoodUntilMs) {                               // content little smile
        p.lidBot = 0.20f; p.mCurve = 1.0f; p.mWidth = 14.0f; p.blush = 1.0f;
      }
      break;
    }
  }

  if (fxEmo != FXE_NONE && fxIdleLike()) fxPoseEmotion(p, now);

  if (fxDetermined) {                                          // finger held: "I'm on it" face
    p.lidTop = 0.28f; p.slope = 0.45f; p.eyeH = 30.0f;
    p.lookX = 0.0f; p.lookY = 0.0f;
    p.mCurve = 0.0f; p.mWidth = 10.0f; p.blush = 0.0f; p.mStyle = 0;
    p.eyeMode = 0; p.wink = 0.0f; p.cross = 0.0f;
  }

  fxEyeW.target = p.eyeW;     fxEyeH.target = p.eyeH;
  fxLookX.target = p.lookX;   fxLookY.target = p.lookY;
  fxFaceY.target = p.faceY;
  fxLidTop.target = p.lidTop; fxSlope.target = p.slope;  fxLidBot.target = p.lidBot;
  fxArc.target = p.arc;       fxBend.target = p.bend;    fxAsym.target = p.asym;
  fxCross.target = p.cross;   fxWink.target = p.wink;
  fxMCurve.target = p.mCurve; fxMWidth.target = p.mWidth; fxMOpen.target = p.mOpen;
  fxBlush.target = p.blush;
  fxShineT = p.shine;
  fxMouthStyle = p.mStyle;
  if (p.eyeMode != fxEyeMode) {                                // little pop whenever the eye shape changes
    fxEyeW.kick(35.0f);
    fxEyeH.kick(35.0f);
  }
  fxEyeMode = p.eyeMode;
  fxPoseBlink = p.blink;
  fxPoseSway = p.sway;
}

void fxStepSprings(float dt) {
  fxEyeW.step(dt);   fxEyeH.step(dt);
  fxLookX.step(dt);  fxLookY.step(dt);
  fxFaceY.step(dt);
  fxLidTop.step(dt); fxSlope.step(dt);  fxLidBot.step(dt);
  fxArc.step(dt);    fxBend.step(dt);   fxAsym.step(dt);
  fxCross.step(dt);  fxWink.step(dt);
  fxMCurve.step(dt); fxMWidth.step(dt); fxMOpen.step(dt);
  fxBlush.step(dt);
  fxShine += (fxShineT - fxShine) * 0.35f;
}

// ==========================================
// 12c. FACE RENDERER
// ==========================================
void fxSparkles(uint32_t now, uint32_t inState, uint32_t first, uint32_t gap) {
  static const int8_t SPX[4] = { 10, 118, 120, 8 };
  static const int8_t SPY[4] = { 14, 10, 34, 36 };
  for (int i = 0; i < 4; i++) {
    if (inState < first + (uint32_t)i * gap) continue;
    float ph = sinf((float)now * 0.011f + (float)i * 2.2f);
    int s = (ph > 0.15f) ? (int)(1.0f + ph * 2.4f) : 0;
    if (s > 0) fxSparkle(SPX[i], SPY[i], s);
  }
}

// props that belong to the current STATE
void fxRenderStateExtras(uint32_t now, uint32_t inState, int fy) {
  switch (currentEyeState) {
    case STATE_HAPPY: {
      switch (fxHappyVar) {
        case FXH_PET: {
          static const int8_t HX[4] = { 8, 118, 16, 110 };
          for (int i = 0; i < 4; i++) {
            float ph = fmodf((float)now / 1500.0f + (float)i * 0.25f, 1.0f);
            int x = HX[i] + fxRound(sinf(ph * 6.28f + (float)i) * 3.0f);
            int y = 38 - fxRound(ph * 34.0f);
            if (ph > 0.05f && ph < 0.95f && y > 0) fxMiniHeart(x, y);
          }
          fxTextSlide("Purr~", 56, inState, 120);
          break;
        }
        case FXH_KISS: {
          for (int i = 0; i < 3; i++) {                        // hearts fly off the lips
            float ph = fmodf((float)(now - fxEnterMs) / 900.0f + (float)i * 0.34f, 1.0f);
            int x = 70 + fxRound(ph * 40.0f);
            int y = 47 - fxRound(ph * 36.0f) + fxRound(sinf(ph * 9.0f) * 2.0f);
            if (ph > 0.04f && y > 1) fxMiniHeart(x, y);
          }
          fxTextSlide("Mwah!", 56, inState, 100);
          break;
        }
        case FXH_TICKLE: {
          fxSparkles(now, inState, 100, 90);
          int o = (int)((now / 60U) % 2U);                     // squirm lines
          display.drawLine(3, 20 + o, 9, 18 + o, FX_ON);
          display.drawLine(3, 28 + o, 9, 28 + o, FX_ON);
          display.drawLine(124, 20 + o, 118, 18 + o, FX_ON);
          display.drawLine(124, 28 + o, 118, 28 + o, FX_ON);
          fxTextSlide("Hehe!", 56, inState, 100);
          break;
        }
        case FXH_RELIEF: {
          if (inState > 250U) {                                // sweat drop slides down the temple
            int y = 6 + fxRound((float)(inState % 1800U) / 1800.0f * 26.0f);
            fxDrawDrop(112, y, true);
          }
          fxTextSlide("Phew!", 56, inState, 400);
          break;
        }
        case FXH_WINK: {
          fxSparkles(now, inState, 100, 90);
          if (inState > 200U) {
            int s = 2 + (int)((now / 140U) % 2U);
            fxSparkle(110, 14 + fy, s);                        // glint by the winking eye
          }
          fxTextSlide("Hello!", 56, inState, 120);
          break;
        }
        default:
          fxSparkles(now, inState, 100, 90);
          fxTextSlide("Hello!", 56, inState, 120);
          break;
      }
      break;
    }

    case STATE_CONFUSED: {
      int bob = fxRound(sinf((float)now / 260.0f) * 2.0f);
      display.setTextSize(2);
      display.setCursor(110, 4 + bob);
      display.print("?");
      break;
    }

    case STATE_IRRITATED: {
      float pulse = (sinf((float)now * 0.02f) + 1.0f) * 0.5f;
      int a = 3 + fxRound(pulse * 2.0f);                       // anger mark
      int mx2 = 116, my2 = 9;
      display.drawLine(mx2 - a - 1, my2 - 1, mx2 - 1, my2 - 1, FX_ON);
      display.drawLine(mx2 - 1, my2 - 1, mx2 - 1, my2 - a - 1, FX_ON);
      display.drawLine(mx2 + a + 1, my2 - 1, mx2 + 1, my2 - 1, FX_ON);
      display.drawLine(mx2 + 1, my2 - 1, mx2 + 1, my2 - a - 1, FX_ON);
      display.drawLine(mx2 - a - 1, my2 + 1, mx2 - 1, my2 + 1, FX_ON);
      display.drawLine(mx2 - 1, my2 + 1, mx2 - 1, my2 + a + 1, FX_ON);
      display.drawLine(mx2 + a + 1, my2 + 1, mx2 + 1, my2 + 1, FX_ON);
      display.drawLine(mx2 + 1, my2 + 1, mx2 + 1, my2 + a + 1, FX_ON);

      display.setTextSize(1);
      display.setCursor(13, fxSlideY(56, inState, 80));
      display.print("Stop poking me!");
      break;
    }

    case STATE_SLEEPY: {
      int bob = fxRound(sinf((float)now / 500.0f) * 2.0f);
      display.setTextSize(1);
      display.setCursor(98, 6 + bob);
      display.print("Zzz");
      break;
    }

    case STATE_SLEEPING: {
      display.setTextSize(1);
      for (int i = 0; i < 3; i++) {
        float ph = fmodf((float)now / 1500.0f + (float)i / 3.0f, 1.0f);
        int xPos = 108 + fxRound(ph * 10.0f);
        int yPos = 20 - fxRound(ph * 16.0f);
        if (ph > 0.12f && ph < 0.92f) {
          display.setCursor(xPos, yPos);
          display.print(i == 0 ? "Z" : "z");
        }
      }
      float bph = (sinf((float)now * 0.0016f) + 1.0f) * 0.5f;  // breathing sleep-bubble
      int br = 2 + fxRound(bph * 4.0f);
      display.drawCircle(76 + br, 50 + fy, br, FX_ON);
      display.drawPixel(76 + br - 1, 50 + fy - 1, FX_ON);
      break;
    }

    case STATE_ACKNOWLEDGED: {
      float p = (float)inState / 380.0f;
      fxDrawCheckProgress(FX_MOUTH_X, FX_MOUTH_Y - 4 + fy, p);
      fxSparkles(now, inState, 120, 80);
      if (fxAckStar) {
        int s = 2 + (int)((now / 120U) % 2U);
        fxSparkle(64, 5, s);                                   // extra twinkle for the first of the day
      }
      fxTextSlide("Done!", 56, inState, 200);
      break;
    }

    default:
      break;
  }
}

// props that belong to the running EMOTION
void fxRenderEmoExtras(uint32_t now) {
  uint32_t te = now - fxEmoStart;
  float t = (float)te;
  switch (fxEmo) {
    case FXE_CONNECT: {
      if (te >= 150) {
        fxDrawBT(116, 12);
        if (te < 600) display.drawCircle(116, 12, 7 + (int)((te - 150U) / 40U), FX_ON);
      }
      if (te > 300) {
        int s = 1 + (int)((now / 130U) % 2U);
        fxSparkle(10, 14, s);
      }
      break;
    }
    case FXE_WORRIED: {
      if (te < 3200) {
        int y = 3 + (int)((te % 1400U) * 22U / 1400U);
        fxDrawDrop(114, y, false);                             // nervous sweat
      } else {
        int y = 36 + (int)(((te - 3200U) % 1000U) * 22U / 1000U);
        fxDrawDrop(28, y, false);                              // tear
      }
      break;
    }
    case FXE_WINK_NOD: {
      if (te >= 400 && te < 1200) fxSparkle(112, 15, 2 + (int)((now / 120U) % 2U));
      break;
    }
    case FXE_DIZZY: {
      for (int i = 0; i < 3; i++) {
        float a = t * 0.012f + (float)i * 2.094f;
        fxSparkle(64 + fxRound(cosf(a) * 36.0f), 5 + fxRound(sinf(a) * 4.0f), 1);   // orbiting stars
      }
      break;
    }
    case FXE_BORED: {
      for (int i = 0; i < 3; i++) {
        if (te > 1000U + (uint32_t)i * 180U) display.fillCircle(100 + i * 7, 6, 1, FX_ON);   // "..."
      }
      break;
    }
    case FXE_DAYDREAM: {
      int bx = 116, by = 9;
      display.fillCircle(106, 21, 2, FX_OFF);
      display.fillCircle(106, 21, 1, FX_ON);
      display.fillCircle(110, 16, 3, FX_OFF);
      display.drawCircle(110, 16, 2, FX_ON);
      display.fillCircle(bx, by, 9, FX_OFF);
      display.drawCircle(bx, by, 8, FX_ON);
      bool beat = ((now / 260U) % 2U) == 0U;
      if (fxDreamIcon == 0) {
        if (beat) fxDrawHeart(bx, by - 1, 4); else fxMiniHeart(bx - 2, by - 2);
      } else if (fxDreamIcon == 1) {
        fxSparkle(bx, by, beat ? 3 : 2);
      } else {                                                 // pill
        display.fillRoundRect(bx - 5, by - 3, 11, 6, 3, FX_ON);
        display.drawFastVLine(bx, by - 3, 6, FX_OFF);
      }
      break;
    }
    case FXE_HICCUP: {
      if ((te >= 300 && te < 580) || (te >= 1100 && te < 1380)) {
        display.setTextSize(1);
        display.setCursor(80, 53);
        display.print("hic!");
      }
      break;
    }
    case FXE_STARTLED: {
      if (te < 1000) {
        display.setTextSize(2);
        display.setCursor(112, 3);
        display.print("!");
        display.drawLine(104, 6, 100, 3, FX_ON);
        display.drawLine(104, 12, 99, 12, FX_ON);
      }
      break;
    }
    case FXE_SMUG: {
      if (te > 800 && te < 1100) fxSparkle(104, 14, 3);        // eye glint
      break;
    }
    default: break;
  }
}

void fxRenderFace(uint32_t now, uint32_t inState) {
  float breath = sinf((float)now * 0.00196f);                  // ~3.2 s breathing cycle
  float n = fxClamp(fxLookX.x / 8.0f, -1.0f, 1.0f);            // gaze direction for perspective
  int   fy = fxRound(fxFaceY.x + breath * 0.7f);

  float blink = 0.0f;
  if (currentEyeState == STATE_BLINKING) blink = fxBlinkAmount(now - stateTimer);
  if (fxPoseBlink > blink) blink = fxPoseBlink;

  float shakeX = fxPoseSway;
  if (currentEyeState == STATE_IRRITATED) {
    float k = 1.0f - fxClamp((float)(now - fxPopMs) / 900.0f, 0.0f, 1.0f);
    shakeX += sinf((float)now * 0.27f) * 2.0f * k;
  }

  const float arc = fxArc.x;
  int eyeBottomY = FX_EYE_Y + fy + 16;

  for (int side = -1; side <= 1; side += 2) {
    float sc = 1.0f + (side < 0 ? 0.08f : -0.10f) * n;         // far eye gets smaller
    float aS = (side < 0) ? fxAsym.x : -fxAsym.x;              // confused: one big/high, one small/low
    float w = (fxEyeW.x - breath * 0.8f) * sc;
    float h = (fxEyeH.x + breath * 1.2f) * sc * (1.0f + 0.10f * aS);
    int cx = ((side < 0) ? FX_EYE_L_X : FX_EYE_R_X) + fxRound(fxLookX.x + shakeX);
    int cy = FX_EYE_Y + fy + fxRound(fxLookY.x * 0.6f - 3.0f * aS);

    float a = (side > 0 && fxWink.x > arc) ? fxWink.x : arc;   // wink closes only the right eye
    float S = (w < h) ? w : h;

    if (fxEyeMode == 1) {
      float beat = 1.0f + 0.08f * sinf((float)now * 0.014f);
      fxDrawHeart(cx, cy, fxRound(S * 0.5f * beat));
      eyeBottomY = cy + fxRound(S * 0.5f);
    } else if (fxEyeMode == 2) {
      fxDrawStar4(cx, cy, fxRound(S * 0.58f * (1.0f + 0.07f * sinf((float)now * 0.012f))),
                  (float)now * 0.0035f);
      eyeBottomY = cy + fxRound(S * 0.5f);
    } else if (fxEyeMode == 3) {
      fxDrawSpiral(cx, cy, fxRound(S * 0.5f), (float)now * 0.011f * (float)(-side));
      eyeBottomY = cy + fxRound(S * 0.5f);
    } else if (a >= 0.5f) {
      fxDrawArcEye(cx, cy, fxRound(w) + 2, fxBend.x, 5);
      eyeBottomY = cy + 9;
    } else {
      float hs = h * (1.0f - 1.7f * a);
      if (hs < 4.0f) hs = 4.0f;
      hs = lerpf(hs, 3.0f, blink);
      float ws = w + 6.0f * blink;
      int ri = (hs < 8.0f) ? (int)(hs / 2.0f) : FX_EYE_CORNER;
      fxDrawSolidEye(cx, cy, fxRound(ws), fxRound(hs), ri,
                     fxLidTop.x, fxSlope.x, side, fxLidBot.x,
                     fxLookX.x * 0.6f - (float)side * fxCross.x * 5.0f, fxLookY.x * 0.6f, fxShine);
      eyeBottomY = cy + fxRound(hs) / 2;
    }

    fxDrawBlush(cx + side * 12, eyeBottomY + 4, fxBlush.x);
  }

  // eyebrow for the (unused-by-touch) CONFUSED state
  if (currentEyeState == STATE_CONFUSED) {
    int bx = FX_EYE_L_X + fxRound(fxLookX.x);
    fxStrokeBezier((float)(bx - 11), (float)(FX_EYE_Y + fy - 24), (float)bx, (float)(FX_EYE_Y + fy - 30),
                   (float)(bx + 11), (float)(FX_EYE_Y + fy - 22), 2);
  }

  // mouth
  int mx = FX_MOUTH_X + fxRound(fxLookX.x * 0.3f + shakeX);
  int my = FX_MOUTH_Y + fy;
  fxDrawMouth(mx, my, fxMCurve.x, fxMWidth.x, fxMOpen.x, fxMouthStyle, now);

  fxRenderStateExtras(now, inState, fy);
  if (fxEmo != FXE_NONE && fxIdleLike()) fxRenderEmoExtras(now);

  if (fxPleadActive) {                                         // escalated reminder: please...
    int y = 4 + fxRound((float)(inState % 1000U) / 1000.0f * 20.0f);
    fxDrawDrop(116, y, false);
    uint32_t pt = inState % (uint32_t)FX_PLEAD_PERIOD;
    uint32_t since = pt - ((uint32_t)FX_PLEAD_PERIOD - (uint32_t)FX_PLEAD_LEN);
    fxTextSlide("Please...", 56, since, 60);
  }

  if (fxDetermined) {                                          // hold-to-SOS progress line
    int wpx = fxRound(fxHoldFrac * (float)SCREEN_WIDTH);
    display.drawFastHLine(0, 62, wpx, FX_ON);
    display.drawFastHLine(0, 63, wpx, FX_ON);
  }
}

// ==========================================
// 12d. CARD SCREENS (Reminder / Status / SOS)
// ==========================================
void fxRenderReminder(uint32_t now, uint32_t inState) {
  display.setTextColor(FX_ON);
  int lvl = reminderLevelFromMs(inState);                      // 0 gentle, 1 insistent, 2 urgent

  float pulse = (sinf((float)now / 220.0f) + 1.0f) * 0.5f;
  int ringPad = fxRound(pulse * 3.0f);

  // ring burst: bells swing, frames shake, echo ring ripples out (faster as it escalates)
  uint32_t period = (lvl == 0) ? 1500U : (lvl == 1 ? 1100U : 800U);
  float amp = (lvl == 0) ? 1.0f : (lvl == 1 ? 1.35f : 1.7f);
  float ph = (float)(now % period) / (float)period;
  float env = (ph < 0.55f) ? (1.0f - ph / 0.55f) : 0.0f;
  float ang = (env > 0.0f) ? 0.42f * amp * env * sinf((float)now * 0.034f)
                           : 0.05f * sinf((float)now * 0.004f);
  ang = fxClamp(ang, -0.85f, 0.85f);
  int shake = fxRound(sinf((float)now * 0.07f) * env * 1.5f * amp);

  const int centers[2] = { FX_EYE_L_X, FX_EYE_R_X };
  for (int i = 0; i < 2; i++) {
    int cx = centers[i] + shake;
    display.drawRoundRect(cx - 15 - ringPad, 1 - ringPad, 30 + ringPad * 2, 26 + ringPad * 2, 7, FX_ON);
    if (env > 0.05f) {
      int echo = 3 + fxRound((1.0f - env) * 4.0f);
      display.drawRoundRect(cx - 15 - echo - ringPad, 1 - echo - ringPad,
                            30 + (echo + ringPad) * 2, 26 + (echo + ringPad) * 2, 9, FX_ON);
    }
    fxDrawBell(cx, 6, ang);
    if (env > 0.25f) {                                         // ring marks
      int sgn = (i == 0) ? -1 : 1;
      display.drawLine(cx + sgn * 10, 9, cx + sgn * 12, 7, FX_ON);
      display.drawLine(cx + sgn * 11, 12, cx + sgn * 13, 12, FX_ON);
    }
  }

  if (lvl >= 1) {                                              // "!" marks bounce beside the bells
    bool show = (lvl == 1) || (((now / 250U) % 2U) == 0U);
    if (show) {
      int bob = fxRound(sinf((float)now * 0.012f) * 1.5f);
      display.setTextSize(2);
      display.setCursor(4, 4 + bob);
      display.print("!");
      display.setCursor(117, 4 + bob);
      display.print("!");
    }
  }

  display.setTextSize(1);
  if (lvl >= 2 && ((now / 350U) % 2U) == 0U) {                 // header flashes inverted when urgent
    display.fillRect(0, 31, 50, 10, FX_ON);
    display.setTextColor(FX_OFF);
  }
  display.setCursor(0, 32);
  display.print("REMINDER");
  display.setTextColor(FX_ON);

  for (int x = 0; x < SCREEN_WIDTH; x++) {                     // marching attention line
    if (((x + (int)(now / 35U)) % 8) < 5) display.drawPixel(x, 41, FX_ON);
  }
  fxPrintMarquee(reminders[activeReminderIndex].title, 44, now);

  display.setTextSize(1);
  display.setCursor(0, 56);
  display.print("Touch to confirm");
  int rr = 1 + (int)((now / 160U) % 5U);                       // touch ripple
  display.drawCircle(116, 58, rr, FX_ON);
  display.fillCircle(116, 58, 1, FX_ON);
}

void fxRenderStatus(uint32_t now, uint32_t inState) {
  display.setTextColor(FX_ON);
  display.setTextSize(1);

  int hy = fxRound(lerpf(-10.0f, 0.0f, easeOutCubic((float)inState / 180.0f)));
  display.fillRect(0, hy, SCREEN_WIDTH, 10, FX_ON);
  display.setTextColor(FX_OFF);
  display.setCursor(13, hy + 1);
  display.print("CARE BUDDY STATUS");
  display.setTextColor(FX_ON);

  int o1 = fxRound((1.0f - easeOutCubic((float)((int)inState - 100) / 220.0f)) * 40.0f);
  int o2 = fxRound((1.0f - easeOutCubic((float)((int)inState - 190) / 220.0f)) * 40.0f);
  int o3 = fxRound((1.0f - easeOutCubic((float)((int)inState - 280) / 220.0f)) * 40.0f);

  display.setCursor(0 + o1, 13);
  display.print("Link:");
  int ix = 46 + o1;
  if (deviceConnected) {
    display.fillCircle(ix, 16, 3, FX_ON);
    int wr = 4 + (int)((now / 220U) % 5U);
    fxArcPolar(ix, 16, wr, -50.0f, 50.0f);                     // radiating signal
    display.setCursor(58 + o1, 13);
    display.print("Connected");
  } else {
    int wr = 3 + (int)((now / 120U) % 6U);
    display.drawCircle(ix, 16, 3, FX_ON);
    if (((now / 300U) % 2U) == 0U) display.drawCircle(ix, 16, wr, FX_ON);
    display.setCursor(58 + o1, 13);
    display.print("Searching");
    int dots = (int)((now / 300U) % 4U);
    for (int d = 0; d < dots; d++) display.drawPixel(58 + o1 + 54 + d * 3, 20, FX_ON);
  }

  display.setCursor(0 + o2, 24);
  display.print("Reminders: ");
  display.print(reminderCount);
  display.print("/");
  display.print(MAX_REMINDERS);
  for (int i = 0; i < MAX_REMINDERS; i++) {
    int x = 4 + o2 + i * 12;
    if (i < reminderCount) display.fillRoundRect(x, 35, 9, 6, 2, FX_ON);
    else display.drawRoundRect(x, 35, 9, 6, 2, FX_ON);
  }

  display.drawFastHLine(0 + o3, 45, SCREEN_WIDTH, FX_ON);
  display.setCursor(0 + o3, 47);
  display.print("Hold 3s on sensor");
  display.setCursor(0 + o3, 55);
  display.print("to call for help");
}

void fxRenderSosCountdown(uint32_t now, uint32_t inState) {
  display.setTextColor(FX_ON);
  (void)inState;

  if (sosCountdownSeconds != fxLastSosSecond) {
    fxLastSosSecond = sosCountdownSeconds;
    fxSosPopMs = now;
  }
  uint32_t sincePop = now - fxSosPopMs;

  float pulse = (sinf((float)now / 150.0f) + 1.0f) * 0.5f;
  int pad = fxRound(pulse * 2.0f);
  display.drawRoundRect(0 - pad, 0 - pad, SCREEN_WIDTH + pad * 2, SCREEN_HEIGHT + pad * 2, 4, FX_ON);

  bool solid = ((now / 300U) % 2U) == 0U;                      // blinking warning triangle
  if (solid) {
    display.fillTriangle(14, 6, 2, 26, 26, 26, FX_ON);
    display.fillRect(13, 12, 2, 8, FX_OFF);
    display.fillCircle(14, 22, 1, FX_OFF);
  } else {
    display.drawTriangle(14, 6, 2, 26, 26, 26, FX_ON);
    display.fillRect(13, 12, 2, 8, FX_ON);
    display.fillCircle(14, 22, 1, FX_ON);
  }

  display.setTextSize(2);
  display.setCursor(40, 4);
  display.print("SOS");

  float frac = 1.0f - fxClamp((float)(now - sosTimer) / 1000.0f, 0.0f, 1.0f);   // ring drains each second
  if (frac > 0.01f) fxArcPolar(64, 41, 13, -90.0f, -90.0f + 360.0f * frac);

  int drop = (sincePop < 220U) ? fxRound((1.0f - (float)sincePop / 220.0f) * 5.0f) : 0;
  display.setTextSize(3);
  display.setCursor(55, 30 - drop);
  display.print(sosCountdownSeconds);

  if (sincePop < 280U) {
    int rr = 13 + (int)(sincePop / 40U);
    display.drawCircle(64, 41, rr, FX_ON);
  }

  display.setTextSize(1);
  display.setCursor(10, 56);
  display.print("Touch to cancel");
}

void fxRenderSosSent(uint32_t now, uint32_t inState) {
  bool flash = ((now / 450U) % 2U) == 0U;
  uint16_t fg = FX_ON;
  if (flash) {
    display.fillRect(0, 0, SCREEN_WIDTH, SCREEN_HEIGHT, FX_ON);
    fg = FX_OFF;
  }
  display.setTextColor(fg);

  int jit = 0;
  if (inState < 450U) jit = fxRound(sinf((float)now * 0.9f) * 2.0f * (1.0f - (float)inState / 450.0f));
  display.setTextSize(2);
  display.setCursor(10 + jit, 16);
  display.print("SOS SENT");
  display.setTextSize(1);
  display.setCursor(6, 42);
  display.print("Alerting your");
  display.setCursor(6, 52);
  display.print("emergency contact");

  display.fillCircle(116, 50, 1, fg);                          // broadcast ripples
  for (int i = 0; i < 3; i++) {
    float p = fmodf((float)now / 900.0f + (float)i / 3.0f, 1.0f);
    int r = 2 + fxRound(p * 6.0f);
    if (p < 0.85f) display.drawCircle(116, 50, r, fg);
  }

  display.setTextColor(FX_ON);
}

// shutter reveal ("eyelids opening") used for screen transitions
void fxApplyShutter(uint32_t inState) {
  if (fxShutterMs == 0 || inState >= fxShutterMs) return;
  float p = easeOutCubic((float)inState / (float)fxShutterMs);
  int bar = fxRound((1.0f - p) * (float)(SCREEN_HEIGHT / 2));
  if (bar <= 0) return;
  display.fillRect(0, 0, SCREEN_WIDTH, bar, FX_OFF);
  display.fillRect(0, SCREEN_HEIGHT - bar, SCREEN_WIDTH, bar, FX_OFF);
  display.drawFastHLine(0, bar, SCREEN_WIDTH, FX_ON);
  display.drawFastHLine(0, SCREEN_HEIGHT - 1 - bar, SCREEN_WIDTH, FX_ON);
}

// ==========================================
// 12e. MAIN ENGINE ENTRY (called every loop, self-throttled to ~30 FPS)
// ==========================================
void updateEyeEngine() {
  uint32_t now = millis();

  if (now - lastDisplayUpdate < DISPLAY_UPDATE_INTERVAL_MS) {
    return;
  }
  float dt = (float)(now - lastDisplayUpdate) * 0.001f;
  if (dt > 0.05f) dt = 0.05f;                                  // never let a hiccup explode the springs
  lastDisplayUpdate = now;

  fxComputeHold(now);
  fxDetectStateChange(now);
  fxPollEvents(now);
  fxAdvanceStateLogic(now);
  fxDetectStateChange(now);                                    // logic may have changed the state
  fxEmoTick(now);
  uint32_t inState = now - fxEnterMs;

  fxPleadActive = (currentEyeState == STATE_REMINDER && activeReminderIndex != -1 &&
                   reminderLevelFromMs(inState) >= 1 &&
                   (inState % (uint32_t)FX_PLEAD_PERIOD) >= (uint32_t)(FX_PLEAD_PERIOD - FX_PLEAD_LEN));

  fxApplyPose(now, inState);
  fxStepSprings(dt);

  // dim the OLED while dozing / late at night (never on alert cards)
  bool wantDim = (currentEyeState == STATE_SLEEPY || currentEyeState == STATE_SLEEPING) ||
                 (fxIsNight() && fxIdleLike());
  if (wantDim != fxDimmed) {
    fxDimmed = wantDim;
    display.dim(wantDim);
  }

  display.clearDisplay();
  display.setTextColor(FX_ON);
  display.setTextWrap(false);                                  // slide-ins / marquee must never wrap

  switch (currentEyeState) {
    case STATE_REMINDER:
      if (activeReminderIndex != -1) {
        if (fxPleadActive) fxRenderFace(now, inState);
        else fxRenderReminder(now, inState);
      } else {
        fxRenderFace(now, inState);
      }
      break;
    case STATE_STATUS:
      fxRenderStatus(now, inState);
      break;
    case STATE_SOS_COUNTDOWN:
      fxRenderSosCountdown(now, inState);
      break;
    case STATE_SOS_SENT:
      fxRenderSosSent(now, inState);
      break;
    default:
      fxRenderFace(now, inState);
      break;
  }

  fxApplyShutter(inState);
  display.display();
}

// ==========================================
// 12f. BOOT ANIMATION (replaces the static splash; ~1.9 s, plays the startup jingle)
// ==========================================
void fxBootAnimation() {
  const uint32_t TOTAL = 1900;
  const char* title = "CareBuddy";
  const char* sub = "Companion Ready";
  uint32_t t0 = millis();

  for (;;) {
    uint32_t t = millis() - t0;
    if (t >= TOTAL) break;

    display.clearDisplay();
    display.setTextColor(FX_ON);
    display.setTextWrap(false);

    const int ey = 18;

    if (t < 260) {
      float p = easeOutCubic((float)t / 260.0f);               // power-on line sweeps out from the centre
      int half = fxRound(p * 42.0f);
      display.fillRoundRect(64 - half, ey - 1, half * 2 + 1, 3, 1, FX_ON);
    } else {
      float p = fxClamp((float)(t - 260U) / 380.0f, 0.0f, 1.0f);
      float grow = easeOutBack(p);
      int h = fxRound(lerpf(3.0f, 26.0f, grow));
      if (h < 3) h = 3;
      int ri = (h < 10) ? h / 2 : 9;
      float look = 0.0f;
      if (t > 800U && t < 1200U) look = sinf((float)(t - 800U) * 0.0157f) * 5.0f;    // curious look around
      float blink = 0.0f;
      if (t > 1600U && t < 1810U) blink = fxBlinkAmount(t - 1600U);
      int hh = fxRound(lerpf((float)h, 3.0f, blink));

      for (int s = -1; s <= 1; s += 2) {
        int cx = (s < 0 ? 38 : 90) + fxRound(look);
        display.fillRoundRect(cx - 13, ey - hh / 2, 26, hh, (hh < 8) ? hh / 2 : ri, FX_ON);
        if (hh >= 14) {
          display.fillCircle(cx - 4 + fxRound(look * 0.4f), ey - 4, 3, FX_OFF);
          display.fillCircle(cx + 4 + fxRound(look * 0.4f), ey + 3, 1, FX_OFF);
        }
      }
      if (p < 1.0f) {
        int gap = fxRound(p * 24.0f);
        display.fillRect(64 - gap / 2, ey - 1, gap, 3, FX_ON);
      }
    }

    if (t > 700U) {                                            // typed title
      int letters = (int)((t - 700U) / 55U) + 1;
      int len = (int)strlen(title);
      if (letters > len) letters = len;
      char buf[16];
      memcpy(buf, title, (size_t)letters);
      buf[letters] = '\0';
      display.setTextSize(2);
      display.setCursor((SCREEN_WIDTH - len * 12) / 2, 36);
      display.print(buf);
    }
    if (t > 1250U) {                                           // subtitle slides up
      float p = easeOutCubic((float)(t - 1250U) / 260.0f);
      int y = fxRound(lerpf(70.0f, 54.0f, p));
      display.setTextSize(1);
      display.setCursor((SCREEN_WIDTH - (int)strlen(sub) * 6) / 2, y);
      display.print(sub);
    }

    display.display();
    updateBuzzerEngine();                                      // jingle plays during the animation
  }

  display.setTextSize(1);
  display.setTextColor(FX_ON);
}

// ==========================================
// 13. BUZZER PATTERN PLAYER (NON-BLOCKING RHYTHM ENGINE)
//     The buzzer is ON/OFF only, so every sound is a RHYTHM: a list of
//     alternating ON / OFF durations in ms (first entry = ON).
//
//     HELD sounds keep going for as long as the situation lasts:
//       * REMINDER  : repeats until it is confirmed, and escalates
//                     (30 s: faster / more beeps, 90 s: most insistent,
//                     10 min: slow persistent nag)
//       * SOS SENT  : international Morse "SOS" (... --- ...) while the
//                     SOS screen is up, then a locator chirp every 3 s
//       * SOS TICK  : 1 / 2 / 3 pulses as the countdown runs 3 -> 2 -> 1
//     If another one-shot sound interrupts a held sound, the held sound
//     resumes automatically afterwards.
// ==========================================

// ---- one-shot rhythms (ms: ON, OFF, ON, OFF ...) ----
static const uint16_t BZ_STARTUP[]    = { 80, 40, 120 };
static const uint16_t BZ_SHORT[]      = { 60 };
static const uint16_t BZ_DOUBLE[]     = { 50, 60, 50 };
static const uint16_t BZ_ACK[]        = { 80, 40, 140 };
static const uint16_t BZ_CHEER[]      = { 80, 40, 80, 40, 80, 40, 220 };          // first reminder of the day
static const uint16_t BZ_CONNECT[]    = { 45, 45, 45, 45, 110 };                  // happy "chirp-chirp-chirrp"
static const uint16_t BZ_DISCONNECT[] = { 150, 70, 100, 70, 60 };                 // sinking
static const uint16_t BZ_SAD[]        = { 220, 100, 150, 100, 90 };
static const uint16_t BZ_WINK[]       = { 40, 50, 90 };
static const uint16_t BZ_PURR[]       = { 40, 35, 40, 35, 40, 35, 40, 35, 40, 35, 40, 35, 40 };   // "brrrr"
static const uint16_t BZ_GIGGLE[]     = { 45, 55, 45, 55, 45, 55, 90 };
static const uint16_t BZ_SIGH[]       = { 190, 70, 90 };
static const uint16_t BZ_SHY[]        = { 40, 90, 40 };
static const uint16_t BZ_DIZZY[]      = { 50, 40, 50, 40, 50, 40, 50, 40, 50, 40, 120 };
static const uint16_t BZ_BORED[]      = { 60, 200, 60 };
static const uint16_t BZ_DAYDREAM[]   = { 50, 80, 50 };                           // tiny heartbeat
static const uint16_t BZ_BLIP[]       = { 50, 70, 90 };
static const uint16_t BZ_STARTLE[]    = { 50, 40, 150 };
static const uint16_t BZ_HICCUP[]     = { 40, 260, 40 };
static const uint16_t BZ_SMUG[]       = { 110, 70, 45 };
static const uint16_t BZ_KISS[]       = { 35, 40, 130 };                          // "mwah"
static const uint16_t BZ_BEACON[]     = { 60 };

// Morse "SOS": ... --- ...   (last entry = pause before it repeats)
static const uint16_t BZ_MORSE_SOS[18] = { 100,100, 100,100, 100,100,
                                           300,100, 300,100, 300,100,
                                           100,100, 100,100, 100,700 };


// held-sound state
uint32_t buzzerReminderStart = 0;   // when the current reminder alert began (drives escalation)
int      buzzerArg = 3;             // SOS tick: countdown value when it was started
bool     buzzerStepReady = false;
bool     buzzerStepOn = false;
uint16_t buzzerStepDur = 0;
uint8_t  buzzerMode = 0;            // SOS alarm: 0 morse, 1 locator chirp

#define BZ_SEQ(arr, out) do { (out).steps = (arr); (out).count = (uint8_t)(sizeof(arr) / sizeof((arr)[0])); } while (0)

static int buzzReminderLevel(uint32_t ms) {          // 0..2 shared with the animation, 3 = persistent slow nag
  return (ms >= REMINDER_NAG_MS) ? 3 : reminderLevelFromMs(ms);
}

static bool buzzGetSeq(BuzzerPattern p, BuzzSeq& s) {
  switch (p) {
    case BUZZ_STARTUP:    BZ_SEQ(BZ_STARTUP, s);    return true;
    case BUZZ_SHORT_BEEP: BZ_SEQ(BZ_SHORT, s);      return true;
    case BUZZ_DOUBLE_BEEP:BZ_SEQ(BZ_DOUBLE, s);     return true;
    case BUZZ_ACK:        BZ_SEQ(BZ_ACK, s);        return true;
    case BUZZ_CHEER:      BZ_SEQ(BZ_CHEER, s);      return true;
    case BUZZ_CONNECT:    BZ_SEQ(BZ_CONNECT, s);    return true;
    case BUZZ_DISCONNECT: BZ_SEQ(BZ_DISCONNECT, s); return true;
    case BUZZ_SAD:        BZ_SEQ(BZ_SAD, s);        return true;
    case BUZZ_WINK:       BZ_SEQ(BZ_WINK, s);       return true;
    case BUZZ_PURR:       BZ_SEQ(BZ_PURR, s);       return true;
    case BUZZ_GIGGLE:     BZ_SEQ(BZ_GIGGLE, s);     return true;
    case BUZZ_SIGH:       BZ_SEQ(BZ_SIGH, s);       return true;
    case BUZZ_SHY:        BZ_SEQ(BZ_SHY, s);        return true;
    case BUZZ_DIZZY:      BZ_SEQ(BZ_DIZZY, s);      return true;
    case BUZZ_BORED:      BZ_SEQ(BZ_BORED, s);      return true;
    case BUZZ_DAYDREAM:   BZ_SEQ(BZ_DAYDREAM, s);   return true;
    case BUZZ_BLIP:       BZ_SEQ(BZ_BLIP, s);       return true;
    case BUZZ_STARTLE:    BZ_SEQ(BZ_STARTLE, s);    return true;
    case BUZZ_HICCUP:     BZ_SEQ(BZ_HICCUP, s);     return true;
    case BUZZ_SMUG:       BZ_SEQ(BZ_SMUG, s);       return true;
    case BUZZ_KISS:       BZ_SEQ(BZ_KISS, s);       return true;
    default:              return false;
  }
}

static void buzzStart(BuzzerPattern pattern) {
  currentBuzzerPattern = pattern;
  buzzerStep = 0;
  buzzerTimer = millis();
  buzzerStepReady = false;
  buzzerMode = 0;
}

void playBuzzerPattern(BuzzerPattern pattern) {
  if (pattern == BUZZ_REMINDER) buzzerReminderStart = millis();   // a fresh alert starts at level 0
  buzzerArg = sosCountdownSeconds;                                // used by the SOS tick
  buzzStart(pattern);
}

static void buzzStop() {
  digitalWrite(BUZZER_PIN, LOW);
  currentBuzzerPattern = BUZZ_NONE;
  buzzerStepReady = false;
}

// Loads the next ON/OFF step of the current pattern. false = pattern finished.
static bool buzzLoadStep(uint32_t now) {
  switch (currentBuzzerPattern) {

    case BUZZ_REMINDER: {
      // level 0: 3 gentle beeps / 2.6 s pause   level 1: 4 beeps / 1.6 s   level 2: 5 beeps / 0.8 s
      // level 3 (after 10 min): 3 beeps every 9 s - never gives up, but stops being a siren
      static const uint8_t  N[4]    = { 3, 4, 5, 3 };
      static const uint16_t ON_[4]  = { 130, 150, 170, 130 };
      static const uint16_t OFF_[4] = { 110, 90, 70, 110 };
      static const uint16_t GAP[4]  = { 2600, 1600, 800, 9000 };
      int lvl = buzzReminderLevel(now - buzzerReminderStart);
      int len = 2 * N[lvl];
      if (buzzerStep >= len) {                       // cycle finished -> start the next one at the current level
        buzzerStep = 0;
        lvl = buzzReminderLevel(now - buzzerReminderStart);
        len = 2 * N[lvl];
      }
      buzzerStepOn = (buzzerStep % 2 == 0);
      buzzerStepDur = buzzerStepOn ? ON_[lvl] : ((buzzerStep == len - 1) ? GAP[lvl] : OFF_[lvl]);
      return true;
    }

    case BUZZ_SOS_ALARM: {
      bool inWindow = (reactionEndMillis != 0 && now < reactionEndMillis);
      if (buzzerStep == 0) buzzerMode = inWindow ? 0 : 1;
      int len = (buzzerMode == 0) ? 18 : 2;
      if (buzzerStep >= len) {
        buzzerStep = 0;
        buzzerMode = inWindow ? 0 : 1;
        len = (buzzerMode == 0) ? 18 : 2;
      }
      buzzerStepOn = (buzzerStep % 2 == 0);
      if (buzzerMode == 0) buzzerStepDur = BZ_MORSE_SOS[buzzerStep];
      else buzzerStepDur = buzzerStepOn ? 70 : 2930;   // locator chirp every 3 s
      return true;
    }

    case BUZZ_SOS_TICK: {
      int n = 4 - buzzerArg;                         // countdown 3 -> 1 pulse, 2 -> 2 pulses, 1 -> 3 pulses
      if (n < 1) n = 1;
      if (n > 3) n = 3;
      if (buzzerStep >= 2 * n - 1) return false;
      buzzerStepOn = (buzzerStep % 2 == 0);
      buzzerStepDur = buzzerStepOn ? (n == 1 ? 130 : 70) : 60;
      return true;
    }

    default: {
      BuzzSeq s;
      if (!buzzGetSeq(currentBuzzerPattern, s)) return false;
      if (buzzerStep >= s.count) return false;
      buzzerStepOn = (buzzerStep % 2 == 0);
      buzzerStepDur = s.steps[buzzerStep];
      return true;
    }
  }
}

void updateBuzzerEngine() {
  uint32_t now = millis();

  // Held sounds resume after being interrupted by a one-shot (e.g. a BLE "beep" while ringing)
  if (currentBuzzerPattern == BUZZ_NONE) {
    if (activeReminderIndex != -1) {
      buzzStart(BUZZ_REMINDER);
    } else if (currentEyeState == STATE_SOS_SENT) {
      buzzStart(BUZZ_SOS_ALARM);
    } else {
      digitalWrite(BUZZER_PIN, LOW);
      return;
    }
  }

  // Held sounds stop the instant the thing that justified them ends
  if (currentBuzzerPattern == BUZZ_REMINDER && activeReminderIndex == -1) { buzzStop(); return; }
  if (currentBuzzerPattern == BUZZ_SOS_ALARM && currentEyeState != STATE_SOS_SENT) { buzzStop(); return; }

  if (!buzzerStepReady) {
    if (!buzzLoadStep(now)) { buzzStop(); return; }
    buzzerStepReady = true;
    buzzerTimer = now;
  }

  digitalWrite(BUZZER_PIN, buzzerStepOn ? HIGH : LOW);

  if (now - buzzerTimer >= buzzerStepDur) {
    buzzerStep++;
    buzzerStepReady = false;
  }
}

// ==========================================
// 14. BLE MESSAGING & PROTOCOL
// ==========================================
void sendBleJson(const JsonDocument& doc) {
  if (deviceConnected && pTxCharacteristic != nullptr) {
    String output;
    serializeJson(doc, output);
    pTxCharacteristic->setValue(output.c_str());
    pTxCharacteristic->notify();
    Serial.print(F("BLE TX -> "));
    Serial.println(output);
  }
}

void processBleMessages(const String& rawJson) {
  Serial.print(F("BLE RX <- "));
  Serial.println(rawJson);

  StaticJsonDocument<512> doc;
  DeserializationError error = deserializeJson(doc, rawJson);

  if (error) {
    Serial.print(F("JSON Parse Error: "));
    Serial.println(error.c_str());
    return;
  }

  const char* type = doc.containsKey("type") ? doc["type"].as<const char*>() : "";

  if (strcmp(type, "ADD_REMINDER") == 0) {
    if (reminderCount < MAX_REMINDERS) {
      int idx = -1;
      const char* id = doc.containsKey("id") ? doc["id"].as<const char*>() : "";
      for (int i = 0; i < reminderCount; i++) {
        if (strcmp(reminders[i].id, id) == 0) { idx = i; break; }
      }
      if (idx == -1) { idx = reminderCount; reminderCount++; }

      strlcpy(reminders[idx].id, id, sizeof(reminders[idx].id));
      strlcpy(reminders[idx].title, doc.containsKey("title") ? doc["title"].as<const char*>() : "Reminder", sizeof(reminders[idx].title));
      strlcpy(reminders[idx].timeStr, doc.containsKey("time") ? doc["time"].as<const char*>() : "10:00 AM", sizeof(reminders[idx].timeStr));
      reminders[idx].timeMinutes = doc.containsKey("minutes") ? doc["minutes"].as<uint16_t>() : 600;
      reminders[idx].enabled = doc.containsKey("enabled") ? doc["enabled"].as<bool>() : true;
      reminders[idx].acknowledgedToday = false;

      saveRemindersToNVS();
      playBuzzerPattern(BUZZ_SHORT_BEEP);
      Serial.print(F("Reminder Stored: "));
      Serial.print(reminders[idx].title);
      Serial.print(F(" at "));
      Serial.print(reminders[idx].timeMinutes);
      Serial.println(F(" mins"));
    }
  } 
  else if (strcmp(type, "DELETE_REMINDER") == 0) {
    const char* id = doc.containsKey("id") ? doc["id"].as<const char*>() : "";
    for (int i = 0; i < reminderCount; i++) {
      if (strcmp(reminders[i].id, id) == 0) {
        for (int j = i; j < reminderCount - 1; j++) {
          reminders[j] = reminders[j + 1];
        }
        reminderCount--;
        saveRemindersToNVS();
        break;
      }
    }
  }
  else if (strcmp(type, "CLEAR_REMINDERS") == 0) {
    reminderCount = 0;
    saveRemindersToNVS();
  }
  else if (strcmp(type, "SYNC_TIME") == 0) {
    if (doc.containsKey("minutes")) {
      currentMinutesOfDay = doc["minutes"].as<uint16_t>();
    }
    if (doc.containsKey("epoch")) {
      currentEpochSeconds = doc["epoch"].as<uint32_t>();
    }
    lastMillisClock = millis(); // Reset reference to avoid double advancing
    Serial.print(F("Time Synced: "));
    Serial.print(currentMinutesOfDay);
    Serial.println(F(" mins since midnight"));
  }
  else if (strcmp(type, "TEST_SOS") == 0) {
    currentEyeState = STATE_SOS_SENT;
    playBuzzerPattern(BUZZ_SOS_ALARM);
    
    StaticJsonDocument<128> reply;
    reply["type"] = "SOS";
    reply["status"] = "TEST";
    sendBleJson(reply);
    reactionEndMillis = millis() + 4000;
  }
}

// ==========================================
// 15. PREFERENCES (NVS) STORAGE MANAGER
// ==========================================
void saveRemindersToNVS() {
  preferences.begin("carebuddy", false);
  preferences.putInt("count", reminderCount);
  for (int i = 0; i < reminderCount; i++) {
    char key[16];
    snprintf(key, sizeof(key), "r_%d", i);
    preferences.putBytes(key, &reminders[i], sizeof(Reminder));
  }
  preferences.end();
  Serial.println(F("Reminders saved to NVS."));
}

void loadRemindersFromNVS() {
  preferences.begin("carebuddy", true);
  reminderCount = preferences.getInt("count", 0);
  if (reminderCount > MAX_REMINDERS) reminderCount = MAX_REMINDERS;

  for (int i = 0; i < reminderCount; i++) {
    char key[16];
    snprintf(key, sizeof(key), "r_%d", i);
    preferences.getBytes(key, &reminders[i], sizeof(Reminder));
  }
  preferences.end();
  Serial.print(F("Loaded Reminders from NVS: "));
  Serial.println(reminderCount);
}
