# CI/CD

Five workflows under `.github/workflows/`, each covering one concern so they can run (and be
re-run, and be reasoned about) independently.

| Workflow | File | Triggers | What it does |
| --- | --- | --- | --- |
| Lint | `lint.yml` | push to `main`, PR, manual | `spotlessCheck` (ktlint) on the backend; `eslint` on the frontend |
| Tests | `test.yml` | push to `main`, PR, manual | `./gradlew test` (JUnit, Testcontainers Postgres) + `vitest run` on the frontend, reported via [dorny/test-reporter](https://github.com/dorny/test-reporter) |
| Vulnerability scan | `security.yml` | push to `main`, PR, weekly, manual | Trivy scan of the backend's resolved JVM dependencies and the frontend's `pnpm-lock.yaml`, SARIF uploaded to the Security tab |
| Benchmark | `benchmark.yml` | push to `main`, PR, manual | `smokeBenchmark` fail-fast, then the full `benchmark` suite reported via [kitlangton/jmh-benchmark-action](https://github.com/kitlangton/jmh-benchmark-action) — see [docs/benchmarks.md](benchmarks.md) |
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

- **Tests** — `dorny/test-reporter` turns the JUnit XML from both the backend and frontend into a
  check run with per-suite/per-test detail, directly on the commit/PR - no need to open logs. The
  raw HTML report + XML is also uploaded as the `backend-test-report` artifact.
- **Vulnerabilities** — each Trivy step writes a finding-count summary to the run's **Summary**
  tab and uploads SARIF to the repo's **Security** tab (if code scanning is available); the raw
  SARIF is also kept as a `trivy-*-sarif` artifact — one per scan target: backend/frontend
  dependencies (`security.yml`) and backend/frontend images (`build-publish.yml`).
- **Benchmarks** — `kitlangton/jmh-benchmark-action` comments a before/after comparison directly
  on the PR (or updates the `main` baseline on a push); the raw JSON plus the `benchmarkReport`
  HTML page are also uploaded as the `benchmark-reports` artifact, kept 90 days.
- **Helm** — the packaged `.tgz` is uploaded as the `helm-chart` artifact on every run, including
  PRs (`build-publish.yml`).

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

## A note on forked-repo PRs

`test.yml` (`checks: write`) and `benchmark.yml` (`contents: write`, `pull-requests: write`) both
declare elevated permissions so `dorny/test-reporter` and `jmh-benchmark-action` can create check
runs / post PR comments / push the benchmark baseline branch. A PR from a branch in *this*
repository gets a token with those permissions; a PR from a fork does not - GitHub always
downgrades `pull_request`-triggered tokens on fork PRs to read-only, regardless of what's declared
here. On a fork PR, the test/benchmark steps in those two workflows still run (so failures are
still visible in the job log and the step summaries still populate), they just can't create the
check run or post the comment. This is a deliberate tradeoff over switching to
`pull_request_target` (which would run PR code with the base repo's elevated token - a real
supply-chain risk for an untrusted fork PR) and is the standard, recommended way to use both
actions.
