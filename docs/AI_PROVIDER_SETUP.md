# JARVIS AI provider configuration

## Four ordered API slots

JARVIS stores four independent provider configurations. Enabled slots are attempted in ascending slot order (1, 2, 3, 4). A slot is skipped if it is disabled or its required key is missing. HTTP errors, timeouts, invalid responses and empty model output cause the client to continue to the next enabled slot. A non-empty response is returned with the provider/model identity so the caller can log the route without logging secrets.

Each slot contains:
- Provider type
- Provider-specific model identifier
- API key (not required for Ollama)
- Optional custom base URL
- Enabled/disabled state

## Supported provider protocols

| Provider | Protocol | Example model IDs |
|---|---|---|
| Google Gemini | Gemini generateContent | gemini-2.5-flash, gemini-2.5-pro |
| OpenAI | OpenAI chat completions | gpt-4.1-mini, gpt-4o-mini |
| Anthropic Claude | Anthropic Messages | claude-sonnet-4-5, claude-haiku-4-5 |
| OpenRouter | OpenAI-compatible | openrouter/auto, openrouter/free |
| NVIDIA NIM | OpenAI-compatible | meta/llama-3.3-70b-instruct |
| Groq | OpenAI-compatible | llama-3.3-70b-versatile |
| Cerebras | OpenAI-compatible | llama-3.3-70b |
| Together AI | OpenAI-compatible | Llama-3.3-70B-Instruct-Turbo |
| Mistral | OpenAI-compatible | mistral-small-latest, codestral-latest |
| DeepSeek | OpenAI-compatible | deepseek-chat, deepseek-reasoner |
| Ollama | Local Ollama chat API | qwen2.5:3b, llama3.2:3b, gemma3:1b |

Provider model names change. These are editable defaults, not a guarantee that an account has access to every model. Check the provider's current model catalogue and usage limits before selecting a model.

## Optional local model

The local fallback is independent of the four API slots. When enabled, JARVIS attempts the imported app-private MediaPipe `.task` model only after all enabled API slots fail. The current runtime adapter is the existing `GemmaEngine`; it expects a compatible Gemma/LiteRT MediaPipe model. Arbitrary GGUF files are not compatible with this runtime.

Ollama is a separate local-server option and is not the same as on-device Gemma. Its custom base URL must be reachable from the Android device. `127.0.0.1` refers to the Android device itself, not another computer on the network.

## Secret handling

- API keys are stored in Android EncryptedSharedPreferences.
- If encrypted storage cannot be initialized, JARVIS refuses to save keys rather than silently writing plaintext.
- Keys are never written to communication logs or returned in provider status labels.
- Cloud prompts (and any screen text included in them) are sent to the selected provider. Do not include passwords, OTPs, payment details or other secrets in prompts.
- The app does not create API keys or provider accounts. Users obtain keys from the provider they choose.

## Build and verification

The APK workflow runs unit tests and assembles the debug APK on pushes to `main`, `rebuild/**`, and `jarvis/**`. A successful GitHub Actions run is required before treating this branch as build-verified.
