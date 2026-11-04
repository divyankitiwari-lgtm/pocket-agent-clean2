# JARVIS — Unified Agent Architecture

## Product direction

JARVIS is one general-purpose agent with a shared reasoning core, shared memory, one task interface, and a registry of capabilities. Users should not have to open separate "coding agent", "video agent", or "research agent" products. The orchestrator selects the appropriate capability from the user's goal and combines capabilities when a task spans more than one area.

## Unified capability set

- **Device and app control:** launch installed apps, inspect accessible UI, tap, type, scroll, navigate, and verify visible results.
- **Coding and project development:** create and edit project files, inspect repositories, run available build/test workflows, diagnose errors, and prepare reviewable changes.
- **Video and media editing:** organise source assets, trim and arrange clips, add captions/titles/transitions, prepare motion-graphics workflows, and export through supported local renderers or connected editing tools.
- **Research and web:** search sources, compare evidence, summarise findings, and produce cited reports.
- **Websites and design:** plan pages, create assets, and generate or edit website/project files through supported tools.
- **Files and productivity:** find, read, organise, transform, and create user-authorised files.
- **Data and analysis:** work with structured data, calculations, spreadsheets, and charts.

These are capability modules of one JARVIS agent, not independent user-facing agents. A capability must not be advertised as operational until its execution tools and tests exist.

## Shared execution loop

1. Understand the requested outcome and identify missing essential information.
2. Inspect the current device, app, file, repository, or connected-service context.
3. Create a short plan with explicit expected results.
4. Select one or more capabilities and the least-privileged available tools.
5. Execute one bounded action at a time.
6. Verify the result from a fresh screen snapshot, tool response, file diff, or build result.
7. Retry only when safe; stop and explain blockers when verification fails.
8. Report completed work separately from attempted or unavailable work.

The planner should reason about the goal and tool descriptions. It must not depend on a fixed list of memorised user phrases or hard-coded target apps.

## App automation versus account connections

### Basic app automation — no per-app connection required

JARVIS should be able to launch an installed app and interact with its visible UI using Android Accessibility where the app exposes accessible controls. This includes common tasks such as opening YouTube, searching in an app, or navigating an editing app. The user must enable the JARVIS accessibility service. Android version, OEM restrictions, app UI implementation, protected screens, and app policies can limit what is possible.

### Optional private account integrations

A separate Connections area is reserved for official OAuth/API integrations such as Gmail, Drive, Canva, GitHub, and Spotify. These are useful for account-level data and reliable structured operations. A connection must be established by the service's real authorisation flow and must show its actual state. Never display "Connected" just because an app is installed or open.

### Fallback

If an official integration is unavailable, JARVIS may offer supported app-based UI automation instead. It must say when this fallback is being used and must not claim access to private API data. If neither path works, explain the exact missing permission, app, integration, or unsupported screen.

## Safety and permissions

- Accessibility actions run only after the user explicitly enables the service and starts a task.
- Ask for confirmation before sending messages, publishing content, deleting or overwriting files, making purchases, changing account/security settings, or other consequential actions.
- Never collect passwords, OTPs, payment credentials, or private secrets through the agent planner.
- Do not bypass app sign-in, platform permissions, paywalls, or security controls.
- Store OAuth tokens only in an appropriately protected store and request the minimum scopes.
- Admin rights are assigned by a trusted server-side Firebase custom claim or equivalent allowlist. Never grant admin from a client-side preference.

## Plans and usage policy (proposed)

- Free: 500 successful actions per day.
- Pro: 5,000 successful actions per day; proposed ₹199/month.
- Max: theoretical ceiling of 2 billion actions per day; proposed ₹499/month, but subject to fair-use, rate, concurrency, model quota, and infrastructure safeguards. This is not a promise that 2 billion real actions can be executed.
- Admin: unlimited normal step allowance for explicitly authorised admin accounts.

Count successful, verified actions rather than prompts or failed attempts. The Android client may display usage for feedback, but enforce paid limits and admin privileges on a trusted backend. Paid plans must not unlock from a local preference or an unverified client claim.

## Model strategy

A practical custom JARVIS model should start from a suitably licensed open-weight foundation model, then be adapted with curated JARVIS instruction/action datasets and LoRA/QLoRA fine-tuning where compute is available. GitHub Actions can validate code and package APKs; it is not a free GPU training platform. Model checkpoints and large weights should be versioned in appropriate model storage/releases, not committed into ordinary source history.

Training data should include varied paraphrases, tool-selection examples, expected states, successful and failed outcomes, recovery cases, and safety pauses. Conversation memory is separate from model training; do not automatically fine-tune on every conversation.

## Engineering milestones

1. Rebuild the mobile shell and Connections navigation with a consistent premium light theme.
2. Stabilise Android Accessibility: semantic clicks, coordinate fallback, gesture callbacks, fresh-screen verification, action logs, and recoverable timeouts.
3. Replace app-specific bootstrap logic with generic intent and target-app resolution.
4. Build the shared capability registry and tool contracts for the unified agent.
5. Add optional OAuth integrations one by one, beginning with services that have a supported official API and a clear user consent flow.
6. Build the coding/repository capability with scoped branches, diffs, tests, and explicit confirmation before destructive changes.
7. Build the media-editing capability with a local render pipeline and supported project/app hand-offs.
8. Add trusted backend entitlements, usage metering, and admin allowlisting.
9. Evaluate the local model/runtime on the target Android devices and add curated fine-tuning only when a suitable compute path is available.

## Current implementation status

This branch begins the rebuild with a light-theme shell, a Connections screen, generic app-name parsing, a daily client-side action counter, and improved Accessibility click/gesture handling. OAuth integrations, trusted billing enforcement, the full multi-capability executor, and custom model training are not complete yet. The build pipeline must pass before this milestone is considered build-verified.
