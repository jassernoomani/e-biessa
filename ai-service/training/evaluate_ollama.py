"""Measure JSON validity and canonical extraction accuracy through Ollama's local API."""

from __future__ import annotations

import argparse
import json
import sys
import urllib.error
import urllib.request
from collections import Counter
from pathlib import Path
from typing import Any


FIELDS = ("brand", "model", "year", "fuelType", "position", "category", "partType")


def arguments() -> argparse.Namespace:
    parser = argparse.ArgumentParser()
    parser.add_argument("--model", default="e-biessa-qwen")
    parser.add_argument("--dataset", type=Path, default=Path(__file__).parent / "data" / "test.json")
    parser.add_argument("--limit", type=int, default=24)
    parser.add_argument("--url", default="http://127.0.0.1:11434/api/chat")
    parser.add_argument("--output", type=Path, default=Path(__file__).parent / "baseline-results.json")
    return parser.parse_args()


def invoke(url: str, model: str, row: dict[str, Any]) -> str:
    payload = {
        "model": model,
        "stream": False,
        "format": "json",
        "messages": [{"role": "user", "content": f'{row["instruction"]}\n\nBuyer request:\n{row["input"]}'}],
        "options": {"temperature": 0, "num_ctx": 4096},
    }
    request = urllib.request.Request(url, json.dumps(payload).encode(), {"Content-Type": "application/json"})
    with urllib.request.urlopen(request, timeout=180) as response:
        return json.loads(response.read())["message"]["content"]


def canonical(field: str, value: Any) -> Any:
    if value is None or field == "year":
        return value
    text = str(value).strip()
    key = text.casefold().replace("_", " ")
    mappings = {
        "fuelType": {"diesel": "Diesel", "petrol": "Petrol", "gasoline": "Petrol", "essence": "Petrol"},
        "position": {name.casefold().replace("_", " "): name for name in
                     ("FRONT", "REAR", "LEFT", "RIGHT", "FRONT_LEFT", "FRONT_RIGHT", "REAR_LEFT", "REAR_RIGHT", "UNIVERSAL")},
        "category": {"brake": "Brake Systems", "brakes": "Brake Systems", "brake systems": "Brake Systems",
                     "electrical": "Electrical Parts", "electrical parts": "Electrical Parts",
                     "suspension": "Suspension", "transmission": "Transmission",
                     "cooling": "Cooling & Heating", "cooling heating": "Cooling & Heating",
                     "body": "Body & Exterior", "body exterior": "Body & Exterior"},
        "partType": {"caliper": "Front brake caliper", "brake caliper": "Front brake caliper",
                     "front brake caliper": "Front brake caliper"},
    }
    return mappings.get(field, {}).get(key, text)


def main() -> int:
    args = arguments()
    rows = json.loads(args.dataset.read_text(encoding="utf-8"))[:args.limit]
    exact_fields, normalized_fields = Counter(), Counter()
    records: list[dict[str, Any]] = []
    valid = exact_records = normalized_records = 0

    try:
        for index, row in enumerate(rows, start=1):
            expected = json.loads(row["output"])
            raw = invoke(args.url, args.model, row)
            try:
                actual = json.loads(raw)
                valid += 1
            except json.JSONDecodeError:
                actual = {}
            exact = all(actual.get(field) == expected.get(field) for field in FIELDS)
            normalized = all(canonical(field, actual.get(field)) == expected.get(field) for field in FIELDS)
            exact_records += int(exact)
            normalized_records += int(normalized)
            for field in FIELDS:
                exact_fields[field] += actual.get(field) == expected.get(field)
                normalized_fields[field] += canonical(field, actual.get(field)) == expected.get(field)
            records.append({"input": row["input"], "language": row["language"], "expected": expected,
                            "actual": actual, "validJson": bool(actual), "exact": exact, "normalized": normalized})
            print(f"[{index:>3}/{len(rows)}] {row['language']}: exact={exact} normalized={normalized}")
    except (urllib.error.URLError, TimeoutError) as error:
        print(f"Could not reach Ollama at {args.url}: {error}", file=sys.stderr)
        return 1

    count = len(rows)
    metrics = {
        "model": args.model, "examples": count,
        "jsonValidity": valid / count,
        "exactRecordAccuracy": exact_records / count,
        "normalizedRecordAccuracy": normalized_records / count,
        "exactFieldAccuracy": {field: exact_fields[field] / count for field in FIELDS},
        "normalizedFieldAccuracy": {field: normalized_fields[field] / count for field in FIELDS},
    }
    args.output.write_text(json.dumps({"metrics": metrics, "records": records}, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(metrics, indent=2))
    print(f"Saved detailed results to {args.output}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
