COPY (
    SELECT id AS stop_id, code AS stop_code, name AS stop_name, lat AS stop_lat, lon AS stop_lon
    FROM public.stops
    WHERE config_rev = :rev
    ORDER BY id
) TO STDOUT WITH (FORMAT csv, HEADER);
