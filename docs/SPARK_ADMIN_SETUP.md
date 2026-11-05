# JARVIS admin setup on Firebase Spark

This workflow does not use Cloud Functions, Cloud Run, or a paid Firebase plan. It runs the Firebase Admin SDK locally in Termux.

## Security rules

- Never put `serviceAccountKey.json` in GitHub, the APK, or a public chat.
- Do not add the service-account private key to Android app code or ordinary app settings.
- Only run this tool on a device/account you control.
- If the key is exposed, revoke it immediately in Google Cloud IAM and create a replacement.
- Admin access is stored as Firebase Authentication custom claims. Firestore rules check `request.auth.token.admin == true`.

## One-time setup

1. In Firebase Console, open Project settings and confirm you are in the intended project.
2. Open the linked Google Cloud project, then IAM & Admin → Service Accounts.
3. Create a dedicated service account for JARVIS administration. Give it the **Firebase Authentication Admin** role (`roles/firebaseauth.admin`). Do not use an Owner/Editor role.
4. Create a JSON key for that service account and download it to your Android device.
5. Place the file in the repository root as `serviceAccountKey.json`. The repository .gitignore excludes this filename. Never commit it.
6. In Termux, from the repository root, run:

```sh
pkg update
pkg install nodejs git -y
npm --prefix tools/firebase-admin install
npm --prefix tools/firebase-admin run check
```

7. Set the owner email in the current Termux session, then bootstrap the first admin:

```sh
export INITIAL_ADMIN_EMAIL="your-verified-owner-email@example.com"
node tools/firebase-admin/manage-admin.js bootstrap "$INITIAL_ADMIN_EMAIL"
```

Use the exact email of an existing Firebase Authentication user whose email is verified.

## Grant or revoke another admin

The target must already exist in Firebase Authentication and have a verified email.

```sh
node tools/firebase-admin/manage-admin.js grant "another-user@example.com"
node tools/firebase-admin/manage-admin.js revoke "another-user@example.com"
```

Keep `INITIAL_ADMIN_EMAIL` set in the same Termux session if you want the utility to protect the initial owner from accidental revocation.

## After changing a role

The user must sign out and sign in again, or the app must force-refresh the Firebase ID token. JARVIS reads the `admin` custom claim from the Firebase token; a Firestore profile field or local preference cannot make someone an admin.

## What remains free

This utility only calls Firebase Authentication Admin APIs using a service-account credential. It does not deploy Cloud Functions or require the Firebase Blaze plan. Normal Firebase product quotas and Google Cloud IAM permissions still apply.
