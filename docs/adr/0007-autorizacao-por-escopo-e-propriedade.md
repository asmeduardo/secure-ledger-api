# ADR 0007 — Autorizar por escopo OAuth 2.0 e por propriedade da conta

## Estado

Aceita.

## Contexto

Os clientes da API são integrações máquina a máquina (uma carteira, um marketplace) e a operação financeira. É preciso separar quem consulta de quem movimenta, impedir que um cliente leia ou mova a conta de outro e não revelar a terceiros quais contas existem. O ambiente local precisa emitir tokens reais, e os testes não podem depender de um provedor de identidade em execução.

## Decisão

- A API é um **resource server OAuth 2.0** sem estado: toda chamada a `/api/**` exige um JWT validado por assinatura (JWKS do emissor, buscado sob demanda), emissor (`iss`), audiência (`aud = secure-ledger-api`) e validade.
- **Escopos independentes:** `ledger.read` para consultas (`GET`), `ledger.operate` para escritas (`POST`) e `ledger.audit` para o back office (`/api/v1/admin/**`: trilha completa e reconciliação). Qualquer outra rota ou método é negado; documentação OpenAPI, health e métricas são públicos.
- **Propriedade:** o cliente é o `sub` do token. A conta guarda `owner_id` na abertura, e toda leitura ou movimentação busca a conta por `id` **e** dono. Transferências exigem as duas contas do mesmo cliente. Conta de outro cliente e conta inexistente respondem exatamente o mesmo `404` (`account-not-found`); o mesmo vale para transações (`transaction-not-found`).
- **Erros:** `401`, `403` e `404` usam `ProblemDetail` (RFC 9457) como o resto da API; o `401` mantém o desafio `WWW-Authenticate: Bearer`. CSRF fica desligado porque a API não usa cookies.
- **Ambiente local:** o módulo `dev-auth-server` usa o Spring Authorization Server com três clientes `client_credentials` fictícios (`demo-wallet`, `demo-marketplace`, `demo-auditor`), segredos lidos do ambiente e audiência `secure-ledger-api` acrescentada aos tokens. Em outro ambiente, basta apontar `SECURE_LEDGER_ISSUER_URI` (e, se preciso, `SECURE_LEDGER_JWK_SET_URI`) para o provedor escolhido.
- **Testes:** testes funcionais usam o suporte a JWT do `spring-security-test`; a validação completa é testada com tokens assinados por uma chave RSA gerada durante a execução, nunca versionada, servida como JWKS por um servidor HTTP local do próprio teste.

## Consequências

`ApiAuthorizationIT`, `JwtValidationIT`, `AccountApiIT`, `MovementApiIT`, `StatementApiIT`, `StatementExportIT` e `ReversalApiIT` cobrem token ausente, escopo insuficiente, assinatura desconhecida, emissor e audiência errados, token expirado e o `404` idêntico para contas de outros clientes. O servidor de autorização local guarda clientes em memória, gera nova chave a cada inicialização e não deve ser exposto fora da máquina de desenvolvimento. Falhas de autenticação e de escopo (`401`/`403`) acontecem no filtro de segurança e não entram na trilha de auditoria; recusas de propriedade em escritas (`404`) entram, porque passam pelo caso de uso.
