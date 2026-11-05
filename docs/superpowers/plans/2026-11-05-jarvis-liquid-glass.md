# JARVIS Liquid Glass Android App Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox syntax.

**Goal:** Deliver the full voice-first liquid-glass JARVIS experience with the uploaded hollow multicolor orb, full-screen auth and permission onboarding, and reliable observable voice-to-accessibility automation.

**Architecture:** Retain the existing native Kotlin app and its Firebase, voice foreground service, AccessibilityService and MediaPipe Gemma engine. Replace the orb renderer and convert the existing modal auth/permission gates into explicit full-screen states, then harden automation result reporting and CI packaging.

**Tech Stack:** Kotlin, Android SDK 26+, Firebase Auth/Firestore, Google Sign-In, Android SpeechRecognizer/TextToSpeech, AccessibilityService, MediaPipe Tasks GenAI, GitHub Actions.

**Spec:** docs/superpowers/specs/2026-11-05-jarvis-liquid-glass-design.md

## Global Constraints
- Package: com.example.pocketagent.
- minSdk 26; compatible with Android 10+.
- No root, Shizuku or shell-based tapping.
- No user-entered API key required for the default local Gemma path.
- Permission use must be explicit and user-controlled.
- Do not claim successful app opening/tapping without service confirmation.
- Work on rebuild/jarvis-unified-agent; never build the main branch for this delivery.

## Review Focus
- Fresh install with no Firebase session: auth must be first screen.
- Existing signed-in user with permissions missing: permission onboarding must precede home.
- User returns from Android Accessibility settings: permission state must refresh.
- Gemma asset missing/corrupt or inference throws: report fallback explicitly, do not claim Gemma answered.
- Accessibility service unavailable or target app absent: automation must stop with a clear error rather than report success.

---

### Task 1: Design record and source-of-truth orb
**Files:**
- Create: `docs/superpowers/specs/2026-11-05-jarvis-liquid-glass-design.md`
- Create: `docs/superpowers/plans/2026-11-05-jarvis-liquid-glass.md`
- Modify: `app/src/main/java/com/example/pocketagent/LiquidOrbView.kt`

- [ ] Confirm docs are committed on the rebuild branch.
- [ ] Implement a hollow contour orb: transparent centre, red/orange upper contour, blue lower contour, multiple offset folds, organic deformation and state-driven speed/amplitude.
- [ ] Keep Canvas APIs available on Android 10; stop animation when detached; expose accessible state description.
- [ ] Verify Kotlin compile through the branch Actions workflow.

### Task 2: Full-screen authentication
**Files:**
- Modify: `app/src/main/java/com/example/pocketagent/MainActivity.kt`

- [ ] Replace the AlertDialog auth gate with a full-screen view using the reference's dark glass card, email arrow affordance, Google sign-in and sign-up/sign-in switch.
- [ ] Keep Firebase email/password, email verification, reset-password and Google token exchange logic.
- [ ] Show loading/error feedback inside the screen; never persist raw passwords.
- [ ] Preserve authenticated account state across relaunch.

### Task 3: Full-screen permission onboarding
**Files:**
- Modify: `app/src/main/java/com/example/pocketagent/MainActivity.kt`
- Modify only if required: `app/src/main/AndroidManifest.xml`

- [ ] Replace modal permission dialog with a full-screen glass setup page.
- [ ] Show microphone, speech recognition and Accessibility status independently.
- [ ] Runtime-request microphone; deep-link to Android Accessibility settings.
- [ ] Re-check permission after returning to MainActivity; do not rely only on a stale SharedPreferences flag.
- [ ] Continue to home only when microphone and Accessibility are actually available.

### Task 4: Voice-first home and orb state
**Files:**
- Modify: `app/src/main/java/com/example/pocketagent/MainActivity.kt`
- Modify: `app/src/main/java/com/example/pocketagent/VoiceSessionService.kt`
- Modify: `app/src/main/java/com/example/pocketagent/VoiceCommandParser.kt`

- [ ] Keep the orb as the main interactive control; no visible task text field or standalone Live Automation tab.
- [ ] Connect orb tap to voice session start/stop.
- [ ] Preserve spoken progress answers during active automation and TTS responses.
- [ ] Keep pause/resume/stop/status controls deterministic; report unavailable speech service honestly.

### Task 5: Automation and Gemma reliability
**Files:**
- Modify: `app/src/main/java/com/example/pocketagent/MainActivity.kt`
- Modify: `app/src/main/java/com/example/pocketagent/AgentAccessibilityService.kt`
- Modify: `app/src/main/java/com/example/pocketagent/GemmaEngine.kt`

- [ ] Verify default engine is bundled Gemma; no blank API key blocks the default path.
- [ ] Keep Gemma loaded once and release safely; show model loading/inference/fallback status.
- [ ] Ensure the planner waits for AccessibilityService result and distinguishes queued gestures from completed actions.
- [ ] Ensure tap text matching and app launch failures return actionable errors; never treat an empty result as success.
- [ ] Preserve stop/pause responsiveness and prevent duplicate pending actions.

### Task 6: CI, artifact and final verification
**Files:**
- Modify only if required: `.github/workflows/build-apk.yml`

- [ ] Trigger build on rebuild/jarvis-unified-agent.
- [ ] Confirm the Gemma asset download step ran and logs its real byte size.
- [ ] Confirm Gradle assembleDebug, signing report and artifact upload all succeed.
- [ ] Download/check artifact size and APK package metadata.
- [ ] Report compile/packaging evidence separately from device-only voice and tap tests.
