-- Legacy side: every trip pattern's ordered stops, to match it to a pattern of the new app.
COPY (
    SELECT tp.config_rev, tp.id, tp.route_id, p.list_index, sp.stop_id
    FROM public.trip_patterns tp
    JOIN public.trip_pattern_to_path p
      ON p.trip_pattern_id = tp.id AND p.trip_pattern_config_rev = tp.config_rev
    JOIN public.stop_paths sp
      ON sp.config_rev = p.stop_path_config_rev
     AND sp.stop_path_id = p.stop_path_stop_path_id
     AND sp.trip_pattern_id = p.stop_path_trip_pattern_id
    WHERE tp.config_rev IN (:revs)
) TO STDOUT WITH (FORMAT csv);
