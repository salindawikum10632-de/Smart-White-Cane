# Smart White Cane

> An AIoT-powered assistive navigation system designed to provide visually impaired users with safer, more independent mobility through intelligent obstacle detection, indoor navigation, outdoor navigation, voice guidance, and haptic feedback.

## Overview

The **Smart White Cane** is a multi-component assistive technology system that extends the functionality of a conventional white cane beyond basic obstacle detection.

The project combines:

- ESP32-S3 embedded firmware
- Ultrasonic obstacle detection
- Multi-zone haptic feedback
- Real-time audio streaming using I2S
- MAX98357A audio amplification
- ESP32-CAM based tactile-path detection
- GPS-based outdoor navigation
- GPS-independent indoor navigation concepts
- Voice guidance and Text-to-Speech
- Bluetooth communication between the mobile application and the cane
- Google Maps services for outdoor navigation
- Dijkstra-based route planning as part of the broader indoor-navigation architecture

The system was developed collaboratively by a **four-member engineering team**, covering embedded firmware, Android application development, AI/computer vision, networking, and hardware-software integration.

---

## System Architecture

```text
                         SMART WHITE CANE
                                │
              ┌─────────────────┴─────────────────┐
              │                                   │
        Physical Cane                         Mobile App
              │                                   │
      ┌───────┼────────┐                 ┌────────┼─────────┐
      │       │        │                 │        │         │
 ESP32-S3  Ultrasonic Haptic          Outdoor   Indoor   Accessibility
      │       Sensor   Actuators      Navigation Navigation  / Voice
      │
      ├── I2S ──> MAX98357A ──> Speaker
      │
      ├── UDP <──> Companion Application
      │
      └── Serial <──> ESP32-CAM
                           │
                    Tactile Path Detection
                           │
                           ▼
                    Navigation Node Data
```

### Embedded Processing

The ESP32-S3 firmware separates time-sensitive functions using the ESP32's dual-core architecture:

- **Core 0:** Handles UDP audio reception and I2S audio output.
- **Core 1:** Handles ultrasonic sensing, haptic feedback, and communication with the camera subsystem.
- Ultrasonic distance is evaluated approximately every **60 ms**.
- Obstacles are classified into distance zones ranging from safe to impact.
- Haptic actuators provide feedback according to the detected distance.

### Camera Navigation

The ESP32-CAM is intended to identify tactile path markers and provide tracking information to the ESP32-S3. Detected markers can be treated as navigation nodes for an indoor navigation graph.

The broader navigation architecture uses **Dijkstra's algorithm** to determine a route between nodes without depending on GPS.

### Mobile Application

The Android application provides:

- User authentication
- Smart Cane Bluetooth connectivity
- Outdoor navigation
- Indoor navigation interface
- Voice guidance
- Accessibility-oriented touch interaction
- Settings and user controls

---

## Repository Structure

```text
smart-white-cane/
│
├── README.md
├── .gitignore
│
├── firmware/
│   └── esp32-s3/
│       └── firmware.ino
│
├── app/
│   ├── build.gradle.kts
│   ├── settings.gradle.kts
│   ├── gradle.properties
│   ├── gradlew
│   ├── gradlew.bat
│   ├── gradle/
│   └── src/
│
├── docs/
│
└── assets/
    ├── images/
    └── screenshots/
```

### Current Repository Contents

The supplied codebase currently contains:

- Android application source code under `app/`
- ESP32-S3 firmware under `firmware/esp32-s3/`

If the ESP32-CAM firmware, backend/Dijkstra implementation, trained model, or model-training notebooks are stored separately, they should be added to their respective folders rather than mixed with the Android or ESP32-S3 source code.

---

## Hardware Components

The main prototype uses components including:

| Component | Purpose |
|---|---|
| ESP32-S3 | Main embedded controller |
| ESP32-CAM | Computer vision / tactile-path detection |
| Ultrasonic sensor | Obstacle-distance measurement |
| Haptic actuators | Tactile navigation feedback |
| MAX98357A | I2S audio amplifier |
| Speaker | Voice/audio output |
| Smartphone | Navigation, voice interaction and application interface |

> Pin assignments and hardware-specific configuration are defined in the firmware source.

---

## Software & Technologies

### Embedded

- C/C++
- ESP32-S3
- Arduino framework
- FreeRTOS task scheduling
- I2S
- UDP
- Wi-Fi
- UART / Serial communication

### Android

- Java
- Android Studio
- Android SDK
- Google Maps / Location Services
- Bluetooth
- Text-to-Speech
- XML layouts

### AI / Computer Vision

- ESP32-CAM
- Computer vision model
- Tactile path marker detection
- Confidence-based detection filtering

### Algorithms

- Dijkstra's shortest-path algorithm
- Distance-zone classification
- Sensor filtering / consecutive-frame validation

---

## ESP32-S3 Firmware

The main firmware is located at:

```text
firmware/esp32-s3/firmware.ino
```

The firmware performs three major functions:

### 1. Ultrasonic Obstacle Detection

The firmware periodically measures the distance to obstacles and classifies the result into zones:

```text
SAFE
  ↓
AWARE
  ↓
WARNING
  ↓
DANGER
  ↓
IMPACT
```

The closer an obstacle becomes, the stronger and/or more frequent the haptic feedback becomes.

### 2. Audio Streaming

Audio packets are received over UDP and forwarded to the MAX98357A through I2S.

```text
Mobile / Companion Application
             │
            UDP
             ▼
         ESP32-S3
             │
            I2S
             ▼
         MAX98357A
             │
          Speaker
```

The audio task is pinned to **Core 0** to keep audio processing separate from the main sensing loop.

### 3. Camera Tracking

The ESP32-S3 receives tracking information from the ESP32-CAM through a serial connection.

A detection is accepted only when:

- The confidence exceeds the configured threshold.
- The required number of consecutive frames is detected.
- The cooldown period has elapsed.

This reduces false-positive navigation triggers.

---

## Android Application

The Android application is located in:

```text
app/
```

Important components include:

```text
app/src/main/java/com/example/smart_cane/
│
├── MainActivity.java
├── LoginActivity.java
├── SignupActivity.java
├── ForgotPasswordDialog.java
├── SettingsActivity.java
│
├── BluetoothService.java
├── DoubleTapHandler.java
├── OnSwipeTouchListener.java
│
├── IndoorNavigationActivity.java
└── OutdoorNavigationActivity.java
```

### Bluetooth

`BluetoothService.java` manages the Bluetooth connection between the Android application and the Smart Cane hardware.

### Outdoor Navigation

`OutdoorNavigationActivity.java` provides:

- Location tracking
- Map display
- Place search
- Navigation instructions
- Distance information
- Text-to-Speech guidance

### Indoor Navigation

`IndoorNavigationActivity.java` provides the current Android-side indoor-navigation interface and step-detection functionality. The complete tactile-node localization and graph-routing pipeline can be maintained separately under the `camera/` and `backend/` directories.

---

## Security Notice

**Do not commit credentials or API keys to a public GitHub repository.**

The original development code contains sensitive configuration values such as:

- Wi-Fi SSID/password
- Network IP addresses
- Google Maps API key
- Firebase configuration

Before publishing the repository:

1. Remove hard-coded Wi-Fi credentials.
2. Remove hard-coded API keys.
3. Regenerate/rotate any API key that has already been exposed.
4. Replace secrets with a local configuration mechanism.
5. Add secret/configuration files to `.gitignore`.
6. If `google-services.json` contains credentials/configuration that should remain private for your project, do not publish it blindly; follow Firebase's recommended Android configuration and API-key restrictions.

Example:

```cpp
const char* ssid = "YOUR_WIFI_SSID";
const char* password = "YOUR_WIFI_PASSWORD";
const char* targetIP = "YOUR_DEVICE_IP";
```

For Android API keys, use a local properties/configuration mechanism rather than hard-coding the key directly in Java source.

---

## Getting Started

### 1. Clone the Repository

```bash
git clone https://github.com/YOUR_USERNAME/smart-white-cane.git
cd smart-white-cane
```

### 2. ESP32-S3 Firmware

Open:

```text
firmware/esp32-s3/firmware.ino
```

Configure:

- Wi-Fi credentials
- Target device IP
- UDP ports
- GPIO assignments
- Audio sample rate

Then compile and upload the firmware using Arduino IDE or another ESP32-compatible development environment.

### 3. Android Application

Open the repository in **Android Studio**.

The Android project is located under:

```text
app/
```

Allow Gradle to synchronize the project and install the required dependencies.

Before building, configure:

- Google Maps API access
- Firebase configuration, if required
- Bluetooth permissions
- Location permissions

### 4. Hardware

Connect the ESP32-S3 to:

- Ultrasonic sensor
- Haptic actuators
- MAX98357A
- Speaker
- ESP32-CAM

Ensure that the GPIO assignments in the firmware match the physical wiring.

---

## Development Workflow

To keep the project maintainable, each subsystem should be developed independently:

```text
Firmware
   │
   ├── Sensor acquisition
   ├── Haptic feedback
   ├── Audio streaming
   └── Camera communication

Android App
   │
   ├── User interface
   ├── Bluetooth
   ├── Outdoor navigation
   ├── Indoor navigation
   └── Voice accessibility

Computer Vision
   │
   ├── Dataset
   ├── Model training
   ├── Model evaluation
   └── ESP32-CAM deployment

Backend
   │
   ├── Navigation graph
   ├── Node management
   └── Dijkstra routing
```

This separation makes the repository easier to understand, test, maintain, and extend.

---

## Project Goals

The Smart White Cane aims to evolve the traditional white cane from a passive obstacle-detection tool into an intelligent navigation platform.

The key objectives are:

- Improve obstacle awareness.
- Provide intuitive haptic feedback.
- Support voice-guided navigation.
- Enable outdoor navigation using location services.
- Enable GPS-independent indoor navigation.
- Reduce dependence on visual interfaces.
- Provide an accessible interaction model for visually impaired users.
- Integrate AI, IoT, embedded systems, and mobile technologies into a single assistive platform.

---

## Team

Developed by a **four-member engineering team** as a collaborative AIoT assistive-technology project.

Responsibilities across the project included:

- Embedded firmware development
- Android application development
- Computer vision and AI model development
- Backend/navigation logic
- Hardware integration
- System testing and debugging

---

## Project Status

🚧 **Development / Prototype**

The project is an engineering prototype and is intended for continued development, hardware testing, model optimization, and system validation.

---

## Disclaimer

This project is intended as an assistive technology prototype. It should not be considered a replacement for a trained guide dog, mobility training, or other professional accessibility support without extensive real-world validation and safety testing.

---

## License

Add the project's selected open-source license here, for example MIT, Apache-2.0, or another license appropriate to the team's requirements.
