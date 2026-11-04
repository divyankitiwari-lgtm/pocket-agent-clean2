#!/usr/bin/env python3
"""Generate a deterministic 10,000,000-example JARVIS planning bootstrap dataset.

All rows are synthetic. They teach high-level skill/tool selection, not app-specific
coordinates or private account access. Review and augment before production use.
"""
from __future__ import annotations

import argparse
import hashlib
import itertools
import json
import random
from pathlib import Path

PARAPHRASES = [
    "Please {goal} using {target}. {context}",
    "Can you {goal} for me in {target}? {context}",
    "Get this done: {goal}. Work with {target}. {context}",
    "Take care of {goal} with {target}. {context}",
    "Could you handle {goal}, working in {target}? {context}",
    "Help me {goal} on {target}. {context}",
    "Use the right tools to {goal} in {target}. {context}",
    "Please complete this workflow: {goal}; target is {target}. {context}",
    "Make progress on {goal} for {target}, then verify it. {context}",
    "Work on {goal} with {target} and tell me what is blocked. {context}",
    "I need you to {goal}. Use {target} and check the result. {context}",
]

CONTEXTS = [
    "Keep existing work intact.",
    "Explain any permission or connection that is missing.",
    "Do not claim success until the result is checked.",
    "Ask before any consequential or destructive action.",
    "Use the least-privileged available tool.",
]

SPECS = {
    "android_app": {
        "targets": ["YouTube", "Gmail", "WhatsApp", "Spotify", "Canva", "Gallery", "Files", "Chrome", "Calendar", "Camera", "Maps", "Notes", "CapCut", "VN Editor", "Settings", "Play Store", "Drive", "Discord", "Telegram", "Calculator", "Clock", "Photos", "Docs", "Sheets", "a video editor"],
        "intents": [
            ("open the requested app", ["android.resolve_app", "android.launch_app", "android.verify_foreground_app"], False),
            ("find the search control and search for the requested topic", ["android.inspect_screen", "android.find_accessible_control", "android.tap", "android.type_text", "android.verify_screen"], False),
            ("navigate to the requested visible section", ["android.inspect_screen", "android.find_accessible_control", "android.tap", "android.verify_screen"], False),
            ("scroll the current screen and locate the requested item", ["android.inspect_screen", "android.scroll", "android.verify_screen"], False),
            ("enter ordinary user-provided text in the selected field", ["android.inspect_screen", "android.find_accessible_control", "android.type_text", "android.verify_screen"], False),
            ("switch to the requested installed app and confirm it opened", ["android.resolve_app", "android.launch_app", "android.verify_foreground_app"], False),
            ("inspect the visible controls and explain the available actions", ["android.inspect_screen", "android.describe_accessible_controls"], False),
            ("prepare a message draft without sending it", ["android.inspect_screen", "android.find_accessible_control", "android.type_text", "android.verify_screen"], False),
            ("review the visible editing controls and prepare the next safe action", ["android.inspect_screen", "android.describe_accessible_controls"], False),
            ("close the current app using the normal Android navigation interface", ["android.inspect_screen", "android.use_system_navigation", "android.verify_foreground_app"], False),
        ],
    },
    "coding": {
        "targets": ["the JARVIS Android repository", "a Kotlin Android app", "a JavaScript project", "a Python project", "a React website", "a small utility app", "the current feature branch", "a test suite", "a build workflow", "a bug report", "a UI component", "an API client", "a data parser", "a command-line tool", "a GitHub project", "a Gradle module", "a TypeScript service", "a reusable library", "a documentation site", "a sample application", "a unit test", "a CI pipeline", "a settings screen", "a login flow", "a code review"],
        "intents": [
            ("inspect the project structure and identify the relevant files", ["code.inspect_repository", "code.read_relevant_files"], False),
            ("create a small project with a clear folder structure", ["code.plan_project", "code.create_files", "code.run_tests"], False),
            ("diagnose the reported build error and propose a minimal fix", ["code.inspect_build_logs", "code.read_relevant_files", "code.propose_patch", "code.run_tests"], False),
            ("add a feature while preserving existing behaviour", ["code.inspect_repository", "code.plan_change", "code.propose_patch", "code.run_tests"], False),
            ("write unit tests for the described behaviour", ["code.inspect_relevant_files", "code.write_tests", "code.run_tests"], False),
            ("review the code for correctness and maintainability", ["code.inspect_repository", "code.review_changes", "code.report_findings"], False),
            ("update project documentation to match the implementation", ["code.inspect_relevant_files", "code.update_documentation", "code.verify_diff"], False),
            ("prepare a scoped branch and a reviewable change", ["code.create_branch", "code.propose_patch", "code.run_tests", "code.prepare_pull_request"], False),
            ("refactor a module without changing its public behaviour", ["code.inspect_relevant_files", "code.plan_refactor", "code.propose_patch", "code.run_tests"], False),
            ("remove an obsolete file only after showing the impact", ["code.inspect_references", "code.propose_deletion", "approval.request_confirmation"], True),
        ],
    },
    "video_editing": {
        "targets": ["a short social video", "an 11-second motion graphic", "a 12-second portfolio clip", "a product explainer", "a tutorial video", "a vertical reel", "a landscape promo", "a title animation", "a dashboard animation", "a UI demo", "a screen recording", "a raw footage folder", "a captioned clip", "a slideshow", "a logo animation", "a short intro", "a presentation video", "a comparison video", "a colour-correction pass", "a voiceover edit", "a music-free cut", "a subtitle track", "a transition sequence", "a preview render", "a final export"],
        "intents": [
            ("inspect the supplied media and propose an edit plan", ["media.list_assets", "media.inspect_metadata", "media.propose_timeline"], False),
            ("trim the beginning and end while preserving the source files", ["media.load_project", "media.set_trim_points", "media.preview"], False),
            ("arrange the selected clips in the requested sequence", ["media.load_assets", "media.build_timeline", "media.preview"], False),
            ("add readable captions from the supplied transcript", ["media.load_project", "media.add_captions", "media.preview"], False),
            ("create clean title cards and text animation", ["media.add_titles", "media.set_text_animation", "media.preview"], False),
            ("add simple transitions between the selected clips", ["media.set_transitions", "media.preview"], False),
            ("resize the composition for the requested aspect ratio", ["media.set_canvas", "media.reframe_clips", "media.preview"], False),
            ("prepare a draft export and verify its output properties", ["media.render_preview", "media.inspect_render"], False),
            ("add a user-supplied audio track at an appropriate level", ["media.import_audio", "media.adjust_audio_levels", "media.preview"], False),
            ("overwrite the existing final export with a new render", ["media.render_to_temporary_file", "media.verify_render", "approval.request_confirmation"], True),
        ],
    },
    "research": {
        "targets": ["Android accessibility automation", "open-weight language models", "video editing workflows", "mobile UI design", "GitHub Actions", "local model inference", "Android app permissions", "website performance", "Kotlin libraries", "JavaScript frameworks", "data visualisation", "model fine-tuning", "OAuth integrations", "multimodal AI", "mobile testing", "open-source licences", "agent evaluation", "app compatibility", "software architecture", "developer tooling", "accessibility testing", "privacy engineering", "on-device AI", "workflow automation", "a technical product comparison"],
        "intents": [
            ("find reliable sources and summarise the key facts", ["research.search_sources", "research.open_sources", "research.summarise_with_citations"], False),
            ("compare the main options using current evidence", ["research.search_sources", "research.extract_evidence", "research.compare_options"], False),
            ("check whether a technical claim is supported by primary sources", ["research.find_primary_sources", "research.verify_claims", "research.report_evidence"], False),
            ("prepare a concise research brief with citations", ["research.search_sources", "research.evaluate_sources", "research.write_brief"], False),
            ("identify limitations, risks, and unresolved questions", ["research.search_sources", "research.assess_uncertainty", "research.report_limitations"], False),
            ("summarise official documentation without copying long passages", ["research.open_official_docs", "research.paraphrase_key_points", "research.cite_sources"], False),
            ("build a feature comparison table from verified sources", ["research.collect_attributes", "research.verify_attributes", "research.build_comparison"], False),
            ("check the publication date and freshness of important sources", ["research.inspect_source_dates", "research.rank_freshness", "research.report_sources"], False),
            ("separate confirmed facts from estimates and opinions", ["research.extract_claims", "research.classify_evidence", "research.write_findings"], False),
            ("prepare a recommendation and explain the supporting evidence", ["research.compare_options", "research.evaluate_tradeoffs", "research.write_recommendation"], False),
        ],
    },
    "website_design": {
        "targets": ["a portfolio landing page", "a product website", "a SaaS dashboard", "a responsive pricing page", "a sign-in screen", "a mobile-first homepage", "a design system", "a contact page", "a project showcase", "a documentation page", "a clean white theme", "a rounded-card interface", "a settings page", "a feature overview", "a responsive navigation", "a case-study page", "a creator portfolio", "a service landing page", "a simple admin dashboard", "a mobile web app", "a component library", "a colour and typography system", "an accessible form", "a product onboarding flow", "a website prototype"],
        "intents": [
            ("create a responsive page structure and preview it", ["design.inspect_requirements", "design.create_layout", "design.run_preview"], False),
            ("improve spacing, hierarchy, and visual consistency", ["design.inspect_current_ui", "design.propose_visual_changes", "design.preview_changes"], False),
            ("create a coherent colour and typography system", ["design.define_tokens", "design.apply_theme", "design.check_contrast"], False),
            ("make the layout work on narrow and wide screens", ["design.inspect_breakpoints", "design.update_responsive_layout", "design.preview_viewports"], False),
            ("build reusable rounded-card components", ["design.define_components", "design.implement_components", "design.run_visual_checks"], False),
            ("improve accessibility labels, contrast, and keyboard flow", ["design.run_accessibility_audit", "design.fix_accessibility_issues", "design.verify_accessibility"], False),
            ("add a clear connection-status panel without inventing connected states", ["design.create_status_component", "design.define_empty_states", "design.preview_changes"], False),
            ("create a polished project showcase with supplied assets", ["design.inspect_assets", "design.create_showcase", "design.preview_changes"], False),
            ("review the design and list the highest-impact improvements", ["design.inspect_current_ui", "design.audit_visual_hierarchy", "design.report_improvements"], False),
            ("publish the website after showing the final preview", ["design.build_production", "design.verify_build", "approval.request_confirmation"], True),
        ],
    },
    "files": {
        "targets": ["the selected project folder", "a supplied PDF", "a spreadsheet", "a set of screenshots", "a video asset folder", "a text document", "a source-code archive", "a folder of exports", "a CSV file", "a presentation", "a set of notes", "a research report", "a group of images", "a subtitle file", "a project backup", "a README document", "a JSON file", "a collection of logs", "a ZIP archive", "a design asset folder", "a draft proposal", "a transcript", "a test report", "a set of source files", "a generated output folder"],
        "intents": [
            ("find the relevant files and summarise their contents", ["files.search", "files.read_relevant", "files.summarise"], False),
            ("extract key information from the supplied document", ["files.inspect_document", "files.extract_text", "files.summarise"], False),
            ("organise files into a proposed folder structure", ["files.inspect_directory", "files.propose_organisation"], False),
            ("convert the supplied file to a suitable accessible format", ["files.inspect_format", "files.convert_copy", "files.verify_output"], False),
            ("check the folder for duplicate or similarly named files", ["files.list_directory", "files.compare_metadata", "files.report_duplicates"], False),
            ("prepare a backup copy before editing", ["files.inspect_source", "files.create_backup", "files.verify_backup"], False),
            ("rename the selected file after showing the proposed new name", ["files.inspect_target", "files.propose_rename", "approval.request_confirmation"], True),
            ("delete obsolete files after listing exactly what would be removed", ["files.inspect_targets", "files.propose_deletion", "approval.request_confirmation"], True),
            ("overwrite the original document with the revised version", ["files.create_revised_copy", "files.verify_copy", "approval.request_confirmation"], True),
            ("create a concise index of the files and their purpose", ["files.list_directory", "files.inspect_metadata", "files.write_index"], False),
        ],
    },
    "data_analysis": {
        "targets": ["a CSV dataset", "a spreadsheet of expenses", "a table of app events", "a set of survey responses", "a time-series file", "a small sales dataset", "a performance report", "a task history export", "a collection of measurements", "a JSON data file", "a user feedback table", "a build metrics sheet", "a website analytics export", "a video project log", "a model evaluation report", "a list of durations", "a table of categories", "a monthly summary", "a quality checklist", "a set of test results", "a tabular report", "a grouped dataset", "a data-cleaning task", "a chart-ready dataset", "a comparison table"],
        "intents": [
            ("inspect the columns, types, and missing values", ["data.load", "data.profile", "data.report_quality"], False),
            ("calculate summary statistics and explain the result", ["data.load", "data.validate", "data.summarise"], False),
            ("clean inconsistent values while preserving the original data", ["data.load", "data.create_clean_copy", "data.validate"], False),
            ("create a clear chart for the main trend", ["data.load", "data.select_visualisation", "data.create_chart"], False),
            ("compare categories and identify meaningful differences", ["data.group", "data.compare", "data.explain_findings"], False),
            ("check the dataset for duplicate rows", ["data.load", "data.detect_duplicates", "data.report_quality"], False),
            ("calculate totals and percentages from the supplied values", ["data.validate", "data.calculate", "data.explain_result"], False),
            ("prepare a compact table suitable for a report", ["data.transform_copy", "data.format_table", "data.verify_output"], False),
            ("test whether the apparent trend is supported by the data", ["data.inspect_distribution", "data.test_trend", "data.report_uncertainty"], False),
            ("replace the original dataset with a cleaned version", ["data.create_clean_copy", "data.verify_output", "approval.request_confirmation"], True),
        ],
    },
    "productivity": {
        "targets": ["a reminder", "a calendar draft", "a task list", "a meeting note", "an email draft", "a study plan", "a project checklist", "a daily summary", "a follow-up note", "a personal schedule", "a set of priorities", "a reading list", "a simple agenda", "a project update", "a weekly plan", "a draft announcement", "a note collection", "a meeting agenda", "a task summary", "a time-block plan", "a document outline", "a research brief", "a learning checklist", "a progress report", "a routine"],
        "intents": [
            ("draft the requested text without sending or publishing it", ["productivity.create_draft", "productivity.review_draft"], False),
            ("organise the tasks into a clear priority list", ["productivity.collect_tasks", "productivity.prioritise", "productivity.present_plan"], False),
            ("prepare a reminder with the requested time details", ["productivity.prepare_reminder", "productivity.verify_schedule"], False),
            ("turn the notes into a concise structured summary", ["productivity.read_notes", "productivity.organise_summary"], False),
            ("create a realistic study or project plan", ["productivity.collect_requirements", "productivity.build_schedule", "productivity.check_feasibility"], False),
            ("prepare a calendar event draft and ask before saving", ["productivity.prepare_event", "approval.request_confirmation"], True),
            ("write an email draft and leave it unsent", ["productivity.create_email_draft", "productivity.review_draft"], False),
            ("send the prepared message only after showing it for approval", ["productivity.prepare_message", "approval.request_confirmation", "productivity.send_after_approval"], True),
            ("summarise progress and list the next actions", ["productivity.collect_progress", "productivity.write_status_summary"], False),
            ("change an existing schedule after showing the proposed changes", ["productivity.inspect_schedule", "productivity.propose_changes", "approval.request_confirmation"], True),
        ],
    },
}

SYSTEM = (
    "You are the JARVIS task planner. Convert the user's goal into a concise, safe, "
    "tool-oriented plan using only the declared capability names. Return valid JSON "
    "with keys skill, goal, tool_plan, requires_confirmation, expected_result, and "
    "user_message. Never claim an action has already happened. Never invent app "
    "connections or permissions. Ask for confirmation before sending/publishing, "
    "deleting/overwriting, purchases, or account/security changes. Never request "
    "passwords, OTPs, or payment credentials. If a capability is unavailable, state "
    "the blocker instead of fabricating execution."
)


def make_row(skill: str, intent: tuple[str, list[str], bool], target: str,
             phrase: str, context: str, serial: int) -> dict:
    goal, tools, confirmation = intent
    user_text = phrase.format(goal=goal, target=target, context=context)
    plan = [{"tool": name, "input": goal, "target": target} for name in tools]
    result = {
        "skill": skill,
        "goal": goal,
        "tool_plan": plan,
        "requires_confirmation": confirmation,
        "expected_result": "A verified result or a clearly reported blocker; no success claim without evidence.",
        "user_message": "Ask for approval before the consequential action." if confirmation else "Proceed only with available permissions and verify each action.",
    }
    messages = [
        {"role": "system", "content": SYSTEM},
        {"role": "user", "content": user_text},
        {"role": "assistant", "content": json.dumps(result, ensure_ascii=False, separators=(",", ":"))},
    ]
    return {
        "id": f"jarvis-{skill}-{serial:06d}",
        "skill": skill,
        "messages": messages,
        "synthetic": True,
    }


def write_jsonl(path: Path, rows: list[dict]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("w", encoding="utf-8") as f:
        for row in rows:
            f.write(json.dumps(row, ensure_ascii=False) + "\n")


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--output-dir", default="data")
    parser.add_argument("--train-minimum", type=int, default=10_000_000)
    parser.add_argument("--validation-count", type=int, default=1_000_000)
    args = parser.parse_args()
    if args.train_minimum < 10_000_000:
        raise SystemExit("JARVIS bootstrap requires at least 10,000,000 training examples.")
    if args.validation_count < 1:
        raise SystemExit("Validation count must be positive.")

    train_rows, val_rows = [], []
    serial = 0
    for skill, spec in SPECS.items():
        combinations = itertools.product(
            spec["intents"], PARAPHRASES, spec["targets"], CONTEXTS
        )
        candidates = list(combinations)
        rng = random.Random(20261104 + len(skill))
        rng.shuffle(candidates)
        # Expand base templates with deterministic scenario constraints.
        # This creates unique synthetic prompts, not 10M human demonstrations.
        train_per_skill = args.train_minimum // len(SPECS)
        val_per_skill = args.validation_count // len(SPECS)
        total_per_skill = train_per_skill + val_per_skill
        modifiers = [
            "preserve the current project state", "prefer reversible changes",
            "verify the final screen or generated output", "report blockers honestly",
            "avoid assuming a tap or save succeeded", "use visible controls and documented APIs",
            "avoid requesting secrets or credentials", "ask before irreversible actions",
            "keep the user informed about permission limits", "do not modify unrelated files",
            "work only with the selected project or account", "retain a copy before editing",
            "stop when a required permission is unavailable", "explain assumptions before proceeding",
            "minimise unnecessary navigation", "keep the result easy to review",
            "check that the intended target is still selected", "summarise what changed",
            "use a safe draft before publishing", "finish the requested task before refinements",
        ]
        for local_idx in range(total_per_skill):
            intent, phrase, target, context = candidates[local_idx % len(candidates)]
            cycle = local_idx // len(candidates)
            m1 = modifiers[(cycle * 7 + local_idx) % len(modifiers)]
            m2 = modifiers[(cycle * 11 + local_idx // 3) % len(modifiers)]
            scenario = f"Scenario constraints: {m1}; {m2}; scenario variant {cycle + 1}."
            serial += 1
            row = make_row(skill, intent, target, phrase, f"{context} {scenario}", serial)
            if local_idx < val_per_skill:
                val_rows.append(row)
            else:
                train_rows.append(row)

    if len(train_rows) < args.train_minimum:
        raise SystemExit(
            f"Generated only {len(train_rows)} training rows; need {args.train_minimum}."
        )
    if len({row["messages"][1]["content"] for row in train_rows}) != len(train_rows):
        raise SystemExit("Duplicate training prompts found; refusing to emit dataset.")

    out = Path(args.output_dir)
    write_jsonl(out / "train.jsonl", train_rows)
    write_jsonl(out / "validation.jsonl", val_rows)
    manifest = {
        "dataset": "JARVIS synthetic planning bootstrap",
        "synthetic_only": True,
        "train_examples": len(train_rows),
        "validation_examples": len(val_rows),
        "skills": sorted(SPECS.keys()),
        "seed": 20261104,
        "note": "10M-scale synthetic template expansion; unique prompts do not imply semantic diversity. Human audit and held-out evaluation are mandatory.",
    }
    (out / "manifest.json").write_text(json.dumps(manifest, indent=2), encoding="utf-8")
    print(json.dumps(manifest, indent=2))


if __name__ == "__main__":
    main()
