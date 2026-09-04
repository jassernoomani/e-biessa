# E-biessa marketplace

E-biessa is the Phase 1 marketplace from the internship PRD: buyers discover and
save used car parts, sellers publish and manage listings with photos, and
administrators manage roles and moderate inventory.

## Architecture

- `marketplace-api`: Spring Boot 3 / Java 21 / MySQL / JWT
- `marketplace-frontend`: responsive Angular 21 standalone application
- `ai-service`: FastAPI / multilingual FastEmbed embeddings / persistent local Qdrant
- Image storage: local filesystem in development; Cloudinary when enabled
- Future AI service: FastAPI boundary for embeddings, RAG, compatibility, and
  recommendations (see `ARCHITECTURE.md`)

## Local setup

1. Create the MySQL database: `CREATE DATABASE marketplace;`
2. Copy `marketplace-api/.env.example` into your preferred local environment
   setup and provide a random `JWT_SECRET` of at least 32 bytes.
3. Start the API with the `dev` Spring profile if you want sample categories,
   vehicles, listings, and development accounts.
4. Run `npm install` and `npm start` from `marketplace-frontend`; Angular serves
   the app at `http://localhost:4200` and calls `http://localhost:8080/api`.

## Phase 2 semantic search

The `ai-service` directory contains the isolated FastAPI semantic retrieval service. It uses a multilingual local embedding model and Qdrant's persistent local mode, so development does not require Docker or an OpenAI API key.

Follow `ai-service/README.md` to create its Python environment. Put the same long random `AI_SERVICE_TOKEN` in `ai-service/.env` and `marketplace-api/.env`, then set `AI_SERVICE_ENABLED=true` in the Spring environment. Start FastAPI on port 8001 before Spring Boot.

After both APIs are running, log in as an administrator and make one request to `POST /api/admin/ai/reindex` with the administrator JWT. This creates embeddings for every active listing. New listing creation, edits, status changes, moderation, and deletion are synchronized automatically after the MySQL transaction commits. Indexing failures never prevent the marketplace operation from completing.

The initial Spring endpoint for semantic retrieval is `GET /api/ai/search?query=your natural language request&limit=12`. The Angular AI assistant will consume this endpoint in the next UI milestone.

The development seed preserves existing records and fills the catalog up to 100
listings. It creates ten reusable sellers, from `seller01@e-biessa.tn` through
`seller10@e-biessa.tn`, all with password `Seller123!`. It also creates
`buyer@e-biessa.tn` with password `Buyer123!` and `admin@e-biessa.tn` with
password `Admin123!`. Each seeded seller owns multiple
listings with full descriptions, vehicle fitment, OEM references, prices,
locations, conditions, and primary images. Restarting the API does not duplicate
the seed data. Never use these credentials in production.

## Required production configuration

Set `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `JWT_SECRET`, and
`CORS_ALLOWED_ORIGINS`. For Cloudinary-backed images, also set
`CLOUDINARY_ENABLED=true`, `CLOUDINARY_CLOUD_NAME`, `CLOUDINARY_API_KEY`, and
`CLOUDINARY_API_SECRET`.

For production, replace the Angular API base URL with the public HTTPS URL of
the deployed Spring API and set `CORS_ALLOWED_ORIGINS` to the frontend origin.

## Social sign-in and account recovery

The account dialog supports password login/registration, Google, Facebook, and
Microsoft sign-in, plus password recovery by registered email or phone number.
Social accounts are linked to an existing E-biessa account when the provider
returns the same verified email address. Provider redirects exchange a
short-lived, single-use code for the normal E-biessa JWT; the JWT is never placed
in a redirect URL.

Create OAuth applications with the providers you want to enable, add the
credentials listed in `marketplace-api/.env.example`, and set
`OAUTH_ENABLED=true`. Register these local callback URLs with the providers:

- `http://localhost:8080/login/oauth2/code/google`
- `http://localhost:8080/login/oauth2/code/facebook`
- `http://localhost:8080/login/oauth2/code/microsoft`

Use the equivalent HTTPS API URLs in production. Google credentials come from
Google Cloud, Facebook credentials from Meta for Developers, and Microsoft
credentials from a Microsoft Entra app registration configured for personal
Microsoft accounts if Outlook.com users should be accepted.

Recovery codes expire after ten minutes, allow five attempts, and can only be
used once. In the `dev` profile the code is shown in the local recovery dialog.
For production email delivery, configure the `SMTP_*` values and set
`RECOVERY_EMAIL_ENABLED=true`. For phone delivery, configure the `TWILIO_*`
values and set `RECOVERY_SMS_ENABLED=true`. Production responses never expose
the recovery code and do not reveal whether an email or phone number exists.
