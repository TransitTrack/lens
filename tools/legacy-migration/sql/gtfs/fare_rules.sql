-- TheTransitClock stores absent optional ids as '' (they're part of its primary key).
COPY (
    SELECT fare_id,
           nullif(route_id, '') AS route_id,
           nullif(origin_id, '') AS origin_id,
           nullif(destination_id, '') AS destination_id,
           nullif(contains_id, '') AS contains_id
    FROM public.fare_rules
    WHERE config_rev = :rev
    ORDER BY fare_id, route_id, origin_id, destination_id, contains_id
) TO STDOUT WITH (FORMAT csv, HEADER);
