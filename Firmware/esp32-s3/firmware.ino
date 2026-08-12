#include <WiFi.h>
#include <WiFiUdp.h>
#include <driver/i2s_std.h>

// ==========================================
// 1. PIN DEFINITIONS & HARDWARE CONFIG
// ==========================================

// --- WIFI & UDP SETTINGS ---
const char* ssid = "vivo Y04";         
const char* password = "200120102069"; 
const char* targetIP = "10.81.142.43"; 
const int camUdpPort = 4210;                 
const int audioReceivePort = 4212; 

WiFiUDP camUdp;
WiFiUDP audioUdp;

// --- CAMERA PINS ---
#define RX_PIN 8 
#define TX_PIN 9 

// --- SPEAKER PINS (I2S OUT - MAX98357A) ---
#define I2S_BCLK 10   
#define I2S_LRC  11   
#define I2S_DIN  12   

// NOTE: Google TTS default is 24000. Samsung is often 16000. 
// If the voice sounds like a deep "slow-motion" voice, change this to 16000 or 48000.
#define SAMPLE_RATE 24000 
i2s_chan_handle_t tx_chan; 

// --- SENSOR PINS ---
#define HAPTIC_LEFT    6
#define HAPTIC_RIGHT   7
#define HAPTIC_BOTTOM  15
#define TRIG_PIN       16
#define ECHO_PIN       5    

// ==========================================
// 2. STATE VARIABLES & ENUMS
// ==========================================

// --- LOGGING TIMERS ---
unsigned long lastSensorLogTime = 0;

// --- CAMERA LOGIC VARIABLES ---
const float THRESHOLD = 0.85;
const int REQUIRED_FRAMES = 3;
const unsigned long COOLDOWN_MS = 3000;
int consecutiveFrames = 0;
unsigned long lastDetectionTime = 0;

// --- ULTRASONIC & HAPTIC VARIABLES ---
unsigned long lastUltrasonicRead = 0;
unsigned int distanceCM = 0;
bool ultrasonicTimeout = false;
bool softError = false;

enum DistanceZone {
  ZONE_SAFE, ZONE_AWARE, ZONE_WARNING, ZONE_DANGER, ZONE_IMPACT, ZONE_INVALID
};

DistanceZone currentZone = ZONE_INVALID;
DistanceZone lastZone    = ZONE_INVALID;

// Task Handles
TaskHandle_t AudioTask;

// ==========================================
// 3. SENSOR & HAPTIC FUNCTIONS
// ==========================================

DistanceZone getDistanceZone(unsigned int d) {
  if (d == 0 || d > 450) return ZONE_INVALID;
  if (d < 15)            return ZONE_IMPACT;
  if (d < 40)            return ZONE_DANGER;
  if (d < 80)            return ZONE_WARNING;
  if (d < 150)           return ZONE_AWARE;
  return ZONE_SAFE;
}

unsigned int readUltrasonicCM() {
  digitalWrite(TRIG_PIN, LOW);
  delayMicroseconds(2);
  digitalWrite(TRIG_PIN, HIGH);
  delayMicroseconds(10);
  digitalWrite(TRIG_PIN, LOW);

  unsigned long duration = pulseIn(ECHO_PIN, HIGH, 25000);
  if (duration == 0) {
    ultrasonicTimeout = true;
    return 0;
  }
  ultrasonicTimeout = false;
  return duration / 58;
}

void hapticAllOff() {
  digitalWrite(HAPTIC_LEFT, LOW);
  digitalWrite(HAPTIC_RIGHT, LOW);
  digitalWrite(HAPTIC_BOTTOM, LOW);
}

void hapticLeft(bool on)   { digitalWrite(HAPTIC_LEFT, on ? HIGH : LOW); }
void hapticRight(bool on)  { digitalWrite(HAPTIC_RIGHT, on ? HIGH : LOW); }
void hapticBottom(bool on) { digitalWrite(HAPTIC_BOTTOM, on ? HIGH : LOW); }

void updateHaptics() {
  static unsigned long lastToggle = 0;
  static bool state = false;
  unsigned long now = millis();
  unsigned long interval = 0;

  switch (currentZone) {
    case ZONE_SAFE: hapticAllOff(); return;
    case ZONE_AWARE: interval = 1000; break;
    case ZONE_WARNING: interval = 400; break;
    case ZONE_DANGER: interval = 150; break;
    case ZONE_IMPACT:
      hapticLeft(true); hapticRight(true); hapticBottom(true);
      return;
    default: hapticAllOff(); return;
  }

  if (now - lastToggle >= interval) {
    lastToggle = now;
    state = !state;
    hapticLeft(state); hapticRight(state); hapticBottom(state);
  }
}

// ==========================================
// 4. AUDIO SETUP & CORE 0 TASKS
// ==========================================

void setup_speaker() {
  i2s_chan_config_t chan_cfg = I2S_CHANNEL_DEFAULT_CONFIG(I2S_NUM_AUTO, I2S_ROLE_MASTER);
  chan_cfg.dma_desc_num = 16;      
  chan_cfg.dma_frame_num = 1024;  
  chan_cfg.auto_clear = true;     

  if (i2s_new_channel(&chan_cfg, &tx_chan, NULL) != ESP_OK) return;

  i2s_std_config_t std_cfg = {
      .clk_cfg  = I2S_STD_CLK_DEFAULT_CONFIG(SAMPLE_RATE), 
      .slot_cfg = I2S_STD_PHILIPS_SLOT_DEFAULT_CONFIG(I2S_DATA_BIT_WIDTH_16BIT, I2S_SLOT_MODE_MONO),
      .gpio_cfg = {
          .mclk = I2S_GPIO_UNUSED,
          .bclk = (gpio_num_t)I2S_BCLK,
          .ws   = (gpio_num_t)I2S_LRC,
          .dout = (gpio_num_t)I2S_DIN,
          .din  = I2S_GPIO_UNUSED,
          .invert_flags = {.mclk_inv = false, .bclk_inv = false, .ws_inv = false},
      },
  };

  i2s_channel_init_std_mode(tx_chan, &std_cfg);
  i2s_channel_enable(tx_chan);
}

void AudioTaskCode(void * pvParameters) {
  audioUdp.begin(audioReceivePort);
  uint8_t incomingAudioBuffer[2048]; 

  for(;;) {
    int packetSize = audioUdp.parsePacket();
    if (packetSize > 0) {
      int bytesRead = audioUdp.read(incomingAudioBuffer, sizeof(incomingAudioBuffer));
      if (bytesRead > 0) {
        size_t bytesWritten = 0;
        i2s_channel_write(tx_chan, incomingAudioBuffer, bytesRead, &bytesWritten, portMAX_DELAY);
      }
    } else {
      // ONLY delay if there is no UDP packet waiting. 
      // This prevents artificial slowdowns when receiving a steady stream of audio chunks.
      vTaskDelay(1); 
    }
  }
}

// ==========================================
// 5. MAIN SETUP
// ==========================================

void setup() {
  Serial.begin(115200);
  Serial1.begin(115200, SERIAL_8N1, RX_PIN, TX_PIN); 
  
  Serial.println("\n=============================================");
  Serial.println("SYSTEM ALIVE: ESP32-S3 Booting up...");
  Serial.println("=============================================");

  pinMode(HAPTIC_LEFT, OUTPUT);
  pinMode(HAPTIC_RIGHT, OUTPUT);
  pinMode(HAPTIC_BOTTOM, OUTPUT);
  hapticAllOff();

  pinMode(TRIG_PIN, OUTPUT);
  pinMode(ECHO_PIN, INPUT);

  // Initialize audio interface (Speaker only)
  setup_speaker();

  WiFi.begin(ssid, password);
  Serial.print("SYSTEM: Connecting to Wi-Fi");
  while (WiFi.status() != WL_CONNECTED) {
    delay(500);
    Serial.print(".");
  }
  Serial.println("\n[OK] Connected to Wi-Fi!");
  
  // Pin Audio TX task to Core 0
  xTaskCreatePinnedToCore(AudioTaskCode, "AudioTask", 10000, NULL, 1, &AudioTask, 0);

  Serial.println("SYSTEM: Background tasks running.");
  Serial.println("SYSTEM: Waiting for Camera Target Data...");
  Serial.println("=============================================\n");
}

// ==========================================
// 6. MAIN LOOP (CORE 1)
// ==========================================

void loop() {
  // ------------------------------------------------
  // TASK A: ULTRASONIC & HAPTICS
  // ------------------------------------------------
  if (millis() - lastUltrasonicRead > 60) {
    lastUltrasonicRead = millis();
    distanceCM = readUltrasonicCM();
    softError = ultrasonicTimeout;
    currentZone = getDistanceZone(distanceCM);
  }

  lastZone = currentZone; 
  updateHaptics();        

  // ------------------------------------------------
  // TASK B: CAMERA TRACKING 
  // ------------------------------------------------
  if (Serial1.available()) {
    String incomingData = Serial1.readStringUntil('\n');
    incomingData.trim(); 
    
    if (incomingData.length() > 0) {
        Serial.print("[CAM RAW] ");
        Serial.println(incomingData);
    }

    if (incomingData.startsWith("TRACK:")) {
      String valueString = incomingData.substring(6); 
      float confidence = valueString.toFloat();
      
      Serial.print("[CAM PARSED] Confidence: ");
      Serial.print(confidence);
      
      if (millis() - lastDetectionTime >= COOLDOWN_MS) {
        if (confidence >= THRESHOLD) {
          consecutiveFrames++;
          Serial.print("  |  Filter: Frame ");
          Serial.print(consecutiveFrames);
          Serial.print("/");
          Serial.println(REQUIRED_FRAMES);
        } else {
          consecutiveFrames = 0;
          Serial.println("  |  Filter: Reset (Below Threshold)");
        }

        if (consecutiveFrames >= REQUIRED_FRAMES) {
          Serial.println("\n>>> [UDP FIRE] THRESHOLD MET! SENDING 'track' TO APP... <<<");
          
          camUdp.beginPacket(targetIP, camUdpPort);
          camUdp.print("track"); 
          camUdp.endPacket();

          Serial.println(">>> [UDP FIRE] SUCCESS! ENTERING 3-SECOND COOLDOWN. <<<\n");

          lastDetectionTime = millis();
          consecutiveFrames = 0;
        }
      } else {
         Serial.println("  |  Status: COOLDOWN ACTIVE");
      }
    }
  }
}