# Implementation Plan: JARVIS Voice-First Liquid Glass Redesign

## Task 1 — Establish baseline and add voice permissions
- Inspect manifest, Gradle dependencies, MainActivity lifecycle, and existing agent state loop.
- Add RECORD_AUDIO and foreground-service permissions/types required for supported Android versions.
- Add focused tests/helpers for voice command parsing: pause, resume/continue, stop, progress/status, and normal task utterances.
- Confirm existing minSdk 26 compatibility; avoid API 31-only rendering calls.

## Task 2 — Build the liquid-glass orb
- Add a dedicated custom View for the orb, driven by a sealed/enum UI state: IDLE, LISTENING, THINKING, SPEAKING, AUTOMATING, PAUSED, ERROR.
- Draw layered radial gradients, animated contour/ripple and highlight bands with Canvas/Path/ValueAnimator or Choreographer.
- Add reduced-motion and lifecycle pause/resume handling.
- Add state transition tests for orb state mapping.

## Task 3 — Add voice session controller
- Add a voice controller/service using SpeechRecognizer and TextToSpeech with runtime permission, recognizer availability, errors, and utterance completion.
- Keep listening active during a task through a user-visible foreground service/notification where Android requires it.
- Route recognized commands to the agent state controller; do not expose a typed composer.
- Ensure user questions are answered aloud from current agent status without restarting the task.

## Task 4 — Wire voice controls into the agent loop
- Refactor agent controls so pause/resume/stop are thread-safe and checked before every action dispatch.
- Add progress snapshots for the spoken status response.
- Speak action progress and failure explanations; avoid speaking every internal model token.
- Verify Stop clears pending action/session state and releases the microphone service.
- Preserve existing planning/model fallback and action verification.

## Task 5 — Redesign authentication and permissions
- Restyle the existing login dialog into a full dark glass login/signup screen.
- Keep Google and email/password; remove no existing Firebase security checks.
- After successful sign-in, show a dark permission onboarding screen until microphone and Accessibility permissions are available.
- Preserve verified Firebase Admin custom-claim logic and actual plan selection.

## Task 6 — Replace the dashboard home screen
- Remove Live Automation button/dialog and bottom Home/Connections tab strip from the primary screen.
- Remove the visible task EditText and dashboard cards from the primary home UI.
- Add hamburger menu, account UID, Settings, logout, current-plan pill, central orb and voice-state caption.
- Keep Connections accessible from Settings/menu if needed without reintroducing a dashboard tab.

## Task 7 — Improve Accessibility tap handling
- Preserve exact/whole-phrase label matching and clickable-node preference.
- Ensure fallback gestures target the centre of the selected clickable node.
- Add tests for label normalization/matching and edge cases.
- Speak when no safe target is found; never silently claim success.

## Task 8 — Build and verify
- Run unit tests and Android lint/build through GitHub Actions.
- Inspect workflow logs and artifact metadata; verify APK exists before sharing.
- Report platform limitations honestly and request on-device testing for microphone behavior and actual app taps.
