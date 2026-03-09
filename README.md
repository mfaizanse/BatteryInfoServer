# Battery Info Server

An Android application that runs a lightweight HTTP server in the background, exposing phone diagnostic information as a REST API.

## Overview

The HTTP server runs on port **9091** and starts automatically on device boot, making it suitable for 24/7 unattended operation.

## API

### `GET /battery`

Returns a JSON response with battery status, device info, and network environment.

**Example response:**

```json
{
  "status": {
    "level": 87,
    "temp": "29.5°C",
    "voltage": 4250,
    "health": "good",
    "is_charging": true,
    "power_source": "usb"
  },
  "device": {
    "model": "Pixel 7",
    "manufacturer": "Google",
    "uptime_seconds": 3600,
    "ip_address": "192.168.1.42"
  },
  "environment": {
    "wifi_signal": -55,
    "data_connection": "wifi"
  }
}
```

**Field descriptions:**

| Field | Type | Description |
|-------|------|-------------|
| `status.level` | int | Battery percentage (0–100) |
| `status.temp` | string | Battery temperature in Celsius |
| `status.voltage` | int | Battery voltage in millivolts |
| `status.health` | string | `good`, `overheat`, `dead`, `over_voltage`, `failure`, `cold`, `unknown` |
| `status.is_charging` | bool | `true` if charging or full |
| `status.power_source` | string | `ac`, `usb`, `wireless`, `dock`, `unplugged`, `unknown` |
| `device.model` | string | Device model name |
| `device.manufacturer` | string | Device manufacturer |
| `device.uptime_seconds` | int | Seconds since last boot |
| `device.ip_address` | string | Device IPv4 address |
| `environment.wifi_signal` | int | WiFi RSSI in dBm, or `-1` if not connected |
| `environment.data_connection` | string | `wifi`, `cellular`, `ethernet`, `vpn`, `bluetooth`, `other`, `none` |

## UI

A minimal single-screen UI with a **Start Server** / **Stop Server** toggle button and a status line showing whether the server is running.

## Auto-start on Boot

The server starts automatically when the device boots (including Direct Boot mode before the user unlocks the screen). No user interaction is required.

## Project Structure

```
app/src/main/
├── AndroidManifest.xml
├── kotlin/com/mfaizanse/batteryinfoserver/
│   ├── MainActivity.kt          # UI: start/stop toggle button
│   ├── BatteryInfoService.kt    # Foreground service + NanoHTTPD HTTP server
│   └── BootReceiver.kt          # Auto-starts the service on device boot
└── res/
    ├── layout/activity_main.xml
    └── values/{strings, colors, themes}.xml
```

## Tech Stack

| Component | Choice |
|-----------|--------|
| Language | Kotlin |
| HTTP server | NanoHTTPD 2.3.1 |
| Background execution | Foreground Service (`START_STICKY`) |
| CPU keep-alive | `PARTIAL_WAKE_LOCK` |
| Min SDK | 26 (Android 8.0) |
| Target SDK | 36 |
| Build system | Gradle 9.3.1 / AGP 9.1.0 |

## Building

Requires the [Android SDK](https://developer.android.com/studio) (installable via Android Studio).

```bash
# Build debug APK
./gradlew assembleDebug

# Install on connected device
adb install app/build/outputs/apk/debug/app-debug.apk
```

## Testing

```bash
# Find the device IP (shown in the app UI or via adb)
adb shell ip addr show wlan0

# Query the endpoint
curl http://<device-ip>:9091/battery
```

## Permissions Used

| Permission | Purpose |
|------------|---------|
| `INTERNET` | Bind and serve HTTP on port 9091 |
| `ACCESS_NETWORK_STATE` | Detect active network type |
| `ACCESS_WIFI_STATE` | Read WiFi signal strength |
| `WAKE_LOCK` | Keep CPU running when screen is off |
| `RECEIVE_BOOT_COMPLETED` | Auto-start after reboot |
| `FOREGROUND_SERVICE` | Long-running background service |
| `FOREGROUND_SERVICE_SPECIAL_USE` | Required on Android 14+ for the service type |
