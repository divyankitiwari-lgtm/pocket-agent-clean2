# Firebase Auth + Firestore setup (not yet wired into the Android app)

This repository now includes a deny-by-default `firestore.rules` starter. It is **not a live database connection** and does not turn the current demo sign-in into real authentication. Do not claim accounts/admin permissions are secure until the Android app and trusted role-granting backend are implemented and tested.

## Free-tier-first setup

1. Create a Firebase project in the Firebase console and register the Android app with package name `com.example.pocketagent`.
2. Download the matching `google-services.json` into `app/` only after verifying the Android package name. Do not paste private service-account keys or admin credentials into the repository.
3. Enable **Authentication → Sign-in method → Email/Password**.
4. Create a Firestore database and publish the repository's `firestore.rules` after testing them in the Firebase Rules Playground / Emulator.
5. Use Firebase Authentication for passwords. The app must never store plaintext passwords, log them, or try to encrypt passwords itself. Firebase manages password authentication.
6. For admin users, assign the `admin: true` custom claim from a trusted server/admin SDK environment. Never let a client app set its own admin claim or trust a local email-only check.
7. Add an admin-only bug-report screen that writes only sanitized reports to `bugReports`. Do not upload API keys, passwords, OTPs, payment information, or private screen contents.
8. Add Firebase Emulator tests for user isolation, denied role changes, and admin-only report reads/updates before releasing.

## Cost and privacy

Firebase has no-cost quotas, but availability and limits depend on the chosen products and can change. Check the current Firebase pricing/usage page and configure budget alerts before enabling any paid billing. Do not assume every usage level is free.

The current app still uses a local demo profile and stores task history on the device. API requests to Gemini/OpenRouter are cloud requests; choosing “Local AI” currently falls back to local rules in the agent path and is not proof that a real on-device model is running. The app must disclose these distinctions clearly.

## Admin plan testing

A UI plan switch is only a testing preview. It must not grant real paid entitlements. Store authoritative entitlements in a trusted backend and validate them server-side. Admin bypasses should be based on verified custom claims, never on a client-editable preference.
