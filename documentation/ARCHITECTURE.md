# Architecture decision: Spring Boot marketplace core

## Decision

The Phase 1 marketplace remains a Spring Boot application. It owns users, roles,
listings, categories, vehicles, favorites, images, moderation, and transactional
data. Rewriting this working foundation in FastAPI would add risk without improving
the marketplace features required for Phase 1.

## Phase 2 boundary

FastAPI is a separate AI service. It owns
embeddings, vector retrieval and model-backed constraint extraction with deterministic compatibility checks.
It receives normalized listing and vehicle data from the Spring API and returns
ranked identifiers and explanations. Spring remains the authorization boundary and
system of record.

## Why this split

- Spring Boot is already established for secure transactional marketplace workflows.
- FastAPI and Python are a better fit for LangChain and the AI ecosystem described by
  the PRD.
- A service boundary allows the AI system to evolve without exposing the marketplace
database or coupling user-facing transactions to model availability.

## Implemented AI search and recommendation boundary

- `ai-service` exposes authenticated listing indexing, deletion, full reindex, health, and semantic search endpoints.
- FastEmbed generates multilingual embeddings locally; Qdrant persists them on disk for development and can be switched to Qdrant Server by configuration.
- Spring publishes listing synchronization events only after successful database commits and treats indexing as a non-blocking secondary operation.
- Spring hydrates ranked identifiers from MySQL before returning semantic results, so inactive or deleted vector records can never become public results.
- Local fine-tuned Qwen runs in Ollama and extracts JSON constraints. Catalog validation and deterministic compatibility checks remain authoritative.
- Angular displays AI constraints, ranked matches and explanations. Spring generates the displayed answer from current listing DTOs rather than trusting generated prose.
- Personalized recommendations are initially an explainable content-based Spring baseline over MySQL: buyer vehicle preferences, deduplicated interaction metadata, saves, seller conversations, popularity and recency. Eligibility and all reasons are deterministic; no private messages or raw queries are sent to the AI service.
- FastAPI now exposes a read-only LangChain orchestrator with `search_agent`, `compatibility_agent`, and `recommendation_agent` tools. Routing is deterministic, and the agent has no mutation, moderation, messaging, or user-management tool.
- For authenticated buyers, Spring passes only sanitized recommendation candidates (listing ID, score, reasons), never raw searches, messages, contact details, or buyer history. It then rehydrates the orchestrated IDs from MySQL, removes stale/inactive/suspended-seller rows, and builds the public answer deterministically.
- Angular shows the tools used by the assistant while preserving the existing verified fitment explanations. See `AGENT_ORCHESTRATION.md` and `RECOMMENDATIONS.md`.
- Authenticated VIN decoding is proxied through Spring and mapped only to local catalog vehicles; VIN values are not stored and never override listing fitment data.
- Seller price guidance is a deterministic MySQL comparison using active, non-suspended listings. It remains advisory and requires explicit seller acceptance.
- Optional CLIP image identification is isolated in FastAPI and lazy-loaded. Spring validates and rate-limits transient uploads; returned labels seed search but never assert compatibility.
- Voice input is a browser-only transcription convenience. Audio is not sent to E-biessa, and submitted text follows the normal keyword or deterministic AI-search path.
- Production deployment is intentionally deferred for the academic project; the local modular boundary remains suitable for demonstration and testing.
