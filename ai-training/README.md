# JARVIS 100K training bootstrap

## Honest status

This folder is a **training pipeline**, not proof that a model has already been trained. It generates a deterministic synthetic bootstrap corpus and provides a QLoRA fine-tuning entry point. The generated examples teach high-level skill selection and safe tool planning; they do not teach reliable screen coordinates, app-specific behaviour, or real-world execution by themselves.

## Base model

Default: [Qwen/Qwen2.5-1.5B-Instruct](https://huggingface.co/Qwen/Qwen2.5-1.5B-Instruct). Its Hugging Face model card identifies it as a 1.54B-parameter instruction model and lists Apache-2.0 licensing. Re-check the model card and all upstream dependencies before redistribution. This is a practical small-model starting point, not a claim that it is the best model for every Android device.

Fine-tuning uses QLoRA/PEFT. These methods train adapter parameters while keeping the base model frozen, reducing training memory requirements; they still require a compatible GPU and can take hours or longer depending on hardware and sequence length. See the [Hugging Face PEFT/TRL guidance](https://huggingface.co/docs/trl/main/en/peft_integration).

## Dataset target

Run:

```bash
python generate_dataset.py --output-dir data --train-minimum 100000
python validate_dataset.py --data-dir data --minimum-train 100000
```

The generator creates 110,000 synthetic examples across eight capabilities and deterministically splits them into **100,000 training examples plus 10,000 validation examples**. The generator is designed to fail rather than silently produce fewer than the required training count.

Capabilities:
- Android app control
- Coding and repository work
- Video editing
- Research and source evaluation
- Website and UI design
- File operations
- Data analysis
- Productivity planning

All examples use fictional task descriptions and generic tool names. They contain no user messages, account credentials, passwords, OTPs, or private files. The data is synthetic and must be reviewed; 100,000 rows do not automatically mean 100,000 high-quality demonstrations.

## Train

Use a CUDA GPU runtime. GitHub Actions in this repository is configured for Android builds and does not provide the GPU capacity required for LLM fine-tuning. A phone-only Android device is not an appropriate environment for this QLoRA run.

```bash
pip install -r requirements.txt
python train_lora.py --data-dir data --epochs 1
```

The script refuses to start without CUDA and refuses to train with fewer than 100,000 training rows. It saves a LoRA adapter and tokenizer under `outputs/`; it does not overwrite the foundation model.

## Before calling it trained

1. Generate and validate the 100K/10K split.
2. Run QLoRA on a real GPU and retain the logs/checkpoints.
3. Run held-out evaluation by capability, including paraphrases not present in the synthetic generator.
4. Test safe confirmation behaviour and unavailable-tool honesty.
5. Compare against the untrained base model; reject the adapter if it regresses.
6. Verify the adapter can be merged/served or converted for the intended Android runtime. Qwen adapter compatibility with the app's existing MediaPipe Gemma runtime is **not automatic**; a runtime integration decision is still required.

Do not label JARVIS as trained or production-ready until these gates pass.
