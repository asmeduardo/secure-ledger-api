-- Customer accounts as the API clients see them: who owns each one and what it is called. The money
-- lives in ledger.ledger_account under the same id; modules do not join each other's tables.
create schema accounts;

create table accounts.customer_account (
    id         uuid         primary key,
    -- Subject (sub) of the access token of the client that opened the account.
    owner_id   varchar(100) not null,
    name       varchar(80)  not null,
    created_at timestamptz  not null,
    constraint ck_customer_account_owner check (length(btrim(owner_id)) > 0),
    constraint ck_customer_account_name check (length(btrim(name)) > 0)
);

create index ix_customer_account_owner on accounts.customer_account (owner_id, created_at, id);
