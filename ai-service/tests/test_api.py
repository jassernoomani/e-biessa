import os

os.environ.setdefault("AI_SERVICE_TOKEN", "test-token-that-is-at-least-24-characters")

from fastapi.testclient import TestClient

from app.main import app, get_store
from app.models import ExtractedConstraints, SearchHit
from app.ollama_client import grounded_answer_fallback


class FakeStore:
    ready = True

    def __init__(self):
        self.documents = {}

    def upsert(self, listing):
        self.documents[listing.id] = listing

    def delete(self, listing_id):
        self.documents.pop(listing_id, None)

    def replace_all(self, listings):
        self.documents = {listing.id: listing for listing in listings if listing.status == "ACTIVE"}
        return len(self.documents)

    def search(self, query, limit, filters, minimum_score):
        hits = [SearchHit(
            listingId=listing.id,
            score=0.91,
            rawScore=0.71,
            title=listing.title,
            price=listing.price,
            sellerName=listing.sellerName,
            categoryName=listing.categoryName,
            location=listing.location,
            compatibleVehicles=listing.compatibleVehicles,
            matchReasons=["Golf compatible", "Diesel", "Brake Systems"],
        ) for listing in list(self.documents.values())[:limit]], ExtractedConstraints(
            brand="Volkswagen", model="Golf", fuelType="Diesel", category="Brake Systems"
        )
        results, constraints = hits
        return results, constraints, grounded_answer_fallback(results, constraints)


fake_store = FakeStore()
app.dependency_overrides[get_store] = lambda: fake_store
client = TestClient(app)
headers = {"X-AI-Service-Token": "test-token-that-is-at-least-24-characters"}
listing = {
    "id": 42,
    "title": "Volkswagen Golf 7 front brake caliper",
    "description": "Original used caliper in good condition",
    "price": 180,
    "condition": "Used - good",
    "categoryName": "Brake systems",
    "sellerId": 7,
    "sellerName": "Tunis Parts",
    "compatibleVehicles": [{"brand": "Volkswagen", "model": "Golf", "year": 2017, "engine": "1.6 TDI", "fuelType": "Diesel"}],
    "oemNumber": "5Q0615123",
    "location": "Tunis",
    "status": "ACTIVE"
}


def setup_function():
    fake_store.documents.clear()


def test_health_is_public_and_does_not_load_the_model():
    response = client.get("/health")
    assert response.status_code == 200
    assert response.json()["service"] == "e-biessa-ai"
    assert response.json()["modelLoaded"] is True


def test_mutations_require_the_shared_service_token():
    assert client.put("/v1/listings/42", json=listing).status_code == 401


def test_index_search_and_delete_lifecycle():
    indexed = client.put("/v1/listings/42", json=listing, headers=headers)
    assert indexed.status_code == 200
    assert indexed.json() == {"indexed": 1, "deleted": 0}

    result = client.post("/v1/search", json={"query": "Golf diesel brake", "limit": 5}, headers=headers)
    assert result.status_code == 200
    assert result.json()["hits"][0]["listingId"] == 42
    assert result.json()["hits"][0]["score"] == 0.91
    assert result.json()["hits"][0]["matchReasons"] == ["Golf compatible", "Diesel", "Brake Systems"]
    assert result.json()["extractedConstraints"]["model"] == "Golf"
    assert result.json()["answer"]["recommendedListingIds"] == [42]
    assert "Listing #42" in result.json()["answer"]["summary"]

    deleted = client.delete("/v1/listings/42", headers=headers)
    assert deleted.status_code == 204
    assert fake_store.documents == {}


def test_reindex_keeps_only_active_listings():
    inactive = {**listing, "id": 43, "status": "SOLD"}
    response = client.post("/v1/listings/reindex", json={"listings": [listing, inactive]}, headers=headers)
    assert response.status_code == 200
    assert response.json()["indexed"] == 1
    assert list(fake_store.documents) == [42]


def test_rejects_invalid_price_filter_range():
    response = client.post("/v1/search", json={
        "query": "brake caliper",
        "filters": {"minPrice": 500, "maxPrice": 100}
    }, headers=headers)
    assert response.status_code == 422
