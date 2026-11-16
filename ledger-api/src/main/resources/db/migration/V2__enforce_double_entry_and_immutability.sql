-- Rules that span several rows cannot be CHECK constraints. They are enforced by constraint
-- triggers deferred to the commit, so the header and the entries of a transaction can be inserted
-- one by one, and the whole set is judged once it is complete.

create function ledger.check_transaction_balanced() returns trigger
    language plpgsql as $$
declare
    tx_id          uuid;
    header_version text;
    entry_count    integer;
    entry_sum      numeric;
begin
    if tg_table_name = 'entry' then
        tx_id := new.transaction_id;
    else
        tx_id := new.id;
    end if;

    select xmin::text into header_version from ledger.ledger_transaction where id = tx_id;
    select count(*), coalesce(sum(amount), 0) into entry_count, entry_sum
      from ledger.entry e where e.transaction_id = tx_id;

    if entry_count < 2 then
        raise exception 'Ledger transaction % has % entries; at least two entries are required',
            tx_id, entry_count
            using errcode = 'check_violation', constraint = 'ck_ledger_transaction_entries';
    end if;
    if entry_sum <> 0 then
        raise exception 'Ledger transaction % is not balanced: its entries sum to %', tx_id, entry_sum
            using errcode = 'check_violation', constraint = 'ck_ledger_transaction_balanced';
    end if;
    -- Rows written by one database transaction share its id (xmin). An entry with another xmin was
    -- added after the transaction had been posted, which would change a closed transaction.
    if exists (select 1 from ledger.entry e
                where e.transaction_id = tx_id
                  and e.xmin::text <> header_version) then
        raise exception 'Entries of ledger transaction % must be posted together with it', tx_id
            using errcode = 'check_violation', constraint = 'ck_ledger_transaction_closed';
    end if;
    return null;
end;
$$;

create constraint trigger tg_ledger_transaction_balanced
    after insert on ledger.ledger_transaction
    deferrable initially deferred
    for each row execute function ledger.check_transaction_balanced();

create constraint trigger tg_entry_balanced
    after insert on ledger.entry
    deferrable initially deferred
    for each row execute function ledger.check_transaction_balanced();

-- Posted transactions and entries are append-only for every role, the table owner included.
-- A mistake is corrected by posting a reversal.
create function ledger.reject_change() returns trigger
    language plpgsql as $$
begin
    raise exception '%.% is append-only: % is not allowed; post a reversal instead',
        tg_table_schema, tg_table_name, tg_op
        using errcode = 'restrict_violation';
end;
$$;

create trigger tg_entry_append_only
    before update or delete on ledger.entry
    for each row execute function ledger.reject_change();

create trigger tg_entry_no_truncate
    before truncate on ledger.entry
    for each statement execute function ledger.reject_change();

create trigger tg_ledger_transaction_append_only
    before update or delete on ledger.ledger_transaction
    for each row execute function ledger.reject_change();

create trigger tg_ledger_transaction_no_truncate
    before truncate on ledger.ledger_transaction
    for each statement execute function ledger.reject_change();
