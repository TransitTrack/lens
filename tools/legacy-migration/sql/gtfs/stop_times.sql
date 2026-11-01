-- stop_times.txt isn't stored by TheTransitClock: rebuild it from the trip's pattern (one stop path per
-- stop, in order) and its scheduled-times list (same list_index). A stop the legacy schedule kept no
-- time for is exported untimed; a stop with only an arrival or only a departure gets it for both.
COPY (
    WITH trip AS (
        SELECT DISTINCT ON (trip_id)
               trip_id, start_time, trippattern_id, coalesce(trippattern_config_rev, config_rev) AS pattern_rev
        FROM public.trips
        WHERE config_rev = :rev
        ORDER BY trip_id, start_time
    )
    SELECT t.trip_id,
           pg_temp.hms(coalesce(s.arrival_time, s.departure_time)) AS arrival_time,
           pg_temp.hms(coalesce(s.departure_time, s.arrival_time)) AS departure_time,
           sp.stop_id,
           coalesce(sp.gtfs_stop_seq, p.list_index) AS stop_sequence
    FROM trip t
    JOIN public.trip_pattern_to_path p
      ON p.trip_pattern_id = t.trippattern_id AND p.trip_pattern_config_rev = t.pattern_rev
    JOIN public.stop_paths sp
      ON sp.config_rev = p.stop_path_config_rev
     AND sp.stop_path_id = p.stop_path_stop_path_id
     AND sp.trip_pattern_id = p.stop_path_trip_pattern_id
    LEFT JOIN public.trip_scheduled_times_list s
      ON s.trip_config_rev = :rev
     AND s.trip_trip_id = t.trip_id
     AND s.trip_start_time = t.start_time
     AND s.list_index = p.list_index
    ORDER BY t.trip_id, p.list_index
) TO STDOUT WITH (FORMAT csv, HEADER);
