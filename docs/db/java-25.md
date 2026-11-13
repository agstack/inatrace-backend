# Java 25 migration notes (issue #148)

Measured on 10 November 2026 on branch `chore/java-25-148`, based on the
Spring Boot 3.5 commit `b8a0916b`.

## Toolchain

- JDK `25.0.5+8` (Temurin)
- Maven `3.9.9`
- Maven Compiler Plugin `3.14.1` (no override required for `release 25`)
- Spring Boot `3.5.16`
- Byte Buddy `1.17.8`, Mockito `5.17.0`, JUnit Jupiter `5.12.2`, Surefire `3.5.6`

## Changes

- `pom.xml`: `java.version` 17 → 25.
- `.github/workflows/_test.yml`: `java-version: '25'`.
- `Dockerfile`: build `maven:3.9-eclipse-temurin-25`, runtime
  `eclipse-temurin:25-jre`.
- `docs/building.md`: runtime image documented as Java 25.
- `StorageService`: `URL(String)` replaced by `URI.create(...).toURL()` to avoid
  the JDK 25 deprecation while keeping the malformed-URL logging behavior.
- Concurrency tests: `Thread.getId()` replaced by `Thread.threadId()`.

## Verification

```bash
JAVA_HOME=/usr/lib/jvm/java-25-openjdk-amd64 \
PATH=/usr/lib/jvm/java-25-openjdk-amd64/bin:$PATH \
  mvn -q -DargLine=-Dapi.version=1.43 clean verify
```

- **419 tests, 0 failures, 1 skipped** (production-derived dump test).
- 95 s total; `FreshInstallMigrationTest` 24.41 s and `DatabaseUpgradeTest`
  12.76 s.
- Schema snapshot and OpenAPI contract unchanged from the #147 baselines.
- `git diff --check` clean.

Docker image:

- `inatrace-backend:java25`
- Runtime: Java 25.0.5+8 (Temurin)
- Digest: `sha256:0fefe55c3ecdc4e09cc2e949e9dce10cbadffeb74483f9322149449fd50ce59f`

## Warnings

- Expected JDK 25 runtime warnings from `sun.misc.Unsafe`, dynamic agent loading
  (Byte Buddy) and Netty native access. They do not fail the suite.
- POI `XWPFRun.getFontSize()` remains pre-existing.
- `@SpyBean` remains deprecated by Boot 3.5; the migration to
  `@MockitoSpyBean` stays in the Spring Boot 4 stage.

## Pending

- Update the platform/dev-container JDK if it pins Java 17 for running the
  backend outside the image.
- Run a full application smoke test with the Java 25 image against MySQL.
- Confirm the intermediate-image/`edge` promotion strategy before integration.
- The production-derived dump validation remains opt-in and skipped.
