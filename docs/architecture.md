# Arquitetura planejada

## Modelo contábil

Cada transferência originará uma transação contábil com pelo menos dois lançamentos. Débitos e créditos usarão a mesma moeda e deverão se compensar. O sistema não armazenará um saldo independente sem mecanismo de reconciliação, porque duas fontes de verdade aumentariam o risco de divergência.

## Módulos

- **identity:** identidade externa e associação com o cliente;
- **accounts:** ciclo de vida e regras da conta;
- **ledger:** transações e lançamentos imutáveis;
- **transfers:** orquestração, limites e idempotência;
- **audit:** trilha operacional e reconciliação.

Os módulos não acessarão repositórios uns dos outros. Casos de uso coordenarão o domínio por interfaces explícitas e eventos internos quando não houver necessidade de resposta imediata.

## Concorrência

A implementação começará com bloqueio pessimista ou otimista, escolhido por teste de carga e documentado em ADR. Restrições de unicidade no PostgreSQL protegerão as chaves de idempotência. O teste deverá provar o comportamento de duas transferências que disputam o mesmo saldo.

## Contratos HTTP

Controllers tratarão apenas HTTP e validação. Services controlarão transações e converterão entidades em DTOs imutáveis. Entidades JPA não serão retornadas pela API. Erros serão representados com `ProblemDetail`, e a paginação terá um contrato próprio e estável.
