# Pocket Agent Clean 2

> **Status:** experimental Android build. The dashboard uses a translucent glass-inspired card system, and the accessibility target selector prefers smaller valid targets. Firebase rules are a starter template only; Firebase Auth/Firestore client flows remain **inactive until the owner configures the Firebase client file, enables providers, and publishes tested rules**.

A clean Android starter for a Gemini-powered screen assistant, designed to build an APK through GitHub Actions so the project can be managed from a phone.

## Current features
- Native Kotlin Android dashboard with a colorful glass-inspired card system, gradient accents, and centered 52dp primary buttons.
- Gemini `generateContent` and OpenRouter-compatible requests using a key configured in the app.
- Encrypted local preferences are used for provider keys when AndroidX encrypted storage initializes successfully. Do not assume this is cloud account storage.
- Optional accessibility observation of visible UI labels while a session is active.
- Accessibility actions prefer the smallest reasonable visible clickable target and use a centered gesture fallback when semantic click actions are unavailable.
- Start, Pause/Resume, and Stop controls, activity log, local history, and an admin-only plan preview. Regular users cannot unlock paid limits by changing a local selector.
- GitHub Actions builds a debug APK and uploads it as an artifact.
- `firestore.rules` provides a deny-by-default starter for a future Firebase integration; see `FIREBASE_SECURITY_SETUP.md`.

## Build and install
1. Open the **Actions** tab in this repository.
2. Open **Build Android APK** and wait for the run to finish successfully.
3. Open the completed run and download the `pocket-agent-debug-apk` artifact.
4. Extract the ZIP on your Android phone and install `app-debug.apk`.

## Set up
1. Open Pocket Agent and enter your Gemini API key and model (default: `gemini-2.5-flash`).
2. If Firebase is configured, use Google sign-in or email/password sign-in. If not, choose **Continue offline** to use local features; account sync and cloud bug reports remain unavailable.
3. Tap **Open Accessibility Settings** and enable Pocket Agent only if you want screen observation and automation.
4. Tap **Start**, switch to the target app, then return and refresh the observed screen text. Enter your task and tap **Run task** to start step-by-step automation. **Pause/Resume** and **Stop** remain available.
5. Cloud mode sends your task and, only when enabled, observed screen labels to the selected provider. Local Rules mode needs no API key. Local AI requires a compatible MediaPipe `.task` model.

## Important limitations
- This is still an experimental UI-control prototype. Accessibility clicks can fail on apps with custom canvases, hidden labels, overlays, or unusual accessibility trees. Test carefully and keep Stop available.
- The glass effect is implemented with native gradients, translucent white card colors, outlines, and elevation; it is not a real-time background blur effect.
- Firebase email/password and Google sign-in flows plus password reset are in the client code. They require the project owner’s matching `app/google-services.json`, configured Firebase providers/OAuth client, matching signing-certificate SHA-1 for Google sign-in, and Firestore configured before they can run. Email verification is requested at account creation. Do not distribute a build as production-ready until configured and tested.
- Firebase Auth and Firestore dependencies/client calls are in the Android app, but no Firebase project config is included in this repository. Rules in `firestore.rules` are a starter and must be tested in Firebase Emulator/Rules Playground before deployment.
- Admin UI checks in a client are not a security boundary. The app checks the Firebase ID token’s `admin` custom claim for its admin console; production admin access still requires granting that claim only from a trusted Admin SDK and enforcing the Firestore rules. The bug-report inbox is stored in Firebase but is not automatically delivered to ChatGPT.
- “Local AI” is not currently a verified on-device LLM integration; some agent paths use deterministic local rules. Importing a model file alone does not prove that inference works.
- Accessibility text may be incomplete on some screens. Cloud mode sends your task and, when enabled, observed screen labels to the selected provider. Do not include passwords, API keys, payment details, OTPs, or other sensitive information.

## Privacy
Provider API keys are stored using AndroidX encrypted preferences when available, with a local fallback if encrypted storage cannot initialize. Cloud requests send the selected task and optionally observed screen labels to Gemini or OpenRouter. Never enter passwords, OTPs, payment details, or other sensitive information into automation tasks.

## Project
- Android: Kotlin, native views
- Min SDK: 26
- Build: GitHub Actions + Gradle 8.9 + JDK 17


## Enhancement tracking
- [Pocket Agent v2 issue #1](https://github.com/divyankitiwari-lgtm/pocket-agent-clean2/issues/1) tracks the remaining UI, accessibility, AI reliability, Firebase authentication, admin plan preview, and bug-report console work.
- [Firebase setup and security notes](FIREBASE_SECURITY_SETUP.md)


## Firebase client status
The app includes Firebase Authentication and Firestore client calls. For a working account/database build, add your Firebase-generated `app/google-services.json` privately (do not add service-account private keys), enable Email/Password Authentication, create Firestore, test and publish `firestore.rules`, and grant admin custom claims through a trusted backend. The current repository does not contain your Firebase project configuration, so the connected account/database path cannot be verified from this code alone.
