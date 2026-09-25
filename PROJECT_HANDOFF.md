# E-biessa Project Handoff

Last updated: 2026-09-19 (PRD implementation and academic verification milestone)

This document is the working context for another developer or AI model continuing the project. Read it together with `README.md`, `ARCHITECTURE.md`, the service-specific READMEs, and the original PRD at `C:\Users\Jasser Noomani\Downloads\internship prd.pdf` when that local file is available.

## 1. Project goal and scope

E-biessa is an academic AI-powered marketplace for used automotive parts in Tunisia. It supports buyers, sellers, and administrators. The PRD requires:

- user accounts and role-based access;
- listing and image management;
- traditional filtered search;
- natural-language AI search;
- embeddings, a vector database, and RAG;
- personalized recommendations;
- a later LangChain agent layer with search, compatibility, and recommendation agents.

This is an academic project. Do not prioritize production deployment, paid infrastructure, or enterprise scaling until the full PRD is complete. Security fundamentals, deterministic compatibility, tests, and a good demonstration still matter.

## 2. Important architecture decision

The marketplace was already implemented in Spring Boot when the FastAPI-only wording in the PRD was reviewed. The accepted design is:

- Spring Boot remains the transactional marketplace core and security boundary.
- FastAPI is a separate AI service for embeddings, semantic retrieval, and model-backed query understanding.
- Angular is the frontend.
- MySQL is the system of record.
- Qdrant local persistent mode stores listing vectors.
- Ollama runs the fine-tuned local Qwen model.

Do not rewrite the working Spring marketplace into FastAPI. Document the hybrid architecture as a justified deviation from the PRD. `ARCHITECTURE.md` contains the decision.

## 3. Repository structure

```text
Stage_2026/
  marketplace-api/       Spring Boot 3, Java 21, MySQL, JWT
  marketplace-frontend/  Angular 21 standalone application
  ai-service/             FastAPI, FastEmbed, Qdrant, Ollama integration
  README.md
  ARCHITECTURE.md
  PROJECT_HANDOFF.md
```

Git repository:

- branch: `main`
- remote: `https://github.com/jassernoomani/e-biessa.git`
- first recorded commit: `357d46d first E-biessa commit`

Never commit `.env`, uploads, dependencies, Qdrant data, Hugging Face caches, GGUF files, LoRA checkpoints, or Playwright artifacts. The root and service `.gitignore` files cover these paths.

## 4. Local service topology

| Component | Address | Purpose |
| --- | --- | --- |
| Angular | `http://localhost:4200` | User interface |
| Spring Boot | `http://localhost:8080` | Marketplace REST API and WebSocket endpoint |
| FastAPI | `http://127.0.0.1:8001` | Semantic indexing and retrieval |
| Ollama | `http://127.0.0.1:11434` | Fine-tuned query-constraint model |
| MySQL | `localhost:3306`, database `marketplace` | Transactional data |

Spring and FastAPI share an internal `AI_SERVICE_TOKEN`. Keep the value only in their ignored `.env` files. `*.env.example` must contain placeholders, not usable secrets.

## 5. Implemented marketplace features

### Authentication and profiles

- Email/password registration and login.
- JWT authentication using the database user identity.
- Google, Facebook, and Microsoft OAuth wiring, enabled only when provider credentials exist.
- Short-lived, single-use OAuth exchange codes; JWTs are not placed in redirect URLs.
- Account recovery by registered email or phone, with development-only code display.
- Separate profile page with name, email, phone, location, business name, website, and biography.
- Buyer, seller, and administrator role-aware interfaces.
- Account suspension prevents authentication and hides active seller listings.

The Google OAuth secret was exposed earlier during development. It must be rotated in Google Cloud if this has not already been done. Never put it in documentation or Git.

### Seller onboarding and reputation

- Buyers submit a seller application after completing business/contact details.
- Applications remain pending until an administrator approves or rejects them.
- Buyers can rate eligible sellers from 1 to 5 stars and optionally leave a comment.
- Public seller profiles show business information, location, website, rating, and active listings.
- Buyers can report sellers; administrators review reports and may suspend accounts.

### Listings

- Seller create, preview, edit, publish, deactivate, delete, and mark-sold workflows.
- Sold listings disappear from the public marketplace immediately.
- Multiple compatible vehicles per listing.
- Manufacturer, part brand, primary and alternative OEM numbers.
- Part position: front, rear, left, right, combinations, or universal.
- Engine and fuel compatibility.
- Warranty, delivery, collection, location, and negotiable-price fields.
- Sorting, pagination, filters preserved in the URL, and expanded compatibility filters.
- Listing reports and administrator moderation queues.

### Images

- JPEG, PNG, and WebP upload.
- Client-side compression and previews.
- Primary-image selection, deletion, and drag-and-drop ordering.
- Local storage for development and optional Cloudinary support.
- Message attachments validate size, claimed MIME type, and file signatures.

### Buyer/seller communication

- Buyer-only seller contact reveal.
- Click-to-call, WhatsApp prepared message, and email link.
- Private buyer-to-seller conversation inbox.
- Image attachments in messages.
- WebSocket delivery using short-lived, single-use realtime tickets rather than JWT query strings.
- Conversations open at the latest message while allowing users to scroll upward.

### Administration

- Separate administrator dashboard.
- User and profile management.
- Seller application queue.
- Listing and seller report queues.
- Listing moderation and account suspension.
- Administrator audit log.

### Frontend design

- Application name is E-biessa.
- Custom automotive marketplace logo.
- Dark/light theme toggle with matching layout dimensions.
- Responsive marketplace, listing detail, profile, seller studio, inbox, and admin views.

## 6. Implemented AI system

### Retrieval

- FastEmbed model: `sentence-transformers/paraphrase-multilingual-MiniLM-L12-v2`.
- Qdrant collection: `e_biessa_parts`.
- Spring synchronizes active listings after successful database commits.
- Administrator full reindex endpoint: `POST /api/admin/ai/reindex`.
- Public search endpoint: `GET /api/ai/search`.
- FastAPI indexing endpoints require `X-AI-Service-Token`.
- Spring rehydrates vector result IDs from MySQL and returns only currently active listings.

### Query understanding and compatibility

- Natural-language extraction supports English, French, and Tunisian Arabic examples.
- Extracted fields: make/brand, model, year, fuel, position, category, and part type.
- Explicit Angular filters override inferred values.
- Raw model output is passed through `merge_model_constraints` and catalog validation.
- Deterministic compatibility checks and reranking are authoritative.
- A minimum relevance threshold suppresses weak results.
- Match explanations include signals such as Golf compatibility, Diesel, position, and brake system.

Never allow an LLM to invent compatibility or apply raw model output directly as a database/vector filter.

### Deterministic answer policy

Ollama is used only for structured constraint extraction. It does not generate recommendation prose.

Spring constructs the displayed answer from active database records after rehydration. It ignores any answer prose or IDs returned by the AI service. This was intentional hardening against unsupported claims and stale vector data.

### AI endpoint hardening

Default Spring settings:

- query length: maximum 240 characters;
- rate limit: 12 searches per 60 seconds per authenticated user or direct guest IP;
- maximum concurrent AI searches: 2;
- queue acquisition timeout: 500 ms;
- response cache: 120 seconds;
- FastAPI connection timeout: 3 seconds;
- FastAPI read timeout: 95 seconds;
- cache invalidation on listing upsert, deletion, or full reindex.

Relevant files:

- `marketplace-api/.../controller/AiSearchController.java`
- `marketplace-api/.../service/AiSearchRateLimiter.java`
- `marketplace-api/.../service/AiServiceClient.java`
- `marketplace-api/.../service/AiMarketplaceService.java`
- `ai-service/app/query_understanding.py`
- `ai-service/app/vector_store.py`
- `ai-service/app/ollama_client.py`

## 7. Fine-tuned local model

Hardware used: NVIDIA RTX 4060 with 8 GB VRAM and 16 GB system RAM.

- Base family: Qwen2.5 7B Instruct, 4-bit.
- Training method: local Unsloth QLoRA.
- Runtime Ollama model: `e-biessa-query-v2`.
- Final adapter: `ai-service/training/out/e-biessa-qwen25-7b-v2/final-adapter`.
- Exported adapter GGUF: `ai-service/training/export/e-biessa-qwen25-7b-v2-lora.gguf`.
- Model binaries and checkpoints are intentionally ignored by Git.

Recorded v2 held-out result from `ai-service/training/README.md`:

- JSON validity: 99.2% (119/120);
- make: 99.2%; model: 96.7%;
- fuel, position, category, and part type: 99.2%;
- hardened full-record accuracy: 96.7% (116/120) after authoritative year/catalog validation.

The model sometimes serializes years incorrectly. Query-year extraction and catalog validation must remain authoritative. Further epochs were not recommended for the current dataset.

## 8. Development data

The local Spring profiles seed development data without duplicating existing rows:

- administrator: `admin@e-biessa.tn` / `Admin123!`;
- buyers: `buyer01@e-biessa.tn` through `buyer03@e-biessa.tn` / `Buyer123!`;
- sellers: `seller01@e-biessa.tn` through `seller03@e-biessa.tn` / `Seller123!`;
- 60 listings across multiple makes, models, categories, locations, and sellers;
- 20 local vehicle definitions based on the CarQuery catalog shape.

These accounts are for academic/local demonstrations only.

CarQuery has been unreliable at times, including TLS/availability problems. The application falls back to local vehicle data. Do not make runtime correctness depend entirely on CarQuery; sellers must confirm exact fitment.

## 9. Testing status and commands

### Spring Boot

Latest verified result: 62 passing tests, including recommendations, VIN decoding, price estimation, authenticated image identification, deterministic AI answers, and rate limiting (2026-09-11).

Use Java 21. The Maven wrapper has failed on some Windows PowerShell setups; IntelliJ's bundled Maven works.

```powershell
cd marketplace-api
.\mvnw.cmd test
```

If the wrapper fails, run the Maven `test` lifecycle from IntelliJ's Maven panel.

### FastAPI

Latest verified result: 28 passed. Vision tests cover validation and use a fake classifier without downloading CLIP (2026-09-11).

```powershell
cd ai-service
.\.venv\Scripts\python.exe -m pytest
```

### Angular

Latest verified unit/component result: 52 passing tests, including recommendations, price guidance, image identification, and voice transcription. Run:

```powershell
cd marketplace-frontend
npm run verify
```

The production build succeeds. Its realistic academic-project budgets now allow a 650 KB initial warning threshold and a 55 KB component-style error threshold; the verified initial bundle is approximately 635 KB and `features.css` is approximately 45 KB.

### Playwright

Two suites exist:

- `e2e/app.spec.ts`: isolated browser UI tests using controlled API fixtures;
- `e2e/live-stack.spec.ts`: real Angular/Spring/FastAPI/Ollama journeys.

Latest isolated result: 7 passed, 4 intentionally skipped live-stack tests. Browser coverage includes recommendations, image-assisted search, voice transcription, roles, and deterministic AI answers:

```powershell
npm run test:e2e
```

Verified result on 2026-09-19: 7 passed, 4 intentionally skipped because the full Spring process was not available to Playwright in the agent environment. The isolated suite itself is clean.

With MySQL, Ollama, FastAPI, Spring, and Angular available:

```powershell
npm run test:e2e:live
```

Expected result: 4 live tests passed.

## 10. Starting the complete stack

1. Start MySQL/XAMPP and ensure the `marketplace` database exists.
2. Start or verify Ollama:

```powershell
ollama list
ollama run e-biessa-query-v2
```

Exit the interactive prompt with `/bye`; the Ollama service remains available.

3. Start FastAPI:

```powershell
cd ai-service
Set-ExecutionPolicy -Scope Process -ExecutionPolicy Bypass
.\.venv\Scripts\Activate.ps1
python -m uvicorn app.main:app --reload --host 127.0.0.1 --port 8001
```

If Windows rejects port 8001, identify the listener or use another port and update `AI_SERVICE_URL` consistently.

4. Start Spring Boot with Java 21 and the local profile.
5. Start Angular:

```powershell
cd marketplace-frontend
npm start
```

6. Log in as the administrator and reindex active listings if the database or Qdrant index was recreated.

## 11. Cleanup already completed

The following disposable artifacts have been removed:

- `marketplace-frontend-vinext-backup`;
- `ai-service/unsloth_compiled_cache`;
- obsolete smoke-test adapters;
- the superseded v1 LoRA run;
- redundant v2 checkpoints.

The final v2 adapter and exported GGUF remain intact.

Legacy Partly/e-bieca account migration lines in `DataInitializer` are intentionally retained until old local data has been migrated. Do not remove them casually.

## 12. Known documentation and maintenance issues

- Root `README.md` and `ARCHITECTURE.md` now reflect implemented AI search, recommendations, and correct seed counts. `RECOMMENDATIONS.md` documents the new algorithm and limitations.
- The Angular application is still concentrated in a large `App` component and large CSS files. Feature splitting is useful but secondary to finishing the PRD.
- The current token storage keys retain old e-bieca/Partly names for migration compatibility. Renaming them is optional academic cleanup and would log users out once.
- `spring.jpa.hibernate.ddl-auto=update` remains in use. Proper migrations are desirable but are not the immediate academic milestone.
- Cloudinary, real recovery email/SMS delivery, and multiple OAuth providers may remain unconfigured for the academic demonstration.

## 13. Personalized recommendations — implemented baseline

Implemented before LangChain orchestration. See `RECOMMENDATIONS.md` and the `RecommendationService`, `BuyerActivityService`, `RecommendationController`, and Angular `recommendations/` feature folder.

### Delivered behavior

1. Buyer interaction snapshots record deduplicated views, returned search categories and seller contact reveals. No raw queries, contact details or message bodies are stored. History is bounded to 200 rows, ranked over 90 days, and expired rows are pruned on new activity.
2. Up to five existing catalog vehicles can be saved. Matching requires fields from the same compatibility row; explicit listing fuel/engine restrictions are checked. Empty matching inventory never silently widens to other vehicles.
3. Saves and buyer-started conversations provide additional authoritative metadata signals. Conversation creation time is intentional: a seller reply must not revive history after a buyer clears it.
4. Deterministic content-based weights combine preferred vehicles, category interests, seller contact/message signals, save popularity and recency, with a diversity pass. This baseline does not yet use embedding-based recommendation candidates or fine-grained part-type interests.
5. Buyer-only GET `/api/recommendations`, GET/PUT `/api/recommendations/settings`, DELETE `/api/recommendations/history`. Own authentication identity only; no admin browsing of buyer recommendation profiles.
6. Excludes inactive/sold/suspended-seller/own/saved listings and recent conversation-start exact listings when personalization is enabled. Explanations are evidence-based, scores are not displayed as compatibility percentages.
7. Dedicated Angular recommended shelf on the unfiltered keyword marketplace, plus vehicle/privacy settings on the buyer profile. Loading, empty, retry and history-clear confirmation states included. Existing catalog definitions are used; no new CarQuery dependency.
8. Buyers can disable activity use or reset history while preserving favorites, messages and explicit vehicle preferences. Turning activity off clears history and prevents new event capture.
9. Eleven Spring integration fixtures, five Angular component tests, and a Playwright recommendation journey are included. Tests establish rule correctness, not a real-user relevance metric.

No retraining, model download, AI reindex, or new environment variables are necessary. Restart Spring for Hibernate's existing schema-update workflow and reload Angular. New default `personalizationEnabled=true` is declared at both entity and database level.

Related verification fixes: explicitly select the injectable AI rate-limiter constructor, return HTTP 400 for invalid method parameters/non-finite AI thresholds, and avoid initializing a network CarQuery client when that integration is disabled.

Do not use the LLM as the source of recommendation eligibility. Compatibility and listing status remain deterministic. Embeddings may generate candidates; deterministic business rules and scores should produce the final ordering and explanations.

## 14. Phase 3 LangChain orchestration — implemented

FastAPI now provides a deterministic, read-only LangChain orchestrator.

The orchestrator exposes these LangChain tools:

- `search_agent`: existing semantic retrieval;
- `compatibility_agent`: deterministic compatibility revalidation;
- `recommendation_agent`: sanitized candidates from Spring's recommendation service.

Spring rehydrates returned IDs from MySQL and constructs the answer deterministically. Angular uses the assistant for signed-in buyers and displays its tool trace. Agents remain read-only; listing writes, moderation actions, and user-state changes continue through authenticated Spring endpoints and explicit user actions. See `AGENT_ORCHESTRATION.md`.

## 15. PRD future improvements - implemented

VIN-assisted vehicle selection is now implemented. It validates full VINs, uses NHTSA vPIC behind authenticated Spring Boot, caches responses, maps decoded data to local catalog rows, stores no VIN, and keeps deterministic seller/listing compatibility authoritative. See `VIN_COMPATIBILITY.md`.

Grounded seller price guidance is implemented as a deterministic comparison over similar active MySQL listings. The seller sees the median, interquartile range, sample size, confidence, evidence, and caution, and must explicitly choose whether to use it. See `PRICE_ESTIMATION.md`.

Image-assisted identification is implemented as an optional, lazy-loaded CLIP service behind authenticated Spring Boot. It suggests a bounded part taxonomy without making fitment claims or retaining uploads. Voice search uses browser speech recognition in French, Arabic, or English and leaves the transcription editable before search. See `IMAGE_AND_VOICE_SEARCH.md`.

All four future improvements explicitly named by the PRD are now represented: image identification, VIN assistance, price guidance, and voice search. Cloud deployment and production scaling remain intentionally deferred for this academic project.

## 16. Working rules for the next contributor

- Preserve unrelated user changes and inspect `git status` before editing.
- Never reveal or commit secrets.
- Use the existing Spring/FastAPI boundary.
- Keep MySQL authoritative for active listing state.
- Keep deterministic compatibility validation after every model inference.
- Add tests with every new endpoint, state transition, or Angular flow.
- Run the relevant suites before committing.
- Normal Git routine: `git status`, `git add .`, `git commit -m "..."`, `git push`.
- Update this handoff file when a major milestone or architecture decision changes.

## 17. Academic report review — 12 September 2026

The reviewed French report is `deliverables/Rapport_de_stage_E-biessa_revise.pdf` (38 pages). The original report was preserved. It adds two use-case views, four UML class views covering the persistent entities, VIN/price/CLIP/voice sections, corrected messaging sequence, PRD coverage, and accurate distinctions between raw model metrics and deterministic corrections. The v2 corpus contains 1120 training, 160 validation and 120 held-out test examples.

The PDF was typeset using ReportLab and visually checked across all pages; no text extends outside page bounds. The editable sibling DOCX contains the revisions but its Word-specific layout remains unverified: the required DOCX renderer failed because LibreOffice is unavailable in the bundled Windows runtime. Do not present that DOCX as visually approved. The reproducible revision builder is `tools/review_e_biessa_report.py`; diagram assets are in `report_assets/reviewed/`. Test counts in the report are recorded previous campaigns, not new test executions during the document review.

## 18. Verification milestone — 19 September 2026

The following checks were executed from the current working tree:

- Spring Boot on Java 21: 62 tests passed, 0 failures, including security, recommendations, VIN decoding, price estimation, rate limiting, and application context startup.
- FastAPI: 28 tests passed, 0 failures.
- Angular: 51 tests passed and the production build completed successfully.
- Playwright isolated browser suite: 7 passed. The 4 explicitly live-stack journeys skipped because Spring was unavailable to Playwright in the agent-launched environment.
- Registered Ollama model `e-biessa-query-v2`: 12/12 smoke examples returned valid JSON; make, model, fuel, position, category, and part type were 100% correct. Raw year serialization remains unreliable by design and is corrected by deterministic query validation before filtering.
- Direct FastAPI retrieval benchmark: 6/6 expected listings appeared at rank 1, all 35 expected constraints matched, and mean measured latency was approximately 1.3 seconds.
- A real French query returned the correct Citroën C3, year 2020, Petrol, FRONT, Electrical Parts, and LED headlight constraints, with a deterministic grounded answer.

Two small verification fixes are part of this milestone:

- `VehicleCatalogService` constructs its optional CarQuery HTTP client lazily, so a disabled external catalog cannot prevent application-context startup.
- Angular production size budgets reflect the completed academic feature set without hiding future material growth.

One environment-specific limitation remains: an agent-launched Java process on this Windows host could connect to MySQL and initialize Spring, but embedded Tomcat could not create the JDK NIO loopback selector (`Unable to establish loopback connection`). This failure occurs inside the host JDK selector before application request handling. Run Spring from IntelliJ/your normal terminal and execute `npm run test:e2e:live` to obtain the final four-process browser evidence; do not weaken application security or networking code to work around this host-specific launcher issue.

## 19. DevOps and observability — 21 September 2026

- `Jenkinsfile` is the primary pipeline: Spring, FastAPI, Angular, and isolated
  Playwright gates run before SonarQube analysis and Docker builds.
- GHCR publication is parameterized and disabled by default; there is no public
  deployment because this remains an academic project.
- Spring exposes `/actuator/health` and `/actuator/prometheus`; `/actuator/info`
  stays protected. FastAPI exposes `/metrics/` using bounded route-template
  labels, with a regression test preventing its service token from appearing.
- `devops/docker-compose.devops.yml` runs Jenkins, SonarQube/PostgreSQL,
  Prometheus, Grafana, Alertmanager, and Blackbox Exporter with named volumes.
- Grafana provisions an E-biessa dashboard. Prometheus includes uptime and
  elevated-error alerts. Follow `DEVOPS.md` for the complete setup.
- Verification after this change: 63 Spring tests, 29 FastAPI tests, 52 Angular
  tests, and 7 isolated Playwright journeys passed; the Angular production
  build, JaCoCo XML, Python coverage XML, LCOV, Compose resolution, dashboard
  JSON, and all monitoring YAML files were also validated.
