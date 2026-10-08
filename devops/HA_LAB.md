# Resilience lab — incremental implementation

This is not yet a running load-balanced application. The lab currently provides
an isolated Redis dependency. The backend now has opt-in `ha` adapters for
single-use tickets and message fan-out; the ordinary application is not switched
to them automatically. The lab does not use the existing marketplace database or uploads.

From the repository root:

```powershell
docker compose -f devops/docker-compose.ha.yml up -d --wait redis
docker compose -f devops/docker-compose.ha.yml exec -T redis redis-cli ping
docker compose -f devops/docker-compose.ha.yml stop
```

Expected Redis response: `PONG`. Stopping retains the dedicated volume. Do not use
`down --volumes` as a routine shutdown. No Redis port is published on Windows.
The internal lab network is separate from the DevOps network. No authentication
is configured for this isolated dependency; do not expose its port or attach
untrusted containers. Persistence here is not Redis high availability.

## AI graceful degradation

Resilience4j 2.2.0 protects search, assistant, and vision with separate circuits.
Three failed calls in a three-call window open the affected circuit for 30 seconds.
After that, one request probes recovery. A successful probe closes the circuit;
a failing probe reopens it. There are no automatic upstream retries.

Configuration overrides:

- `app.ai.circuit.failure-window` (default 3)
- `app.ai.circuit.open-seconds` (default 30)

Transport errors, server errors, rate limiting, and empty bodies count as failures.
Other upstream 4xx responses are ignored by circuit statistics. Caller validation
does not become an upstream outage. Existing cached search responses may still be
used, subject to the existing backend listing validation.

Circuit breakers are per process. They do not provide shared AI quotas, database
failover, or a durable indexing queue. Index updates/reindex are not covered by
these interactive-feature circuits; their retry/outbox work remains pending.

## Next gates

1. Finish full-context HA validation after initializing an isolated SQL schema.
2. OAuth state and global AI admission control.
3. A new isolated database/schema and initialization gate.
4. Two API replicas and gateway, only after state-sharing tests pass.

See `../HA_RESILIENCE_PLAN.md` for rollback and fault-test requirements.

## Redis messaging implementation

`ha` selects Redis ticket storage and Pub/Sub relay; all other profiles use local
adapters. JWTs remain unchanged. HA requires explicit `HA_DB_URL`,
`HA_DB_USERNAME`, and `HA_DB_PASSWORD` and validates the schema without updating it.
Seeding is disabled even when `local` or `dev` is combined with `ha`. Do not enable
this profile against the user's existing database to experiment with failover.

Tickets use hashed keys, a 30-second TTL and atomic GETDEL. Redis outage rejects
new tickets and fails consumption closed. AFTER_COMMIT notification publishing
cannot turn a successful saved message into a failed response. Each subscriber
delivers only to its local participant sockets. Redis Pub/Sub is not durable;
the inbox reconciles via authenticated HTTP every ten seconds while visible,
including when the socket remains connected but a notification was missed.
Requests time out after eight seconds and do not overlap. Unsent text and newer
locally received messages are preserved. Reconnects use capped backoff and jitter.

## Opt-in real-Redis tests (no application database)

From repository root, start the temporary loopback test bridge:

```powershell
docker compose -f devops/docker-compose.ha.yml -f devops/docker-compose.ha-test.yml up -d --wait redis
```

From `marketplace-api`, with Java 21/Maven available:

```powershell
$env:HA_REDIS_TEST_PORT = '16379'
.\mvnw.cmd test
Remove-Item Env:HA_REDIS_TEST_PORT
```

Finally from repository root, restore network isolation (retains Redis data):

```powershell
docker compose -f devops/docker-compose.ha.yml up -d --wait redis
docker network rm e-biessa-ha-lab_test-access
```

The opt-in tests use two ticket stores and two subscriber instances against real
Redis. They are not yet a two-server browser failover test. Without the environment
flag they are skipped, not counted as passed. The temporary test bridge exists
because Docker's internal network does not support host access on its own.
