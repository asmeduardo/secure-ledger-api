# Roadmap

## Fase 1 — domínio

- [x] definir tipos monetários e moedas aceitas (`Money` em `BigDecimal` na escala da moeda; BRL, USD e EUR);
- [x] modelar contas, transações e lançamentos;
- [x] implementar e testar a partida dobrada ([ADR 0003](adr/0003-partidas-dobradas-garantidas-no-banco.md));
- [x] criar testes de propriedade para as invariantes (JUnit com geração aleatória semeada).

## Fase 2 — persistência e API

- [x] configurar PostgreSQL e Flyway;
- [x] persistir transações e lançamentos atomicamente, com as invariantes também no banco;
- [x] criar API REST com DTOs e `ProblemDetail`;
- [x] documentar contratos com OpenAPI e publicar Swagger UI;
- [x] testar o banco com Testcontainers.

## Fase 3 — confiabilidade

- [x] implementar chaves de idempotência ([ADR 0006](adr/0006-idempotencia-por-cliente-e-chave.md));
- [x] testar requisições duplicadas, conflitantes e simultâneas;
- [x] implementar o controle de concorrência ([ADR 0004](adr/0004-concorrencia-com-bloqueio-pessimista-ordenado.md));
- [x] executar testes simultâneos de débito e de transferências cruzadas;
- [x] criar reconciliação e auditoria ([ADR 0008](adr/0008-auditoria-append-only-e-reconciliacao.md));
- [x] corrigir apenas por estorno ([ADR 0005](adr/0005-lancamentos-imutaveis-e-estorno.md));
- [x] paginar o extrato por cursor e exportá-lo em CSV ([ADR 0009](adr/0009-extrato-por-cursor-e-exportacao-csv.md)).

## Fase 4 — segurança e operação

- [x] integrar OAuth 2.0 com JWT: resource server na API e servidor de autorização local para desenvolvimento ([ADR 0007](adr/0007-autorizacao-por-escopo-e-propriedade.md));
- [x] aplicar autorização por escopo e propriedade;
- [x] adicionar métricas (Actuator, Micrometer e Prometheus) e logs estruturados;
- [x] criar Docker Compose com containers sem root;
- [x] configurar CI, análise estática (CodeQL) e auditoria de dependências (dependency review e Dependabot);
- [x] carregar dados fictícios e escrever um roteiro de avaliação local de poucos minutos;
- [ ] publicar a demonstração em servidor com dados descartáveis (depende de aprovação do dono do repositório).

## Próximos passos (não iniciados)

- [ ] usar em produção um papel de banco sem posse das tabelas, separado do papel das migrações;
- [ ] aplicar limites por operação e por cliente;
- [ ] definir retenção das chaves de idempotência;
- [ ] tornar a reconciliação incremental, a partir do último lançamento conferido.
