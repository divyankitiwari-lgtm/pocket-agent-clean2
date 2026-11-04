#!/usr/bin/env python3
"""QLoRA supervised fine-tuning for the JARVIS planning dataset.

Run on a CUDA GPU. This produces a PEFT adapter, not a new foundation model.
"""
from __future__ import annotations

import argparse
import json
from pathlib import Path

import torch
from datasets import load_dataset
from peft import LoraConfig, prepare_model_for_kbit_training
from transformers import (
    AutoModelForCausalLM,
    AutoTokenizer,
    BitsAndBytesConfig,
    DataCollatorForLanguageModeling,
    Trainer,
    TrainingArguments,
)


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--model", default="Qwen/Qwen2.5-1.5B-Instruct")
    parser.add_argument("--data-dir", default="data")
    parser.add_argument("--output-dir", default="outputs/jarvis-qwen2.5-1.5b-lora")
    parser.add_argument("--epochs", type=float, default=1.0)
    parser.add_argument("--max-length", type=int, default=1024)
    parser.add_argument("--batch-size", type=int, default=1)
    parser.add_argument("--grad-accum", type=int, default=16)
    parser.add_argument("--learning-rate", type=float, default=2e-4)
    args = parser.parse_args()

    if not torch.cuda.is_available():
        raise SystemExit(
            "CUDA GPU not detected. Do not run 100k-example LLM fine-tuning on a phone "
            "or GitHub Actions CPU runner. Use a GPU notebook/runtime."
        )

    data_dir = Path(args.data_dir)
    train_path = data_dir / "train.jsonl"
    validation_path = data_dir / "validation.jsonl"
    if not train_path.exists() or not validation_path.exists():
        raise SystemExit("Dataset files missing. Run generate_dataset.py first.")
    with train_path.open(encoding="utf-8") as f:
        train_count = sum(1 for line in f if line.strip())
    if train_count < 100_000:
        raise SystemExit(f"Refusing to train: expected >=100,000 examples, found {train_count}.")

    tokenizer = AutoTokenizer.from_pretrained(args.model, trust_remote_code=False)
    if tokenizer.pad_token is None:
        tokenizer.pad_token = tokenizer.eos_token

    quant_config = BitsAndBytesConfig(
        load_in_4bit=True,
        bnb_4bit_quant_type="nf4",
        bnb_4bit_compute_dtype=torch.float16,
        bnb_4bit_use_double_quant=True,
    )
    model = AutoModelForCausalLM.from_pretrained(
        args.model,
        quantization_config=quant_config,
        device_map="auto",
        torch_dtype=torch.float16,
        trust_remote_code=False,
    )
    model = prepare_model_for_kbit_training(model)
    lora_config = LoraConfig(
        r=16,
        lora_alpha=32,
        lora_dropout=0.05,
        bias="none",
        task_type="CAUSAL_LM",
        target_modules=["q_proj", "k_proj", "v_proj", "o_proj", "gate_proj", "up_proj", "down_proj"],
    )
    from peft import get_peft_model
    model = get_peft_model(model, lora_config)

    dataset = load_dataset(
        "json",
        data_files={
            "train": str(train_path),
            "validation": str(validation_path),
        },
    )

    def format_and_tokenize(row):
        rendered = tokenizer.apply_chat_template(
            row["messages"],
            tokenize=False,
            add_generation_prompt=False,
        )
        return tokenizer(
            rendered,
            truncation=True,
            max_length=args.max_length,
            padding=False,
        )

    tokenized = dataset.map(
        format_and_tokenize,
        remove_columns=dataset["train"].column_names,
        desc="Formatting JARVIS conversations",
    )
    training_args = TrainingArguments(
        output_dir=args.output_dir,
        num_train_epochs=args.epochs,
        per_device_train_batch_size=args.batch_size,
        per_device_eval_batch_size=1,
        gradient_accumulation_steps=args.grad_accum,
        learning_rate=args.learning_rate,
        warmup_ratio=0.03,
        lr_scheduler_type="cosine",
        logging_steps=25,
        evaluation_strategy="steps",
        eval_steps=500,
        save_steps=500,
        save_total_limit=3,
        fp16=True,
        bf16=False,
        gradient_checkpointing=True,
        optim="paged_adamw_8bit",
        report_to="none",
        load_best_model_at_end=True,
        metric_for_best_model="eval_loss",
        greater_is_better=False,
    )
    trainer = Trainer(
        model=model,
        args=training_args,
        train_dataset=tokenized["train"],
        eval_dataset=tokenized["validation"],
        data_collator=DataCollatorForLanguageModeling(tokenizer=tokenizer, mlm=False),
    )
    trainer.train()
    trainer.save_model(args.output_dir)
    tokenizer.save_pretrained(args.output_dir)
    with (Path(args.output_dir) / "training_summary.json").open("w", encoding="utf-8") as f:
        json.dump({
            "base_model": args.model,
            "train_examples": train_count,
            "epochs": args.epochs,
            "adapter_type": "QLoRA",
            "note": "Adapter only; run the evaluation suite before integrating into JARVIS.",
        }, f, indent=2)
    print(f"Saved JARVIS adapter to {args.output_dir}")


if __name__ == "__main__":
    main()
