# JARVIS Liquid Glass Android App — Design

## Intent
Turn the existing Kotlin Android JARVIS app into a coherent, voice-first liquid-glass assistant. The uploaded 20-second orb animation is the source of truth for the JARVIS identity: black negative space, a hollow near-black centre, a fine irregular red/orange upper rim, electric-blue lower rim, and several offset fluid contour folds that grow, curl, travel around the orb, and settle.

## Goals
- Rebuild login/sign-up as a full-screen glass interface, not an AlertDialog.
- After authentication, show a dedicated full-screen permission setup screen before the home screen.
- Request microphone permission at runtime and direct the user to Android Accessibility settings for app-control permission; explain why and re-check when returning.
- Make the home screen voice-first: central tappable liquid orb, clear state captions, compact glass menu, no visible task textbox or API-key controls.
- Keep the orb state synchronized with listening, thinking, speaking, automation, paused, and error states.
- Keep existing Firebase email/password and Google sign-in, account persistence, admin custom-claim verification, history and settings.
- Keep app opening and screen actions in the AccessibilityService, with explicit user-enabled service permission and visible success/failure status.
- Keep Gemma local inference as the default no-user-key path; verify the model asset is present, complete, copied safely, loaded once, and failures are explicit. Local rules remain a clearly labelled fallback, not a false claim that Gemma ran.
- Use GitHub Actions on the rebuild branch for APK packaging and artifact delivery.

## Non-goals
- No root, Shizuku, shell-tap or hidden accessibility bypass.
- No end-user API-key requirement.
- No hard-coded target app or app-specific automation workflow.
- No payment/plan upsell work in this redesign.
- No claim that tapping works on every OEM/device without device testing.

## Constraints
- Native Kotlin Android app; retain package com.example.pocketagent.
- Android 10+ compatibility (minSdk 26); avoid Android 12-only blur APIs.
- User works Android-only and expects GitHub Actions APK artifacts.
- User explicitly asked to begin without another design-approval round; the supplied reference and recalled requirements are treated as approved.
- Keep sensitive permissions transparent and user-controlled.

## Visual system
- Canvas: near-black #030407 with subtle dark navy depth.
- Orb: transparent centre; thin layered contour strokes, not a filled sphere. Upper light: coral/red-orange #FF6B55; lower light: electric blue #367BFF; transitional violet #9B72FF. Glow is restrained and localized to the contour.
- Glass panels: translucent charcoal/navy with bright hairline border, soft inner highlight, rounded corners, and layered depth. Use rounded rectangles and circles only.
- Text: high-contrast cool white; secondary blue-grey; coral/blue only for meaningful state accents.
- Motion: slow organic contour deformation at idle; faster multi-fold movement while listening/thinking; directional flowing contour while automating; stable dimmed contour while paused; restrained warning hue for errors. Respect system reduced-motion setting.

## Screen flow
Login / Sign-up → authenticated permission setup → JARVIS home.
Permission setup includes microphone, Accessibility app control, and speech service availability explanation. Continue only when required permissions are confirmed; re-check Accessibility after returning from Settings.
Home orb tap starts/stops the foreground voice session. Voice task broadcasts start the autonomous agent. Follow-up speech during an active task is answered by local Gemma where available and spoken via TTS. Pause/resume/stop/status commands remain available.
Settings: Permissions & voice, Connections, Saved history, Log out. No standalone Live Automation tab.

## Architecture
Keep the existing MainActivity, VoiceSessionService, AgentAccessibilityService, VoiceCommandParser and GemmaEngine architecture. Replace the orb drawing with a contour-first renderer. Refactor authentication and permission gates to full-screen views within MainActivity while retaining the existing Firebase methods. Keep automation execution and screen inspection in AgentAccessibilityService; add truthful status propagation and never report an unconfirmed tap as success.

## Acceptance checks
- Login and sign-up render as a full-screen screen with no modal dialog.
- After successful login, permission setup appears before home if either required permission is missing.
- Accessibility state is re-checked on resume from system settings.
- Orb matches the supplied reference's hollow multi-contour appearance and changes state with voice/automation.
- Orb tap starts and stops voice listening; answers are spoken; status follow-ups work during automation.
- App launch/tap/scroll actions wait for AccessibilityService confirmation and surface timeout/failure honestly.
- Build log proves the Gemma asset is included and exceeds the minimum integrity threshold; runtime logs distinguish Gemma inference from local-rule fallback.
- GitHub Actions run is from rebuild/jarvis-unified-agent, not main.
- APK artifact exists and can be downloaded; device interaction remains pending until tested on the target device.
