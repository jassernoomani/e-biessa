import re
from typing import Any

from langchain_core.tools import StructuredTool

from .models import (
    AssistantRequest,
    AssistantResponse,
    AssistantResult,
    ExtractedConstraints,
    GroundedAnswer,
    RecommendationCandidate,
    SearchFilters,
    SearchHit,
)
from .ollama_client import grounded_answer_fallback
from .query_understanding import normalize
from .vector_store import ListingVectorStore


RECOMMENDATION_WORDS = (
    "recommend", "recommendation", "suggest", "best option", "for me",
    "conseille", "conseil", "propose", "meilleur", "pour moi", "اقترح", "انصح",
)
COMPATIBILITY_WORDS = (
    "compatible", "compatibility", "fit", "fits", "fitment", "convient",
    "compatible avec", "mta3", "يركب", "يناسب",
)


class MarketplaceAgentOrchestrator:
    """A read-only LangChain tool orchestrator with deterministic routing.

    The language model may extract query constraints upstream, but it cannot choose
    mutation tools or invent listing IDs. Every returned ID comes from either the
    active Qdrant index or Spring's sanitized recommendation candidates.
    """

    def __init__(self, store: ListingVectorStore):
        self.store = store
        self.search_tool = StructuredTool.from_function(
            func=self._search,
            name="search_agent",
            description="Retrieve active marketplace listings using semantic search and structured filters.",
        )
        self.compatibility_tool = StructuredTool.from_function(
            func=self._compatibility,
            name="compatibility_agent",
            description="Deterministically verify vehicle, fuel, position, category and part constraints.",
        )
        self.recommendation_tool = StructuredTool.from_function(
            func=self._recommend,
            name="recommendation_agent",
            description="Rank sanitized recommendation candidates supplied by the authenticated marketplace.",
        )

    def run(self, request: AssistantRequest, took_ms: int = 0) -> AssistantResponse:
        query = request.query.strip()
        intent = self._intent(query)
        tools_used = [self.search_tool.name]
        search_data = self.search_tool.invoke({
            "query": query,
            "limit": request.limit,
            "minimum_score": request.minimumScore,
            "filters": request.filters.model_dump(mode="json"),
        })
        hits = [SearchHit.model_validate(item) for item in search_data["hits"]]
        constraints = ExtractedConstraints.model_validate(search_data["constraints"])

        if any(value is not None for value in constraints.model_dump().values()):
            tools_used.append(self.compatibility_tool.name)
            compatibility_data = self.compatibility_tool.invoke({
                "hits": [hit.model_dump(mode="json") for hit in hits],
                "constraints": constraints.model_dump(mode="json"),
            })
            hits = [SearchHit.model_validate(item) for item in compatibility_data]

        search_results = [AssistantResult(
            listingId=hit.listingId,
            score=hit.score,
            matchReasons=hit.matchReasons,
            source="SEARCH",
        ) for hit in hits]
        results = search_results
        if intent == "RECOMMENDATION" and request.recommendationCandidates:
            tools_used.append(self.recommendation_tool.name)
            enforce_allowed_ids = self._has_part_constraints(constraints)
            allowed_ids = [hit.listingId for hit in hits] if enforce_allowed_ids else []
            recommendation_data = self.recommendation_tool.invoke({
                "candidates": [candidate.model_dump(mode="json") for candidate in request.recommendationCandidates],
                "limit": request.limit,
                "allowed_ids": allowed_ids,
                "enforce_allowed_ids": enforce_allowed_ids,
            })
            recommendations = [AssistantResult.model_validate(item) for item in recommendation_data]
            results = self._merge(recommendations, search_results, request.limit)

        answer_hits = [hit for result in results for hit in hits if hit.listingId == result.listingId]
        fallback = grounded_answer_fallback(answer_hits if answer_hits else hits[: request.limit], constraints)
        summary = fallback.summary if answer_hits or not results else (
            f"Selected {len(results)} marketplace recommendation{'s' if len(results) != 1 else ''} "
            "from the buyer's current eligible listing feed."
        )
        answer = GroundedAnswer(
            summary=summary,
            recommendedListingIds=[result.listingId for result in results[:3]],
            cautions=fallback.cautions,
        )
        return AssistantResponse(
            query=query,
            intent=intent,
            results=results[: request.limit],
            tookMs=took_ms,
            extractedConstraints=constraints,
            toolsUsed=tools_used,
            answer=answer,
        )

    def _search(self, query: str, limit: int, minimum_score: float, filters: dict[str, Any]) -> dict[str, Any]:
        hits, constraints, _ = self.store.search(query, limit, SearchFilters.model_validate(filters), minimum_score)
        return {
            "hits": [hit.model_dump(mode="json") for hit in hits],
            "constraints": constraints.model_dump(mode="json"),
        }

    def _compatibility(self, hits: list[dict[str, Any]], constraints: dict[str, Any]) -> list[dict[str, Any]]:
        verified = self.store.validate_compatibility(
            [SearchHit.model_validate(hit) for hit in hits],
            ExtractedConstraints.model_validate(constraints),
        )
        return [hit.model_dump(mode="json") for hit in verified]

    @staticmethod
    def _recommend(candidates: list[dict[str, Any]], limit: int, allowed_ids: list[int],
                   enforce_allowed_ids: bool) -> list[dict[str, Any]]:
        parsed = [RecommendationCandidate.model_validate(candidate) for candidate in candidates]
        allowed = set(allowed_ids)
        if enforce_allowed_ids:
            parsed = [candidate for candidate in parsed if candidate.listingId in allowed]
        parsed.sort(key=lambda candidate: (-candidate.score, candidate.listingId))
        maximum = max((candidate.score for candidate in parsed), default=1.0) or 1.0
        return [AssistantResult(
            listingId=candidate.listingId,
            score=round(min(1.0, candidate.score / maximum), 6),
            matchReasons=candidate.reasons,
            source="RECOMMENDATION",
        ).model_dump(mode="json") for candidate in parsed[:limit]]

    @staticmethod
    def _intent(query: str) -> str:
        normalized = normalize(query)
        if any(normalize(term) in normalized for term in RECOMMENDATION_WORDS):
            return "RECOMMENDATION"
        if any(re.search(rf"(?:^|\s){re.escape(normalize(term))}(?:$|\s)", normalized) for term in COMPATIBILITY_WORDS):
            return "COMPATIBILITY"
        return "SEARCH"

    @staticmethod
    def _has_part_constraints(constraints: ExtractedConstraints) -> bool:
        return any((constraints.brand, constraints.model, constraints.year, constraints.fuelType,
                    constraints.position, constraints.category, constraints.partType))

    @staticmethod
    def _merge(primary: list[AssistantResult], secondary: list[AssistantResult], limit: int) -> list[AssistantResult]:
        merged: list[AssistantResult] = []
        seen: set[int] = set()
        for result in [*primary, *secondary]:
            if result.listingId not in seen:
                seen.add(result.listingId)
                merged.append(result)
            if len(merged) == limit:
                break
        return merged
