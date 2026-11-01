COPY (
    SELECT agency_id, agency_name, agency_url, agency_timezone, agency_lang, agency_phone, agency_fare_url
    FROM public.agencies
    WHERE config_rev = :rev
    ORDER BY agency_name
) TO STDOUT WITH (FORMAT csv, HEADER);
