# Upgrade safety-net baseline

Measured on 10 November 2026 against upstream main revision
`99a1ba6ac2bd41cacdb247ccaee16100fcb431a7`. Two measurements are recorded:
the clean upstream revision and the `test/spring-boot-safety-net-146` branch.
The application remained on Spring Boot 3.3.3 and Java 17.0.20.1; the runner
used Docker 29.9.3 and MySQL 8.4.11. Results are local measurements, not CI
performance guarantees.

## Clean upstream baseline (`99a1ba6a`, detached worktree)

```bash
JAVA_HOME=/opt/jdk-17.0.20.1+1 PATH=/opt/jdk-17.0.20.1+1/bin:$PATH \
  mvn -q -DargLine=-Dapi.version=1.43 clean verify
```

- **415 tests, 0 failures, 0 skipped.**
- `FreshInstallMigrationTest`: 13.75 s; `DatabaseUpgradeTest`: 15.35 s;
  `OpenApiContractTest`: 17.21 s.
- Dependency tree SHA-256:
  `50830587bb456e968efab9c476578adc397fad1353e5f529ff55e6d3213eb1a0`
  (`mvn dependency:tree -DoutputFile=target/dependency-tree-clean.txt`).
  The tree is a build artifact, not a locked dependency manifest.
- A clean `test-compile` with deprecation reporting compiled 544 main and
  58 test source files and reported two uses of the deprecated Apache POI
  `XWPFRun.getFontSize()` in `DocxDocumentCreator` (lines 235–236).

The OpenAPI baseline `docs/api-contract/openapi.json` has SHA-256
`9f1943cc254a062464cc8a28d18083cf423ac2c6ad7d9d8ac2bddacaf1cfdb28`.
The existing OpenAPI test produces a runtime manifest in
`target/api-contract/manifest.json`; it includes the checked-out revision and
dirty-tree digest.

## Safety-net branch (`test/spring-boot-safety-net-146`)

```bash
JAVA_HOME=/opt/jdk-17.0.20.1+1 PATH=/opt/jdk-17.0.20.1+1/bin:$PATH \
  mvn -q -DargLine=-Dapi.version=1.43 clean verify
```

- **419 tests, 0 failures, 1 skipped**, 113 s.
- `FreshInstallMigrationTest`: 28.09 s; `DatabaseUpgradeTest`: 14.70 s.
- The skipped test is the separate, production-derived `ProductionDumpValidateTest`.
  The fresh-install test exercises the controlled, synthetic dump path,
  strict validation and the schema-snapshot negative control.
- The branch adds HTTP contract checks for pagination, `LocalDate`, `Instant`,
  an order-type enum, null-field omission (`comments` and
  `preferredWayOfPayment`), validation/authentication errors and binary
  `Content-Type`.

The dependency tree is unchanged by the test-only branch; the clean hash above
still applies. The snapshot baseline is `docs/db/schema-snapshot.json`; it
describes the fresh test installation, not a production database.

## PIT pilot

An explicit PIT run examined the configured mapper target: **13 mutations
generated, 13 killed, 17/20 lines covered**. This is a narrowly scoped pilot,
not a mutation score for the entire backend.

## Migrations

The source tree contains 20 SQL migration files and 25 versioned Java
migrations. These are file counts, not the number applied to every database.
The upgrade-fixture test separately checks its declared migration history.

## Accepted limitations and pending gates

- **Production schema gate pending:** an authorized sanitized dump and an
  isolated clone of the actual deployed version are needed to establish the
  deployed schema and pre-existing differences. The fresh snapshot and the
  synthetic dump do not establish production compatibility.
- **Snapshot names preserved:** generated index and constraint names are kept
  in the snapshot instead of being normalized. This is accepted for the first
  version; name changes can add diff noise even when the underlying
  relationship is unchanged. Normalization remains a follow-up.
- Existing startup warnings include Flyway's MySQL 8.4 support warning,
  Hibernate Envers metamodel warnings and missing equals/hashCode on a
  composite ID; their presence predates this test-only change and warrants
  review during the upgrade.
