# keycloak/

## OVERVIEW
OIDC application lifecycle against the Keycloak Admin REST API: list, register, deregister,
refresh client secret, and change app settings. Largest feature slice in the repo (~1,150 LOC).

## STRUCTURE
```
keycloak/
├── OidcController.java              # GET /apps (query)
├── command/                         # POST /apps/commands/* (write controllers)
├── dto/                             # @Serdeable records (shared across controllers)
├── http/KeycloakAdminClient.java    # @Client("keycloak") declarative admin client
└── service/KeycloakOidcService.java # orchestration + persistence of mappings
```

## WHERE TO LOOK
| Route | Class | File |
|-------|-------|------|
| `GET /apps` | `OidcController` | `OidcController.java` |
| `POST /apps/commands/register-oidc-client` | `RegisterOidcApplication` | `command/RegisterOidcApplication.java` |
| `POST /apps/commands/deregister-oidc-client` | `DeregisterOidcApplication` | `command/DeregisterOidcApplication.java` |
| `POST /apps/commands/refresh-client-secret` | `RefreshClientSecret` | `command/RefreshClientSecret.java` |
| `POST /apps/commands/change-oidc-app-settings` | `ChangeOidcAppSettings` | `command/ChangeOidcAppSettings.java` |

## CONVENTIONS
- Write controllers live in `command/` and are named after the action; `RegisterOidcApplication` uses `@Controller("/apps")` while the others declare the full path on the method with a bare `@Controller`.
- One-off request/response payloads are nested records inside the controller; shared wire records live in `dto/`.
- `KeycloakOidcService` reads config via `@Value` (`keycloak.realm`, `keycloak.admin.*`); admin token is fetched per operation.
- Ownership is resolved through `OidcMappingRepository`; failing ownership returns **401** (some OpenAPI annotations claim 404 — known mismatch).
- Client secrets are never returned in full: `OidcApplication.clientSecret` exposes only the last 6 chars (`secretSuffix`).

## ANTI-PATTERNS / GOTCHAS
- Registration is **not atomic**: Keycloak client is created, then a scope is added, then the DB mapping is written. A failure mid-flow orphans the Keycloak client (no compensation).
- `rethrowVoid` hardcodes `"Failed to deregister OpenID Connect application"` and is reused by `refreshClientSecret`, so refresh failures are misreported.
- `fetchApplications` issues one Keycloak GET per mapping (N+1 remote calls).
- `OidcController` does fake pagination: returns all rows as page 0 and ignores `Pageable`.
- Dead code: `KeycloakAdminClient.getClientSecret()` and the `GetAppsResponse` interface are never used.
- Do NOT log the admin request body/token from `KeycloakOidcService` (contains `client_secret`).

## COMMANDS
```bash
mvn test-compile   # verify changes stay NullAway/ErrorProne clean (JSpecify @Nullable)
```
