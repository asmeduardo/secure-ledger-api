-- Double-entry ledger: accounts, transactions and their entries.
create schema ledger;

-- A customer account keeps a materialized balance (the sum of its entries, checked by the
-- reconciliation) that can never be negative. A settlement account is the counterpart of money
-- entering or leaving the ledger; it keeps no balance of its own, so that deposits and
-- withdrawals of one currency do not all wait for a lock on the same row.
create table ledger.ledger_account (
    id         uuid        primary key,
    currency   varchar(3)  not null,
    type       varchar(20) not null,
    balance    numeric(19, 2),
    created_at timestamptz not null,
    constraint ck_ledger_account_currency check (currency in ('BRL', 'USD', 'EUR')),
    constraint ck_ledger_account_type check (type in ('CUSTOMER', 'SETTLEMENT')),
    constraint ck_ledger_account_balance check (
        (type = 'CUSTOMER' and balance is not null and balance >= 0)
        or (type = 'SETTLEMENT' and balance is null)),
    -- Target of the composite foreign key that keeps each entry in the currency of its account.
    constraint uk_ledger_account_currency unique (id, currency)
);

create unique index uk_ledger_account_settlement on ledger.ledger_account (currency) where type = 'SETTLEMENT';

-- One settlement account per supported currency; the last digits are the ISO 4217 numeric code.
insert into ledger.ledger_account (id, currency, type, balance, created_at) values
    ('00000000-0000-4000-8000-000000000986', 'BRL', 'SETTLEMENT', null, now()),
    ('00000000-0000-4000-8000-000000000840', 'USD', 'SETTLEMENT', null, now()),
    ('00000000-0000-4000-8000-000000000978', 'EUR', 'SETTLEMENT', null, now());

create table ledger.ledger_transaction (
    id           uuid         primary key,
    type         varchar(20)  not null,
    currency     varchar(3)   not null,
    initiated_by varchar(100) not null,
    reference    varchar(140),
    reverses_id  uuid,
    created_at   timestamptz  not null,
    constraint ck_ledger_transaction_type check (type in ('DEPOSIT', 'WITHDRAWAL', 'TRANSFER', 'REVERSAL')),
    constraint ck_ledger_transaction_currency check (currency in ('BRL', 'USD', 'EUR')),
    constraint ck_ledger_transaction_initiator check (length(btrim(initiated_by)) > 0),
    constraint ck_ledger_transaction_reversal check ((type = 'REVERSAL') = (reverses_id is not null)),
    constraint fk_ledger_transaction_reverses foreign key (reverses_id) references ledger.ledger_transaction (id),
    -- A transaction is reversed at most once.
    constraint uk_ledger_transaction_reverses unique (reverses_id),
    constraint uk_ledger_transaction_currency unique (id, currency)
);

-- Signed amounts: a credit is positive and raises the balance of the account, a debit is negative.
-- The identity column gives every entry of an account a position that only grows, because entries
-- of a customer account are inserted while its row is locked; statements page on it.
create table ledger.entry (
    id             bigint generated always as identity primary key,
    transaction_id uuid           not null,
    account_id     uuid           not null,
    currency       varchar(3)     not null,
    amount         numeric(19, 2) not null,
    balance_after  numeric(19, 2),
    created_at     timestamptz    not null,
    constraint ck_entry_amount check (amount <> 0),
    constraint ck_entry_balance_after check (balance_after >= 0),
    constraint fk_entry_transaction foreign key (transaction_id, currency)
        references ledger.ledger_transaction (id, currency),
    constraint fk_entry_account foreign key (account_id, currency)
        references ledger.ledger_account (id, currency),
    constraint uk_entry_transaction_account unique (transaction_id, account_id)
);

create index ix_entry_account on ledger.entry (account_id, id);
