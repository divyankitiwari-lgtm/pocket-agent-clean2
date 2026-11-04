# JARVIS Voice-First Liquid Glass Redesign

## Intent
Replace the current dashboard-heavy home screen with a focused voice-first JARVIS experience. The user should speak requests, hear all JARVIS responses, and control an active automation by speaking without opening a separate Live Automation tab.

## Goals
- Preserve Firebase Google and email/password authentication, account identity, verified plan logic, admin custom-claim checks, local Gemma integration, Connections screen, and AccessibilityService automation.
- Keep the existing dark login/signup direction: email/password and Continue with Google only.
- After sign-in, show a dark permission onboarding screen explaining microphone and Accessibility access before opening the home screen.
- Make the home screen dark, minimal, and centred on one animated liquid-glass orb.
- Show the actual current plan in a rounded translucent glass pill above the orb: Free, Pro, Max, or Admin.
- Provide a three-line menu with account UID, Settings, and Log out.
- Remove the Live Automation tab/button and old text task entry/dashboard cards from the primary home experience.
- Support spoken task input, spoken answers, and spoken in-task controls/status questions.
- Improve safe, verified Accessibility tap target selection; never claim a tap succeeded until AccessibilityService reports a result.

## Non-goals
- No X sign-in.
- No separate Live Automation tab or text chat UI.
- No Shizuku, root, or shell-tap dependency.
- No new paid service, mandatory cloud API key, or separate Replit-hosted web app.
- No claim that every third-party app can be automated reliably.

## User experience
1. Signed out: dark glass login card; Google sign-in plus email/password sign-in and account creation.
2. Signed in, permissions incomplete: dark onboarding screen with clear explanations and separate actions to enable microphone and Accessibility.
3. Home: top hamburger menu; current-plan pill; one large animated liquid-glass orb; small voice-state caption. The orb visually distinguishes idle, listening, thinking, speaking, automating, paused, and error.
4. Voice session: user speaks a task; JARVIS transcribes internally, plans, executes, verifies and speaks results. No transcript/chat composer is exposed as the main interaction.
5. During automation: a microphone foreground service remains active with a persistent Android notification where platform rules require it. Spoken questions such as "How is it going?" receive a spoken progress answer without losing the current task. "Pause", "Continue", and "Stop" update the agent state.
6. Menu: account UID, Settings, Log out. Settings may contain permission status, voice preferences, and diagnostic options, but not a second task console.

## Motion and visual direction
- Dark near-black background with deep graphite glass surfaces.
- Single orb built from native Android drawing/animation rather than a looping video or static bitmap.
- Layered translucent radial gradients, soft cyan/teal and restrained violet highlights, a moving liquid contour, subtle internal refraction-like bands, and soft bloom simulated with Android-10-compatible drawing.
- Slow idle breathing; more active fluid motion while listening/thinking/speaking/automating.
- Use a custom View/Canvas or shader path compatible with minSdk 26. Do not rely on Android 12 RenderEffect APIs.
- Respect reduced-motion preferences and keep the animation battery-conscious.

## Voice and platform constraints
- Request RECORD_AUDIO at runtime.
- Use Android speech recognition and TextToSpeech with explicit availability/error handling.
- To keep listening after launching another app, use a foreground service with the appropriate microphone service type and notification on supported Android versions; request microphone access while the app is visible and start the service before handing off to another app.
- On-device recognition is preferred when available; clearly handle recognizers that require network access.
- Do not claim always-on wake-word support. Initial implementation is push-to-talk/active-session listening with a persistent session service, not covert background recording.
- Speech recognition callbacks, TextToSpeech utterances, and agent state transitions must be serialized safely.
- Spoken interruption commands have priority over the next automation step. Pause/stop must be honored before dispatching another action.

## Automation safety and reliability
- Keep existing AccessibilityService and app-launch pathways.
- Prefer exact normalized label matches and clickable nodes; use the smallest relevant clickable ancestor centre as fallback.
- Never tap guessed payment, purchase, deletion, permission, or other consequential controls without explicit user instruction.
- Verify action result and update orb state from real service callbacks.
- If a target cannot be matched, speak the problem and ask for a clearer instruction instead of blind tapping.

## Implementation approach
Use native Kotlin Android Views in the existing application. The current project is a native Kotlin app, not a JavaScript app; replacing the app with a WebView or separate Replit app would risk Firebase auth, local Gemma, and AccessibilityService integration. Replit can be used only as a supplementary prototype/inspection environment if it proves connected; it is not the source of truth. Canva/Picsart are not needed for the runtime orb because the orb must react to live state and be rendered natively.

## Acceptance criteria
- No Live Automation button/tab remains in the home UI.
- The home UI exposes no typed task composer.
- Login retains email/password and Google only; no X sign-in.
- Permission onboarding appears after successful sign-in until required permissions are granted.
- Current plan label reflects verified entitlement/custom claim only.
- Hamburger menu shows Firebase UID, Settings, and Log out.
- Orb has visible state-specific animation on Android 10/API 29 and current Android.
- User can start a task by voice and hear JARVIS responses.
- During an active automation, user can ask for status or say Pause/Continue/Stop; the agent handles these before another tap.
- Accessibility tap results are verified, not assumed.
- GitHub Actions APK build passes and artifact is verified before delivery.
