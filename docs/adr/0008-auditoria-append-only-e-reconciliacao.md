# ADR 0008 — Registrar auditoria append-only e reconciliar o razão

## Estado

Aceita.

## Contexto

A operação precisa saber quem pediu cada escrita, quando, qual operação e como terminou, inclusive as recusas. Também precisa de uma forma de descobrir se o saldo materializado ([ADR 0003](0003-partidas-dobradas-garantidas-no-banco.md)) ainda bate com os lançamentos, mesmo depois de um script ou correção manual que tenha contornado as proteções do banco ([ADR 0005](0005-lancamentos-imutaveis-e-estorno.md)).

## Decisão

- **Trilha:** `audit.audit_event` guarda instante, ator (`sub` do token ou `system:<tarefa>`), operação, resultado (`SUCCEEDED`, `REPLAYED` ou `REJECTED`), recurso criado, chave de idempotência, código do problema que recusou e um resumo da requisição com apenas identificadores e valores. Texto livre (nome da conta, referência) fica de fora. A tabela é append-only pelos mesmos triggers dos lançamentos.
- **Atomicidade:** o sucesso é gravado na mesma transação do efeito, então não existe movimento sem evento. Repetições e recusas não têm efeito e são gravadas depois, em transação própria. O componente `AuditedWrites` combina idempotência e auditoria para todas as escritas.
- **Consulta:** cada cliente lê os próprios eventos (`GET /api/v1/audit-events`, `ledger.read`); o back office lê todos e filtra por ator (`GET /api/v1/admin/audit-events`, `ledger.audit`). Ambos paginam por cursor.
- **Reconciliação:** compara, em um snapshot `REPEATABLE READ`, o saldo materializado de cada conta de cliente com a soma dos seus lançamentos, e lista transações com menos de dois lançamentos ou soma diferente de zero. Roda sob demanda (`POST /api/v1/admin/reconciliations`) e diariamente às 03:30 UTC (`secure-ledger.reconciliation.cron`). Ela apenas reporta: corrigir é decisão de uma pessoa, por estorno.
- **Evidência de cada rodada:** um evento `RECONCILIATION` na trilha, um log estruturado (`WARN` com as contagens quando há divergência) e o gauge `ledger.reconciliation.divergences` no Prometheus.

## Consequências

`AuditTrailIT` comprova sucesso, repetição e recusa registrados com o motivo, isolamento entre clientes, paginação e imutabilidade. `ReconciliationIT` injeta duas divergências diretamente no banco — um saldo alterado por `UPDATE` e uma transação de um só lançamento gravada com os triggers desligados — e mostra cada uma no relatório. A reconciliação percorre todas as contas e transações; em um razão grande ela passaria a conferir só os lançamentos posteriores à última rodada, guardando a posição pelo id do lançamento.
