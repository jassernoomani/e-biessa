"""Generate corrected multilingual training data while preserving the original test set."""

from __future__ import annotations

import json
import random
from collections import Counter
from pathlib import Path

from generate_dataset import PARTS, TEMPLATES, VEHICLES


ROOT = Path(__file__).resolve().parent
DATA_DIR = ROOT / "data"
SEED = 20260903
YEARS = tuple(range(2014, 2025))
INSTRUCTION = """Extract automotive search constraints from the buyer request.
Return only valid JSON with exactly these keys: brand, model, year, fuelType, position, category, partType.
Use null when a value is not stated or cannot be inferred safely.
When a year is stated, copy all four digits exactly and return it as an integer; never abbreviate or repeat digits.
Normalize fuelType to Diesel or Petrol.
Normalize position to FRONT, REAR, LEFT, RIGHT, FRONT_LEFT, FRONT_RIGHT, REAR_LEFT, REAR_RIGHT, or UNIVERSAL.
Use canonical marketplace category and part-type names."""

NO_YEAR_TEMPLATES = {
    "en": "I need a {part} for a {fuel} {brand} {model}; the year is not specified.",
    "fr": "Je cherche un {part} pour une {brand} {model} {fuel}, année non précisée.",
    "tn": "Nlawwej 3la {part} mta3 {brand} {model} {fuel}, ma na3rech el année.",
    "ar": "أبحث عن {part} لسيارة {brand} {model} {fuel}، السنة غير محددة.",
}


def localized_fuel(language: str, fuel: str) -> str:
    if language == "en":
        return fuel
    if language in {"fr", "tn"}:
        return "diesel" if fuel == "Diesel" else "essence"
    return "ديزل" if fuel == "Diesel" else "بنزين"


def make_row(
    vehicle: tuple[str, str, int, str],
    part: tuple[str, ...],
    language: str,
    template_index: int,
    year: int | None,
) -> dict[str, str]:
    brand, model, _, fuel = vehicle
    canonical_part, category, position, en, fr, tn, ar = part
    localized_part = {"en": en, "fr": fr, "tn": tn, "ar": ar}[language]
    values = {
        "part": localized_part,
        "brand": brand,
        "model": model,
        "year": year,
        "fuel": localized_fuel(language, fuel),
    }
    template = TEMPLATES[language][template_index] if year is not None else NO_YEAR_TEMPLATES[language]
    expected = {
        "brand": brand,
        "model": model,
        "year": year,
        "fuelType": fuel,
        "position": position,
        "category": category,
        "partType": canonical_part,
    }
    return {
        "instruction": INSTRUCTION,
        "input": template.format(**values),
        "output": json.dumps(expected, ensure_ascii=False, separators=(",", ":")),
        "language": language,
    }


def export(path: Path, rows: list[dict[str, str]]) -> None:
    payload = [{key: row[key] for key in ("instruction", "input", "output")} for row in rows]
    path.write_text(json.dumps(payload, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


def main() -> None:
    test_path = DATA_DIR / "test.json"
    if not test_path.is_file():
        raise FileNotFoundError("Generate the original fixed test.json before the corrected training data")
    original_test = json.loads(test_path.read_text(encoding="utf-8"))
    held_out_inputs = {row["input"] for row in original_test}

    year_rows: list[dict[str, str]] = []
    no_year_rows: list[dict[str, str]] = []
    for vehicle_index, vehicle in enumerate(VEHICLES):
        for part_index, part in enumerate(PARTS):
            for language_index, language in enumerate(TEMPLATES):
                for template_index in range(len(TEMPLATES[language])):
                    year_index = vehicle_index * 7 + part_index * 5 + language_index * 3 + template_index
                    year_rows.append(make_row(vehicle, part, language, template_index, YEARS[year_index % len(YEARS)]))
                no_year_rows.append(make_row(vehicle, part, language, 0, None))

    year_rows = [row for row in year_rows if row["input"] not in held_out_inputs]
    no_year_rows = [row for row in no_year_rows if row["input"] not in held_out_inputs]
    rng = random.Random(SEED)
    rng.shuffle(year_rows)
    rng.shuffle(no_year_rows)
    rows = year_rows[:960] + no_year_rows[:320]
    rng.shuffle(rows)
    validation, train = rows[:160], rows[160:]

    train_inputs = {row["input"] for row in train}
    validation_inputs = {row["input"] for row in validation}
    if train_inputs & validation_inputs or train_inputs & held_out_inputs or validation_inputs & held_out_inputs:
        raise RuntimeError("Dataset leakage detected")

    export(DATA_DIR / "train_v2.json", train)
    export(DATA_DIR / "validation_v2.json", validation)
    years = Counter(json.loads(row["output"])["year"] for row in rows)
    languages = Counter(row["language"] for row in rows)
    print(f"Generated {len(train)} train and {len(validation)} validation examples.")
    print(f"Year distribution: {dict(sorted(years.items(), key=lambda item: str(item[0])))}")
    print(f"Language distribution: {dict(sorted(languages.items()))}")
    print("Original test.json was preserved unchanged.")


if __name__ == "__main__":
    main()
