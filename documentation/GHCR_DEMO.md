# Local deployment from GHCR

## Status and scope

The demo files are prepared; a successful end-to-end deployment has NOT yet been
verified. Build 21's Angular bundle hardcodes localhost:8080. First publish a new
Jenkins build containing the runtime-config change. The launcher deliberately
rejects old frontend images, rather than sending demo traffic to your real API.

This is a single-host resilience demonstration, not production HA. It has two API
replicas but only one database, Redis, AI service, gateway and Docker host.
OAuth is disabled (shared provider state is unfinished). AI quotas/circuit breakers
are per replica, not global. No automatic mutation retries are enabled: an
interrupted message send can have an ambiguous outcome; reload history before
resending. Message idempotency and durable AI indexing recovery remain future work.

## Isolation and prerequisites

- Project name is fixed to `e-biessa-ghcr-demo`. Do not override it.
- Dedicated MySQL schema `ebiessa_demo` and project-scoped named volumes.
- No host database, uploads, model folders or existing DevOps volumes are mounted.
- Only loopback ports 4280 (web), 19090 (Prometheus), 13000 (Grafana) are exposed.
- MySQL, Redis, APIs and FastAPI have no host ports. Metrics stay on this network.
- The trained host Ollama model is reused through host.docker.internal:11434;
  this is a deliberate shared dependency, not a second model installation.
- OAuth, real email/SMS sending and Cloudinary are disabled. Vision is disabled
  in this lightweight demo. Do not use real personal data in demo fixtures.
- Containers can make outbound requests (model downloads/catalog lookups).
- Allow roughly 5 GB additional Docker memory at configured limits. The observed
  existing services used about 4 GB of the 7.76 GB Docker allocation. Do not start
  the whole demo beside an active Jenkins build/Sonar analysis without headroom.
  After publishing, you can explicitly stop Jenkins/SonarQube via Docker Desktop
  to free memory; their volumes must remain. Alternatively increase Docker/WSL
  memory within your PC's limits. The launcher does not stop unrelated services.

## 1. Publish the runtime-config correction

Commit/review the frontend changes and demo files, push, then run Jenkins with
RUN_SONAR, BUILD_IMAGES and PUBLISH_IMAGES enabled. Record the successful numeric
build number. Do not use `latest`; it changes on each successful publishing run.
Numeric tags are not cryptographically immutable either: protect them from
replacement; record image digests for stronger provenance.

## 2. Configure private credentials

From the repository root in PowerShell:

```powershell
Copy-Item devops/demo/.env.example devops/demo/.env
notepad devops/demo/.env
```

Set IMAGE_TAG to the new build. Replace every secret placeholder with a different
random value (at least 32 characters). To generate each one in Windows PowerShell:

```powershell
$demoBytes = New-Object byte[] 32
$demoRng = [System.Security.Cryptography.RandomNumberGenerator]::Create()
$demoRng.GetBytes($demoBytes)
$demoRng.Dispose()
[Convert]::ToBase64String($demoBytes)
```

The ignored .env is used at runtime only. Never put these values in a Dockerfile,
Jenkinsfile, screenshot, Git commit, or command shared publicly. Docker operators
can inspect container environment variables: this local lab is not a secret vault.

If host Docker cannot pull the private packages, run `docker login ghcr.io -u
jassernoomani` and paste a PAT with read:packages into its password prompt. Never
use the PAT as a command-line argument. Jenkins login is not the host's login.

## 3. Pull, initialize, start

Use PowerShell. If local policy blocks scripts, use process-scoped execution policy
for this trusted local session only, not a machine-wide change.

```powershell
Set-ExecutionPolicy -Scope Process -ExecutionPolicy Bypass
./devops/demo/demo.ps1 Pull
./devops/demo/demo.ps1 Bootstrap
./devops/demo/demo.ps1 Start
./devops/demo/demo.ps1 Status
```

Bootstrap is explicit and only for the first initialization: it creates/updates
the isolated schema and seeds 60 synthetic listings using the existing local
initializer, then stops. Runtime API replicas use `ha` with schema validation,
never concurrent schema updates. Do not repeatedly bootstrap an edited demo DB:
the initializer may refresh fixtures. No data is copied from your current DB.

The existing fixture logins are admin@e-biessa.tn / Admin123!,
seller01@e-biessa.tn / Seller123!, buyer01@e-biessa.tn / Buyer123!.
These public development passwords are only appropriate for this loopback lab.

Open http://localhost:4280. Keep the regular app on 4200/8080 unchanged.
Grafana is http://localhost:13000 (admin + GRAFANA_PASSWORD from the demo .env).
Prometheus is http://localhost:19090/targets. Wait at least 15 seconds for scrapes.

## 4. Basic checks

```powershell
./devops/demo/verify.ps1
```

This checks the runtime configuration, frontend hook, two distinct responding
upstreams, three Prometheus targets and Grafana health. It is NOT proof of login,
model inference, WebSocket delivery or fault recovery.

Browser smoke tests (against published containers, no Angular dev server):

```powershell
cd marketplace-frontend
npx playwright test --config playwright.demo.config.ts
```

The tests log in with the three fixture roles, open their workspaces, verify
listings, and reject any browser request to the old backend on port 8080.

For messaging, open two browser profiles/windows, log in as buyer01 and seller01,
open a seller01 listing, send a uniquely named text plus a sample non-private
image, and reply from the seller. Both should see each message once. Reload both
to verify persistence. In browser Network, confirm the WebSocket uses :4280/ws,
not :8080. Automated two-browser cross-replica messaging remains pending.

## 5. AI check (separate from core health)

Start Ollama on the host and make sure `ollama list` includes e-biessa-query-v2.
Verify container reachability without changing the host binding:

```powershell
docker compose --env-file devops/demo/.env -f devops/demo/compose.yml exec -T ai python -c "import urllib.request; print(urllib.request.urlopen('http://host.docker.internal:11434/api/tags', timeout=5).status)"
```

If it fails, investigate host networking/firewall before exposing Ollama on all
interfaces. Do not blindly open it to the LAN. The first embedding-model download
can be slow and is stored only in the demo ai_data volume.

Log in as demo admin on :4280, then in that tab's console run:

```javascript
fetch('/api/admin/ai/reindex', {
  method: 'POST',
  headers: { Authorization: `Bearer ${localStorage.getItem('e_bieca_token')}` }
}).then(async r => ({status: r.status, body: await r.json()})).then(console.log)
```

Expect HTTP 200 and 60 indexed listings in a fresh demo. Query an actual seeded
listing's title/vehicle in AI fitment search and inspect the returned constraints
and results. Compare French and English queries. A /health 200 alone does not
prove that embeddings or Ollama inference work. Keep compatibility validation.

## 6. Controlled fault demonstration

Run only after the healthy baseline. These commands target only the demo project.
Keep records of timestamps, first-request errors and recovery duration; no
zero-downtime claim. Run ONE fault at a time and restore in finally blocks.

```powershell
$demoCompose = @('compose','--env-file','devops/demo/.env','-f','devops/demo/compose.yml')
try {
  docker @demoCompose stop api-1
  # Refresh listings repeatedly on :4280; inspect X-Demo-Upstream.
  # An initial 503 is possible. Subsequent traffic should reach api-2.
  # Log in and reload the buyer/seller message history.
} finally { docker @demoCompose start api-1 }
```

Wait until api-1 is healthy; verify both upstreams again. Container IP changes are
resolved by the gateway's Docker DNS resolver (5-second cache). Planned shutdown
uses Spring graceful shutdown (30 seconds, Docker grace 40 seconds) but no routing
drain controller: this is not a zero-downtime rolling deployment.

Repeat separately with `stop ai`/`start ai`: keyword browsing/login should keep
working; use an uncached AI query to observe a clear unavailable response, not
fabricated results. Restore and wait at least 30 seconds for circuit probing.

Repeat separately with `stop redis`/`start redis`: existing JWT/core operations
should remain usable; new socket tickets fail closed; HTTP inbox reconciliation
should recover persisted messages. Do not claim Pub/Sub is durable. Avoid
retrying ambiguous sends. Restore and retest before the next fault.

Grafana's E-biessa isolated deployment dashboard shows each API and AI scrape
target plus request/error rates. Scrape availability is not MySQL readiness or
successful model inference. No dedicated Redis/exporter or outbox panels yet.

## Stop and rollback

```powershell
./devops/demo/demo.ps1 Stop
```

This retains all demo volumes. Never run `down -v` or `docker system prune` as a
rollback. Your original local app is unchanged. For a demo code rollback, stop,
set IMAGE_TAG to a previously verified runtime-config-capable build, Pull, Start.
Do NOT run Bootstrap during a code rollback. Database schema backward
compatibility must be checked first; image rollback is not a database backup.

## Verification record

- Compose schema: validated with placeholder credentials, all profiles enabled.
- Frontend tests: 59 passing, including 4 runtime URL/isolation regression tests.
- Angular production build: passed. Demo Playwright suite: 3 tests discovered;
  not yet executed against containers.
- Nginx 1.28 gateway configuration: `nginx -t` passed in a disposable container.
- PowerShell launcher/check scripts: syntax parsed successfully; live lifecycle
  commands are not yet validated. Demo .env is confirmed ignored by Git.
- Full GHCR runtime, login, messages, inference, metrics and failure recovery:
  pending a newly published frontend image and available Docker memory.

Technical references: [Compose services](https://docs.docker.com/reference/compose-file/services/),
[Nginx upstream DNS](https://nginx.org/en/docs/http/ngx_http_upstream_module.html).
