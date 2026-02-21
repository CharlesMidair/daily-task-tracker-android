# Daily Task Tracker (Android + Wear OS)

A simple daily task logging app for phone and Pixel Watch.

You define tasks (for example: pushups, pullups, pills, stretches), then tap to log each completion with a timestamp.

## Features

### Phone app
- Add, rename, delete tasks.
- Reorder tasks.
- Tap to log unlimited events per task.
- Expand a task to see event timestamps.
- Manual reset:
  - Same day: confirmation required.
  - Later day: resets immediately.
- Reset clears logs/counts but keeps tasks and task order.
- One-step global undo for last action.
- Local persistent storage (session data since last reset).

### Wear OS app
- Dedicated watch app module (not just phone mirroring).
- Manual refresh from phone state.
- Task list with counts.
- Task detail view with:
  - `Log Task`
  - `Undo Last Watch Log` (one-step, watch-only)
  - recent timestamps (up to 10 shown)
- Phone and watch sync both ways through Wear Data Layer messaging.

## Project structure

- `app/` - phone app module
- `wear/` - Wear OS app module

## Tech stack

- Kotlin
- Jetpack Compose (phone + wear compose)
- Android ViewModel + coroutines/flows
- SharedPreferences JSON storage
- Google Play Services Wearable API (Data Layer messaging)

## Tooling

- Android Gradle Plugin: `8.3.2`
- Gradle wrapper: `8.5`
- Kotlin: `1.9.24`
- Compile SDK: `34`

These versions are pinned for stability on this Windows setup.

## Requirements

- Android Studio
- Android SDK (API 34)
- Phone device/emulator (tested on Pixel 8 Pro)
- Wear OS watch/emulator (tested on Pixel Watch)

## Run the phone app

1. Open project in Android Studio.
2. Let Gradle sync finish.
3. Select run configuration `app`.
4. Select phone target device.
5. Run.

## Run the watch app

1. Ensure watch is ADB-connected (wireless debugging).
2. Select run configuration `wear`.
3. Select watch target device.
4. Run.
5. Open app on watch and tap `Refresh Tasks`.

## ADB quick commands (watch)

Use your actual watch IP/ports from watch wireless debugging screens.

```powershell
& "C:\Users\ccohen\AppData\Local\Android\Sdk\platform-tools\adb.exe" kill-server
& "C:\Users\ccohen\AppData\Local\Android\Sdk\platform-tools\adb.exe" start-server
& "C:\Users\ccohen\AppData\Local\Android\Sdk\platform-tools\adb.exe" pair WATCH_IP:PAIR_PORT
& "C:\Users\ccohen\AppData\Local\Android\Sdk\platform-tools\adb.exe" connect WATCH_IP:DEBUG_PORT
& "C:\Users\ccohen\AppData\Local\Android\Sdk\platform-tools\adb.exe" devices
```

## Notes

- Watch refresh is currently manual by design.
- No cloud sync/account required.
- No long-term history beyond current session since last reset.
