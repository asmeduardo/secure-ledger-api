-- Operational audit trail: who asked for what, when, and how it ended. Successful writes are
-- recorded in the same transaction as their effect, so no effect exists without its event.
create schema audit;

create table audit.audit_event (
    id              bigint generated always as identity primary key,
    occurred_at     timestamptz  not null,
    -- Subject (sub) of the access token, or system:<job> for scheduled work.
    actor           varchar(100) not null,
    operation       varchar(40)  not null,
    outcome         varchar(20)  not null,
    -- Account or transaction created (or replayed) by the request.
    resource_id     uuid,
    idempotency_key varchar(100),
    -- Problem code of the rule that refused the request.
    reason          varchar(60),
    -- Identifiers and amounts of the request; free text such as names or references is left out.
    request         jsonb        not null default '{}'::jsonb,
    constraint ck_audit_event_outcome check (outcome in ('SUCCEEDED', 'REPLAYED', 'REJECTED')),
    constraint ck_audit_event_reason check ((outcome = 'REJECTED') = (reason is not null))
);

create index ix_audit_event_actor on audit.audit_event (actor, id);

create function audit.reject_change() returns trigger
    language plpgsql as $$
begin
    raise exception '%.% is append-only: % is not allowed', tg_table_schema, tg_table_name, tg_op
        using errcode = 'restrict_violation';
end;
$$;

create trigger tg_audit_event_append_only
    before update or delete on audit.audit_event
    for each row execute function audit.reject_change();

create trigger tg_audit_event_no_truncate
    before truncate on audit.audit_event
    for each statement execute function audit.reject_change();
