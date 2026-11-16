# Image of one Spring Boot module, built from the jar that ./mvnw package left in <module>/target.
#   docker build --build-arg MODULE=ledger-api -t secure-ledger/ledger-api:local .
# compose.yaml builds both modules with it.

FROM eclipse-temurin:25-jre-alpine AS layers
ARG MODULE
WORKDIR /build
COPY ${MODULE}/target/${MODULE}-*.jar application.jar
# Split the jar into layers, so a code change does not rebuild the layer of the dependencies.
RUN java -Djarmode=tools -jar application.jar extract --layers --launcher --destination extracted

FROM eclipse-temurin:25-jre-alpine
RUN addgroup -S -g 10001 app && adduser -S -D -H -u 10001 -G app app
WORKDIR /app
COPY --from=layers /build/extracted/dependencies/ ./
COPY --from=layers /build/extracted/spring-boot-loader/ ./
COPY --from=layers /build/extracted/snapshot-dependencies/ ./
COPY --from=layers /build/extracted/application/ ./
# Never run as root: the process gets an unprivileged user with no shell login and no home.
USER 10001:10001
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "org.springframework.boot.loader.launch.JarLauncher"]
