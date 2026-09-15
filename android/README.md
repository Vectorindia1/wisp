# Wisp for Android

Native Kotlin port of the desktop app's core loop: floating overlay ->
screenshot -> LLM -> streamed response, plus BYOK settings and live mic
transcription. See `docs/progress.md` (session 6) for the full session log
and `docs/memory.md` for the architectural tradeoffs made to get here.

## How this differs from the desktop app (Electron)

| | Desktop | Android |
|---|---|---|
| LLM calls | Python FastAPI backend, PyInstaller-bundled | **Client-only** -- the app calls provider HTTP APIs directly (`providers/*.kt`), no local server |
| Screenshot | `desktopCapturer` (no permission dialog) | `MediaProjection` -- **requires user consent every app (re)start** |
| Overlay window | Electron `BrowserWindow` + `setContentProtection(true)` | `WindowManager` + `TYPE_APPLICATION_OVERLAY` + `FLAG_SECURE` |
| Overlay UI | React (`Overlay.tsx`) | Plain Android Views, not Compose -- see `OverlayService.kt`'s class doc for why (Compose-in-a-raw-WindowManager-window lifecycle wiring is a real sharp edge, not worth the risk with zero device access to verify it) |
| Local transcription | `faster-whisper` (Python, via a background thread) | Android's `SpeechRecognizer` API, **not** whisper.cpp -- see `TranscriptionManager.kt`'s doc comment |
| Settings storage | Plaintext `userData/settings.json` | `EncryptedSharedPreferences` (Android Keystore-backed) -- a real improvement, not just a port |

## What is genuinely unverified

This was built with **no Android emulator, no connected device, and no
local Gradle/Android-SDK install** in the dev environment -- everything
below is "should work based on the documented API contracts," not
"observed working":

- **Does it compile at all?** Verified only via `.github/workflows/build-android.yml`'s CI run producing a debug APK -- check that workflow's latest run before assuming any of the below.
- **FLAG_SECURE against a real screen share** -- untested against actual Google Meet/Zoom/Teams screen sharing on a real device, same caveat the desktop app carries for `setContentProtection` (docs/PRD.md §6.3). Test this first; if it doesn't hold up, the entire "invisible overlay" premise fails on Android same as it would on any platform.
- **MediaProjection screenshot pixel format handling** in `CaptureManager.kt` (the `rowPadding`/`pixelStride` math) is standard for this API but has zero runtime verification -- a device-specific stride quirk would show up as a corrupted or shifted screenshot.
- **SpeechRecognizer's restart-on-every-result loop** in `TranscriptionManager.kt` -- untested for how it behaves over a long session (battery impact, whether Android throttles rapid restarts, whether `isOnDeviceRecognitionAvailable` actually engages on a real device vs. falling back to network recognition).
- **The whole permission chain in `MainActivity.kt`** -- overlay permission's `ACTION_MANAGE_OVERLAY_PERMISSION` intent, the mic/notification runtime permission dialogs, and the MediaProjection consent dialog all need to be exercised in the actual order a real user would hit them.

## Building it yourself

```bash
cd android
# Open in Android Studio (recommended) -- it will offer to generate the
# Gradle wrapper for you. No gradlew is checked in; see build.gradle.kts's
# top comment for why.
# Or, with a local Gradle 8.7+ install:
gradle assembleDebug
```

APK lands at `app/build/outputs/apk/debug/app-debug.apk`.

## Or: download the CI-built APK

Same pattern as the desktop app's Windows `.exe` -- push to `android/**`
(or trigger `workflow_dispatch` on `.github/workflows/build-android.yml`)
and download the `wisp-android-debug` artifact from the completed run. No
local Android tooling required.

**Installing an unsigned debug APK**: enable "Install unknown apps" for
whatever app you use to open the downloaded file (Settings > Apps > Special
access > Install unknown apps), then open the APK.

## First run

1. Launch Wisp -> grant "draw over other apps", microphone, and (Android
   13+) notifications permissions from the setup screen
2. Tap **Start Wisp** -> a system dialog asks to confirm screen-recording
   permission (this is MediaProjection's consent, required every launch --
   there's no way to skip this on Android, unlike the desktop app)
3. The floating overlay appears -- tap **Capture** to screenshot + ask your
   configured LLM, drag the overlay to reposition it
4. Tap the ⚙ on the overlay (or "Settings" on the setup screen) to pick a
   provider and paste an API key -- same BYOK model as desktop
