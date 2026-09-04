"""Generate deterministic multilingual instruction data for automotive query extraction."""

from __future__ import annotations

import json
import random
from pathlib import Path


OUTPUT_DIR = Path(__file__).resolve().parent / "data"
SEED = 20260830
INSTRUCTION = """Extract automotive search constraints from the buyer request.
Return only valid JSON with exactly these keys: brand, model, year, fuelType, position, category, partType.
Use null when a value is not stated or cannot be inferred safely.
Normalize fuelType to Diesel or Petrol.
Normalize position to FRONT, REAR, LEFT, RIGHT, FRONT_LEFT, FRONT_RIGHT, REAR_LEFT, REAR_RIGHT, or UNIVERSAL.
Use canonical marketplace category and part-type names."""

VEHICLES = [
    ("Volkswagen", "Golf", 2020, "Diesel"), ("Volkswagen", "Passat", 2019, "Diesel"),
    ("Renault", "Clio", 2020, "Petrol"), ("Renault", "Megane", 2019, "Diesel"),
    ("Peugeot", "208", 2021, "Petrol"), ("Peugeot", "308", 2020, "Diesel"),
    ("Citroën", "C3", 2019, "Petrol"), ("Citroën", "C4", 2020, "Diesel"),
    ("Toyota", "Corolla", 2020, "Petrol"), ("Toyota", "Yaris", 2019, "Petrol"),
    ("Mercedes-Benz", "C-Class", 2020, "Petrol"), ("Mercedes-Benz", "E-Class", 2019, "Diesel"),
    ("Ford", "Focus", 2020, "Diesel"), ("Ford", "Fiesta", 2019, "Petrol"),
    ("Hyundai", "i20", 2020, "Petrol"), ("Hyundai", "Tucson", 2019, "Diesel"),
    ("Kia", "Rio", 2020, "Petrol"), ("Kia", "Sportage", 2019, "Diesel"),
    ("Seat", "Leon", 2020, "Diesel"), ("Skoda", "Octavia", 2019, "Diesel"),
]

# canonical name, category, position, English, French, Tunisian Latin, Arabic
PARTS = [
    ("Front brake caliper", "Brake Systems", "FRONT", "front brake caliper", "étrier de frein avant", "étrier frein avant", "مِلقط فرامل أمامي"),
    ("Brake disc pair", "Brake Systems", "FRONT", "front brake disc pair", "paire de disques de frein avant", "deux disques frein avant", "زوج أقراص فرامل أمامية"),
    ("ABS pump module", "Brake Systems", "UNIVERSAL", "ABS pump module", "bloc pompe ABS", "bloc pompe ABS", "وحدة مضخة ABS"),
    ("LED front headlight", "Electrical Parts", "FRONT", "LED front headlight", "phare avant LED", "phare avant LED", "مصباح أمامي LED"),
    ("Rear tail light", "Electrical Parts", "REAR", "rear tail light", "feu arrière", "feu arrière", "مصباح خلفي"),
    ("Alternator", "Electrical Parts", "UNIVERSAL", "alternator", "alternateur", "alternateur", "مولد كهربائي"),
    ("Starter motor", "Electrical Parts", "UNIVERSAL", "starter motor", "démarreur", "démarreur", "بادئ تشغيل"),
    ("Front shock absorber", "Suspension", "FRONT", "front shock absorber", "amortisseur avant", "amortisseur avant", "ممتص صدمات أمامي"),
    ("Automatic gearbox", "Transmission", "UNIVERSAL", "automatic gearbox", "boîte de vitesses automatique", "boîte auto", "علبة تروس أوتوماتيكية"),
    ("Clutch kit", "Transmission", "UNIVERSAL", "clutch kit", "kit embrayage", "kit embrayage", "طقم قابض"),
    ("Radiator", "Cooling & Heating", "FRONT", "radiator", "radiateur", "radiateur", "مُبرّد المحرك"),
    ("Left door mirror", "Body & Exterior", "LEFT", "left door mirror", "rétroviseur gauche", "rétroviseur gauche", "مرآة باب يسار"),
]

TEMPLATES = {
    "en": [
        "I need a {part} for a {year} {fuel} {brand} {model}.",
        "Looking for a {part}, compatible with my {brand} {model} {fuel} from {year}.",
        "Do you have a {year} {brand} {model} {part} for the {fuel} version?",
    ],
    "fr": [
        "Je cherche un {part} pour une {brand} {model} {fuel} de {year}.",
        "Besoin d'un {part}, compatible avec ma {brand} {model} {year} {fuel}.",
        "Avez-vous un {part} pour {brand} {model} version {fuel}, année {year} ?",
    ],
    "tn": [
        "Nlawwej 3la {part} mta3 {brand} {model} {fuel} {year}.",
        "3andi {brand} {model} {year} {fuel} w n7eb {part}.",
        "Famma {part} compatible m3a {brand} {model} {fuel} modèle {year} ?",
    ],
    "ar": [
        "أبحث عن {part} لسيارة {brand} {model} {fuel} سنة {year}.",
        "أحتاج {part} متوافقا مع {brand} {model} موديل {year} {fuel}.",
        "هل يوجد {part} لسيارة {brand} {model} {fuel} لعام {year}؟",
    ],
}


def example(vehicle: tuple[str, str, int, str], part: tuple[str, ...], language: str, template_index: int) -> dict:
    brand, model, year, fuel = vehicle
    canonical_part, category, position, en, fr, tn, ar = part
    localized = {"en": en, "fr": fr, "tn": tn, "ar": ar}[language]
    buyer_request = TEMPLATES[language][template_index].format(
        part=localized, brand=brand, model=model, year=year,
        fuel={"en": fuel, "fr": "diesel" if fuel == "Diesel" else "essence",
              "tn": "diesel" if fuel == "Diesel" else "essence",
              "ar": "ديزل" if fuel == "Diesel" else "بنزين"}[language],
    )
    expected = {
        "brand": brand, "model": model, "year": year, "fuelType": fuel,
        "position": position, "category": category, "partType": canonical_part,
    }
    return {
        "instruction": INSTRUCTION,
        "input": buyer_request,
        "output": json.dumps(expected, ensure_ascii=False, separators=(",", ":")),
        "language": language,
    }


def write_json(name: str, rows: list[dict], *, include_language: bool = False) -> None:
    OUTPUT_DIR.mkdir(parents=True, exist_ok=True)
    exported_rows = rows if include_language else [
        {key: row[key] for key in ("instruction", "input", "output")}
        for row in rows
    ]
    with (OUTPUT_DIR / name).open("w", encoding="utf-8") as stream:
        json.dump(exported_rows, stream, ensure_ascii=False, indent=2)
        stream.write("\n")


def main() -> None:
    rows = [
        example(vehicle, part, language, template_index)
        for vehicle in VEHICLES
        for part in PARTS
        for language in TEMPLATES
        for template_index in range(len(TEMPLATES[language]))
    ]
    random.Random(SEED).shuffle(rows)

    # Fixed held-out cases are intentionally never included in training.
    held_out = [
        example(VEHICLES[0], PARTS[0], "en", 0),
        example(VEHICLES[0], PARTS[0], "fr", 0),
        example(VEHICLES[0], PARTS[0], "tn", 0),
    ]
    held_out_inputs = {row["input"] for row in held_out}
    rows = [row for row in rows if row["input"] not in held_out_inputs]
    train, validation = rows[:720], rows[720:840]
    test = held_out + rows[840:957]

    write_json("train.json", train)
    write_json("validation.json", validation)
    # LitGPT receives only its documented instruction/input/output fields.
    # The evaluator keeps the language metadata for per-language reporting.
    write_json("test.json", test, include_language=True)
    print(f"Generated {len(train)} train, {len(validation)} validation, and {len(test)} test examples in {OUTPUT_DIR}")


if __name__ == "__main__":
    main()
