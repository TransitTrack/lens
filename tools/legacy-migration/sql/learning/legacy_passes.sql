-- Legacy side: stop passes as the new app defines them - leaving the first stop (its departure),
-- reaching every other stop (its arrival).
COPY (
    SELECT vehicle_id, config_rev, trip_id, freq_start_time, stop_path_index, stop_id, "time"
    FROM public.arrivals_departures
    WHERE "time" >= :'since'::timestamp AND "time" < :'until'::timestamp
      AND config_rev IS NOT NULL AND stop_path_index IS NOT NULL
      AND ((stop_path_index = 0 AND NOT is_arrival) OR (stop_path_index > 0 AND is_arrival))
) TO STDOUT WITH (FORMAT csv);
