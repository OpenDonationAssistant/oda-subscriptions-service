# repository/

## OVERVIEW
Micronaut Data JDBC persistence for subscriptions and OIDC mappings (PostgreSQL + Flyway).
Entities, `CrudRepository` interfaces, and `@Singleton` facades all live in this one flat package.

## STRUCTURE
```
repository/
├── SubscriptionData.java / OidcMapping.java   # @MappedEntity records
├── *DataRepository.java                        # @JdbcRepository(POSTGRES) CrudRepository
├── *Repository.java                            # @Singleton async facades
└── Subscription.java                           # domain wrapper around SubscriptionData
```

## CONVENTIONS
- Entities are `record` + `@MappedEntity`; `@Id` on the key; list columns use `@MappedProperty(converter = StringListConverter.class)` (converter from `oda-commons`).
- Table names: `subscriptions`, `oidc`. Columns are implicitly snake_case from record camelCase.
- Generated repos extend `CrudRepository<Entity, String>`, annotated `@JdbcRepository(dialect = Dialect.POSTGRES)`; derived queries such as `findByOwnerIdAndDeregisteredFalse`.
- Every public facade method returns `CompletableFuture<T>`; blocking calls are wrapped in `CompletableFuture.supplyAsync` / `runAsync`.
- `Subscription` is the only non-record wrapper; it exposes the underlying entity via `data()`.

## ANTI-PATTERNS / GOTCHAS
- `supplyAsync`/`runAsync` use **no explicit executor** → blocking JDBC runs on the ForkJoinPool common pool; `EventsListener` `.join()`s on it.
- `OidcMappingRepository.markDeregistered` silently no-ops when the mapping is absent (`runAsync` + `ifPresent`) and still reports success.
- Schema changes must go through `src/main/resources/db/migration/V{n}__desc.sql`; tables are created unqualified in the `subscriptions` schema, which must pre-exist.
- No tests and no test fixtures exist here yet (`src/test/.../repository/` is empty).

## COMMANDS
```bash
mvn test-compile   # NullAway/ErrorProne gate
```
