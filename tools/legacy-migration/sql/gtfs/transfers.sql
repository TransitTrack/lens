COPY (
    SELECT from_stop_id, to_stop_id, transfer_type, min_transfer_time
    FROM public.transfers
    WHERE config_rev = :rev
    ORDER BY from_stop_id, to_stop_id
) TO STDOUT WITH (FORMAT csv, HEADER);
