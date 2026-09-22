# CI/CD

Five workflows under `.github/workflows/`, each covering one concern so they can run (and be
re-run, and be reasoned about) independently.

| Workflow | File | Triggers | What it does |
| --- | --- | --- | --- |
| Lint | `lint.yml` | push to `main`, PR, manual | `spotlessCheck` (ktlint) on the backend; `eslint` on the frontend |
| Tests | `test.yml` | push to `main`, PR, manual | `./gradlew test` (JUnit, Testcontainers Postgres) + `vitest run` on the frontend; uploads the HTML test report |
| Vulnerability scan | `security.yml` | push to `main`, PR, weekly, manual | Trivy scan of the backend's resolved JVM dependencies and the frontend's `pnpm-lock.yaml`, SARIF uploaded to the Security tab |
| Benchmark | `benchmark.yml` | push to `main`, PR (smoke only); weekly, manual (full) | `smokeBenchmark` on every push/PR (~30s pipeline sanity check); full `benchmark` suite + `benchmarkReport` HTML weekly — see [docs/benchmarks.md](benchmarks.md) |
| Build & publish | `build-publish.yml` | push to `main`, `v*` tags, PR (build+scan only), manual | Builds + Trivy-scans the backend and frontend Docker images and packages the Helm chart on every run; **pushes** to GHCR only on a push to `main` or a `v*` tag |

## Where things land

Everything publishes to GitHub Container Registry (`ghcr.io`) under this repository, using the
built-in `GITHUB_TOKEN` — no registry secrets to create:

- Backend image: `ghcr.io/<owner>/<repo>:<tag>`
- Frontend image: `ghcr.io/<owner>/<repo>-web:<tag>`
- Helm chart (OCI): `oci://ghcr.io/<owner>/charts/transittrack:<version>`

Tags follow `docker/metadata-action`'s defaults: the branch name for a branch push (`main`), `pr-<N>`
for a PR build (built and scanned, never pushed), a semver tag when the ref is a `v*` git tag, and
the short commit SHA — so every build is addressable by commit even without a version tag.

The Helm chart version is the git tag (`vX.Y.Z` → `X.Y.Z`) on a tag push, or `Chart.yaml`'s current
`version` field suffixed with `+<short-sha>` otherwise — see `helm-chart`'s "Determine chart
version" step in `build-publish.yml`.

## Reports

Every workflow writes a short markdown summary to the run's own **Summary** tab (test pass/fail
counts, vulnerability finding counts, benchmark scores) so you don't have to open logs for the
headline numbers. Full detail is uploaded as a workflow artifact:

- `backend-test-report` — the JUnit HTML report + raw XML (`test.yml`)
- `trivy-*-sarif` — one per scan target: backend/frontend dependencies (`security.yml`) and
  backend/frontend images (`build-publish.yml`); also uploaded as SARIF to the repo's **Security**
  tab if code scanning is available
- `benchmark-reports` — the raw JSON plus the `benchmarkReport`-rendered HTML page, kept 90 days
  (`benchmark.yml`, weekly run only)
- `helm-chart` — the packaged `.tgz`, on every run including PRs (`build-publish.yml`)

## Vulnerability scan policy

Both Trivy steps run with `exit-code: "0"` — they report findings (SARIF + step summary) without
failing the build. This is deliberate for now: turning a scanner on for the first time against an
existing dependency tree almost always surfaces a backlog of pre-existing CVEs, and a hard gate at
that point just blocks all merges until someone triages the backlog. Once that triage has happened
(or you've decided which severities actually warrant blocking), flip the relevant `exit-code` to
`"1"` in `security.yml` and/or `build-publish.yml` - optionally paired with `.trivyignore` entries
for accepted findings.

## Requirements to actually push

`packages: write` on `GITHUB_TOKEN` is enough to push images/charts to this repository's own GHCR
namespace under default repository settings. If your org restricts package creation via
`GITHUB_TOKEN`, allow it for this repo (Settings → Actions → General → Workflow permissions), or
switch `build-publish.yml`'s login steps to a PAT-backed secret instead.

SARIF upload to the Security tab needs GitHub code scanning enabled, which is free for public
repos but requires GitHub Advanced Security on private ones - the upload steps are
`continue-on-error: true` so a private repo without GHAS still gets the artifact and the step
summary, it just won't populate the Security tab.
