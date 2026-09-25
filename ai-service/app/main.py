from contextlib import asynccontextmanager
from functools import lru_cache
from time import perf_counter

from fastapi import Body, Depends, FastAPI, Header, HTTPException, Response, status
from prometheus_client import Counter, Histogram, make_asgi_app
from starlette.requests import Request

from .config import Settings, get_settings
from .models import AssistantRequest, AssistantResponse, BulkListingRequest, IndexResponse, ListingDocument, SearchRequest, SearchResponse, VisionIdentificationResponse
from .orchestrator import MarketplaceAgentOrchestrator
from .security import require_service_token
from .vector_store import ListingVectorStore
from .vision_identifier import InvalidPartImage, VisionPartIdentifier, VisionUnavailable


HTTP_REQUESTS = Counter(
    "e_biessa_ai_http_requests_total",
    "FastAPI requests completed.",
    ("method", "route", "status"),
)
HTTP_DURATION = Histogram(
    "e_biessa_ai_http_request_duration_seconds",
    "FastAPI request duration in seconds.",
    ("method", "route"),
)


@lru_cache
def get_store() -> ListingVectorStore:
    return ListingVectorStore(get_settings())


@lru_cache
def get_vision_identifier() -> VisionPartIdentifier:
    return VisionPartIdentifier(get_settings())


@asynccontextmanager
async def lifespan(_: FastAPI):
    yield
    get_store().close()


def create_app() -> FastAPI:
    application = FastAPI(
        title="E-biessa AI Service",
        version="0.1.0",
        description="Semantic listing indexing and retrieval boundary for the E-biessa marketplace.",
        lifespan=lifespan,
    )

    @application.middleware("http")
    async def observe_http(request: Request, call_next):
        if request.url.path.startswith("/metrics"):
            return await call_next(request)
        started = perf_counter()
        response_status = 500
        try:
            response = await call_next(request)
            response_status = response.status_code
            return response
        finally:
            route = getattr(request.scope.get("route"), "path", "unmatched")
            HTTP_REQUESTS.labels(request.method, route, str(response_status)).inc()
            HTTP_DURATION.labels(request.method, route).observe(perf_counter() - started)

    application.mount("/metrics", make_asgi_app())

    @application.get("/health")
    def health(settings: Settings = Depends(get_settings), store: ListingVectorStore = Depends(get_store)) -> dict:
        return {
            "status": "ok",
            "service": "e-biessa-ai",
            "embeddingModel": settings.embedding_model,
            "vectorStore": "remote" if settings.qdrant_url else "local",
            "modelLoaded": store.ready,
            "queryModelEnabled": settings.ollama_enabled,
            "queryModel": settings.ollama_model if settings.ollama_enabled else None,
            "visionEnabled": settings.vision_enabled,
            "visionModel": settings.vision_model if settings.vision_enabled else None,
            "visionModelLoaded": get_vision_identifier().ready,
        }

    @application.put("/v1/listings/{listing_id}", response_model=IndexResponse, dependencies=[Depends(require_service_token)])
    def index_listing(listing_id: int, listing: ListingDocument, store: ListingVectorStore = Depends(get_store)) -> IndexResponse:
        if listing.id != listing_id:
            from fastapi import HTTPException
            raise HTTPException(status_code=400, detail="Path listing ID does not match the document ID")
        if listing.status == "ACTIVE":
            store.upsert(listing)
            return IndexResponse(indexed=1)
        store.delete(listing.id)
        return IndexResponse(deleted=1)

    @application.delete("/v1/listings/{listing_id}", status_code=status.HTTP_204_NO_CONTENT, dependencies=[Depends(require_service_token)])
    def delete_listing(listing_id: int, store: ListingVectorStore = Depends(get_store)) -> Response:
        store.delete(listing_id)
        return Response(status_code=status.HTTP_204_NO_CONTENT)

    @application.post("/v1/listings/reindex", response_model=IndexResponse, dependencies=[Depends(require_service_token)])
    def reindex(request: BulkListingRequest, store: ListingVectorStore = Depends(get_store)) -> IndexResponse:
        return IndexResponse(indexed=store.replace_all(request.listings))

    @application.post("/v1/search", response_model=SearchResponse, dependencies=[Depends(require_service_token)])
    def search(request: SearchRequest, store: ListingVectorStore = Depends(get_store)) -> SearchResponse:
        started = perf_counter()
        hits, constraints, answer = store.search(request.query.strip(), request.limit, request.filters, request.minimumScore)
        return SearchResponse(query=request.query.strip(), hits=hits,
                              tookMs=round((perf_counter() - started) * 1000),
                              extractedConstraints=constraints, answer=answer)

    @application.post("/v1/assistant", response_model=AssistantResponse, dependencies=[Depends(require_service_token)])
    def assistant(request: AssistantRequest, store: ListingVectorStore = Depends(get_store)) -> AssistantResponse:
        started = perf_counter()
        response = MarketplaceAgentOrchestrator(store).run(request)
        return response.model_copy(update={"tookMs": round((perf_counter() - started) * 1000)})

    @application.post("/v1/vision/identify", response_model=VisionIdentificationResponse,
                      dependencies=[Depends(require_service_token)])
    def identify_part_image(
            image: bytes = Body(...),
            content_type: str | None = Header(default=None, alias="Content-Type"),
            identifier: VisionPartIdentifier = Depends(get_vision_identifier),
    ) -> VisionIdentificationResponse:
        media_type = (content_type or "").split(";", 1)[0].strip().lower()
        if media_type not in {"image/jpeg", "image/png", "image/webp"}:
            raise HTTPException(status_code=status.HTTP_415_UNSUPPORTED_MEDIA_TYPE,
                                detail="Only JPEG, PNG, and WebP images are supported.")
        try:
            return identifier.identify(image)
        except InvalidPartImage as exception:
            raise HTTPException(status_code=status.HTTP_400_BAD_REQUEST, detail=str(exception)) from exception
        except VisionUnavailable as exception:
            raise HTTPException(status_code=status.HTTP_503_SERVICE_UNAVAILABLE, detail=str(exception)) from exception

    return application


app = create_app()
