# Phase 3 LangChain orchestration

## Implemented flow

1. Angular sends a buyer's natural-language request to authenticated Spring endpoint `GET /api/ai/assistant`.
2. Spring calculates its existing deterministic recommendation candidates for that buyer and sends FastAPI only listing IDs, numeric scores, and human-readable reasons.
3. FastAPI's deterministic orchestrator invokes LangChain tools as needed:
   - `search_agent`: multilingual vector retrieval over active listing projections;
   - `compatibility_agent`: re-checks make, model, year, fuel, position, category, and part type against one declared compatibility row;
   - `recommendation_agent`: ranks only the sanitized candidate list supplied by Spring.
4. Spring rehydrates the returned IDs from MySQL, removes inactive listings and suspended sellers, and creates the final grounded answer from the current listing DTOs.
5. Angular displays results, evidence, extracted constraints, and the tool workflow.

## Safety boundary

The orchestrator has no write-capable tool. It cannot create listings, contact sellers, change roles, moderate reports, or update buyer state. It never receives raw queries from recommendation history, message text, phone numbers, email addresses, favorites belonging to other buyers, or database credentials. Model output can help extract constraints, but deterministic code owns compatibility, active-state checks, returned IDs, and answer text.

Public guests continue to use `GET /api/ai/search`. Personalized orchestration requires an active authenticated account, and only BUYER accounts receive personalized candidates.

## Verification

- FastAPI tests cover internal-token enforcement, tool routing, compatibility filtering, and rejection of incompatible recommendation candidates.
- Spring tests cover sanitized candidate transfer, active MySQL rehydration, tool-name allowlisting, and deterministic answer reconstruction.
- Angular tests cover the assistant request, workflow labels, explanations, and result rendering.

This completes the core Phase 3 PRD agent architecture. Remaining PRD ideas—image recognition, VIN-based checks, price estimation, and voice search—are optional future improvements rather than prerequisites for the implemented academic workflow.
