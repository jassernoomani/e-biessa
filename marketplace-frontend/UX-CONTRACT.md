# E-biessa UI contract

## Product and business evidence

Buyers find parts and contact sellers; sellers manage inventory; administrators
moderate the marketplace. UI language is English, currency TND, existing
date formatting is preserved. Accessibility target: WCAG 2.2 AA; this document
does not claim a completed whole-product accessibility certification.

| Scope                                                   | Source                                                          |
| ------------------------------------------------------- | --------------------------------------------------------------- |
| Product jobs and market                                 | ../README.md                                                    |
| Authorization, AI boundaries and persistence            | ../documentation/ARCHITECTURE.md                                |
| Photo identification uncertainty and upload constraints | ../documentation/IMAGE_AND_VOICE_SEARCH.md                      |
| Recommendation privacy and eligibility                  | ../documentation/RECOMMENDATIONS.md                             |
| Permission/lifecycle implementation                     | ../marketplace-api/src/main/java/com/readdlytech/marketplaceapi |

No billing, retention, deletion or permission policy is changed by this refresh.

## Canonical UI Map

| Capability     | Canonical owner                           | Source of truth           | Allowed variants                    | Verification         |
| -------------- | ----------------------------------------- | ------------------------- | ----------------------------------- | -------------------- |
| Select/Listbox | native HTML select + Angular forms        | app.html and DESIGN.md    | platform-owned popup                | keyboard/browser E2E |
| Form           | Angular FormsModule/ngForm                | app.html and App handlers | existing auth/profile/listing forms | unit + E2E           |
| Scrollbar      | src/workshop.css global baseline          | DESIGN.md                 | table horizontal overflow           | computed style/E2E   |
| Toast          | App.notice and one app toast              | src/app/app.ts            | existing feedback tones             | unit/E2E             |
| CRUD           | App + ApiService and Spring authorization | source paths above        | existing create/edit/moderation     | project tests        |

Table selection and date-picker primitives are not introduced. Native dropdown
appearance, collision and keyboard behavior are deliberately OS/browser-owned.

## Visual and state contract

DESIGN.md mirrors runtime tokens in workshop.css. Light and dark change colours
only. Shared buttons/fields/cards and UiIconComponent are canonical; no route-local
replacement theme. Hover is supplemental; visible keyboard focus is required.
Inputs preserve values on recoverable failures. Existing skeletons reserve list
geometry; loading/empty/error feedback must not be hidden by visual styling.

## Dataset and navigation

Marketplace uses server pagination, sort and committed query/filter URL state.
Existing role-specific views and list/detail Back behavior are preserved.
Typing alone is draft input; explicit search submits. No new debounce or remote
request semantics are introduced. Mobile categories scroll; filters open inline.
Administration tables use owned horizontal overflow rather than hiding columns.
Seller inventory uses labelled stacked rows on phones, preserving every action.
The catalogue has a viewport-edge sticky filter rail on desktop/tablet and an
inline disclosure on phones. Grid columns adapt to available CSS width (including
browser zoom); server page size and pagination are unchanged. On phones, inbox
switches between conversation list and thread, with an explicit return button;
realtime refresh must not dismiss the list while the user is choosing a thread.

## Flow ledger

| Operation            | Owner                                   | Preserved outcome                                                       | Recovery                                    |
| -------------------- | --------------------------------------- | ----------------------------------------------------------------------- | ------------------------------------------- |
| Browse/search        | applyFilters/loadParts/runAiSearch      | catalogue/verified search results                                       | existing keyword fallback and feedback      |
| Photo identify       | identifyPartPhoto/searchVisionCandidate | suggestion seeds fitment search; uncertain returns no forced suggestion | crop/clearer-photo caution and inline error |
| Save listing         | saveListing                             | existing studio list refresh                                            | existing form/submit status                 |
| Edit/delete/moderate | App + ApiService                        | existing authorization and list update                                  | existing confirmation/feedback              |
| Contact/message      | App messaging handlers                  | existing protected seller contact/inbox                                 | existing inline errors                      |
| Login/logout         | App auth handlers                       | existing session/role workspace                                         | existing validation/recovery                |

## Verification and known migration boundaries

Run npm run test:ci, npm run build and npm run test:e2e; browser E2E covers
catalogue light/dark/mobile, auth, buyer/seller/admin and photo uncertainty.
Static premium audit is supplementary: its generic HTML parser does not recognize
Angular (click) handlers and may flag real actions as actionless. Inspect those
against Angular template and E2E rather than changing event semantics to appease
the parser. Existing modal focus/validation and full CRUD resilience remain
separate behavior-review work; do not claim they were rebuilt in a visual task.
