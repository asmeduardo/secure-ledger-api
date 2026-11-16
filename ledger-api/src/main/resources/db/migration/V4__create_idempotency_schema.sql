-- One row per write request that succeeded. The key belongs to the client that sent it (the
-- subject of its access token), so two clients may choose the same key without ever seeing each
-- other's responses. The row is inserted before the effect, in the same transaction: a concurrent
-- request with the same key waits on the primary key and then replays the stored response.
create schema idempotency;

create table idempotency.idempotency_record (
    client_id           varchar(100) not null,
    idempotency_key     varchar(100) not null,
    operation           varchar(40)  not null,
    request_fingerprint varchar(64)  not null,
    -- Written in the same transaction as the insert, after the effect, so every committed row has it.
    response_body       jsonb,
    created_at          timestamptz  not null,
    constraint pk_idempotency_record primary key (client_id, idempotency_key),
    constraint ck_idempotency_record_key check (idempotency_key ~ '^[A-Za-z0-9._:-]{8,100}$'),
    constraint ck_idempotency_record_fingerprint check (request_fingerprint ~ '^[0-9a-f]{64}$')
);
