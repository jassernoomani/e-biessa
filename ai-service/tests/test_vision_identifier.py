from io import BytesIO

import pytest
from PIL import Image

from app.config import Settings
from app.vision_identifier import InvalidPartImage, VisionPartIdentifier, VisionUnavailable
from app.vision_policy import choose_part
from app.vision_taxonomy import PART_TAXONOMY, CLASSIFIER_LABELS


TOKEN = "test-token-that-is-at-least-24-characters"
buffer = BytesIO()
Image.new("RGB", (1, 1), (230, 80, 40)).save(buffer, format="PNG")
ONE_PIXEL_PNG = buffer.getvalue()


def test_validator_accepts_a_small_real_png_without_loading_clip():
    identifier = VisionPartIdentifier(Settings(ai_service_token=TOKEN, vision_enabled=False))
    image = identifier._validated_image(ONE_PIXEL_PNG)
    assert image.size == (1, 1)
    assert image.mode == "RGB"


def test_disabled_identifier_fails_before_loading_any_model():
    identifier = VisionPartIdentifier(Settings(ai_service_token=TOKEN, vision_enabled=False))
    with pytest.raises(VisionUnavailable, match="disabled"):
        identifier.identify(ONE_PIXEL_PNG)


def test_validator_rejects_non_images_and_oversized_dimensions():
    identifier = VisionPartIdentifier(Settings(ai_service_token=TOKEN, vision_enabled=True))
    with pytest.raises(InvalidPartImage, match="valid JPEG"):
        identifier._validated_image(b"not an image")

    strict = VisionPartIdentifier(Settings(
        ai_service_token=TOKEN, vision_enabled=True, vision_max_image_pixels=1_000_000
    ))
    strict.settings.vision_max_image_bytes = 1024
    with pytest.raises(InvalidPartImage, match="size limit"):
        strict._validated_image(b"x" * 1025)


def evidence(first, second, first_score=0.7, second_score=0.1):
    labels = [label for label, _ in PART_TAXONOMY]
    scores = [0.0] * (len(labels) + 1)
    similarities = [0.21] * len(scores)
    similarities[labels.index(first)] = 0.32
    similarities[labels.index(second)] = 0.30
    scores[labels.index(first)] = first_score
    scores[labels.index(second)] = second_score
    return scores, similarities


def test_blank_image_abstains_without_loading_models():
    identifier = VisionPartIdentifier(Settings(ai_service_token=TOKEN, vision_enabled=True))
    response = identifier.identify(ONE_PIXEL_PNG)
    assert response.status == "UNCERTAIN"
    assert response.candidates == []
    assert not identifier.ready


def test_exterior_and_interior_labels_match_marketplace_categories():
    taxonomy = dict(PART_TAXONOMY)
    assert taxonomy["door"] == "Body & Exterior"
    assert taxonomy["car seat"] == "Interior"
    assert taxonomy["steering wheel"] == "Interior"
    assert taxonomy["clutch disc"] != taxonomy["brake disc or rotor"]
    assert set(CLASSIFIER_LABELS.values()).issubset(taxonomy)
    assert len(taxonomy) == len(PART_TAXONOMY)


def test_low_margin_abstains_without_claiming_a_part():
    scores, similarities = evidence("door", "door interior trim panel", 0.4, 0.38)
    choice, reason = choose_part(scores, similarities)
    assert choice is None
    assert "similar" in reason


def test_whole_scene_or_non_part_wins_abstention():
    scores, similarities = evidence("door", "bumper")
    scores[-1] = 0.9
    assert choose_part(scores, similarities)[0] is None


def test_low_absolute_similarity_abstains_even_with_high_softmax():
    scores, similarities = evidence("door", "bumper")
    similarities = [0.1] * len(scores)
    assert choose_part(scores, similarities)[0] is None


def test_softmax_does_not_hide_a_tiny_raw_similarity_gap():
    scores, similarities = evidence("exhaust muffler", "catalytic converter", 0.6, 0.28)
    similarities[[label for label, _ in PART_TAXONOMY].index("catalytic converter")] = 0.315
    assert choose_part(scores, similarities)[0] is None


def test_classifier_can_support_alternator_when_clip_also_ranks_it_highly():
    scores, similarities = evidence("water pump", "alternator", 0.4, 0.3)
    choice, _ = choose_part(scores, similarities, [("ALTERNATOR", 0.9), ("WATER PUMP", 0.04)])
    assert PART_TAXONOMY[choice[0]][0] == "alternator"


def test_strong_disagreement_on_supported_class_abstains():
    scores, similarities = evidence("water pump", "turbocharger")
    assert choose_part(scores, similarities, [("ALTERNATOR", 0.9), ("WATER PUMP", 0.04)])[0] is None


def test_missing_classifier_category_can_use_clear_clip_evidence():
    scores, similarities = evidence("catalytic converter", "starter motor", 0.9, 0.04)
    choice, _ = choose_part(scores, similarities, [("STARTER", 0.9), ("IGNITION COIL", 0.04)])
    assert PART_TAXONOMY[choice[0]][0] == "catalytic converter"


def test_diffuse_assembly_prediction_can_use_corroborated_trained_component():
    scores, similarities = evidence("complete engine assembly", "water pump", 0.08, 0.07)
    alternator = [label for label, _ in PART_TAXONOMY].index("alternator")
    similarities[alternator] = 0.315
    choice, _ = choose_part(scores, similarities, [("ALTERNATOR", 0.66), ("AIR COMPRESSOR", 0.14)])
    assert PART_TAXONOMY[choice[0]][0] == "alternator"


def test_new_untrained_label_requires_stronger_evidence():
    scores, similarities = evidence("car seat", "seat belt", 0.8, 0.08)
    assert choose_part(scores, similarities)[0] is None
    scores[[label for label, _ in PART_TAXONOMY].index("car seat")] = 0.9
    assert choose_part(scores, similarities)[0] is not None
