# ADR 0006 — Exigir chave de idempotência por cliente em toda escrita

## Estado

Aceita.

## Contexto

Integrações repetem requisições depois de timeout ou queda de conexão. Sem proteção, cada repetição de uma transferência move o dinheiro outra vez. O padrão adotado por APIs de pagamento, descrito na especificação em elaboração no IETF (`Idempotency-Key`), é o cliente enviar uma chave única por tentativa de operação. No projeto irmão OrderFlow, a primeira versão usava chaves globais, e um teste mostrou um cliente recebendo o pedido de outro ao repetir a mesma chave.

## Decisão

- Toda escrita (abrir conta, depósito, retirada, transferência e estorno) exige o cabeçalho `Idempotency-Key` (8 a 100 caracteres: letras, dígitos, `.`, `_`, `:` ou `-`).
- A chave pertence ao cliente: a chave primária de `idempotency.idempotency_record` é `(client_id, idempotency_key)`, com `client_id` vindo do `sub` do token validado, nunca do corpo.
- A tabela guarda o SHA-256 de uma forma canônica da requisição, que inclui o nome da operação. Zeros à direita nos valores não mudam a impressão digital; valor ausente difere de valor vazio.
- **Mesma chave e mesmo conteúdo:** devolve a resposta gravada (JSONB) com o mesmo status `201`, o mesmo `Location`, o mesmo corpo e `Idempotent-Replayed: true`, sem novo efeito.
- **Mesma chave com outro conteúdo ou outra operação:** `422` com o tipo `urn:secure-ledger:problem:idempotency-key-reused`.
- **Concorrência:** a linha da chave é inserida antes do efeito, na mesma transação. Uma requisição simultânea com a mesma chave espera na chave primária até a primeira terminar, falha por duplicidade, tem a transação desfeita e responde com a resposta gravada pela vencedora.
- **Recusas não consomem a chave:** só respostas de sucesso são gravadas. Uma requisição recusada por regra de negócio (por exemplo, saldo insuficiente) não teve efeito, e a mesma chave pode ser usada de novo depois.

## Consequências

`IdempotencyIT` comprova a repetição idêntica, a recusa de outro conteúdo e de outra operação, o isolamento entre clientes, 12 requisições simultâneas com a mesma chave gerando um único lançamento, a chave obrigatória e a reutilização após recusa; `IdempotencyTest` cobre com Mockito a ordem reservar → executar → gravar e a corrida perdida. A resposta gravada não muda se o recurso mudar depois (o saldo de uma conta, por exemplo), porque a repetição devolve exatamente a resposta original. As chaves não expiram; uma política de retenção poderá ser criada se o volume justificar.
