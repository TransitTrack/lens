COPY (
    SELECT fare_id, price, currency_type, payment_method, transfers, transfer_duration
    FROM public.fare_attributes
    WHERE config_rev = :rev
    ORDER BY fare_id
) TO STDOUT WITH (FORMAT csv, HEADER);
