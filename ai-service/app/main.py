from contextlib import asynccontextmanager
from functools import lru_cache
from time import perf_counter

from fastapi import Depends, FastAPI, Response, status

from .config import Settings, get_settings
from .models import BulkListingRequest, IndexResponse, ListingDocument, SearchRequest, SearchResponse
from .security import require_service_token
from .vector_store import ListingVectorStore


@lru_cache
def get_store() -> ListingVectorStore:
    return ListingVectorStore(get_settings())


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

    return application


app = create_app()
