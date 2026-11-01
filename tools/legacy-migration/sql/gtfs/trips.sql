-- TheTransitClock keeps one row per (trip_id, start_time): exact_times frequency trips are expanded
-- into copies. GTFS wants the template trip once; frequencies.txt re-expands it.
COPY (
    SELECT DISTINCT ON (t.trip_id)
           t.route_id, t.service_id, t.trip_id,
           t.headsign AS trip_headsign,
           t.trip_short_name,
           t.direction_id,
           t.block_id,
           CASE WHEN EXISTS (SELECT 1 FROM public.trip_patterns tp
                             WHERE tp.config_rev = :rev AND tp.shape_id = t.shape_id)
                THEN t.shape_id END AS shape_id
    FROM public.trips t
    WHERE t.config_rev = :rev
    ORDER BY t.trip_id, t.start_time
) TO STDOUT WITH (FORMAT csv, HEADER);
