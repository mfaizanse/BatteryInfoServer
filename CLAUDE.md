# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build Commands

```bash
# Build debug APK
./gradlew assembleDebug

# Build release APK
./gradlew assembleRelease

# Install on connected device via ADB
adb install app/build/outputs/apk/debug/app-debug.apk

# Test the running server (find device IP first)
adb shell ip addr show wlan0
curl http://<device-ip>:9091/battery
```

There are no unit tests in this project. All testing is manual, via the running HTTP endpoint on a physical device or emulator.

## Architecture

This is a single-module Android app (`minSdk 26`, `targetSdk 36`, Kotlin, AGP 9.1.0 / Gradle 9.3.1).

The app has three components that work together:

1. **`BatteryInfoService`** — The core of the app. A foreground `Service` that embeds a `NanoHTTPD` HTTP server on port 9091. It holds a `PARTIAL_WAKE_LOCK` to keep responding when the screen is off. Controlled via `ACTION_START` / `ACTION_STOP` intents. Returns `START_STICKY` so the OS restarts it after killing it under memory pressure. The inner class `BatteryHttpServer` routes requests: `/battery` returns JSON, all other paths return 404.

2. **`MainActivity`** — Minimal UI with a single toggle button (Start/Stop). Checks `ActivityManager.getRunningServices()` on cold start to sync button state with the service (which may have been auto-started at boot).

3. **`BootReceiver`** — `BroadcastReceiver` listening for both `BOOT_COMPLETED` and `LOCKED_BOOT_COMPLETED` (Direct Boot). Fires `startForegroundService` on the `BatteryInfoService` after reboot, requiring no user interaction.

### Data flow in `BatteryInfoService`

When `GET /battery` is received, `buildBatteryJson()` calls three private methods:
- `readBatteryStatus()` — reads the sticky `ACTION_BATTERY_CHANGED` broadcast (no receiver registration needed)
- `readDeviceInfo()` — uses `Build`, `SystemClock`, and `NetworkInterface` enumeration for IPv4
- `readEnvironmentInfo()` — uses `ConnectivityManager` (network type) and `WifiManager` (RSSI); both WiFi APIs are deprecated but remain the correct approach for this use case

### Key constraints
- `foregroundServiceType="specialUse"` is required for Android 14+ because the service doesn't fit a typed category (location, camera, etc.). The manifest includes the mandatory `PROPERTY_SPECIAL_USE_FGS_SUBTYPE` property alongside it.
- `local.properties` is gitignored and not committed. GitHub Actions runners expose the Android SDK via `$ANDROID_HOME` automatically, so no `local.properties` is needed in CI.
