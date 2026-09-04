import json

import httpx

from app.models import ExtractedConstraints, SearchHit
from app.ollama_client import OllamaConstraintExtractor, grounded_answer_fallback


def test_extract_returns_structured_model_values(monkeypatch):
    expected = {"brand": "Volkswagen", "model": "Golf", "year": "2020"}

    def fake_post(*args, **kwargs):
        request = httpx.Request("POST", args[0])
        return httpx.Response(
            200,
            request=request,
            json={"message": {"content": json.dumps(expected)}},
        )

    monkeypatch.setattr(httpx, "post", fake_post)
    extractor = OllamaConstraintExtractor("http://127.0.0.1:11434/", "test-model")
    assert extractor.extract("Golf diesel 2020") == expected


def test_extract_falls_back_on_invalid_json(monkeypatch):
    def fake_post(*args, **kwargs):
        request = httpx.Request("POST", args[0])
        return httpx.Response(200, request=request, json={"message": {"content": "not json"}})

    monkeypatch.setattr(httpx, "post", fake_post)
    extractor = OllamaConstraintExtractor("http://127.0.0.1:11434", "test-model")
    assert extractor.extract("anything") is None


def test_extract_falls_back_when_ollama_is_unavailable(monkeypatch):
    def fake_post(*args, **kwargs):
        raise httpx.ConnectError("offline")

    monkeypatch.setattr(httpx, "post", fake_post)
    extractor = OllamaConstraintExtractor("http://127.0.0.1:11434", "test-model")
    assert extractor.extract("anything") is None


def test_answer_is_deterministically_assembled_from_ranked_hits():
    hit = SearchHit(
        listingId=7, score=.95, rawScore=.65,
        title="Volkswagen Golf front brake caliper", price=310,
        matchReasons=["Golf compatible", "Diesel", "Front position"],
    )

    answer = grounded_answer_fallback([hit], ExtractedConstraints(model="Golf"))

    assert answer.generatedBy == "deterministic"
    assert answer.recommendedListingIds == [7]
    assert answer.summary.endswith("Listing #7: Volkswagen Golf front brake caliper.")


def test_no_result_answer_contains_no_recommendation():
    answer = grounded_answer_fallback([], ExtractedConstraints(model="Golf", fuelType="Diesel"))

    assert answer.generatedBy == "deterministic"
    assert answer.recommendedListingIds == []
    assert "No active listing" in answer.summary
