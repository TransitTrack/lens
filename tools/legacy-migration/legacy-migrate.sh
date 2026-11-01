#!/usr/bin/env bash
# Migrates a TheTransitClock (Transitime) database into TransitTrack lens. See README.md.
set -euo pipefail

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SQL="$HERE/sql"
# Override to run psql elsewhere, e.g. PSQL="docker exec -i my-postgres psql". SQL is always fed on stdin.
read -r -a PSQL_CMD <<< "${PSQL:-psql}"

usage() {
    cat <<'EOF'
Usage:
  legacy-migrate.sh export-gtfs --out FILE.zip [--config-rev N]
      Rebuild a GTFS zip from a legacy config revision (default: the active one).
      Needs LEGACY_DB_URL.

  legacy-migrate.sh seed-learning --feed-code CODE [--since TS] [--until TS]
                                  [--legacy-tz ZONE] [--replace] [--keep-staging]
      Replay legacy arrivals/departures into the learned travel times of GTFS feed CODE,
      which must already be ingested. Needs LEGACY_DB_URL and TARGET_DB_URL.
        --since/--until  legacy local time window, e.g. '2025-01-01' (default: everything)
        --legacy-tz      zone the legacy timestamps are in (default: the legacy agency timezone)
        --replace        overwrite learned rows that already exist (default: keep them)
        --keep-staging   keep the legacy_migration schema in the target DB for inspection

  legacy-migrate.sh cleanup
      Drop the legacy_migration staging schema from the target DB. Needs TARGET_DB_URL.

Environment:
  LEGACY_DB_URL, TARGET_DB_URL  libpq connection strings, e.g. postgresql://user:pass@host:5432/db
  PSQL                          psql command (default: psql)
  Learner parameters (defaults match transittrack.predict.learn):
    MAX_PLAUSIBLE_SEC=1800 HISTORY_WINDOW=50 HISTORY_PRIOR_SAMPLES=5
    OUTLIER_MIN_DEV_SEC=60 OUTLIER_MAX_REL=1.0
    KALMAN_INITIAL_VAR=3600 KALMAN_MEAS_NOISE=400 KALMAN_PROC_NOISE=25 KALMAN_GROWTH_PER_HOUR=400
EOF
}

die() { echo "legacy-migrate: $*" >&2; exit 1; }

need() { [[ -n "${!1:-}" ]] || die "$1 is not set"; }

# psql URL [psql args...] < sql
psql_run() {
    local url="$1"; shift
    "${PSQL_CMD[@]}" "$url" -X -q -v ON_ERROR_STOP=1 "$@"
}

legacy_scalar() {
    psql_run "$LEGACY_DB_URL" -A -t "$@"
}

export_gtfs() {
    local out="" rev=""
    while [[ $# -gt 0 ]]; do
        case "$1" in
            --out) out="${2:?}"; shift 2 ;;
            --config-rev) rev="${2:?}"; shift 2 ;;
            *) die "unknown option $1" ;;
        esac
    done
    [[ -n "$out" ]] || die "--out is required"
    need LEGACY_DB_URL

    if [[ -z "$rev" ]]; then
        rev="$(legacy_scalar <<'SQL'
SELECT coalesce(
    (SELECT config_rev FROM public.active_revisions WHERE config_rev IS NOT NULL ORDER BY id DESC LIMIT 1),
    (SELECT max(config_rev) FROM public.trips))
SQL
)"
        [[ -n "$rev" ]] || die "legacy DB has no config revision"
    fi
    [[ "$rev" =~ ^[0-9]+$ ]] || die "invalid config revision: $rev"
    echo "Exporting legacy config revision $rev"

    local dir
    dir="$(mktemp -d)"
    trap 'rm -rf "$dir"' EXIT

    local f name lines
    for f in "$SQL"/gtfs/*.sql; do
        name="$(basename "$f" .sql)"
        [[ "$name" == _* ]] && continue
        cat "$SQL/gtfs/_prelude.sql" "$f" | psql_run "$LEGACY_DB_URL" -v rev="$rev" > "$dir/$name.txt"
        lines="$(($(wc -l < "$dir/$name.txt")))"
        if [[ "$lines" -le 1 ]]; then
            case "$name" in
                agency|routes|stops|trips|stop_times) die "$name.txt is empty for config revision $rev" ;;
            esac
            rm "$dir/$name.txt"
        else
            printf '  %-20s %8d rows\n' "$name.txt" "$((lines - 1))"
        fi
    done
    [[ -f "$dir/calendar.txt" || -f "$dir/calendar_dates.txt" ]] || die "no calendar or calendar_dates rows"

    mkdir -p "$(dirname "$out")"
    out="$(cd "$(dirname "$out")" && pwd)/$(basename "$out")"
    rm -f "$out"
    (cd "$dir" && zip -q -X "$out" ./*.txt)
    echo "Wrote $out"
}

seed_learning() {
    local feed="" since="-infinity" until="infinity" tz="" replace=false keep=false
    while [[ $# -gt 0 ]]; do
        case "$1" in
            --feed-code) feed="${2:?}"; shift 2 ;;
            --since) since="${2:?}"; shift 2 ;;
            --until) until="${2:?}"; shift 2 ;;
            --legacy-tz) tz="${2:?}"; shift 2 ;;
            --replace) replace=true; shift ;;
            --keep-staging) keep=true; shift ;;
            *) die "unknown option $1" ;;
        esac
    done
    [[ -n "$feed" ]] || die "--feed-code is required"
    need LEGACY_DB_URL
    need TARGET_DB_URL

    if [[ -z "$tz" ]]; then
        tz="$(legacy_scalar <<'SQL'
SELECT agency_timezone FROM public.agencies WHERE agency_timezone IS NOT NULL ORDER BY config_rev DESC LIMIT 1
SQL
)"
        [[ -n "$tz" ]] || die "legacy DB has no agency timezone; pass --legacy-tz"
    fi

    local revs
    revs="$(legacy_scalar -v since="$since" -v until="$until" < "$SQL/learning/legacy_revs.sql")"
    [[ -n "$revs" ]] || die "no legacy arrivals/departures between $since and $until"
    echo "Legacy timestamps in $tz; config revisions with arrivals/departures: $revs"

    [[ "$keep" == true ]] || trap 'cleanup' EXIT
    psql_run "$TARGET_DB_URL" < "$SQL/learning/stage.sql"

    echo "Staging legacy trip patterns, trips and stop passes..."
    psql_run "$LEGACY_DB_URL" -v revs="$revs" < "$SQL/learning/legacy_pattern_stops.sql" \
        | psql_run "$TARGET_DB_URL" -c "COPY legacy_migration.pattern_stop FROM STDIN WITH (FORMAT csv)"
    psql_run "$LEGACY_DB_URL" -v revs="$revs" < "$SQL/learning/legacy_trips.sql" \
        | psql_run "$TARGET_DB_URL" -c "COPY legacy_migration.trip FROM STDIN WITH (FORMAT csv)"
    { echo "SET datestyle = ISO;"; cat "$SQL/learning/legacy_passes.sql"; } \
        | psql_run "$LEGACY_DB_URL" -v since="$since" -v until="$until" \
        | psql_run "$TARGET_DB_URL" -c "SET datestyle = ISO" \
            -c "COPY legacy_migration.pass FROM STDIN WITH (FORMAT csv)"
    psql_run "$TARGET_DB_URL" -c "ANALYZE legacy_migration.pattern_stop, legacy_migration.trip, legacy_migration.pass"

    echo "Replaying into feed $feed..."
    psql_run "$TARGET_DB_URL" -P pager=off \
        -v feed_code="$feed" -v legacy_tz="$tz" -v replace="$replace" \
        -v max_plausible_sec="${MAX_PLAUSIBLE_SEC:-1800}" \
        -v history_window="${HISTORY_WINDOW:-50}" \
        -v history_prior_samples="${HISTORY_PRIOR_SAMPLES:-5}" \
        -v outlier_min_dev="${OUTLIER_MIN_DEV_SEC:-60}" \
        -v outlier_max_rel="${OUTLIER_MAX_REL:-1.0}" \
        -v kalman_initial_var="${KALMAN_INITIAL_VAR:-3600}" \
        -v kalman_meas_noise="${KALMAN_MEAS_NOISE:-400}" \
        -v kalman_proc_noise="${KALMAN_PROC_NOISE:-25}" \
        -v kalman_growth_per_hour="${KALMAN_GROWTH_PER_HOUR:-400}" \
        < "$SQL/learning/seed.sql"
}

cleanup() {
    need TARGET_DB_URL
    psql_run "$TARGET_DB_URL" -c "SET client_min_messages = warning" \
        -c "DROP SCHEMA IF EXISTS legacy_migration CASCADE"
}

case "${1:-}" in
    export-gtfs) shift; export_gtfs "$@" ;;
    seed-learning) shift; seed_learning "$@" ;;
    cleanup) shift; cleanup ;;
    -h|--help|help|"") usage ;;
    *) usage >&2; exit 2 ;;
esac
