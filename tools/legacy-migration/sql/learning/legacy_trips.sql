-- Legacy side: each trip's pattern and scheduled start (the earliest copy of an exact_times trip).
COPY (
    SELECT DISTINCT ON (config_rev, trip_id)
           config_rev, trip_id, coalesce(trippattern_config_rev, config_rev), trippattern_id, start_time
    FROM public.trips
    WHERE config_rev IN (:revs) AND trippattern_id IS NOT NULL
    ORDER BY config_rev, trip_id, start_time
) TO STDOUT WITH (FORMAT csv);
