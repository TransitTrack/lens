-- shapes.txt isn't stored by TheTransitClock either: rebuild each shape from the stop-path geometry
-- of the longest trip pattern using it, dropping the duplicated point where two stop paths meet.
COPY (
    WITH pattern AS (
        SELECT DISTINCT ON (tp.shape_id) tp.shape_id, tp.id, tp.config_rev
        FROM public.trip_patterns tp
        JOIN public.trip_pattern_to_path p
          ON p.trip_pattern_id = tp.id AND p.trip_pattern_config_rev = tp.config_rev
        WHERE tp.config_rev = :rev AND tp.shape_id IS NOT NULL
        GROUP BY tp.shape_id, tp.id, tp.config_rev
        ORDER BY tp.shape_id, count(*) DESC, tp.id
    ),
    point AS (
        SELECT pt.shape_id, l.lat, l.lon, p.list_index AS path_index, l.list_index AS point_index,
               lag(l.lat) OVER w AS prev_lat, lag(l.lon) OVER w AS prev_lon
        FROM pattern pt
        JOIN public.trip_pattern_to_path p
          ON p.trip_pattern_id = pt.id AND p.trip_pattern_config_rev = pt.config_rev
        JOIN public.stoppath_locations l
          ON l.stoppath_config_rev = p.stop_path_config_rev
         AND l.stoppath_stop_path_id = p.stop_path_stop_path_id
         AND l.stoppath_trip_pattern_id = p.stop_path_trip_pattern_id
        WINDOW w AS (PARTITION BY pt.shape_id ORDER BY p.list_index, l.list_index)
    )
    SELECT shape_id, lat AS shape_pt_lat, lon AS shape_pt_lon,
           row_number() OVER (PARTITION BY shape_id ORDER BY path_index, point_index) AS shape_pt_sequence
    FROM point
    WHERE lat IS NOT NULL AND lon IS NOT NULL
      AND (prev_lat IS DISTINCT FROM lat OR prev_lon IS DISTINCT FROM lon)
    ORDER BY shape_id, shape_pt_sequence
) TO STDOUT WITH (FORMAT csv, HEADER);
