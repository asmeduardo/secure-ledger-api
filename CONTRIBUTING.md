# Contribuição

Toda alteração deve começar por um problema e por critérios de aceite verificáveis. Código, testes, migrações e documentação pertencem à mesma entrega quando tratam a mesma mudança.

## Como trabalhar

- Escreva o teste antes da implementação e veja-o falhar pelo motivo esperado; erro de compilação não conta como falha.
- Todo bug recebe um teste de regressão na mesma alteração.
- Alterações de schema entram como nova migração Flyway em `ledger-api/src/main/resources/db/migration`; migrações já publicadas não são editadas.
- Regras contábeis ficam no domínio e, quando possível, também no banco ([ADR 0003](docs/adr/0003-partidas-dobradas-garantidas-no-banco.md)). Lançamentos nunca são alterados: correções são estornos.

## Comandos

```bash
./mvnw -pl ledger-api verify -Dit.test=NomeDoTesteIT   # um teste de integração durante o desenvolvimento
./mvnw -pl ledger-api test -Dtest=NomeDoTeste          # um teste unitário
./mvnw spotless:apply                                  # corrige a formatação
./mvnw verify                                          # formatação, unitários e integração (o mesmo do CI)
```

Os testes de integração precisam do Docker em execução.

## Antes do pull request

1. execute `./mvnw verify` uma vez ao final;
2. confirme que nenhum segredo, token ou dado pessoal foi incluído;
3. descreva os riscos e as evidências no pull request.
