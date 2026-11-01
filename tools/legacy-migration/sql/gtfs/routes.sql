-- TheTransitClock doesn't keep routes.agency_id; it's only required when there are several agencies.
COPY (
    SELECT r.id AS route_id,
           (SELECT max(a.agency_id) FROM public.agencies a WHERE a.config_rev = :rev
            HAVING count(*) = 1) AS agency_id,
           r.short_name AS route_short_name,
           CASE WHEN r.short_name IS NULL AND r.long_name IS NULL THEN r.name ELSE r.long_name END AS route_long_name,
           r.description AS route_desc,
           r.type AS route_type,
           r.color AS route_color,
           r.text_color AS route_text_color,
           r.route_order AS route_sort_order
    FROM public.routes r
    WHERE r.config_rev = :rev
    ORDER BY r.id
) TO STDOUT WITH (FORMAT csv, HEADER);
