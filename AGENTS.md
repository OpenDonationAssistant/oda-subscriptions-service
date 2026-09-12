# PROJECT KNOWLEDGE BASE

**Generated:** 2026-09-12T06:57:47Z
**Commit:** b288caa
**Branch:** main

## OVERVIEW
Micronaut 5 / Java 25 microservice for OpenDonationAssistant: manages donor subscriptions and
Keycloak/OIDC application registration. RabbitMQ events are fanned out to subscribers;
PostgreSQL (Flyway) holds state. 23 Java files, ~1,690 LOC.

## STRUCTURE
```
.
├── src/main/java/io/github/opendonationassistant/
│   ├── Application.java          # main(), default env "standalone", RabbitMQ @Factory topology
│   ├── keycloak/                 # OIDC app lifecycle (controllers, service, admin client, dto)
│   ├── listener/                 # RabbitMQ fan-out listener + publisher
│   ├── repository/               # JDBC entities + async repo facades
│   └── subscription/commands/    # subscription query/command controllers
├── src/main/resources/           # application*.yml + db/migration (Flyway)
├── src/test/java/.../repository/ # EMPTY (no tests exist)
├── pom.xml                       # parent io.micronaut.platform:micronaut-parent:5.1.0
└── .github/workflows/maven.yml   # delegates to oda-libraries reusable release workflow
```

## WHERE TO LOOK
| Task | Location | Notes |
|------|----------|-------|
| Subscription endpoints | `subscription/commands/` | classes named by action, not `*Controller` |
| Add/change endpoints | `keycloak/command/` | POST `/apps/commands/*` |
| Keycloak REST calls | `keycloak/http/KeycloakAdminClient.java` | `@Client("keycloak")`; base URL NOT in repo config |
| OIDC orchestration | `keycloak/service/KeycloakOidcService.java` | 407 LOC, largest class |
| Event fan-out | `listener/EventsListener.java` | queue `subscriptions.events` |
| Persistence | `repository/` | Micronaut Data JDBC + Flyway |
| Schema changes | `src/main/resources/db/migration/` | `V{n}__desc.sql` |
| Runtime/bean wiring | `Application.java` | default env + Rabbit topology |
| Config | `src/main/resources/application.yml` | `application-standalone.yml` always active |

## CODE MAP
_LSP/ast-grep unavailable; reference centrality unmeasured._
| Symbol | Type | Location | Role |
|--------|------|----------|------|
| `Application` | class | `Application.java:32` | `main()`, RabbitMQ `@Factory` |
| `Application.Configurer` | class | `Application.java:41` | forces default env `standalone` |
| `EventsListener` | class | `listener/EventsListener.java:13` | `@RabbitListener` fan-out |
| `EventsListener.EventPublisher` | interface | `listener/EventsListener.java:54` | `@RabbitClient` publish |
| `KeycloakAdminClient` | interface | `keycloak/http/KeycloakAdminClient.java:31` | Keycloak Admin REST client |
| `KeycloakOidcService` | class | `keycloak/service/KeycloakOidcService.java` | register/deregister/refresh/list |
| `OidcController` | class | `keycloak/OidcController.java` | `GET /apps` |
| `SubscriptionRepository` | class | `repository/SubscriptionRepository.java:10` | `CompletableFuture` facade |
| `SubscriptionData` | record | `repository/SubscriptionData.java` | `@MappedEntity("subscriptions")` |
| `OidcMapping` | record | `repository/OidcMapping.java` | `@MappedEntity("oidc")` |

## CONVENTIONS (deviations from generic Micronaut)
- Java 25; DTOs/entities are `record` + `@Serdeable`; **no Lombok**.
- Constructor injection with `jakarta.inject.Inject`; `@Singleton` services/repos.
- NullAway runs at **ERROR** with JSpecify mode → annotate `@Nullable` (`org.jspecify.annotations`); build fails otherwise.
- Public service/repo methods return `CompletableFuture`; blocking Micronaut Data calls are wrapped in `supplyAsync`/`runAsync`.
- Logging via `ODALogger` (`oda-commons`), not SLF4J directly.
- Controllers extend `commons.micronaut.BaseController` (external `oda-commons`) and are `@Secured(SecurityRule.IS_AUTHENTICATED)`.
- Command endpoints live under `/commands/...`; controllers are named after the action (`AddSubscription`, `RegisterOidcApplication`).
- 2-space indent, ~80 cols; **no formatter/lint plugin** (no spotless/checkstyle/editorconfig).
- Shared base logic lives in external libs `oda-commons` / `oda-rabbit-conf` — not in this repo.

## ANTI-PATTERNS (THIS PROJECT)
- Do NOT assume tests exist: `src/test` is empty and `mvn test` is a no-op; `mvn test-compile` is the real gate.
- Do NOT log admin `client_secret`, bearer tokens, or the JWKS token map.
- Do NOT rely on `application-standalone.yml` being absent in production — `defaultEnvironments("standalone")` always activates it.
- Do NOT commit secrets; Keycloak admin `client-secret` defaults to empty and the service fails fast.
- Do NOT add test-only deps as `compile` scope (existing offender: `oda-test-utils`).

## UNIQUE STYLES
- Shared transport records live in `keycloak/dto`; one-off command payloads are nested inside their controller.
- `repository/` mixes `@MappedEntity` records, `CrudRepository` interfaces, and `@Singleton` facades in one flat package.

## COMMANDS
```bash
mvn test-compile                              # compile gate (ErrorProne + NullAway)
mvn test                                      # runs tests (currently none)
mvn verify                                    # tests + JaCoCo report
mvn clean package                             # jar
mvn clean package -Dpackaging=native-image    # native image (CI default)
docker build -t oda-subscriptions-service .   # expects native binary
./target/oda-subscriptions-service            # run packaged binary
```
No Maven wrapper — use system `mvn` and JDK 25. `.mvn/jvm.config` supplies required `--add-exports`.

## NOTES
- `keycloak.url` / the `@Client("keycloak")` base URL is **not defined** in any `application*.yml`; it must be provided at runtime.
- `micronaut.executors.events-listener` config is dead — never referenced by code.
- DB schema `subscriptions` must pre-exist; Flyway creates unqualified tables there.
- JWKS cache TTL is 240h; JWKS supplied via env `JWKS_URI`.
- CI builds native-image (GraalVM JDK 25), pushes to ghcr.io, and publishes OpenAPI to the docs repo.
- AGPL-3.0 declared in OpenAPI but no `LICENSE` file exists.
- `.classpath` / `.project` / `.factorypath` are untracked Eclipse artifacts, not build inputs.
