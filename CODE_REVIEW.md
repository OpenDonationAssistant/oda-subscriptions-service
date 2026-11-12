# Code Review — oda-subscriptions-service

## Feature review — micronaut-parent-5.2.5 (2026-11-10)

**Feature:** Bump io.micronaut.platform:micronaut-parent to 5.2.5 across ODA Micronaut services.
**Change set:** `53f0b95ceb65d587db02764d18bf4ec42a05b877` → working tree; 2 files changed (+2/−2)
(pom.xml +1/−1, AGENTS.md +1/−1). `pre-edit-state.txt` lists no pre-existing modifications for this
repo, so these numbers are exactly this change — no approximation needed.
**Reviewer:** independent review subagent (fresh context)
**Verdict:** PASS WITH ISSUES

**Verification:**
- `mvn -B -o -DskipTests -Dmaven.repo.local=.tmp-vbump/m2repo clean test-compile` → **BUILD SUCCESS**
  (20.6 s; 23 main sources + 1 test source; one pre-existing Error Prone *warning*,
  `TypeParameterUnusedInFormals` at `KeycloakOidcService.java:436`).
- `help:evaluate` → `micronaut.core.version=5.2.22`, `micronaut.data.version=5.2.4`,
  `micronaut.serialization.version=3.2.6`, `micronaut.security.version=5.4.1`,
  `micronaut.openapi.version=7.3.0`, `project.parent.version=5.2.5` → the new BOM is in effect and
  every `${micronaut.*.version}` used in `annotationProcessorPaths` resolves.
- `mvn -B -o -Dmaven.repo.local=.tmp-vbump/m2repo test` → **BUILD FAILURE**,
  `Tests run: 3, Failures: 0, Errors: 3, Skipped: 0` — `EventsListenerTest.<init>:24 »
  IllegalState Could not initialize plugin: interface org.mockito.plugins.MockMaker`, root cause
  `com.sun.tools.attach.AgentInitializationException: Agent JAR loaded but agent failed to
  initialize` (Mockito 5.12.0 inline Byte Buddy mock maker cannot load its agent on JDK 25).
  Repro'd identically with the brief's `user.home` workaround
  (`-DargLine=-Duser.home=$PWD/target/fakehome`) → the fakehome redirect is irrelevant here: this
  test class uses plain Mockito and needs no Micronaut Test Resources / containers at all.
- `mvn … -Dmaven-surefire-plugin.version=3.5.6 test` (same code, same BOM, only the plugin pinned to
  the version the **old** parent managed) → **BUILD SUCCESS**, `Tests run: 3, Failures: 0, Errors: 0,
  Skipped: 0`. Isolates the regression to the parent's surefire 3.5.6 → 3.6.0 delta.
- `mvn … -DargLine='@{argLine} -XX:+EnableDynamicAgentLoading' test` → **BUILD SUCCESS**, 3/3 green,
  `target/jacoco.exec` still produced (coverage preserved) → verified one-line remediation.
- `mvn … -DargLine=-Djdk.attach.allowAttachSelf=true test` → still 3 errors (that flag alone is not
  sufficient).
- Probe build of the same tree at `.tmp-vbump/probe/oda-subscriptions-service` with the parent
  reverted to `5.1.0` → **BUILD SUCCESS**; artifact comparison against the 5.2.5 build:
  generated SerDe sources 6 → 28 files; `target/classes/META-INF/swagger/oda-subscriptions-service-0.7.0.yml`
  12,464 → 12,670 bytes (see Low finding below).

**Findings:**
- **High** `pom.xml:10-14` — the bump inherits `maven-surefire-plugin` 3.6.0 from the new parent and
  that makes this repo's only test class fail, so `mvn test`/`mvn verify` is red: 3 tests run,
  3 errors, `MockitoInitializationException: … your JDK does not supply a working agent attachment
  mechanism` / `AgentInitializationException: Agent JAR loaded but agent failed to initialize`.
  It is not the Micronaut API level (holding surefire at 3.5.6 under the *new* parent is green) and
  not the sandbox's `user.home` quirk (the test is pure Mockito, no test resources); it is the
  plugin-version delta on JDK 25 (Red Hat 25.0.1). CI is affected: every service delegates to
  `oda-libraries/.github/workflows/build.yml`, which runs `mvn -B -q verify` (build.yml:49) — tests
  are not skipped there, only in the packaging step (build.yml:52). Fix before merge, at this repo
  level or family-wide: add
  `<argLine>@{argLine} -XX:+EnableDynamicAgentLoading</argLine>` to the
  `maven-surefire-plugin` configuration (verified green *and* jacoco-compatible here), or pin
  `<maven-surefire-plugin.version>3.5.6</maven-surefire-plugin.version>` (also verified green).
  The same fix will be needed in every ODA repo that has Mockito tests (this one has 3 tests, all
  Mockito-based), so it is probably better decided once, not per repo.
- **Low** `pom.xml:10-14` (effect visible via `OidcController.java:62`) — the bump also changes the
  published API artifact: the generated OpenAPI document gains
  `Sort.Order.nullOrdering` + a `Sort.Order.NullOrdering` enum (micronaut-data 5.2.4 /
  openapi 7.3.0 reflecting `Pageable`). Additive and non-breaking, but `openapi-config.json`
  publishes `@opendonationassistant/oda-subscriptions-client`, so the next release will ship a
  client diff that is not attributable to any product change. Worth knowing when the client is
  regenerated; no code action strictly required.
- **Low** `AGENTS.md:22` — the file this change edits still claims
  `src/test/java/.../repository/ # EMPTY (no tests exist)`, while
  `src/test/java/.../listener/EventsListenerTest.java` (3 tests, added in `2a95834`, an ancestor of
  the base commit) exists and `mvn test` does run something. Pre-existing drift, not introduced
  here, but the implementer touched this exact file and the ≤1-line fix was in reach.
- **Info** `pom.xml:31` — `${oda.version}` stays at `0.11.232`, i.e. `oda-commons` /
  `oda-rabbit-conf` / `oda-test-utils` are still the artifacts compiled against Micronaut 5.1.x and
  are now consumed alongside micronaut-core 5.2.22. Compile and unit tests are green, so no
  compile-level breakage; native-image linkage and runtime behaviour under the new core were not
  verified here (see Notes).
- **Info** `src/main/java/io/github/opendonationassistant/keycloak/service/KeycloakOidcService.java:436`
  — `TypeParameterUnusedInFormals` Error Prone warning in an untouched file; warning only, not
  build-failing, not caused by this change.

**Notes:**
- No stale version left behind: `git grep -E "5\.1\.[0-9]|<micronaut\.version>|micronaut-parent"` over
  tracked files matches only the updated `pom.xml`/`AGENTS.md` lines. `README.md` has no Micronaut
  version claim (badges only), `.github/workflows/maven.yml` pins nothing (delegates to the reusable
  workflow with `version: ${{ github.RUN_NUMBER }}`), `Dockerfile` copies a pre-built binary, and
  `.mvn/jvm.config` is untouched (all `--add-exports`/`--add-opens` intact).
- This repo has **no** `<micronaut.version>` property override to align (unlike oda-tribute-service);
  the pom only uses inherited `${micronaut.*.version}` properties in `annotationProcessorPaths`, and
  all of them resolve under 5.2.5.
- Untracked-but-ignored `.tmp/external-context/*` session notes still say "Micronaut 5.1.0"; they are
  gitignored via the user-level gitignore (`~/.config/git/gitignore`) and are not repo documentation,
  so no action. `git status` is clean apart from the two intended files.
- Not verified here: GraalVM native-image build (CI default `-Dpackaging=native-image`), an actual
  GitHub Actions run, and whether the GraalVM 25 JDK in CI behaves exactly like the local Red Hat
  25.0.1 build. The dynamic-agent restriction that trips Mockito is a JVM-level default, so CI is
  expected to fail the same way; the fixed `argLine` is JDK-version independent.
- Out of scope, verified read-only for the lead's cross-repo bookkeeping (not part of this repo's
  change set): `oda-telegram-service` has no `.git` (no git history exists for it; its `pom.xml`
  shows parent `5.2.5`), and `oda-tribute-service/pom.xml:20` now carries
  `<micronaut.version>5.2.5</micronaut.version>`.

## Fix addendum - micronaut-parent-5.2.5 (2026-11-10)

**Finding addressed:** **High** `pom.xml:10-14` — parent 5.2.5 inherits `maven-surefire-plugin`
3.6.0, whose fork no longer lets Mockito's inline Byte Buddy mock maker self-attach on JDK 25, so
`mvn test` ran 3 tests / 3 errors (`Could not initialize inline Byte Buddy mock maker`,
`AgentInitializationException`).
**Fix:** `pom.xml:329-339` — new `maven-surefire-plugin` `<configuration>` carrying
`<argLine>@{argLine} -XX:+EnableDynamicAgentLoading</argLine>`; `@{argLine}` keeps the JaCoCo
`prepare-agent` argument. Post-review delta is exactly that block: `pom.xml` went from `+1/−1` to
`+13/−1` (`git diff --stat`: 14 insertions, 2 deletions total), and the parent bump plus
`AGENTS.md:23` are untouched from the reviewed set — nothing unrelated changed.
**Re-verification:** `mvn test -Dmaven.repo.local=/home/stcarolas/Coding/projects/.tmp-vbump/m2repo
-s /home/stcarolas/.m2/settings.xml` → **BUILD SUCCESS**, `Tests run: 3, Failures: 0, Errors: 0,
Skipped: 0` under `surefire:3.6.0` (5.8 s), `target/jacoco.exec` produced (110,397 B).
`mvn -X test …` fork command line shows both
`-javaagent:…/org.jacoco.agent-0.8.14-runtime.jar=destfile=…/target/jacoco.exec` and
`-XX:+EnableDynamicAgentLoading` applied; `mvn test -Djacoco.skip=true …` also green
(JaCoCo sets `argLine` to empty, so `@{argLine}` still resolves).
**Residual risk:** Not re-checked and still open: the **Low** `AGENTS.md:22` drift ("`src/test` is
empty", "`mvn test` is a no-op") and the **Low**/`Info` OpenAPI + `${oda.version}` observations —
this fix touched only the surefire block. Untested here: native-image build
(`-Dpackaging=native-image`) and a real GitHub Actions run. The fix is repo-local, so every other
ODA Mockito repo still needs the same `argLine` (or a family-wide decision); and it silently depends
on `jacoco-maven-plugin` continuing to define the `argLine` property — remove JaCoCo and the literal
`@{argLine}` token would no longer be substituted.
