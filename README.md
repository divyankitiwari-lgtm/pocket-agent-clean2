# Pocket Agent Clean 2

A clean Android starter for a Gemini-powered screen assistant, designed to build an APK through GitHub Actions so the project can be managed from a phone.

## Current features
- Gemini `generateContent` API request using a key you enter in the app.
- API key is kept in memory only; it is not written to app preferences.
- Optional local accessibility observation of visible UI labels while a session is active.
- Start, Pause, and Stop controls for local screen observation.
- GitHub Actions builds a debug APK and uploads it as an artifact.

## Build and install
1. Open the **Actions** tab in this repository.
2. Open **Build Android APK** and wait for the run to finish successfully.
3. Open the completed run and download the `pocket-agent-debug-apk` artifact.
4. Extract the ZIP on your Android phone and install `app-debug.apk`.

## Set up
1. Open Pocket Agent and enter your Gemini API key and model (default: `gemini-2.5-flash`).
2. Tap **Open Accessibility Settings** and enable Pocket Agent only if you want local screen-label observation.
3. Tap **Start**, switch to the app you want help with, return and tap **Refresh observed screen text**.
4. Enter a task and tap **Ask Gemini**. Screen text is sent to Gemini only when the checkbox is enabled.

## Important limitations
This is an early assistant build, not a fully autonomous tapping agent. It does not click buttons, type into other apps, submit forms, install apps, or make purchases. Accessibility text may be incomplete on some screens. The app sends your prompt (and optionally screen labels) to Google Gemini when you tap Ask Gemini. Do not include private or sensitive information.

## Privacy
The API key is held in memory while the app process is running and is not saved by this app. It is sent to Google's Gemini API when a request is made. If the app closes, enter the key again.

## Project
- Android: Kotlin, native views
- Min SDK: 26
- Build: GitHub Actions + Gradle 8.9 + JDK 17
