COPY (
    SELECT trip_id,
           pg_temp.hms(start_time) AS start_time,
           pg_temp.hms(end_time) AS end_time,
           headway_secs,
           exact_times::int AS exact_times
    FROM public.frequencies
    WHERE config_rev = :rev
    ORDER BY trip_id, start_time
) TO STDOUT WITH (FORMAT csv, HEADER);
