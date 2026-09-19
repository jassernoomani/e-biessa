# Personalized recommendations — academic baseline

## Scope and architecture

Spring owns preferences, user authorization and ranking over current MySQL listings. Angular has a dedicated `RecommendationsComponent`, used in the marketplace and buyer profile. This is an explainable **content-based baseline**, not collaborative filtering or an additional fine-tuned model. The Phase 3 LangChain orchestrator now consumes only its sanitized candidate IDs, scores, and reasons through a read-only recommendation tool.

## Buyer API

All endpoints require an active BUYER identified from authentication, never a client-supplied buyer ID. Sellers/admins cannot inspect a buyer's recommendation profile through these endpoints.

| Endpoint | Behavior |
| --- | --- |
| GET `/api/recommendations?limit=6` | 1–24 suggestions, mode, numeric ranking scores, reasons and fitment caution |
| GET `/api/recommendations/settings` | Own activity toggle and preferred catalog vehicle DTOs |
| PUT `/api/recommendations/settings` | `{ "personalizationEnabled": true, "vehicleIds": [1, 2] }`; max five valid IDs |
| DELETE `/api/recommendations/history` | Forget old behavioral evidence; preserve preferences, favorites and messages |

The schema is added by the project's existing Hibernate update workflow. Keep `buyer_preferred_vehicles`, `buyer_interactions`, and the user preference fields with other application data. There are no new secrets or model dependencies.

## Ranking and eligibility

1. Fetch ACTIVE MySQL listings, reject suspended sellers and the buyer's own listings.
2. Exclude currently saved exact listings; those remain in Saved. With activity enabled, exclude exact listings from recent buyer-started conversations.
3. If preferred vehicles exist, require a matching declared vehicle row: make, model, and each specified year, engine and fuel value. Never combine fields from different rows. Check explicit listing-level fuel/engine restrictions too. Use the legacy vehicle only when the multiple-compatibility collection is empty. Unknown/mismatched configuration does not earn a match. No automatic widening if the feed is empty.
4. Score with deterministic weights: preferred vehicle 40; saved category 12; seller messaged 8; contact reveal 6; search-result category 4; viewed category 3. Activity decays with a 30-day half-life and is ignored after 90 days. Keep the strongest event per explanation, cap total behavioral score at 30. Add up to 5 points from save count and up to 5 from recency.
5. Sort by score, then descending listing ID for stable ties. Defer a seller's third and later entries until other sellers' top two have been considered. Return up to the requested limit; deferred entries fill remaining slots.

Scores are **not probabilities or compatibility percentages**. Reasons say “Listed for your …” and always accompany an OEM/fitment caution. Strict configuration matching can omit usable parts if seller data is incomplete or free-text engine restrictions are unusual; correcting catalog/listing data is preferable to inventing fitment.

Modes: PERSONALIZED if recent enabled behavioral signals exist; VEHICLE for vehicle-only cold start; DISCOVER for recent/popular general exploration. Favorites continue to be excluded from discovery even with personalization off.

## Activity and privacy

- Successful authenticated buyer detail views record a VIEW snapshot. Guests and sellers are not tracked.
- First-page keyword searches (search text or category filter) and successful AI searches record up to three distinct **returned categories**, not the query text. This is a weak search-results signal, not proof of intent.
- Successful contact reveal records the seller ID. No phone, email or message body is copied.
- Favorites are read from the authoritative favorites table. Conversation **creation time** indicates buyer initiation; a seller reply cannot reactivate cleared buyer history. Continuing an old conversation is not currently a new recommendation signal.
- History deduplicates `(buyer, kind, target)` under a per-buyer database lock. At most 200 rows are retained. Expired rows are pruned on the next recorded action; ranking always ignores rows older than 90 days even if the buyer never returns.
- Clear removes recorded rows and sets a cutoff for existing favorites/conversations. These original objects remain untouched; new actions can personalize again. Saving preferences with activity off also clears history; future events are ignored until re-enabled. Explicit vehicle choices remain active while behavioral personalization is off.
- New buyer data uses the default enabled setting, disclosed in the preference editor. No cross-user recommendation history is exposed or shared with FastAPI.

## Evaluation and demonstration

Run Spring tests (`mvn test` with Java 21), Angular `npm run verify`, FastAPI `python -m pytest`, and Angular `npm run test:e2e`. The normal browser suite mocks API data: it is genuine browser/UI testing, **not** a live-database E2E claim. `npm run test:e2e:live` remains a separate opt-in suite requiring the complete running stack.

`RecommendationIntegrationTests` uses H2 and deterministic relevance fixtures covering:

- cold start and stable order;
- exact multiple-vehicle compatibility and contradictory fuel;
- cross-row and no-compatible-inventory rejection;
- saved-category preference over unrelated inventory;
- contacted/message seller alternatives, excluding contacted exact listings;
- role restrictions, parameter validation and buyer isolation;
- deduplicated view/search/contact capture, bounded/expired history;
- reset/opt-out without deleting transactional data;
- deleting previously viewed listings safely.

Angular tests cover rendering/reasons, opening suggestions, settings/opt-out, clear confirmation, limits and recoverable errors. Playwright covers the buyer settings journey and theme geometry parity. These synthetic cases establish rule correctness, not measured real-user recommendation quality. Collect anonymized, manually judged demo cases before claiming precision/recall or personalization uplift.

## Remaining work

- Evaluate the completed Phase 3 orchestration with a larger manually judged multilingual query set.
- Optional next recommendation iteration: semantic candidate expansion, additional buyer-initiated message signals, richer part-type interests and explicit feedback on recommendations.
- For a much larger catalog, move candidate filtering/aggregation into bounded database queries and evaluate ranking quality with held-out human judgments. The current all-active-candidate baseline is scoped to the small academic catalog.
