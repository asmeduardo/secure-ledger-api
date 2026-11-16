# ADR 0005 — Tornar lançamentos imutáveis e corrigir apenas por estorno

## Estado

Aceita.

## Contexto

Um razão só é confiável se o histórico não puder ser reescrito. Se um lançamento errado pudesse ser alterado ou apagado, o saldo deixaria de ser explicável e a auditoria perderia valor. Proteger só na aplicação (entidades sem setters, colunas `updatable = false`) não impede um `UPDATE` manual.

Alternativas consideradas no banco:

- **`REVOKE UPDATE, DELETE`:** não vale para o dono da tabela, que é o usuário das migrações e, neste repositório, também o da aplicação.
- **Triggers que rejeitam a operação:** valem para qualquer papel, inclusive o dono.

## Decisão

- Triggers `BEFORE UPDATE OR DELETE` (por linha) e `BEFORE TRUNCATE` (por comando) em `ledger.entry`, `ledger.ledger_transaction` e `audit.audit_event` abortam a operação com uma mensagem que orienta a postar um estorno.
- No código, `Entry` e `LedgerTransaction` não têm métodos de alteração e todas as colunas são `updatable = false`; o saldo da conta é a única coluna monetária atualizável, e ele é conferido pela reconciliação.
- A correção é o **estorno**: `POST /api/v1/transactions/{id}/reversal` posta uma transação `REVERSAL` com os lançamentos opostos, ligada à original por `reverses_id`. Uma transação é estornada no máximo uma vez (`unique (reverses_id)`), um estorno não pode ser estornado e o estorno respeita o saldo (não pode deixar conta negativa). Só o dono de todas as contas de cliente da transação pode estorná-la.

## Consequências

`LedgerInvariantsIT` e `AuditTrailIT` comprovam que `UPDATE`, `DELETE` e `TRUNCATE` são recusados; `ReversalApiIT` cobre o estorno, o duplo estorno (inclusive concorrente) e o estorno que deixaria saldo negativo. Limite conhecido: um papel com posse da tabela ou superusuário ainda pode desabilitar os triggers (`ALTER TABLE ... DISABLE TRIGGER` ou `session_replication_role`), como o próprio `ReconciliationIT` faz para injetar uma divergência. Em produção, a aplicação deveria usar um papel sem posse das tabelas e sem privilégio de `UPDATE`/`DELETE` nelas, separado do papel das migrações; essa separação não está implementada neste repositório. A reconciliação existe justamente para detectar o que passar por essa brecha.
