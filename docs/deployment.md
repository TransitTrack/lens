# Deployment

This covers running transittrack as a container: the image build, the local
`docker compose` stack, and the Kubernetes role-split topology (Helm chart)
for horizontally-scaled deployments. Design reference:
[docs/superpowers/specs/2026-09-18-k8s-role-split-design.md](superpowers/specs/2026-09-18-k8s-role-split-design.md).

---

## 1. Container image

`Dockerfile` at the repo root is a multi-stage build:

```bash
docker build -t transittrack:local .
```

- **Build stage** — `eclipse-temurin:25-jdk`, runs `./gradlew bootJar`. Needs
  JDK 25 specifically (matches `build.gradle.kts`'s
  `JavaLanguageVersion.of(25)`); `settings.gradle.kts` has no Foojay
  toolchain-resolver plugin, so Gradle can't auto-download a different JDK
  inside the container.
- **Runtime stage** — `eclipse-temurin:25-jre`, copies the built jar, runs as
  a non-root `transittrack` user, exposes `8080`.

No `SPRING_PROFILES_ACTIVE` is baked into the image — it's supplied per
container/Deployment (see [§3](#3-roles)).

### Smoke-testing the image locally

```bash
docker compose up -d db   # needs a reachable Postgres; see §2
docker run -d --name transittrack-smoke -p 18080:8080 \
  -e SPRING_DATASOURCE_URL=jdbc:postgresql://host.docker.internal:5432/explorer \
  -e POSTGRES_PASSWORD=password \
  transittrack:local
curl -sf http://localhost:18080/actuator/health
docker rm -f transittrack-smoke   # see the warning below before doing this
```

**Don't force-kill a running container that owns feed locks.** `docker rm -f`
(or any hard kill) skips graceful shutdown, so `@PreDestroy` never runs and
`FeedLockCoordinator` never releases its leases — another instance can't
claim those feeds until the lease naturally expires (`lockAtMostFor`, a few
minutes; see [§4](#4-coordination)). Prefer `docker stop` (SIGTERM, graceful)
for anything you expect another instance to pick up immediately after.

---

## 2. Local development stack

```bash
docker compose up -d      # Postgres + Prometheus + Grafana
./gradlew bootRun          # runs the app directly on the host, monolith mode
```

`compose.yaml` brings up:

| Service | Port | Notes |
| --- | --- | --- |
| `db` (Postgres 18) | `5432` | `explorer` database, password `password` (override via `POSTGRES_PASSWORD`) |
| `prometheus` | `9090` | Scrapes the backend's `/actuator/prometheus` |
| `grafana` | `4000` | Provisioned dashboards; local credentials `admin`/`admin` |

With no `SPRING_PROFILES_ACTIVE` set, every background component runs in
this one process — see [§3](#3-roles).

---

## 3. Roles

The same image runs as one of four roles, selected entirely by
`SPRING_PROFILES_ACTIVE`, so there's nothing role-specific to build:

| Role profile | What it runs | Typical replica count |
| --- | --- | --- |
| `role-api` | GraphQL/web layer (always loaded in every role — see below) | N, scaled on request load |
| `role-ingester` | `GtfsIngestScheduler` (GTFS Schedule re-ingestion) | N — scales safely (§4) |
| `role-feed-processor` | `AvlPoller`, `AvlMatchProcessor`, plus every cluster-singleton housekeeping job (retention sweepers, temp-file sweeper, metrics gauge refresher) | N |
| `role-predictor` | `PredictionProcessor` | N |

**Monolith-by-default:** if *no* `role-*` profile is active, every role's
components run in the one process — this is what local `bootRun` and the
Docker smoke test above do, and it's also what every `@SpringBootTest` in the
codebase gets by default. Only setting one or more `role-*` profiles
restricts a process to those roles. This is implemented by
`eu.transittrack.config.ConditionalOnRole` (a `Condition`, not a bare
`@Profile`) — see the design spec for why a bare `@Profile` wouldn't give this
semantics.

GraphQL, REST controllers, and the actuator endpoints are loaded in **every**
role, not just `role-api` — they're cheap, mostly-stateless reads.
`role-api` is about which Deployment/Service actually receives external
traffic and gets scaled for it, not which pods have that code loaded; this
keeps the web layer completely untouched by the role split.

Set the role via environment variable, same in Docker Compose, `docker run
-e`, or a Kubernetes `env:` entry:

```bash
docker run -e SPRING_PROFILES_ACTIVE=role-feed-processor transittrack:local
```

---

## 4. Coordination

Running more than one replica of `role-feed-processor`, `role-predictor`, or
`role-ingester` is safe *because of*, not despite, the role split — each
uses a coordination primitive matched to its work shape:

- **`AvlPoller` / `AvlMatchProcessor`** (continuous per-feed background
  loops) share one `FeedLockCoordinator` instance per role
  (`avlFeedLockCoordinator`) built on ShedLock's `LockProvider`. Every
  ~60s reconcile tick, each replica tries to claim (or extend) a lease per
  feed id; whichever replica holds a feed's lease owns polling *and*
  matching for it. Add a replica and it picks up currently-unclaimed feeds
  automatically; kill one and its feeds free up within the lease's
  `lockAtMostFor` (a few minutes) for another replica to claim — no manual
  rebalancing, no `StatefulSet` ordinals required.
- **`PredictionProcessor`** gets its own independent `FeedLockCoordinator`
  (`predictorFeedLockCoordinator`, its own lock namespace) — a separate
  deployment, no reason to share ownership with feed-processor pods.
- **`GtfsIngestScheduler`** doesn't run a continuous loop — it's an
  occasional sweep that fires a one-shot ingest per due feed. Each due
  feed's ingest call is wrapped in a scoped ShedLock critical section
  (`LockingTaskExecutor.executeWithLock`) instead, so two `role-ingester`
  replicas racing on the same sweep tick never both ingest the same feed.
- **Cluster-singleton housekeeping jobs** (retention sweepers, the
  temp-file sweeper, the metrics gauge refresher) run under
  `role-feed-processor` and are each guarded by a plain
  `@SchedulerLock` — since that role can have N replicas, this stops any
  of them from double-running on the same tick. `GtfsIngestScheduler`'s own
  sweep method also carries a `@SchedulerLock`, as a safety net against a
  rolling deploy briefly running old+new pods of a role meant to stay at a
  single replica.

All of this is backed by one `shedlock` table (Liquibase migration
`0009-shedlock.yaml`), created automatically on first startup — no separate
infrastructure to provision.

**Rolling deploys:** set `terminationGracePeriodSeconds` a bit above the
~60s reconcile interval so a terminating pod's graceful shutdown
(`@PreDestroy` releasing its leases) reliably completes before Kubernetes
force-kills it — otherwise the freed-up feeds wait out the full
`lockAtMostFor` instead of transferring immediately.

---

## 5. Kubernetes / Helm

The chart at `deploy/helm/transittrack/` renders one Deployment per role
(from `values.yaml`'s `roles` map) plus a Service for the `api` role:

```bash
helm lint deploy/helm/transittrack
helm template transittrack deploy/helm/transittrack   # inspect the rendered manifests
helm install transittrack deploy/helm/transittrack \
  --set image.repository=your-registry/transittrack \
  --set image.tag=1.2.3
```

Key `values.yaml` knobs:

```yaml
image:
  repository: transittrack
  tag: local

roles:
  api:
    replicaCount: 2
    service: { enabled: true, port: 8080 }
  ingester:
    replicaCount: 1        # can be raised — see §4
  feed-processor:
    replicaCount: 2
  predictor:
    replicaCount: 1

postgres:
  host: transittrack-postgres
  port: 5432
  database: explorer
  username: postgres
  passwordSecretName: transittrack-postgres   # a Secret you create/manage separately
  passwordSecretKey: password
```

Each role's Deployment gets `SPRING_PROFILES_ACTIVE` set to its
`roles.<name>.profile` value and a liveness/readiness probe against
`/actuator/health/{liveness,readiness}`. The chart's `<release>-config`
ConfigMap is an intentionally empty placeholder for `transittrack.*`
overrides you later want to inject without rebuilding the image — populate
it as that need arises; today the feed list and all other
`transittrack.*` settings ship inside the jar's `application.yaml`
(see [docs/configuration.md](configuration.md)).

**Not included:** in-cluster Prometheus/Grafana wiring — point your
cluster's existing monitoring stack at each pod's `/actuator/prometheus`
the same way you would for any other service. The `prometheus`/`grafana`
services in `compose.yaml` are local-dev-only.

---

## 6. Scaling guidance

| Role | Scale up when | Notes |
| --- | --- | --- |
| `api` | Request/GraphQL latency or throughput demands it | Purely stateless from this app's perspective; scale like any web tier |
| `ingester` | You track many GTFS feeds and a single replica can't keep up with the sweep | Safe to scale per §4; each due feed is still only ingested once |
| `feed-processor` | You track many AVL feeds, or per-feed poll/match load is high | Feeds distribute across replicas automatically; adding a replica doesn't require reconfiguring existing feeds |
| `predictor` | Prediction backlog (`vehicle_match` rows in `PENDING` state) grows faster than one replica drains it | Independent of `feed-processor`'s scale — a slow predictor doesn't block matching |

Watch the `transittrack_avl_pending_reports` and
`transittrack_prediction_pending_matches` gauges (per-feed backlog depth,
refreshed on `transittrack.observability.gauge-refresh-ms` — see
[docs/configuration.md §8](configuration.md#8-observability--transittrackobservability))
for backlog growth before scaling.

---

## 7. Frontend (optional)

The Nuxt vehicle dashboard (`web/`, see [web/README.md](../web/README.md)) has
its own image and is **off by default** in the Helm chart —
`frontend.enabled: false`.

```bash
docker build -t transittrack-web:local web/
```

`web/Dockerfile` is a two-stage build: `node:22-alpine` runs `pnpm run
generate` (the app is client-side-only — `ssr: false` — so this produces a
fully static bundle in `.output/public/`, no Node runtime needed at
runtime), and `nginx:alpine` serves it. The Apollo client calls a relative
`/graphql` URL (see `app/plugins/apollo.client.ts`), which a static bundle
has no way to point at a backend host itself — nginx fills that gap via
`web/nginx.conf.template`, reverse-proxying `/graphql` to `${BACKEND_URL}`.
That template is processed by the official `nginx` image's own
env-substitution-on-startup feature (any `*.template` under
`/etc/nginx/templates/`), so the backend target is a plain environment
variable, not a rebuild:

```bash
docker run -p 8081:80 -e BACKEND_URL=http://your-backend:8080 transittrack-web:local
```

To turn it on in the Helm chart:

```bash
helm template transittrack deploy/helm/transittrack --set frontend.enabled=true
```

```yaml
frontend:
  enabled: true
  image: { repository: your-registry/transittrack-web, tag: "1.2.3" }
  replicaCount: 1
  service: { port: 80 }
  backendUrl: ""   # empty (default) = the in-cluster api Service, http://<release>-api:<roles.api.service.port>
```

Renders one Deployment + one Service, both no-ops when `frontend.enabled` is
`false`. `backendUrl` only needs setting explicitly if the dashboard should
point somewhere other than this chart's own `api` role (e.g. a backend
running outside the cluster, or in a different namespace/release).
