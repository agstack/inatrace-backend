# Spring Boot 4.1 migration notes (issue #149)

Measured on 10 November 2026 on branch `chore/spring-boot-4.1-149`, based on
the Java 25 commit `53c41fb5`.

## Toolchain

- JDK `25.0.5+8` (Temurin)
- Maven `3.9.9`
- Spring Boot `4.1.2`
- Spring Framework `7.0.10`, Security `7.1.2`, Tomcat `11.0.27`
- Hibernate ORM `7.4.13.Final`, Jakarta Persistence `3.2.0`
- Jackson `3.1.7` (the `com.fasterxml.jackson.annotation` artifact `2.21`
  is still the annotation module used by Jackson 3)
- Flyway `12.4.0`, MySQL Connector/J `9.7.0`
- JUnit Jupiter `6.0.3`, Mockito `5.23.0`, Byte Buddy `1.18.15`
- Testcontainers `2.0.5`
- springdoc `3.2.0`

## Changes

### Build

- Parent `3.5.16` → `4.1.2`; Java 25 retained.
- Modular starters: `spring-boot-starter-web` → `spring-boot-starter-webmvc`,
  plus `spring-boot-starter-restclient` and `spring-boot-starter-flyway`.
- Test starters: `spring-boot-starter-security-test`,
  `spring-boot-starter-data-jpa-test`, `spring-boot-starter-webmvc-test` and
  `spring-boot-resttestclient`.
- Testcontainers 2 artifacts: `mysql` → `testcontainers-mysql`,
  `junit-jupiter` → `testcontainers-junit-jupiter`; version `2.0.5`.
- springdoc `2.8.17` → `3.2.0` on both artifacts.
- Removed the `junit-vintage-engine` exclusion (the engine no longer exists)
  and the temporary `spring-boot-properties-migrator`.

### Application code

- Jackson 3 packages: `com.fasterxml.jackson.databind.*` →
  `tools.jackson.databind.*`, `JsonProcessingException` →
  `JacksonException`, `JsonNode.fieldNames()`/`elements()` →
  `properties()`/`values()`.
- Custom converters now extend `ValueSerializer`/`ValueDeserializer`;
  `ObjectMapper` configuration uses `JsonMapper.builder()`.
- `RestTemplateConfig` uses `org.springframework.boot.restclient.RestTemplateBuilder`
  and the autoconfigured bean.
- `SpringExceptionResponseBuilder` builds a
  `JacksonJsonHttpMessageConverter` from the Boot `JsonMapper` bean;
  `MappingJackson2HttpMessageConverter` is no longer auto-configured.
- `SpringExceptionConverter` reads `JacksonException.Reference`
  (`from()`, `getPropertyName()`).
- Flyway auto-configuration moved to
  `org.springframework.boot.flyway.autoconfigure`; `JpaMigrationResolver`
  follows the Flyway 12 `Scanner` constructor.
- `WebMvcRegistrations` moved to
  `org.springframework.boot.webmvc.autoconfigure`.

### Tests

- `@AutoConfigureMockMvc` moved to
  `org.springframework.boot.webmvc.test.autoconfigure`.
- `TestRestTemplate` moved to `org.springframework.boot.resttestclient`;
  the classes that inject it now declare `@AutoConfigureTestRestTemplate`.
- `@SpyBean` → `@MockitoSpyBean` (`TransactionTransitionConcurrencyTest`,
  `StockConsumptionConcurrencyTest`).
- Testcontainers classes keep the `org.testcontainers.*` imports through the
  new artifacts.

### Runtime configuration

- `spring.jpa.properties.hibernate.max_fetch_depth = 2` added to the template
  and the test profile. Hibernate 7.4 removed `MySQLDialect`'s implicit default
  of `2`; without it, loading an entity by id join-fetched the whole eager
  graph and exceeded MySQL's 61-table join limit.
- `spring.flyway.check-location` → `spring.flyway.fail-on-missing-locations`.
- `spring.flyway.ignore-missing-migrations = true` →
  `spring.flyway.ignore-migration-patterns = *:missing,*:future`.
- `springdoc.api-docs.version = OPENAPI_3_0` keeps the published 3.0.1
  document with springdoc 3.2.

## Reviewed schema delta

`FreshInstallMigrationTest` compared the new snapshot with the #148 baseline.
No table, column-set or foreign-key changes; the reviewed differences are:

- **70 columns changed from nullable to `NOT NULL`**: the `creationTimestamp`
  and `updateTimestamp` columns of timestamped entities, and the
  `entityVersion` columns. Hibernate 7.4 infers `@CreationTimestamp`,
  `@UpdateTimestamp` and `@Version` as non-null in generated DDL. Existing
  databases keep their nullable columns (`hibernate.hbm2ddl.auto=update`
  does not alter them); only fresh installations get the stricter DDL.
- **7 generated unique indexes renamed from `UK…` to `IDX…`**, with the same
  columns and uniqueness: `CompanyTranslation`, `CompanyValueChain`,
  `User.email`, `ValueChainFacilityType`, `ValueChainMeasureUnitType`,
  `ValueChainProcEvidenceType` and `ValueChainSemiProduct`. On the upgrade
  path Hibernate adds the new `IDX…` index beside the legacy `UK…` constraint;
  `DatabaseUpgradeTest` records that delta. Removing the redundant legacy
  constraints on upgraded databases is a follow-up cleanup migration.

The baseline was regenerated with:

```bash
mvn -DargLine='-Dapi.version=1.43 -Dschema.snapshot.update=true' \
    -Dtest=FreshInstallMigrationTest test
```

`docs/stability/database-upgrade.md` describes the upgrade-path gate.

## Application fix required by Hibernate 7.4

`FacilityService.listCollectingFacilitiesByCompany` paginates a query with a
fetch join and a predicate on the joined collection. Hibernate 7.4 rewrites
`limit` + collection fetch joins into a subquery, and the rewrite lost the
fetch-join alias (`Unknown column 'ft1_0.language'`). The query now sets
`org.hibernate.limitInMemory`, restoring the pre-7.4 in-memory pagination for
that endpoint until the rewrite can be used there.

## TorpedoQuery verdict

TorpedoQuery `org.torpedoquery.jakarta:1.0.1` compiled and passed all query
tests against Hibernate `7.4.13.Final`. No replacement or downgrade is needed
for this stage; the spike notes remain in `docs/spikes/torpedo-hibernate7/`.

## Verification

```bash
JAVA_HOME=/usr/lib/jvm/java-25-openjdk-amd64 \
PATH=/usr/lib/jvm/java-25-openjdk-amd64/bin:$PATH \
  mvn -q -DargLine=-Dapi.version=1.43 clean verify
```

- **431 tests, 0 failures, 1 skipped** (the opt-in production dump test).
- About 85 s total in the final local run.
- `DatabaseUpgradeTest`, `FreshInstallMigrationTest` and
  `OpenApiContractTest` pass. The suite also gained happy-path HTTP tests for the
  12 routes that had no HTTP request in the previous measurement
  (`CompanyApiTest` and `UserAuthApiTest`).
- Route coverage measured over the real HTTP requests: **184 of 198 routes
  now answer at least one 2xx**. The remaining 14 have only denied/negative
  requests and are pending a policy decision.
- `mvn dependency:analyze` reports only the expected starter/transitive
  warnings; no new unused library dependency was introduced.
- `git diff --check` clean.

## Remaining warnings

- `jakarta.persistence.Temporal` on `CurrencyPair.date` (JPA 3.2 deprecation).
- `@Valid` on `List` parameters/fields (pre-existing Bean Validation warning).
- POI `XWPFRun.getFontSize()` (pre-existing).
- JDK 25 `sun.misc.Unsafe` and dynamic-agent warnings from the toolchain.

## Image

- `inatrace-backend:boot41`
- Built from `Dockerfile` (`maven:3.9-eclipse-temurin-25` →
  `eclipse-temurin:25-jre`).
- Digest: `sha256:3a074ad72d384f45553ffc91d1fdb6c79cb3662dbc16b53f2d728a841f234a2f`
- Runtime: `OpenJDK 25.0.5+8` (Temurin); `spring-boot-properties-migrator`
  is absent from the extracted application layers.

## Pending

- Run the opt-in `ProductionDumpValidateTest` against an authorized,
  sanitized production dump.
- Rehearse the upgrade on a copy of a real database and confirm the
  70 nullability changes are not required there (Hibernate `update` leaves
  existing columns untouched).
- Decide whether to add a cleanup migration for the legacy `UK…` unique
  constraints on upgraded databases.
- Open the contract/frontend/rollout stage (#150) with the generated
  `target/api-contract/` artifact.
