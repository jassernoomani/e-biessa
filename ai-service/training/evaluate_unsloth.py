"""Evaluate the fine-tuned LoRA adapter on the held-out extraction set."""

from __future__ import annotations

# Unsloth must patch Transformers before it is imported transitively.
import unsloth

import argparse
import json
import sys
from collections import Counter, defaultdict
from pathlib import Path
from typing import Any

import torch
from unsloth import FastLanguageModel

from evaluate_ollama import FIELDS, canonical


ROOT = Path(__file__).resolve().parent
sys.path.insert(0, str(ROOT.parent))

from app.query_understanding import validated_year


def arguments() -> argparse.Namespace:
    parser = argparse.ArgumentParser()
    parser.add_argument("--adapter", type=Path, required=True)
    parser.add_argument("--dataset", type=Path, default=ROOT / "data" / "test.json")
    parser.add_argument("--limit", type=int, default=12)
    parser.add_argument(
        "--repetition-penalty",
        type=float,
        default=1.0,
        help="Keep at 1.0 for extraction: penalizing prompt tokens corrupts copied years.",
    )
    parser.add_argument("--output", type=Path, default=ROOT / "adapter-results.json")
    return parser.parse_args()


def invoke(
    model: Any,
    tokenizer: Any,
    row: dict[str, Any],
    repetition_penalty: float,
) -> str:
    inputs = tokenizer.apply_chat_template(
        [
            {
                "role": "system",
                "content": row["instruction"] + "\nCopy a stated year exactly as a complete four-digit integer.",
            },
            {"role": "user", "content": row["input"]},
        ],
        tokenize=True,
        add_generation_prompt=True,
        return_dict=True,
        return_tensors="pt",
    ).to("cuda")
    with torch.inference_mode():
        prompt_length = inputs["input_ids"].shape[-1]
        generated = model.generate(
            **inputs,
            max_length=prompt_length + 160,
            do_sample=False,
            repetition_penalty=repetition_penalty,
            use_cache=True,
            pad_token_id=tokenizer.eos_token_id,
        )
    return tokenizer.decode(generated[0, prompt_length:], skip_special_tokens=True).strip()


def harden_prediction(query: str, actual: dict[str, Any]) -> dict[str, Any]:
    """Apply the same trust boundary used by the runtime to unsafe model fields."""
    hardened = dict(actual)
    model_year = hardened.get("year")
    if isinstance(model_year, str) and model_year.isdigit():
        model_year = int(model_year)
    if not isinstance(model_year, int) or isinstance(model_year, bool):
        model_year = None
    hardened["year"] = validated_year(query, model_year=model_year)
    return hardened


def main() -> int:
    args = arguments()
    if not torch.cuda.is_available():
        raise RuntimeError("CUDA is unavailable. A CUDA-enabled PyTorch build is required.")
    if not (args.adapter / "adapter_config.json").is_file():
        raise FileNotFoundError(f"LoRA adapter not found at {args.adapter}")

    rows = json.loads(args.dataset.read_text(encoding="utf-8"))[: args.limit]
    print(f"Loading adapter {args.adapter} on {torch.cuda.get_device_name(0)}")
    model, tokenizer = FastLanguageModel.from_pretrained(
        model_name=str(args.adapter.resolve()),
        max_seq_length=256,
        load_in_4bit=True,
        device_map={"": 0},
    )
    FastLanguageModel.for_inference(model)
    model.generation_config.do_sample = False
    model.generation_config.repetition_penalty = args.repetition_penalty
    model.generation_config.temperature = None
    model.generation_config.top_k = None
    model.generation_config.top_p = None

    exact_fields, normalized_fields = Counter(), Counter()
    language_counts: dict[str, Counter] = defaultdict(Counter)
    records: list[dict[str, Any]] = []
    valid = exact_records = normalized_records = hardened_records = 0
    for index, row in enumerate(rows, start=1):
        expected = json.loads(row["output"])
        raw = invoke(model, tokenizer, row, args.repetition_penalty)
        try:
            actual = json.loads(raw)
            is_valid = isinstance(actual, dict)
            if not is_valid:
                actual = {}
        except json.JSONDecodeError:
            actual = {}
            is_valid = False
        valid += int(is_valid)
        exact = is_valid and all(actual.get(field) == expected.get(field) for field in FIELDS)
        normalized = is_valid and all(
            canonical(field, actual.get(field)) == expected.get(field) for field in FIELDS
        )
        hardened = harden_prediction(row["input"], actual) if is_valid else {}
        hardened_exact = is_valid and all(hardened.get(field) == expected.get(field) for field in FIELDS)
        exact_records += int(exact)
        normalized_records += int(normalized)
        hardened_records += int(hardened_exact)
        language = row["language"]
        language_counts[language]["count"] += 1
        language_counts[language]["valid"] += int(is_valid)
        language_counts[language]["exact"] += int(exact)
        for field in FIELDS:
            exact_fields[field] += actual.get(field) == expected.get(field)
            normalized_fields[field] += canonical(field, actual.get(field)) == expected.get(field)
        records.append(
            {
                "input": row["input"], "language": language, "expected": expected,
                "actual": actual, "hardened": hardened, "raw": raw, "validJson": is_valid,
                "exact": exact, "normalized": normalized, "hardenedExact": hardened_exact,
            }
        )
        print(f"[{index:>3}/{len(rows)}] {language}: valid={is_valid} exact={exact}")

    count = len(rows)
    metrics = {
        "adapter": str(args.adapter),
        "examples": count,
        "jsonValidity": valid / count,
        "exactRecordAccuracy": exact_records / count,
        "normalizedRecordAccuracy": normalized_records / count,
        "hardenedRecordAccuracy": hardened_records / count,
        "exactFieldAccuracy": {field: exact_fields[field] / count for field in FIELDS},
        "normalizedFieldAccuracy": {field: normalized_fields[field] / count for field in FIELDS},
        "byLanguage": {
            language: {
                "examples": values["count"],
                "jsonValidity": values["valid"] / values["count"],
                "exactRecordAccuracy": values["exact"] / values["count"],
            }
            for language, values in sorted(language_counts.items())
        },
    }
    args.output.write_text(
        json.dumps({"metrics": metrics, "records": records}, ensure_ascii=False, indent=2) + "\n",
        encoding="utf-8",
    )
    print(json.dumps(metrics, indent=2))
    print(f"Saved detailed results to {args.output}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
