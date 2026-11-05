# JARVIS — Unified Android Agent

> **Status:** active rebuild branch. This is an experimental Android agent, not a production-ready general AI operating system. The rebuild is being developed on rebuild/jarvis-unified-agent; the default main branch is kept separate.

JARVIS is designed as one agent with a shared reasoning core and multiple capabilities: Android app control, coding, video editing, research, websites/design, files, and data work. Users should describe an outcome once; JARVIS should select the right capability and tools. Capability modules must only be labelled active after their execution paths are implemented and tested.

## What this rebuild currently adds

- Premium light visual direction with rounded cards, a violet accent palette, JARVIS branding, and a redesigned launcher icon.
- A dedicated Connections screen.
- A clear distinction between ordinary app automation and optional private account integrations.
- Generic target-app extraction instead of a hard-coded Quiz and Earn workflow.
- Improved accessibility label matching, semantic click attempts, coordinate-gesture fallback, gesture callbacks, and a small circular tap marker.
- India-timezone daily action accounting in the client: Free 500/day, Pro 5,000/day, Max up to a theoretical 2 billion/day, Admin unlimited when verified by a Firebase admin claim.
- A written unified-agent architecture and staged implementation plan.
- GitHub Actions APK builds for main and rebuild/** branches.

## App automation and Connections

**Normal app automation does not require a separate connection for every app.** With the JARVIS Accessibility Service enabled, the agent can try to launch installed apps and interact with visible accessible controls. This can support ordinary tasks such as opening an app, searching its visible interface, scrolling, and entering ordinary text.

Android/OEM restrictions, protected screens, custom canvases, inaccessible controls, and app policies can prevent some actions. A gesture being dispatched is not proof that the app accepted it; JARVIS must inspect the next screen before claiming the task is complete.

The Connections screen is for optional official OAuth/API integrations (for example Gmail, Drive, Canva, GitHub, and Spotify) when a task needs account-level data or reliable structured operations. These OAuth integrations are **not configured yet** in this build. If an official connection is unavailable, JARVIS can still try supported app-based UI automation and must clearly report which route it used.

## Current AI engine status

The current project still contains legacy Gemini/OpenRouter developer configuration and an imported MediaPipe .task local-model path. The end-user API-key-free custom JARVIS model, hosted inference service, fine-tuning pipeline, and full multi-capability executor are **not implemented yet**. Do not treat Local Rules as a trained AI model or assume a model file is compatible merely because it can be selected.

See docs/JARVIS_ARCHITECTURE.md for the unified skill model, integration rules, safety policy, pricing proposal, and development milestones.

## Plans — proposed, not yet for sale

| Plan | Proposed price | Daily allowance |
|---|---:|---:|
| Free | ₹0 | 500 successful actions |
| Pro | ₹199/month | 5,000 successful actions |
| Max | ₹499/month | Up to 2 billion theoretical actions, subject to fair-use and infrastructure safeguards |
| Admin | Private | Unlimited for authorised admin accounts |

Prices are proposals, not active subscriptions. Paid tiers require secure billing and server-verified entitlements. Client-side usage counters are for the current prototype only and are not a security boundary. Admin access must be granted only through a trusted Firebase Admin SDK custom claim; never grant it from local preferences.

## Build an APK

1. Open the repository's Actions tab.
2. Select the latest Build JARVIS Android APK run for the rebuild/jarvis-unified-agent branch.
3. Wait for a successful result.
4. Download the jarvis-debug-apk artifact, extract it on Android, and install app-debug.apk.

The workflow uses JDK 17 and Gradle 8.9. The app's minimum Android version is API 26 (Android 8.0).

## Important limitations

- Accessibility automation is best-effort and cannot guarantee control of every app or screen.
- OAuth account integrations, server-side billing, server-side usage enforcement, and the full multi-capability skill executor are not yet complete.
- Cloud mode still depends on the legacy provider key configured in the app; this is temporary and does not meet the final API-key-free product goal.
- Firebase sign-in and Firestore features require the owner's matching app/google-services.json, enabled providers, an OAuth client, configured Firestore, and tested security rules.
- Local model inference must be validated on the actual target device. GitHub Actions builds APKs; it is not a free GPU training service.
- Never automate passwords, OTPs, payment credentials, account-security changes, purchases, or destructive actions without the appropriate safeguards and explicit confirmation.

## Repository

- Android app: Kotlin and native Android views
- Package: com.example.pocketagent (package ID retained for upgrade compatibility)
- Build: GitHub Actions, Gradle 8.9, JDK 17
- Active rebuild branch: rebuild/jarvis-unified-agent

## AI provider slots and optional local fallback

The `jarvis/a-z-agent-foundation` development branch adds a four-slot AI provider manager. Configure it from **Settings → AI providers & models**. Enabled slots are tried in order; failures fall through to the next configured slot. Optional imported Gemma inference can be used after the API routes when the local fallback switch is enabled.

Supported provider families include Gemini, OpenAI, Anthropic Claude, OpenRouter, NVIDIA NIM, Groq, Cerebras, Together AI, Mistral, DeepSeek and Ollama. Each slot has its own provider, model ID, key and optional custom base URL. API keys are stored with Android EncryptedSharedPreferences; the app refuses to silently store keys in plaintext if secure storage cannot initialize.

See [AI provider setup](docs/AI_PROVIDER_SETUP.md) for protocol support, model ID notes, local model requirements and privacy details. This is a development branch and must pass its GitHub Actions build before it is considered build-verified.
