-- Target side: replays the staged legacy stop passes into the learned travel times of one GTFS feed.
-- Variables (set by legacy-migrate.sh): feed_code, legacy_tz, replace, and the learner parameters
-- max_plausible_sec, history_window, history_prior_samples, outlier_min_dev, outlier_max_rel,
-- kalman_initial_var, kalman_meas_noise, kalman_proc_noise, kalman_growth_per_hour.
\set ON_ERROR_STOP on
SET client_min_messages = warning;

BEGIN;

-- Patterns the app knows for the feed, once per pattern_key (the ACTIVE revision's row first: it's
-- the one live vehicles match, so its scheduled travel times are the learners' seed).
CREATE TEMP TABLE target_pattern ON COMMIT DROP AS
SELECT DISTINCT ON (tp.pattern_key) tp.pattern_key, tp.id AS trip_pattern_id, tp.route_id
FROM trip_patterns tp
JOIN gtfs_revision r ON r.id = tp.revision_id
JOIN gtfs_feed f ON f.id = r.feed_id
WHERE f.code = :'feed_code' AND r.kind = 'IMPORT' AND r.status IN ('ACTIVE', 'READY', 'SUPERSEDED')
ORDER BY tp.pattern_key, (r.status = 'ACTIVE') DESC, r.id DESC;

SELECT NOT EXISTS (SELECT 1 FROM target_pattern) AS no_target_patterns \gset
\if :no_target_patterns
SELECT legacy_migration.fail(format('GTFS feed %L has no derived trip patterns: ingest its GTFS first', :'feed_code'));
\endif

-- Service days are computed in the zone the app matches in: the ACTIVE revision's agency timezone.
SELECT coalesce(
    (SELECT a.agency_timezone
     FROM agencies a
     JOIN gtfs_revision r ON r.id = a.revision_id
     JOIN gtfs_feed f ON f.id = r.feed_id
     WHERE f.code = :'feed_code' AND r.status = 'ACTIVE' AND a.agency_timezone IS NOT NULL
     ORDER BY a.id LIMIT 1),
    :'legacy_tz') AS agency_tz \gset

-- A legacy pattern matches every app pattern of the same route with the same ordered stops (the app's
-- pattern_key also hashes the shape id, which needn't have survived). Stop path indexes then line up.
CREATE TEMP TABLE pattern_map ON COMMIT DROP AS
WITH target_sig AS (
    SELECT t.pattern_key, t.route_id, string_agg(sp.stop_id, E'\x1f' ORDER BY sp.stop_path_index) AS stops
    FROM target_pattern t
    JOIN stop_path sp ON sp.trip_pattern_id = t.trip_pattern_id
    GROUP BY t.pattern_key, t.route_id
),
legacy_sig AS (
    SELECT config_rev, trip_pattern_id, route_id, string_agg(stop_id, E'\x1f' ORDER BY stop_path_index) AS stops
    FROM legacy_migration.pattern_stop
    GROUP BY config_rev, trip_pattern_id, route_id
)
SELECT l.config_rev, l.trip_pattern_id AS legacy_pattern_id, t.pattern_key
FROM legacy_sig l
JOIN target_sig t ON t.route_id = l.route_id AND t.stops = l.stops;

CREATE INDEX ON pattern_map (config_rev, legacy_pattern_id);

-- seedStopToStopSec: the stop path's scheduled travel time plus the dwell at the stop before it.
CREATE TEMP TABLE seed ON COMMIT DROP AS
SELECT t.pattern_key, tt.stop_path_index,
       tt.travel_time_sec + CASE WHEN tt.stop_path_index >= 2 THEN coalesce(prev.dwell_time_sec, 0) ELSE 0 END
           AS seed_sec
FROM target_pattern t
JOIN travel_times_for_stop_path tt ON tt.trip_pattern_id = t.trip_pattern_id
LEFT JOIN travel_times_for_stop_path prev
       ON prev.trip_pattern_id = tt.trip_pattern_id AND prev.stop_path_index = tt.stop_path_index - 1
WHERE tt.travel_time_sec IS NOT NULL;

CREATE INDEX ON seed (pattern_key, stop_path_index);

-- Each run's first pass of each stop (GPS jitter can record one twice), on a matched pattern, whose
-- stop agrees with the legacy pattern. A run is one vehicle on one trip on one service day; the
-- service day is the one the trip's scheduled start falls on (frequency runs: their start, with runs
-- starting before 03:00 counted on the previous day).
CREATE TEMP TABLE pass ON COMMIT DROP AS
WITH resolved AS (
    SELECT p.vehicle_id, p.config_rev, p.trip_id, p.freq_start_time,
           lt.pattern_config_rev, lt.trip_pattern_id, p.stop_path_index AS k,
           p.ts AT TIME ZONE :'legacy_tz' AS ts,
           (p.ts AT TIME ZONE :'legacy_tz') AT TIME ZONE :'agency_tz' AS local_ts,
           (p.freq_start_time AT TIME ZONE :'legacy_tz') AT TIME ZONE :'agency_tz' AS local_freq_start,
           lt.start_time_sec
    FROM legacy_migration.pass p
    JOIN legacy_migration.trip lt ON lt.config_rev = p.config_rev AND lt.trip_id = p.trip_id
    JOIN legacy_migration.pattern_stop ps
      ON ps.config_rev = lt.pattern_config_rev AND ps.trip_pattern_id = lt.trip_pattern_id
     AND ps.stop_path_index = p.stop_path_index AND ps.stop_id = p.stop_id
    WHERE EXISTS (SELECT 1 FROM pattern_map m
                  WHERE m.config_rev = lt.pattern_config_rev AND m.legacy_pattern_id = lt.trip_pattern_id)
),
dated AS (
    SELECT r.*,
           CASE WHEN r.freq_start_time IS NULL
                THEN (r.local_ts - make_interval(secs => r.start_time_sec) + interval '12 hours')::date
                ELSE (r.local_freq_start - interval '3 hours')::date
           END AS service_date
    FROM resolved r
)
SELECT DISTINCT ON (vehicle_id, config_rev, trip_id, freq_start_time, service_date, k)
       vehicle_id, config_rev, trip_id, freq_start_time, service_date, pattern_config_rev, trip_pattern_id, k, ts
FROM dated
ORDER BY vehicle_id, config_rev, trip_id, freq_start_time, service_date, k, ts;

CREATE INDEX ON pass (vehicle_id, config_rev, trip_id, service_date, k);
ANALYZE pass;

-- One stop-to-stop sample per pass whose previous stop the same run passed earlier, within the
-- plausible maximum - bucketed (day type, time band) by when it left that previous stop.
CREATE TEMP TABLE sample ON COMMIT DROP AS
WITH pair AS (
    SELECT b.pattern_config_rev, b.trip_pattern_id, b.k, b.ts, a.ts AS prev_ts, b.service_date,
           extract(epoch FROM b.ts - a.ts)::float8 AS sample_sec
    FROM pass b
    JOIN pass a
      ON a.vehicle_id = b.vehicle_id AND a.config_rev = b.config_rev AND a.trip_id = b.trip_id
     AND a.freq_start_time IS NOT DISTINCT FROM b.freq_start_time
     AND a.service_date = b.service_date AND a.k = b.k - 1
    WHERE b.ts > a.ts AND b.ts - a.ts <= make_interval(secs => :max_plausible_sec)
),
banded AS (
    SELECT p.*,
           CASE extract(isodow FROM p.service_date) WHEN 6 THEN 1 WHEN 7 THEN 2 ELSE 0 END AS day_type,
           ((trunc(floor(extract(epoch FROM p.prev_ts
                   - (((p.service_date + time '12:00') AT TIME ZONE :'agency_tz') - interval '12 hours')))
                 / 3600)::int % 24) + 24) % 24 AS hour
    FROM pair p
)
SELECT pattern_config_rev, trip_pattern_id, k, ts, sample_sec, day_type,
       CASE WHEN hour >= 19 THEN 4 WHEN hour >= 15 THEN 3 WHEN hour >= 9 THEN 2 WHEN hour >= 6 THEN 1 ELSE 0 END
           AS time_band
FROM banded;

-- Each sample also updates its whole day type (time band ALL = 5) and the overall bucket (day type ALL = 3).
CREATE TEMP TABLE learned ON COMMIT DROP AS
SELECT m.pattern_key, s.k AS stop_path_index, v.day_type, v.time_band,
       legacy_migration.running_average(
           s.sample_sec, coalesce(sd.seed_sec, s.sample_sec),
           (:history_window)::integer, (:history_prior_samples)::integer,
           (:outlier_min_dev)::float8, (:outlier_max_rel)::float8
           ORDER BY s.ts) AS average,
       legacy_migration.kalman(
           s.sample_sec, coalesce(sd.seed_sec, s.sample_sec), extract(epoch FROM s.ts)::float8,
           (:kalman_initial_var)::float8, (:kalman_meas_noise)::float8, (:kalman_proc_noise)::float8,
           (:kalman_growth_per_hour)::float8, (:outlier_min_dev)::float8, (:outlier_max_rel)::float8
           ORDER BY s.ts) AS kalman,
       max(s.ts) AS updated_at
FROM sample s
JOIN pattern_map m ON m.config_rev = s.pattern_config_rev AND m.legacy_pattern_id = s.trip_pattern_id
CROSS JOIN LATERAL (VALUES (s.day_type, s.time_band), (s.day_type, 5), (3, 5)) v(day_type, time_band)
LEFT JOIN seed sd ON sd.pattern_key = m.pattern_key AND sd.stop_path_index = s.k
GROUP BY m.pattern_key, s.k, v.day_type, v.time_band;

\if :replace
INSERT INTO travel_time_observation (
    gtfs_feed_code, pattern_key, stop_path_index, day_type, time_band, sample_count, mean_sec, updated_at
)
SELECT :'feed_code', pattern_key, stop_path_index, day_type, time_band, average[1]::bigint, average[2], updated_at
FROM learned
ON CONFLICT (gtfs_feed_code, pattern_key, stop_path_index, day_type, time_band) DO UPDATE SET
    sample_count = excluded.sample_count, mean_sec = excluded.mean_sec, updated_at = excluded.updated_at;

INSERT INTO kalman_travel_time_state (
    gtfs_feed_code, pattern_key, stop_path_index, day_type, time_band,
    estimate_sec, error_variance, sample_count, updated_at
)
SELECT :'feed_code', pattern_key, stop_path_index, day_type, time_band,
       kalman[1], kalman[2], kalman[3]::bigint, updated_at
FROM learned
ON CONFLICT (gtfs_feed_code, pattern_key, stop_path_index, day_type, time_band) DO UPDATE SET
    estimate_sec = excluded.estimate_sec, error_variance = excluded.error_variance,
    sample_count = excluded.sample_count, updated_at = excluded.updated_at;
\else
INSERT INTO travel_time_observation (
    gtfs_feed_code, pattern_key, stop_path_index, day_type, time_band, sample_count, mean_sec, updated_at
)
SELECT :'feed_code', pattern_key, stop_path_index, day_type, time_band, average[1]::bigint, average[2], updated_at
FROM learned
ON CONFLICT (gtfs_feed_code, pattern_key, stop_path_index, day_type, time_band) DO NOTHING;

INSERT INTO kalman_travel_time_state (
    gtfs_feed_code, pattern_key, stop_path_index, day_type, time_band,
    estimate_sec, error_variance, sample_count, updated_at
)
SELECT :'feed_code', pattern_key, stop_path_index, day_type, time_band,
       kalman[1], kalman[2], kalman[3]::bigint, updated_at
FROM learned
ON CONFLICT (gtfs_feed_code, pattern_key, stop_path_index, day_type, time_band) DO NOTHING;
\endif

\echo
\echo 'Legacy patterns matched to app patterns:'
SELECT count(*) FILTER (WHERE m.pattern_key IS NOT NULL) AS matched,
       count(*) FILTER (WHERE m.pattern_key IS NULL) AS unmatched
FROM (SELECT DISTINCT config_rev, trip_pattern_id FROM legacy_migration.pattern_stop) l
LEFT JOIN (SELECT DISTINCT config_rev, legacy_pattern_id, pattern_key FROM pattern_map) m
       ON m.config_rev = l.config_rev AND m.legacy_pattern_id = l.trip_pattern_id;

\echo 'Stop passes staged / on matched patterns, samples, learner rows:'
SELECT (SELECT count(*) FROM legacy_migration.pass) AS staged_passes,
       (SELECT count(*) FROM pass) AS matched_passes,
       (SELECT count(*) FROM sample) AS samples,
       (SELECT count(*) FROM learned) AS learner_rows,
       (SELECT count(DISTINCT pattern_key) FROM learned) AS patterns_learned;

\echo 'Routes with the most stop passes on unmatched patterns (route or stop sequence changed):'
SELECT ps.route_id, count(*) AS passes
FROM legacy_migration.pass p
JOIN legacy_migration.trip lt ON lt.config_rev = p.config_rev AND lt.trip_id = p.trip_id
JOIN (SELECT DISTINCT config_rev, trip_pattern_id, route_id FROM legacy_migration.pattern_stop) ps
  ON ps.config_rev = lt.pattern_config_rev AND ps.trip_pattern_id = lt.trip_pattern_id
WHERE NOT EXISTS (SELECT 1 FROM pattern_map m
                  WHERE m.config_rev = lt.pattern_config_rev AND m.legacy_pattern_id = lt.trip_pattern_id)
GROUP BY ps.route_id
ORDER BY passes DESC
LIMIT 20;

COMMIT;
