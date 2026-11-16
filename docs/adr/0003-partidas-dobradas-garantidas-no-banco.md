# ADR 0003 — Garantir as partidas dobradas no domínio e no banco

## Estado

Aceita.

## Contexto

Toda transação precisa ter pelo menos dois lançamentos na mesma moeda cuja soma seja zero, e cada lançamento precisa estar na moeda da sua conta. Validar só no Java protege o caminho feliz, mas não um script de correção, uma migração malfeita ou um bug futuro que grave direto na tabela. Uma restrição `CHECK` do PostgreSQL vale apenas para uma linha, e a regra da soma envolve várias.

Alternativas consideradas para a soma zero:

- **Guardar os lançamentos em JSON dentro da transação** com um `CHECK`: perde integridade referencial e consultas por conta.
- **Trigger comum `AFTER INSERT`**: dispararia no primeiro lançamento, quando a transação ainda está incompleta.
- **Constraint trigger adiado para o commit** (`DEFERRABLE INITIALLY DEFERRED`): julga o conjunto só quando ele está completo.

## Decisão

- **Domínio:** `LedgerTransaction.post` rejeita menos de dois lançamentos, valor zero, conta repetida, moedas diferentes e soma diferente de zero; `Money` usa `BigDecimal` na escala da moeda, com moeda explícita, e nunca arredonda (um valor com casas a mais é recusado). As moedas aceitas são BRL, USD e EUR, todas com duas casas, como as colunas `numeric(19, 2)`.
- **Banco, soma e quantidade:** constraint triggers adiados em `ledger.entry` e `ledger.ledger_transaction` verificam no commit que cada transação tem dois ou mais lançamentos somando zero. O mesmo trigger recusa lançamentos acrescentados depois a uma transação já confirmada (comparando o `xmin` das linhas), porque isso mudaria uma transação fechada.
- **Banco, moeda:** o lançamento guarda a moeda e tem duas chaves estrangeiras compostas, `(account_id, currency)` para a conta e `(transaction_id, currency)` para a transação. A moeda fica redundante na linha, mas a coerência passa a ser declarativa.
- **Saldo:** a conta de cliente guarda o saldo materializado (`CHECK balance >= 0`) e cada lançamento guarda `balance_after`. O saldo é atualizado na mesma transação dos lançamentos, sob bloqueio ([ADR 0004](0004-concorrencia-com-bloqueio-pessimista-ordenado.md)), e conferido contra a soma dos lançamentos pela reconciliação ([ADR 0008](0008-auditoria-append-only-e-reconciliacao.md)).
- **Conta de liquidação:** depósitos e retiradas têm como contrapartida uma conta de liquidação por moeda (`SETTLEMENT`), criada pela migração. Ela não guarda saldo (é derivado dos lançamentos) e não é bloqueada; assim, depósitos e retiradas de contas diferentes não disputam a mesma linha.

## Consequências

`LedgerInvariantsIT` e `LedgerSchemaIT` gravam SQL diretamente e mostram o banco recusando transação desbalanceada, transação sem lançamentos, lançamento tardio, moeda divergente, valor zero e saldo negativo. `DoubleEntryPropertiesTest` gera 500 casos aleatórios (com semente registrada) para cada uma de quatro propriedades. Os constraint triggers executam uma consulta por linha inserida no commit; para transações de dois ou três lançamentos o custo é pequeno. Aceitar uma moeda com três casas decimais exigirá nova migração das colunas monetárias.
