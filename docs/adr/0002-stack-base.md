# ADR 0002 — Adotar Java 25, Spring Boot 4.1 e Maven multimódulo

## Estado

Aceita.

## Contexto

O README inicial previa Java 21 e Spring Boot 3. Em novembro de 2026, o Java 25 é a versão LTS atual e a linha estável do Spring Boot é a 4.1, a mesma usada no projeto irmão OrderFlow. O conjunto precisa cobrir Spring MVC, Spring Data JPA/Hibernate, Spring Security (resource server e authorization server), Bean Validation, Flyway, springdoc-openapi, Testcontainers e observabilidade sem bibliotecas fora desse ecossistema.

## Decisão

- **Java 25 (LTS)** para compilação e execução, inclusive no CI e nas imagens (`eclipse-temurin:25-jre-alpine`).
- **Spring Boot 4.1.2** como base; as versões vêm do BOM do Boot (Spring Framework 7, Hibernate 7, Spring Security 7, Flyway, Jackson 3, JUnit 6 Jupiter, Testcontainers 2). Fora do BOM, apenas springdoc-openapi 3.2 e ArchUnit 1.5.
- **Maven com wrapper** versionado e dois módulos: `ledger-api` (a aplicação) e `dev-auth-server` (servidor de autorização só para desenvolvimento, [ADR 0007](0007-autorizacao-por-escopo-e-propriedade.md)).
- **PostgreSQL 18** com **Flyway** como autoridade do schema; Hibernate apenas valida o mapeamento (`ddl-auto=validate`) e `open-in-view` fica desligado.
- **Observabilidade com o que o Boot já oferece:** Actuator (health com probes de liveness/readiness), Micrometer com registro Prometheus e log estruturado nativo (`logging.structured.format.console=ecs`, ligado no Compose).
- **Qualidade no build:** Spotless (palantir-java-format) no `verify`, testes unitários (`*Test`) no Surefire e de integração (`*IT`) no Failsafe contra PostgreSQL real; o agente do Mockito é carregado explicitamente, como exige o JDK 25.

## Consequências

O projeto usa APIs atuais (`MockMvcTester`, `ProblemDetail`, `HttpStatus.UNPROCESSABLE_CONTENT`, Jackson 3) sem camadas de compatibilidade. Parte da documentação pública ainda descreve o Spring Boot 3, e qualquer biblioteca nova precisa ter versão compatível com o Boot 4 antes de entrar. Kafka e outras tecnologias de mensageria ficam de fora: a [ADR 0001](0001-monolito-modular.md) mantém o monólito modular com transação local.
