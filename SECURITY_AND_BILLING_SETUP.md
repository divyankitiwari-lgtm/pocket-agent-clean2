# Pocket Agent account security and subscriptions

## Security boundary

The current Android app uses a local demo profile. A typed email address is NOT proof of ownership and must never grant the owner/admin role. Do not ship the demo admin check as production authorization.

## Required production architecture

- Firebase Authentication: email/password sign-up, email verification, password reset.
- Firebase Cloud Functions or another trusted backend: all admin authorization and purchase verification.
- Firestore (or another server database): user profile, role, subscription entitlement, and security audit events.
- Google Play Billing: monthly auto-renewing Premium and Max products.
- Play Developer API verification from the backend: verify purchase token and product ID before setting an entitlement.
- Google Play Real-time Developer Notifications: update entitlement on renewal, cancellation, expiration, refund, or revocation.

## Owner/admin role

- Seed the initial owner role server-side for the verified owner UID after the owner has completed sign-in and verification.
- Do not grant admin because an email string matches in the APK.
- Only an owner-authorized backend operation may grant or revoke admin roles.
- Never put a permanent admin password, Firebase service-account key, or server secret in the APK or repository.
- Use a unique generated password stored in a password manager, and enable strong account protection. Do not share one admin password among people.

## New-device login alerts

When Firebase Authentication reports a successful sign-in, a trusted backend should record an audit event with the account UID, event time, app/device metadata available from the client, and risk signals. Device metadata is not strong proof of identity and may be spoofed. Send an email notification for a new or suspicious sign-in using a server-side email provider. Do not claim that a new-device alert proves a password was cracked.

Provide an owner-only security page listing recent sessions/events and a way to revoke refresh tokens or sessions. Requiring re-authentication and an additional verification step for suspicious sign-ins is recommended. Never log passwords, OTPs, API keys, or full payment credentials.

## Monthly plan entitlements

Use stable product IDs such as `pocket_premium_monthly` and `pocket_max_monthly`. The client sends the purchase token to the backend. The backend verifies it with Google Play before writing an entitlement. On app start/resume, the client fetches the current entitlement from the backend. Subscription renewal, grace period, cancellation, expiry, refund, and revocation must be handled from verified store state. Client-side plan selection is UI only and must not unlock paid features.

## Required setup before production code can work

1. Create or select a Firebase project.
2. Register the Android app using its exact application ID and add the project's `google-services.json` to the Android app module (do not commit secrets).
3. Enable Email/Password authentication and email verification.
4. Set up Firestore rules and deploy backend functions with server-only role and entitlement writes.
5. Configure Google Play subscription products and the Play Developer API.
6. Configure a server-side email sender for security alerts.
7. Test sign-up, verification, password reset, new-device sign-in, session revocation, purchase, renewal, cancellation, refund, and account switching before release.

## Current status

This document is an implementation checklist, not a claim that Firebase authentication, server-side roles, login alerts, or Google Play billing are already connected. The existing local demo profile and plan selector are not production-secure.
