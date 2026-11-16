-- Fictitious demonstration data, loaded only with the "demo" profile. No real person, company or
-- money is represented. Initial balances are deposits from the settlement account, so the demo
-- ledger obeys the same rules as any other and reconciles.

insert into accounts.customer_account (id, owner_id, name, created_at) values
    ('a1000000-0000-4000-8000-000000000001', 'demo-wallet', 'Carteira da Ana (ficticia)', now()),
    ('a1000000-0000-4000-8000-000000000002', 'demo-wallet', 'Carteira do Bruno (ficticia)', now() + interval '1 millisecond'),
    ('b2000000-0000-4000-8000-000000000001', 'demo-marketplace', 'Repasses do marketplace (ficticio)', now());

insert into ledger.ledger_account (id, currency, type, balance, created_at) values
    ('a1000000-0000-4000-8000-000000000001', 'BRL', 'CUSTOMER', 1000.00, now()),
    ('a1000000-0000-4000-8000-000000000002', 'BRL', 'CUSTOMER', 250.00, now()),
    ('b2000000-0000-4000-8000-000000000001', 'BRL', 'CUSTOMER', 5000.00, now());

insert into ledger.ledger_transaction (id, type, currency, initiated_by, reference, created_at) values
    ('d0000000-0000-4000-8000-000000000001', 'DEPOSIT', 'BRL', 'system:demo-seed', 'Saldo inicial ficticio', now()),
    ('d0000000-0000-4000-8000-000000000002', 'DEPOSIT', 'BRL', 'system:demo-seed', 'Saldo inicial ficticio', now()),
    ('d0000000-0000-4000-8000-000000000003', 'DEPOSIT', 'BRL', 'system:demo-seed', 'Saldo inicial ficticio', now());

insert into ledger.entry (transaction_id, account_id, currency, amount, balance_after, created_at) values
    ('d0000000-0000-4000-8000-000000000001', '00000000-0000-4000-8000-000000000986', 'BRL', -1000.00, null, now()),
    ('d0000000-0000-4000-8000-000000000001', 'a1000000-0000-4000-8000-000000000001', 'BRL', 1000.00, 1000.00, now()),
    ('d0000000-0000-4000-8000-000000000002', '00000000-0000-4000-8000-000000000986', 'BRL', -250.00, null, now()),
    ('d0000000-0000-4000-8000-000000000002', 'a1000000-0000-4000-8000-000000000002', 'BRL', 250.00, 250.00, now()),
    ('d0000000-0000-4000-8000-000000000003', '00000000-0000-4000-8000-000000000986', 'BRL', -5000.00, null, now()),
    ('d0000000-0000-4000-8000-000000000003', 'b2000000-0000-4000-8000-000000000001', 'BRL', 5000.00, 5000.00, now());

insert into audit.audit_event (occurred_at, actor, operation, outcome, resource_id, request) values
    (now(), 'system:demo-seed', 'OPEN_ACCOUNT', 'SUCCEEDED', 'a1000000-0000-4000-8000-000000000001', '{"currency": "BRL"}'),
    (now(), 'system:demo-seed', 'OPEN_ACCOUNT', 'SUCCEEDED', 'a1000000-0000-4000-8000-000000000002', '{"currency": "BRL"}'),
    (now(), 'system:demo-seed', 'OPEN_ACCOUNT', 'SUCCEEDED', 'b2000000-0000-4000-8000-000000000001', '{"currency": "BRL"}'),
    (now(), 'system:demo-seed', 'DEPOSIT', 'SUCCEEDED', 'd0000000-0000-4000-8000-000000000001',
        '{"accountId": "a1000000-0000-4000-8000-000000000001", "amount": "1000.00", "currency": "BRL"}'),
    (now(), 'system:demo-seed', 'DEPOSIT', 'SUCCEEDED', 'd0000000-0000-4000-8000-000000000002',
        '{"accountId": "a1000000-0000-4000-8000-000000000002", "amount": "250.00", "currency": "BRL"}'),
    (now(), 'system:demo-seed', 'DEPOSIT', 'SUCCEEDED', 'd0000000-0000-4000-8000-000000000003',
        '{"accountId": "b2000000-0000-4000-8000-000000000001", "amount": "5000.00", "currency": "BRL"}');
