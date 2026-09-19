from pathlib import Path
from threading import Lock

from fastembed import TextEmbedding
from qdrant_client import QdrantClient, models

from .config import Settings
from .models import ExtractedConstraints, GroundedAnswer, ListingDocument, SearchFilters, SearchHit
from .ollama_client import OllamaConstraintExtractor, grounded_answer_fallback
from .query_understanding import merge_model_constraints, rank_candidate


class ListingVectorStore:
    def __init__(self, settings: Settings):
        self.settings = settings
        self._client: QdrantClient | None = None
        self._embedder: TextEmbedding | None = None
        self._lock = Lock()
        self._constraint_extractor = (
            OllamaConstraintExtractor(
                settings.ollama_url,
                settings.ollama_model,
                settings.ollama_timeout_seconds,
                settings.ollama_keep_alive,
            )
            if settings.ollama_enabled else None
        )

    @property
    def ready(self) -> bool:
        return self._client is not None

    def close(self) -> None:
        if self._client is not None:
            self._client.close()
            self._client = None
            self._embedder = None

    def _client_instance(self) -> QdrantClient:
        if self._client is not None:
            return self._client
        with self._lock:
            if self._client is None:
                Path(self.settings.embedding_cache_dir).mkdir(parents=True, exist_ok=True)
                if self.settings.qdrant_url:
                    self._client = QdrantClient(
                        url=self.settings.qdrant_url,
                        api_key=self.settings.qdrant_api_key,
                    )
                else:
                    Path(self.settings.qdrant_path).mkdir(parents=True, exist_ok=True)
                    self._client = QdrantClient(path=str(self.settings.qdrant_path))
                self._embedder = TextEmbedding(
                    model_name=self.settings.embedding_model,
                    cache_dir=str(self.settings.embedding_cache_dir),
                    lazy_load=True,
                )
                self._ensure_collection()
        return self._client

    def _ensure_collection(self) -> None:
        assert self._client is not None
        if self._client.collection_exists(self.settings.qdrant_collection):
            return
        self._client.create_collection(
            collection_name=self.settings.qdrant_collection,
            vectors_config=models.VectorParams(
                size=self._embedding_dimension(),
                distance=models.Distance.COSINE,
            ),
        )

    def upsert(self, listing: ListingDocument) -> None:
        if listing.status != "ACTIVE":
            self.delete(listing.id)
            return
        client = self._client_instance()
        vector = self._embed([listing.searchable_text()])[0]
        client.upload_points(
            collection_name=self.settings.qdrant_collection,
            points=[self._point(listing, vector)],
            wait=True,
        )

    def replace_all(self, listings: list[ListingDocument]) -> int:
        client = self._client_instance()
        if client.collection_exists(self.settings.qdrant_collection):
            client.delete_collection(self.settings.qdrant_collection)
        self._ensure_collection()
        active = [listing for listing in listings if listing.status == "ACTIVE"]
        if active:
            vectors = self._embed([listing.searchable_text() for listing in active])
            client.upload_points(
                collection_name=self.settings.qdrant_collection,
                points=[self._point(listing, vector) for listing, vector in zip(active, vectors)],
                batch_size=32,
                wait=True,
            )
        return len(active)

    def delete(self, listing_id: int) -> None:
        client = self._client_instance()
        client.delete(
            collection_name=self.settings.qdrant_collection,
            points_selector=models.PointIdsList(points=[listing_id]),
            wait=True,
        )

    def search(self, query: str, limit: int, filters: SearchFilters, minimum_score: float) -> tuple[list[SearchHit], ExtractedConstraints, GroundedAnswer]:
        client = self._client_instance()
        catalog = self._catalog_payloads()
        model_values = self._constraint_extractor.extract(query) if self._constraint_extractor else None
        constraints = merge_model_constraints(query, filters, catalog, model_values)
        effective_filters = filters.model_copy(update={
            "brand": filters.brand or constraints.brand,
            "model": filters.model or constraints.model,
            "year": filters.year or constraints.year,
            "fuelType": filters.fuelType or constraints.fuelType,
            "partPosition": filters.partPosition or constraints.position,
            "category": filters.category or constraints.category,
        })
        query_vector = self._embed([query])[0]
        result = client.query_points(
            collection_name=self.settings.qdrant_collection,
            query=query_vector,
            query_filter=self._filter(effective_filters),
            limit=min(100, max(40, limit * 6)),
            with_payload=True,
        )
        hits: list[SearchHit] = []
        for point in result.points:
            ranked = rank_candidate(point.payload, point.score, constraints)
            if ranked is None:
                continue
            score, reasons = ranked
            if score < minimum_score:
                continue
            hits.append(SearchHit(
                listingId=int(point.id),
                score=round(score, 6),
                rawScore=round(point.score, 6),
                title=point.payload["title"],
                price=point.payload["price"],
                sellerName=point.payload.get("sellerName"),
                categoryName=point.payload.get("categoryName"),
                location=point.payload.get("location"),
                compatibleVehicles=point.payload.get("compatibleVehicles", []),
                matchReasons=reasons,
            ))
        hits.sort(key=lambda hit: hit.score, reverse=True)
        selected = hits[:limit]
        answer = grounded_answer_fallback(selected, constraints)
        return selected, constraints, answer

    def validate_compatibility(self, hits: list[SearchHit], constraints: ExtractedConstraints) -> list[SearchHit]:
        """Re-check retrieved rows against the authoritative structured listing payloads."""
        payloads = {int(payload["id"]): payload for payload in self._catalog_payloads() if payload.get("id") is not None}
        verified: list[SearchHit] = []
        for hit in hits:
            payload = payloads.get(hit.listingId)
            if payload is None:
                continue
            ranked = rank_candidate(payload, hit.rawScore, constraints)
            if ranked is None:
                continue
            score, reasons = ranked
            verified.append(hit.model_copy(update={"score": round(score, 6), "matchReasons": reasons}))
        return verified

    def _point(self, listing: ListingDocument, vector: list[float]) -> models.PointStruct:
        payload = listing.model_dump(mode="json")
        payload["price"] = float(listing.price)
        payload["document"] = listing.searchable_text()
        payload["vehicleBrands"] = sorted({vehicle.brand for vehicle in listing.compatibleVehicles})
        payload["vehicleModels"] = sorted({vehicle.model for vehicle in listing.compatibleVehicles})
        payload["vehicleYears"] = sorted({vehicle.year for vehicle in listing.compatibleVehicles if vehicle.year})
        payload["vehicleFuels"] = sorted({vehicle.fuelType for vehicle in listing.compatibleVehicles if vehicle.fuelType})
        return models.PointStruct(
            id=listing.id,
            vector=vector,
            payload=payload,
        )

    def _embed(self, texts: list[str]) -> list[list[float]]:
        self._client_instance()
        assert self._embedder is not None
        return [vector.tolist() for vector in self._embedder.embed(texts, batch_size=32)]

    def _embedding_dimension(self) -> int:
        supported = TextEmbedding.list_supported_models()
        metadata = next((model for model in supported if model["model"] == self.settings.embedding_model), None)
        if metadata is None:
            raise ValueError(f"Unsupported FastEmbed model: {self.settings.embedding_model}")
        return int(metadata["dim"])

    def _catalog_payloads(self) -> list[dict]:
        client = self._client_instance()
        points, _ = client.scroll(
            collection_name=self.settings.qdrant_collection,
            scroll_filter=models.Filter(must=[models.FieldCondition(
                key="status", match=models.MatchValue(value="ACTIVE")
            )]),
            limit=1000,
            with_payload=True,
            with_vectors=False,
        )
        return [point.payload for point in points if point.payload]

    def _filter(self, filters: SearchFilters) -> models.Filter:
        must: list[models.FieldCondition] = [models.FieldCondition(
            key="status", match=models.MatchValue(value="ACTIVE")
        )]
        for key, value in (
            ("vehicleBrands", filters.brand), ("vehicleModels", filters.model),
            ("vehicleYears", filters.year), ("condition", filters.condition),
            ("categoryName", filters.category), ("location", filters.location),
            ("vehicleFuels", filters.fuelType), ("partPosition", filters.partPosition),
        ):
            if value is not None:
                must.append(models.FieldCondition(key=key, match=models.MatchValue(value=value)))
        if filters.minPrice is not None or filters.maxPrice is not None:
            must.append(models.FieldCondition(
                key="price",
                range=models.Range(
                    gte=float(filters.minPrice) if filters.minPrice is not None else None,
                    lte=float(filters.maxPrice) if filters.maxPrice is not None else None,
                ),
            ))
        return models.Filter(must=must)
