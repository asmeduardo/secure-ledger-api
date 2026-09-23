# Roadmap

## Fase 1 — domínio

- [ ] definir tipos monetários e moedas aceitas;
- [ ] modelar contas, transações e lançamentos;
- [ ] implementar e testar a partida dobrada;
- [ ] criar testes de propriedade para as invariantes.

## Fase 2 — persistência e API

- [ ] configurar PostgreSQL e Flyway;
- [ ] persistir transações e lançamentos atomicamente;
- [ ] criar API REST com DTOs e `ProblemDetail`;
- [ ] documentar contratos com OpenAPI;
- [ ] testar o banco com Testcontainers.

## Fase 3 — confiabilidade

- [ ] implementar chaves de idempotência;
- [ ] testar requisições duplicadas e conflitantes;
- [ ] implementar o controle de concorrência;
- [ ] executar testes simultâneos de débito;
- [ ] criar reconciliação e auditoria.

## Fase 4 — segurança e operação

- [ ] integrar OAuth 2.0/OIDC;
- [ ] aplicar autorização por escopo e propriedade;
- [ ] adicionar métricas e logs estruturados;
- [ ] criar Docker Compose;
- [ ] configurar CI, análise estática e auditoria de dependências;
- [ ] publicar a demonstração e a documentação de uso.
