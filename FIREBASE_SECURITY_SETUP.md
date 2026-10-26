# Firebase Auth + Firestore setup

The Android client now includes Firebase email/password sign-up/sign-in, user-profile writes, and an admin bug-report inbox. These features remain inactive until the project owner adds a matching `app/google-services.json`, enables the required Firebase services, publishes tested rules, and grants admin claims from a trusted environment. The rules file is a starter and must be tested before production use.

## Free-tier-first setup

1. Create a Firebase project in the Firebase console and register the Android app with package name `com.example.pocketagent`.
2. Download the matching `google-services.json` into `app/` only after verifying the Android package name. Do not paste private service-account keys or admin credentials into the repository.
3. Enable **Authentication → Sign-in method → Email/Password**.
4. Create a Firestore database and publish the repository's `firestore.rules` after testing them in the Firebase Rules Playground / Emulator.
5. Use Firebase Authentication for passwords. The app must never store plaintext passwords, log them, or try to encrypt passwords itself. Firebase manages password authentication.
6. For admin users, assign the `admin: true` custom claim from a trusted server/admin SDK environment. Never let a client app set its own admin claim or trust a local email-only check.
7. The app's admin console can submit and review sanitized bug reports in `bugReports` once Firebase is configured and the user has a verified `admin` custom claim. Reports are stored in Firestore; they are not automatically delivered to ChatGPT or fixed in the background. Never upload API keys, passwords, OTPs, payment information, or private screen contents.
8. Add Firebase Emulator tests for user isolation, denied role changes, and admin-only report reads/updates before releasing.

## Cost and privacy

Firebase has no-cost quotas, but availability and limits depend on the chosen products and can change. Check the current Firebase pricing/usage page and configure budget alerts before enabling any paid billing. Do not assume every usage level is free.

Task history and API keys remain device-local. Firebase account profiles and bug reports are cloud data once the project is configured. API requests to Gemini/OpenRouter are cloud requests. Local AI attempts to run the imported MediaPipe `.task` model and reports an error if it cannot; it is not proof that every model file is compatible.

## Admin plan testing

A UI plan switch is only a testing preview. It must not grant real paid entitlements. Store authoritative entitlements in a trusted backend and validate them server-side. Admin bypasses should be based on verified custom claims, never on a client-editable preference.
