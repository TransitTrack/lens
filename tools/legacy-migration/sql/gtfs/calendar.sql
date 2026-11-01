COPY (
    SELECT service_id,
           monday::int AS monday, tuesday::int AS tuesday, wednesday::int AS wednesday,
           thursday::int AS thursday, friday::int AS friday, saturday::int AS saturday, sunday::int AS sunday,
           to_char(start_date, 'YYYYMMDD') AS start_date,
           to_char(end_date, 'YYYYMMDD') AS end_date
    FROM public.calendars
    WHERE config_rev = :rev
    ORDER BY service_id, start_date
) TO STDOUT WITH (FORMAT csv, HEADER);
