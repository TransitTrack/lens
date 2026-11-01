-- Legacy side: the config revisions arrivals/departures in the window were recorded against.
SELECT coalesce(string_agg(DISTINCT config_rev::text, ','), '')
FROM public.arrivals_departures
WHERE "time" >= :'since'::timestamp AND "time" < :'until'::timestamp
  AND config_rev IS NOT NULL;
