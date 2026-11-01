-- Session-local helpers for the GTFS export queries; pg_temp needs no write access to the legacy DB.

-- Seconds past service-day midnight as GTFS HH:MM:SS (hours may exceed 24).
CREATE FUNCTION pg_temp.hms(secs integer) RETURNS text
LANGUAGE sql IMMUTABLE AS $$
    SELECT CASE WHEN secs IS NULL THEN NULL ELSE
        lpad((secs / 3600)::text, greatest(2, length((secs / 3600)::text)), '0') || ':' ||
        lpad((secs % 3600 / 60)::text, 2, '0') || ':' ||
        lpad((secs % 60)::text, 2, '0')
    END
$$;
