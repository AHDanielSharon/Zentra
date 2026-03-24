# ZENTRA (Android Voice Assistant)

ZENTRA is a hands-free assistant app for Android that listens for the wake word **"Hey Zentra"** and then immediately processes voice commands.

## What it does

- Always listens in a foreground background service (no button needed).
- Wake word: **Hey Zentra**.
- After wake word, listens for command instantly.
- Supported commands:
  - "open google" / "open chrome" → opens Google Chrome
  - "open whatsapp" → opens WhatsApp
  - "open youtube" → opens YouTube
  - "open settings" → opens phone settings
- Shows heard text and status in the app UI.
- Speaks short confirmation like "Opening WhatsApp".
- Handles errors:
  - Unknown command → "Command not recognized"
  - App missing → "<App> is not installed"

---

## One-click Render deployment

Render cannot run Android apps directly on user phones, but this repo includes a **one-click Render blueprint** (`render.yaml`) that deploys a static landing service successfully.

### Deploy on Render

1. Push this repo to GitHub.
2. In Render, choose **New +** → **Blueprint**.
3. Select this repository.
4. Render auto-detects `render.yaml` and deploys `zentra-landing` with no extra config.

This gives you a smooth deployment on Render while the Android app source remains fully ready to build and install.

---

## Build and run Android app locally

### Requirements

- Android Studio (latest stable)
- Android SDK 34
- Device/emulator with microphone and Google speech recognition

### Build

```bash
./gradlew assembleDebug
```

APK output:

```text
app/build/outputs/apk/debug/app-debug.apk
```

### Install

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

---

## Permissions used

- `RECORD_AUDIO` for wake word + command listening
- `FOREGROUND_SERVICE` for always-on background behavior
- `POST_NOTIFICATIONS` (Android 13+) for foreground service notification
- `QUERY_ALL_PACKAGES` for checking app availability (Chrome, WhatsApp, YouTube)

---

## Notes

- Continuous speech recognition is implemented with Android `SpeechRecognizer` restart loop for low-latency behavior.
- Real production hotword engines can be swapped in later (e.g., offline wake-word engine) if needed.
