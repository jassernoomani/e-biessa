import re
import unicodedata
from collections.abc import Iterable
from typing import Any

from .models import ExtractedConstraints, SearchFilters


CATEGORY_ALIASES = {
    "brake": "Brake Systems", "brakes": "Brake Systems", "frein": "Brake Systems",
    "caliper": "Brake Systems", "brake disc": "Brake Systems", "abs pump": "Brake Systems",
    "engine": "Engine Parts", "motor": "Engine Parts", "moteur": "Engine Parts",
    "electrical": "Electrical Parts", "electric": "Electrical Parts", "electrique": "Electrical Parts",
    "suspension": "Suspension", "shock absorber": "Suspension", "amortisseur": "Suspension",
    "body": "Body & Exterior", "bumper": "Body & Exterior", "mirror": "Body & Exterior", "carrosserie": "Body & Exterior",
    "interior": "Interior", "seat": "Interior", "dashboard": "Interior", "multimedia": "Interior",
    "transmission": "Transmission", "gearbox": "Transmission", "clutch": "Transmission",
    "cooling": "Cooling & Heating", "radiator": "Cooling & Heating", "air conditioning": "Cooling & Heating",
}

PART_TYPE_ALIASES = {
    "complete engine": "Complete engine assembly", "engine assembly": "Complete engine assembly",
    "turbocharger": "Turbocharger unit", "turbo": "Turbocharger unit",
    "alternator": "Alternator", "starter motor": "Starter motor", "starter": "Starter motor",
    "front headlight": "LED front headlight", "headlight": "LED front headlight",
    "tail light": "Rear tail light", "rear light": "Rear tail light",
    "brake caliper": "Front brake caliper", "caliper": "Front brake caliper",
    "brake disc": "Brake disc pair", "brake rotor": "Brake disc pair",
    "abs pump": "ABS pump module", "shock absorber": "Front shock absorber",
    "steering rack": "Steering rack", "gearbox": "Automatic gearbox", "clutch": "Clutch kit",
    "radiator": "Radiator", "air conditioning compressor": "Air conditioning compressor",
    "ac compressor": "Air conditioning compressor", "front bumper": "Front bumper", "bumper": "Front bumper",
    "door mirror": "Left door mirror", "side mirror": "Left door mirror", "mirror": "Left door mirror",
    "driver seat": "Driver seat", "instrument cluster": "Dashboard instrument cluster",
    "head unit": "Multimedia head unit",
}

FUEL_ALIASES = {
    "diesel": "Diesel", "tdi": "Diesel", "dci": "Diesel", "hdi": "Diesel",
    "bluehdi": "Diesel", "crdi": "Diesel", "ecoblue": "Diesel",
    "petrol": "Petrol", "gasoline": "Petrol", "essence": "Petrol", "puretech": "Petrol",
    "ecoboost": "Petrol", "tce": "Petrol",
}

POSITION_ALIASES = {
    "front": "FRONT", "avant": "FRONT", "rear": "REAR", "back": "REAR", "arriere": "REAR",
    "left": "LEFT", "gauche": "LEFT", "right": "RIGHT", "droite": "RIGHT",
}

COMPOUND_POSITION_ALIASES = {
    "front left": "FRONT_LEFT", "left front": "FRONT_LEFT", "avant gauche": "FRONT_LEFT",
    "front right": "FRONT_RIGHT", "right front": "FRONT_RIGHT", "avant droite": "FRONT_RIGHT",
    "rear left": "REAR_LEFT", "left rear": "REAR_LEFT", "arriere gauche": "REAR_LEFT",
    "rear right": "REAR_RIGHT", "right rear": "REAR_RIGHT", "arriere droite": "REAR_RIGHT",
}


def normalize(value: str | None) -> str:
    if not value:
        return ""
    plain = "".join(character for character in unicodedata.normalize("NFKD", value) if not unicodedata.combining(character))
    return re.sub(r"[^a-z0-9]+", " ", plain.lower()).strip()


def contains_phrase(query: str, phrase: str) -> bool:
    normalized_phrase = normalize(phrase)
    return bool(normalized_phrase and re.search(rf"(?:^|\s){re.escape(normalized_phrase)}(?:$|\s)", query))


def validated_year(query: str, filter_year: int | None = None, model_year: int | None = None) -> int | None:
    """Resolve a trustworthy year, preferring explicit UI and query values over model output."""
    if filter_year is not None:
        return filter_year
    year_match = re.search(r"\b(?:19|20)\d{2}\b", normalize(query))
    if year_match:
        return int(year_match.group())
    if model_year is not None and 1900 <= model_year <= 2099:
        return model_year
    return None


def supported_model_position(query: str, part_type: str | None, position: str | None) -> str | None:
    """Accept a model position only when the request or canonical part type supports it."""
    if not position or position == "UNIVERSAL":
        return None
    evidence = f"{normalize(query)} {normalize(part_type)}"
    raw = query.casefold()
    terms = {
        "FRONT": ("front", "avant", "9odem", "9oddem", "أمام", "امام", "قدام"),
        "REAR": ("rear", "back", "arriere", "5alf", "خلف", "وراء", "لورا"),
        "LEFT": ("left", "gauche", "isar", "يسار"),
        "RIGHT": ("right", "droite", "imin", "يمين"),
    }
    required = position.split("_")
    for direction in required:
        if not any(contains_phrase(evidence, term) or term in raw for term in terms.get(direction, ())):
            return None
    return position


def extract_constraints(query: str, filters: SearchFilters, payloads: Iterable[dict[str, Any]]) -> ExtractedConstraints:
    normalized_query = normalize(query)
    payload_list = list(payloads)
    vehicles = [vehicle for payload in payload_list for vehicle in payload.get("compatibleVehicles", [])]
    brands = sorted({vehicle.get("brand") for vehicle in vehicles if vehicle.get("brand")}, key=len, reverse=True)
    models = sorted({vehicle.get("model") for vehicle in vehicles if vehicle.get("model")}, key=len, reverse=True)

    brand = filters.brand or next((value for value in brands if contains_phrase(normalized_query, value)), None)
    model = filters.model or next((value for value in models if contains_phrase(normalized_query, value)), None)
    if model and not brand:
        model_brands = {vehicle.get("brand") for vehicle in vehicles if normalize(vehicle.get("model")) == normalize(model)}
        if len(model_brands) == 1:
            brand = next(iter(model_brands))

    year = validated_year(query, filters.year)

    fuel_type = filters.fuelType or _alias_match(normalized_query, FUEL_ALIASES)
    position = filters.partPosition or _alias_match(normalized_query, COMPOUND_POSITION_ALIASES) or _alias_match(normalized_query, POSITION_ALIASES)
    category = filters.category or _category_match(normalized_query, payload_list)
    part_type = _alias_match(normalized_query, PART_TYPE_ALIASES)

    return ExtractedConstraints(
        brand=brand, model=model, year=year, fuelType=fuel_type,
        position=position, category=category, partType=part_type,
    )


def merge_model_constraints(
    query: str,
    filters: SearchFilters,
    payloads: Iterable[dict[str, Any]],
    model_values: dict[str, Any] | None,
) -> ExtractedConstraints:
    """Merge untrusted model output with deterministic, catalog-aware extraction.

    Explicit filters and values stated in the query win. Model values are accepted
    only after validation and canonicalization, so malformed years and invented
    taxonomy labels cannot become hard compatibility filters.
    """
    payload_list = list(payloads)
    deterministic = extract_constraints(query, filters, payload_list)
    values = model_values if isinstance(model_values, dict) else {}
    vehicles = [vehicle for payload in payload_list for vehicle in payload.get("compatibleVehicles", [])]
    brands = {normalize(vehicle.get("brand")): vehicle.get("brand") for vehicle in vehicles if vehicle.get("brand")}
    models = {normalize(vehicle.get("model")): vehicle.get("model") for vehicle in vehicles if vehicle.get("model")}
    categories = {
        normalize(payload.get("categoryName")): payload.get("categoryName")
        for payload in payload_list if payload.get("categoryName")
    }

    model_year = values.get("year")
    if isinstance(model_year, str) and model_year.isdigit():
        model_year = int(model_year)
    if not isinstance(model_year, int) or isinstance(model_year, bool):
        model_year = None

    model_category = _canonical_alias(values.get("category"), CATEGORY_ALIASES)
    model_part_type = _canonical_alias(values.get("partType"), PART_TYPE_ALIASES)
    model_fuel = _canonical_alias(values.get("fuelType"), FUEL_ALIASES)
    model_position = (
        _canonical_alias(values.get("position"), COMPOUND_POSITION_ALIASES)
        or _canonical_alias(values.get("position"), POSITION_ALIASES)
    )

    return ExtractedConstraints(
        brand=deterministic.brand or brands.get(normalize(values.get("brand"))),
        model=deterministic.model or models.get(normalize(values.get("model"))),
        year=validated_year(query, filters.year, model_year),
        fuelType=deterministic.fuelType or model_fuel,
        position=deterministic.position or supported_model_position(query, model_part_type, model_position),
        category=deterministic.category or categories.get(normalize(model_category)),
        partType=deterministic.partType or model_part_type,
    )


def rank_candidate(payload: dict[str, Any], raw_score: float, constraints: ExtractedConstraints) -> tuple[float, list[str]] | None:
    vehicles = payload.get("compatibleVehicles", [])
    title = normalize(payload.get("title"))
    reasons: list[str] = []
    bonus = 0.0

    if constraints.partType:
        expected = normalize(constraints.partType)
        if expected not in title:
            return None
        reasons.append(constraints.partType)
        bonus += 0.10
    if constraints.model:
        if not any(normalize(vehicle.get("model")) == normalize(constraints.model) for vehicle in vehicles):
            return None
        reasons.append(f"{constraints.model} compatible")
        bonus += 0.15
    if constraints.brand:
        if not any(normalize(vehicle.get("brand")) == normalize(constraints.brand) for vehicle in vehicles):
            return None
        reasons.append(constraints.brand)
        bonus += 0.08
    if constraints.year:
        if not any(vehicle.get("year") == constraints.year for vehicle in vehicles):
            return None
        reasons.append(f"{constraints.year} fitment")
        bonus += 0.05
    if constraints.fuelType:
        vehicle_fuels = [vehicle.get("fuelType") for vehicle in vehicles]
        compatible = any(normalize(value) == normalize(constraints.fuelType) for value in vehicle_fuels)
        compatible = compatible or normalize(payload.get("fuelCompatibility")) == normalize(constraints.fuelType)
        if not compatible:
            return None
        reasons.append(constraints.fuelType)
        bonus += 0.07
    if constraints.position:
        if normalize(payload.get("partPosition")) != normalize(constraints.position):
            return None
        reasons.append(f"{constraints.position.replace('_', ' ').title()} position")
        bonus += 0.06
    if constraints.category:
        if normalize(payload.get("categoryName")) != normalize(constraints.category):
            return None
        reasons.append("Brake system" if normalize(constraints.category) == "brake systems" else constraints.category)
        bonus += 0.10

    if not reasons:
        reasons.append("Semantic description match")
    return min(1.0, raw_score + bonus), reasons


def _alias_match(query: str, aliases: dict[str, str]) -> str | None:
    for alias in sorted(aliases, key=len, reverse=True):
        if contains_phrase(query, alias):
            return aliases[alias]
    return None


def _canonical_alias(value: Any, aliases: dict[str, str]) -> str | None:
    normalized_value = normalize(value) if isinstance(value, str) else ""
    if not normalized_value:
        return None
    for alias, canonical in aliases.items():
        if normalized_value in {normalize(alias), normalize(canonical)}:
            return canonical
    return None


def _category_match(query: str, payloads: list[dict[str, Any]]) -> str | None:
    categories = {payload.get("categoryName") for payload in payloads if payload.get("categoryName")}
    exact = next((category for category in sorted(categories, key=len, reverse=True) if contains_phrase(query, category)), None)
    if exact:
        return exact
    aliased = _alias_match(query, CATEGORY_ALIASES)
    return next((category for category in categories if normalize(category) == normalize(aliased)), aliased)
