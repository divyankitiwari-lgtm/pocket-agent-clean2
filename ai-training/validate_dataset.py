#!/usr/bin/env python3
"""Validate JARVIS JSONL training and validation splits."""
from __future__ import annotations

import argparse
import json
from collections import Counter
from pathlib import Path

REQUIRED_KEYS = {"id", "skill", "messages", "synthetic"}
VALID_ROLES = {"system", "user", "assistant"}


def read_rows(path: Path) -> list[dict]:
    rows = []
    with path.open(encoding="utf-8") as f:
        for line_no, line in enumerate(f, 1):
            if not line.strip():
                continue
            try:
                row = json.loads(line)
            except json.JSONDecodeError as exc:
                raise SystemExit(f"{path}:{line_no}: invalid JSON: {exc}")
            if not REQUIRED_KEYS.issubset(row):
                raise SystemExit(f"{path}:{line_no}: missing keys {REQUIRED_KEYS - row.keys()}")
            if row["synthetic"] is not True:
                raise SystemExit(f"{path}:{line_no}: expected synthetic=true marker")
            if not isinstance(row["messages"], list) or len(row["messages"]) != 3:
                raise SystemExit(f"{path}:{line_no}: expected system/user/assistant messages")
            if [m.get("role") for m in row["messages"]] != ["system", "user", "assistant"]:
                raise SystemExit(f"{path}:{line_no}: invalid message role order")
            for message in row["messages"]:
                if not isinstance(message.get("content"), str) or not message["content"].strip():
                    raise SystemExit(f"{path}:{line_no}: empty message content")
            try:
                output = json.loads(row["messages"][2]["content"])
            except json.JSONDecodeError:
                raise SystemExit(f"{path}:{line_no}: assistant output is not JSON")
            needed = {"skill", "goal", "tool_plan", "requires_confirmation", "expected_result", "user_message"}
            if not needed.issubset(output):
                raise SystemExit(f"{path}:{line_no}: assistant JSON missing {needed - output.keys()}")
            if not isinstance(output["tool_plan"], list) or not output["tool_plan"]:
                raise SystemExit(f"{path}:{line_no}: empty tool plan")
            rows.append(row)
    return rows


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--data-dir", default="data")
    parser.add_argument("--minimum-train", type=int, default=10_000_000)
    args = parser.parse_args()
    root = Path(args.data_dir)
    train = read_rows(root / "train.jsonl")
    val = read_rows(root / "validation.jsonl")
    if len(train) < args.minimum_train:
        raise SystemExit(f"Need at least {args.minimum_train} train rows; found {len(train)}.")
    train_prompts = {r["messages"][1]["content"] for r in train}
    val_prompts = {r["messages"][1]["content"] for r in val}
    overlap = train_prompts & val_prompts
    if overlap:
        raise SystemExit(f"Train/validation leakage: {len(overlap)} duplicate prompts.")
    counts = Counter(r["skill"] for r in train)
    print(json.dumps({
        "status": "valid",
        "train_examples": len(train),
        "validation_examples": len(val),
        "train_skill_counts": dict(sorted(counts.items())),
    }, indent=2))


if __name__ == "__main__":
    main()
