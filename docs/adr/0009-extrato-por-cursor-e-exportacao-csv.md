# ADR 0009 — Paginar o extrato por cursor e exportar em CSV

## Estado

Aceita.

## Contexto

O extrato é lido do mais recente para o mais antigo, e novos lançamentos chegam enquanto o cliente pagina. Com paginação por número de página (`offset`), cada lançamento novo empurra os demais: a página 2 repete itens da página 1 e o cliente pode concluir que houve movimentos em dobro. A exportação precisa cobrir um período inteiro sem segurar uma transação aberta durante o download.

## Decisão

- `GET /api/v1/accounts/{id}/entries` pagina por **cursor** (keyset): a resposta tem `items` e `nextCursor`, um valor opaco (Base64 de `v1:<id do lançamento>`) que o cliente devolve sem interpretar. Cada página continua estritamente abaixo do último id já devolvido.
- A ordem é estável porque os lançamentos são append-only ([ADR 0005](0005-lancamentos-imutaveis-e-estorno.md)) e os de uma conta de cliente são inseridos com a linha da conta bloqueada ([ADR 0004](0004-concorrencia-com-bloqueio-pessimista-ordenado.md)): um lançamento novo sempre recebe id maior que os já confirmados daquela conta. O índice `(account_id, id)` atende a consulta. Cursor malformado responde `400 invalid-cursor`.
- A trilha de auditoria usa o mesmo contrato. A listagem de contas continua paginada por número de página, porque é pequena e não cresce durante a leitura.
- `GET /api/v1/accounts/{id}/statement.csv?from=&to=` exporta o período `[from, to)` (até 366 dias) em CSV RFC 4180, do mais antigo para o mais recente, com linha de cabeçalho. O id do lançamento mais recente é fixado no início e a leitura avança em páginas de 500, cada uma em transação curta; como o histórico não muda, as páginas formam um extrato consistente.
- Células de texto que uma planilha executaria como fórmula (iniciadas por `=`, `+`, `-`, `@` ou caractere de controle) recebem um apóstrofo no início; o valor monetário é exportado sem sinal, com a direção em coluna própria.

## Consequências

`StatementApiIT` lança novos movimentos entre uma página e outra e mostra que nenhuma linha se repete ou some; `StatementExportIT` cobre o conteúdo, o período e a neutralização de fórmulas; `StatementExportTest` cobre a paginação interna da exportação. O cliente não consegue pular para uma página arbitrária nem saber o total de itens; para um extrato, avançar e exportar por período cobre o uso esperado.
