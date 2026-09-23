# ADR 0001 — Adotar um monólito modular

## Estado

Aceita para a implementação inicial.

## Contexto

Transferências e lançamentos precisam de atomicidade forte. Separar o fluxo em serviços independentes exigiria coordenação distribuída sem produzir benefício proporcional para o primeiro release.

## Decisão

O sistema será um monólito modular. Fronteiras entre contas, razão e transferências serão protegidas por testes de arquitetura. A persistência permanecerá na mesma transação quando as invariantes contábeis exigirem atomicidade.

## Consequências

A solução preservará consistência com menor custo operacional. A modularidade permitirá extrair componentes no futuro caso métricas e requisitos demonstrem essa necessidade.
