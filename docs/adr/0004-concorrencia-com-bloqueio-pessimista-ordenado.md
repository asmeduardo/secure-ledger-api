# ADR 0004 — Controlar a concorrência com bloqueio pessimista em ordem de id

## Estado

Aceita.

## Contexto

Duas retiradas simultâneas podem ler o mesmo saldo e gastá-lo duas vezes. A primeira versão do serviço lia e gravava o saldo sem bloqueio; o teste `ConcurrencyIT` mostrou o problema: com saldo de 10,00, as 40 retiradas simultâneas de 1,00 foram aceitas (atualização perdida). Transferências cruzadas (A→B e B→A ao mesmo tempo) ainda podem causar deadlock se cada transação bloquear as contas em ordem diferente.

As duas alternativas foram medidas com `LockingStrategyBenchmark`, que executa a mesma retirada (bloqueio, verificação de saldo, atualização, transação e lançamentos, com os triggers ativos) nas duas estratégias. A versão otimista compara o `xmin` da linha, então o experimento não exigiu coluna de versão. Ambiente: Windows 10 com Docker Desktop (WSL 2, 4 CPUs, 4 GB), PostgreSQL 18 em Testcontainers com `fsync=off`, 8 threads, pool padrão de 10 conexões, até 20 tentativas na versão otimista; mediana de 3 rodadas após aquecimento.

| Estratégia | Cenário | Mediana (ms) | Lançadas | Recusadas por saldo | Retentativas | Abandonadas |
| --- | --- | ---: | ---: | ---: | ---: | ---: |
| Pessimista | conta disputada (400 retiradas, saldo 200) | 1127 | 200 | 200 | 0 | 0 |
| Otimista | conta disputada (400 retiradas, saldo 200) | 1884 | 200 | 177 | 1102 | 23 |
| Pessimista | 64 contas (800 retiradas) | 1057 | 800 | 0 | 0 | 0 |
| Otimista | 64 contas (800 retiradas) | 846 | 800 | 0 | 66 | 0 |

Para reproduzir: `./mvnw -pl ledger-api verify -Dit.test=LockingStrategyBenchmark -Dtest=none -Dsurefire.failIfNoSpecifiedTests=false`. Os números variam com a máquina; a forma do resultado é o que sustenta a decisão.

## Decisão

- **Bloqueio pessimista:** antes de ler os saldos, o razão executa `SELECT ... FOR NO KEY UPDATE` (`PESSIMISTIC_WRITE` do JPA) nas contas de cliente envolvidas, em uma única consulta com `ORDER BY id`. O PostgreSQL bloqueia as linhas na ordem em que a consulta as devolve, então transações que tocam as mesmas contas entram em fila em vez de se travarem.
- O saldo é verificado e atualizado sob o bloqueio, e o banco ainda garante `balance >= 0` caso algum caminho futuro ignore o bloqueio.
- A conta de liquidação não é bloqueada ([ADR 0003](0003-partidas-dobradas-garantidas-no-banco.md)).
- O estorno bloqueia as mesmas contas e só então verifica se a transação já foi estornada, de modo que estornos concorrentes produzem um `201` e respostas `409` determinísticas; `unique (reverses_id)` é a última barreira.

## Consequências

Na conta disputada, que é exatamente o risco do produto, a estratégia otimista ficou 67% mais lenta, fez 1.102 retentativas e abandonou 23 requisições que o cliente veria como erro. Com pouca disputa ela foi cerca de 20% mais rápida, mas ambas foram corretas. O bloqueio pessimista dá resultado determinístico (aceita ou recusa por saldo) e dispensa lógica de retentativa. `ConcurrencyIT` comprova que 40 débitos simultâneos sobre saldo de 10,00 resultam em exatamente 10 aceitos e 30 recusados sem saldo negativo, que 40 transferências cruzadas terminam sem deadlock e que depósitos e retiradas simultâneos de contas distintas, que compartilham a conta de liquidação, são todos aceitos com saldos corretos. Requisições sobre a mesma conta esperam umas pelas outras durante a transação, por isso as transações de lançamento devem continuar curtas.
