-- Target side: (re)creates the legacy_migration staging schema and the replay helpers.
\set ON_ERROR_STOP on
SET client_min_messages = warning;

DROP SCHEMA IF EXISTS legacy_migration CASCADE;
CREATE SCHEMA legacy_migration;

CREATE TABLE legacy_migration.pattern_stop (
    config_rev      integer NOT NULL,
    trip_pattern_id text    NOT NULL,
    route_id        text,
    stop_path_index integer NOT NULL,
    stop_id         text
);

CREATE TABLE legacy_migration.trip (
    config_rev         integer NOT NULL,
    trip_id            text    NOT NULL,
    pattern_config_rev integer NOT NULL,
    trip_pattern_id    text    NOT NULL,
    start_time_sec     integer NOT NULL
);

CREATE TABLE legacy_migration.pass (
    vehicle_id      text      NOT NULL,
    config_rev      integer   NOT NULL,
    trip_id         text      NOT NULL,
    freq_start_time timestamp,
    stop_path_index integer   NOT NULL,
    stop_id         text      NOT NULL,
    ts              timestamp NOT NULL
);

CREATE FUNCTION legacy_migration.fail(msg text) RETURNS void
LANGUAGE plpgsql AS $$ BEGIN RAISE EXCEPTION '%', msg; END $$;

-- SampleClip.clip (eu.transittrack.predict.learn.TravelTimeLearners).
CREATE FUNCTION legacy_migration.clip(sample float8, estimate float8, min_dev float8, max_rel float8) RETURNS float8
LANGUAGE sql IMMUTABLE AS $$
    SELECT least(greatest(sample, estimate - greatest(min_dev, max_rel * estimate)),
                 estimate + greatest(min_dev, max_rel * estimate))
$$;

-- updateRunningAverage, one sample at a time. State: {sample_count, mean_sec}.
CREATE FUNCTION legacy_migration.running_average_step(
    state float8[], sample float8, seed float8, win integer, prior integer, min_dev float8, max_rel float8
) RETURNS float8[]
LANGUAGE sql IMMUTABLE AS $$
    SELECT CASE
        WHEN state IS NULL THEN
            ARRAY[1, seed + (sample - seed) / least(1 + greatest(prior, 0), greatest(win, 1))]::float8[]
        ELSE
            ARRAY[state[1] + 1,
                  state[2] + (legacy_migration.clip(sample, state[2], min_dev, max_rel) - state[2])
                             / least(state[1] + 1 + greatest(prior, 0), greatest(win, 1))]::float8[]
    END
$$;

CREATE AGGREGATE legacy_migration.running_average(float8, float8, integer, integer, float8, float8) (
    SFUNC = legacy_migration.running_average_step,
    STYPE = float8[]
);

-- updateKalman, one sample at a time, with the error-variance growth PredictionWriter.lockKalmanStates
-- applies for the time since the state was last updated. State: {estimate, variance, count, updated_at epoch}.
CREATE FUNCTION legacy_migration.kalman_step(
    state float8[], sample float8, seed float8, at_epoch float8,
    initial_variance float8, measurement_noise float8, process_noise float8, growth_per_hour float8,
    min_dev float8, max_rel float8
) RETURNS float8[]
LANGUAGE sql IMMUTABLE AS $$
    SELECT CASE
        WHEN state IS NULL THEN
            ARRAY[seed + initial_variance / (initial_variance + measurement_noise) * (sample - seed),
                  initial_variance * measurement_noise / (initial_variance + measurement_noise),
                  1,
                  at_epoch]::float8[]
        ELSE (
            SELECT ARRAY[state[1] + p.prior / (p.prior + measurement_noise)
                                    * (legacy_migration.clip(sample, state[1], min_dev, max_rel) - state[1]),
                         p.prior * measurement_noise / (p.prior + measurement_noise),
                         state[3] + 1,
                         greatest(state[4], at_epoch)]::float8[]
            FROM (SELECT state[2] + greatest(floor(at_epoch - state[4]), 0) / 3600.0 * growth_per_hour
                         + process_noise AS prior) p
        )
    END
$$;

CREATE AGGREGATE legacy_migration.kalman(float8, float8, float8, float8, float8, float8, float8, float8, float8) (
    SFUNC = legacy_migration.kalman_step,
    STYPE = float8[]
);
