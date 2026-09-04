# Architecture decision: Spring Boot marketplace core

## Decision

The Phase 1 marketplace remains a Spring Boot application. It owns users, roles,
listings, categories, vehicles, favorites, images, moderation, and transactional
data. Rewriting this working foundation in FastAPI would add risk without improving
the marketplace features required for Phase 1.

## Phase 2 boundary

FastAPI remains part of the target architecture as a separate AI service. It will
own embeddings, vector retrieval, RAG, compatibility reasoning, and recommendations.
It will receive normalized listing and vehicle data from the Spring API and return
ranked identifiers and explanations. Spring remains the authorization boundary and
system of record.

## Why this split

- Spring Boot is already established for secure transactional marketplace workflows.
- FastAPI and Python are a better fit for LangChain and the AI ecosystem described by
  the PRD.
- A service boundary allows the AI system to evolve without exposing the marketplace
database or coupling user-facing transactions to model availability.

## Implemented Phase 2 foundation

- `ai-service` exposes authenticated listing indexing, deletion, full reindex, health, and semantic search endpoints.
- FastEmbed generates multilingual embeddings locally; Qdrant persists them on disk for development and can be switched to Qdrant Server by configuration.
- Spring publishes listing synchronization events only after successful database commits and treats indexing as a non-blocking secondary operation.
- Spring hydrates ranked identifiers from MySQL before returning semantic results, so inactive or deleted vector records can never become public results.
