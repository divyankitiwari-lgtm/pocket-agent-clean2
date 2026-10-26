# Pocket Agent Clean 2

> **Status:** experimental Android build. The dashboard now has a colorful glass-inspired visual refresh and the accessibility target selector has been tightened. Firebase rules are a starter template only; Firebase Auth/Firestore are **not yet connected to the app**, and demo sign-in is not secure online authentication.

A clean Android starter for a Gemini-powered screen assistant, designed to build an APK through GitHub Actions so the project can be managed from a phone.

## Current features
- Native Kotlin Android dashboard with a colorful glass-inspired card system, gradient accents, and centered 52dp primary buttons.
- Gemini `generateContent` and OpenRouter-compatible requests using a key configured in the app.
- Encrypted local preferences are used for provider keys when AndroidX encrypted storage initializes successfully. Do not assume this is cloud account storage.
- Optional accessibility observation of visible UI labels while a session is active.
- Accessibility actions prefer the smallest reasonable visible clickable target and use a centered gesture fallback when semantic click actions are unavailable.
- Start, Pause, and Stop controls, activity log, local history, and demo plan selector.
- GitHub Actions builds a debug APK and uploads it as an artifact.
- `firestore.rules` provides a deny-by-default starter for a future Firebase integration; see `FIREBASE_SECURITY_SETUP.md`.

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
- This is still an experimental UI-control prototype. Accessibility clicks can fail on apps with custom canvases, hidden labels, overlays, or unusual accessibility trees. Test carefully and keep Stop available.
- The glass effect is implemented with native gradients, translucent white card colors, outlines, and elevation; it is not a real-time background blur effect.
- Sign-in is currently a local demo profile, not Firebase Authentication. Never use it to protect real user data.
- Firebase Auth/Firestore are not wired into the Android app yet. Rules in `firestore.rules` are a starter and must be tested in Firebase Emulator/Rules Playground before deployment.
- Admin UI checks in a client are not a security boundary. Production admin access requires trusted Firebase custom claims and server-side enforcement.
- “Local AI” is not currently a verified on-device LLM integration; some agent paths use deterministic local rules. Importing a model file alone does not prove that inference works.
- Accessibility text may be incomplete on some screens. Cloud mode sends your task and, when enabled, observed screen labels to the selected provider. Do not include passwords, API keys, payment details, OTPs, or other sensitive information.

## Privacy
The API key is held in memory while the app process is running and is not saved by this app. It is sent to Google's Gemini API when a request is made. If the app closes, enter the key again.

## Project
- Android: Kotlin, native views
- Min SDK: 26
- Build: GitHub Actions + Gradle 8.9 + JDK 17


## Enhancement tracking
- [Pocket Agent v2 issue #1](https://github.com/divyankitiwari-lgtm/pocket-agent-clean2/issues/1) tracks the remaining UI, accessibility, AI reliability, Firebase authentication, admin plan preview, and bug-report console work.
- [Firebase setup and security notes](FIREBASE_SECURITY_SETUP.md)
