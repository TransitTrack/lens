COPY (
    SELECT service_id, to_char(date, 'YYYYMMDD') AS date, exception_type
    FROM public.calendar_dates
    WHERE config_rev = :rev
    ORDER BY service_id, date
) TO STDOUT WITH (FORMAT csv, HEADER);
