from app.models import SearchFilters
from app.query_understanding import extract_constraints, merge_model_constraints, rank_candidate, validated_year


golf_caliper = {
    "title": "Volkswagen Golf Front brake caliper 2020",
    "categoryName": "Brake Systems",
    "partPosition": "FRONT",
    "fuelCompatibility": "Diesel",
    "compatibleVehicles": [{
        "brand": "Volkswagen", "model": "Golf", "year": 2020,
        "engine": "1.6 TDI", "fuelType": "Diesel"
    }],
}

golf_engine = {
    "title": "Volkswagen Golf Complete engine assembly 2020",
    "categoryName": "Engine Parts",
    "partPosition": "UNIVERSAL",
    "fuelCompatibility": "Diesel",
    "compatibleVehicles": golf_caliper["compatibleVehicles"],
}


def test_extracts_vehicle_fuel_position_category_and_part_type():
    constraints = extract_constraints(
        "I need a front brake caliper for a 2020 diesel Golf",
        SearchFilters(), [golf_caliper, golf_engine]
    )
    assert constraints.brand == "Volkswagen"
    assert constraints.model == "Golf"
    assert constraints.year == 2020
    assert constraints.fuelType == "Diesel"
    assert constraints.position == "FRONT"
    assert constraints.category == "Brake Systems"
    assert constraints.partType == "Front brake caliper"


def test_reranking_explains_compatible_candidate_and_rejects_wrong_part_type():
    constraints = extract_constraints(
        "front brake caliper for diesel Golf", SearchFilters(), [golf_caliper, golf_engine]
    )
    ranked = rank_candidate(golf_caliper, 0.61, constraints)
    assert ranked is not None
    score, reasons = ranked
    assert score > 0.9
    assert "Golf compatible" in reasons
    assert "Diesel" in reasons
    assert "Brake system" in reasons
    assert rank_candidate(golf_engine, 0.54, constraints) is None


def test_explicit_filters_are_preserved_as_constraints():
    constraints = extract_constraints(
        "something for my car",
        SearchFilters(model="Golf", fuelType="Diesel", partPosition="FRONT"),
        [golf_caliper]
    )
    assert constraints.model == "Golf"
    assert constraints.fuelType == "Diesel"
    assert constraints.position == "FRONT"


def test_extracts_compound_position_before_single_position_words():
    constraints = extract_constraints(
        "front left headlight for Golf", SearchFilters(), [golf_caliper]
    )
    assert constraints.position == "FRONT_LEFT"


def test_query_year_overrides_invalid_or_truncated_model_year():
    assert validated_year("Golf diesel from 2020", model_year=2) == 2020
    assert validated_year("Golf diesel from 2019", model_year=2219) == 2019


def test_invalid_model_year_is_rejected_when_query_has_no_year():
    assert validated_year("Golf diesel", model_year=2) is None
    assert validated_year("Golf diesel", model_year=2219) is None


def test_explicit_filter_year_has_highest_precedence():
    assert validated_year("Golf diesel from 2020", filter_year=2018, model_year=2019) == 2018


def test_model_constraints_are_canonicalized_and_query_year_wins():
    constraints = merge_model_constraints(
        "Need a caliper for my Golf diesel from 2020",
        SearchFilters(),
        [golf_caliper],
        {
            "brand": "volkswagen",
            "model": "golf",
            "year": 2,
            "fuelType": "diesel",
            "position": "front",
            "category": "Brakes",
            "partType": "Caliper",
        },
    )
    assert constraints.brand == "Volkswagen"
    assert constraints.model == "Golf"
    assert constraints.year == 2020
    assert constraints.fuelType == "Diesel"
    assert constraints.position == "FRONT"
    assert constraints.category == "Brake Systems"
    assert constraints.partType == "Front brake caliper"


def test_unknown_model_taxonomy_and_invalid_year_are_rejected():
    constraints = merge_model_constraints(
        "Need a component",
        SearchFilters(),
        [golf_caliper],
        {"brand": "Invented", "model": "Imaginary", "year": 22, "category": "Magic"},
    )
    assert constraints.brand is None
    assert constraints.model is None
    assert constraints.year is None
    assert constraints.category is None


def test_model_position_is_rejected_without_query_or_part_type_evidence():
    constraints = merge_model_constraints(
        "Nlawwej 3la alternateur mta3 Ford Focus diesel 2020",
        SearchFilters(),
        [golf_caliper],
        {"position": "REAR_RIGHT", "partType": "Alternator"},
    )
    assert constraints.position is None
