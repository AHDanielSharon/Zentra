# ZENTRA

ZENTRA now supports **easy one-click deployment on Render** with a fully working web voice-assistant mode.

## 1-click Render deploy (recommended)

[![Deploy to Render](https://render.com/images/deploy-to-render-button.svg)](https://render.com/deploy?repo=https://github.com/your-username/your-repo)

Or manually:
1. Push this repo to GitHub.
2. In Render click **New + → Blueprint**.
3. Select the repo.
4. Render auto-uses `render.yaml` and deploys immediately.

The deployed app is a static voice-assistant web app under `site/index.html`.

### Install as app (PWA)

- Open deployed URL in Chrome.
- Click **Install ZENTRA App** button shown on the page.
- If button is hidden, open browser menu and choose **Install app** / **Add to Home screen**.

## Web app behavior (works on deployed Render URL)

- Always listening in browser (where supported).
- Wake word: **Hey Zentra**.
- Direct command mode also works (example: `open whatsapp` without wake word).
- Then command:
  - `open google`
  - `open whatsapp`
  - `open youtube`
  - `open settings` (browser-safe message shown)
- Shows status + what was heard.
- Gives short voice responses.

### Important limitation (web mode)

- Browser mode cannot open apps when this page is closed or running in background tabs due to browser security rules.
- For true system-wide, screen-off/background app opening, use the Android app build in `app/` (foreground service mode).

## Android mode capabilities

- Supports specific commands (`open whatsapp`, `open youtube`, etc.) and generic command style: `open <app name>`.
- Direct command works without wake word too in Android mode (for example: `open camera`).
- Service is configured to restart on boot/package update and attempts recovery if app task is removed.
- If user **force stops** the app from Android Settings, Android blocks restart until the user manually opens the app again.

> Best experience: latest Chrome with microphone permission enabled.

---

## Android source still included

Android app source code remains in `app/` for Android Studio builds.

### Android build

```bash
gradle :app:assembleDebug
```

APK path:

```text
app/build/outputs/apk/debug/app-debug.apk
```
