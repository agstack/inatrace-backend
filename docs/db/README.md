# Fresh-install schema snapshot

`schema-snapshot.json` records MySQL 8.4.11 metadata after the fresh-install
bootstrap and migrations exercised by `FreshInstallMigrationTest`. It includes
tables, columns, indexes and foreign keys, without business data. It describes
the test installation, not an existing production database.

The test writes the current snapshot to `target/db/fresh-schema.json` and fails
if it differs from the reviewed baseline. To regenerate it deliberately after
reviewing a schema change, run:

```bash
mvn -DargLine=-Dapi.version=1.43 -Dtest=FreshInstallMigrationTest -Dschema.snapshot.update=true test
```

Review the resulting diff before committing. Names of generated indexes and
constraints are currently preserved, so a change in generated names can also
require investigation even if the underlying relationship is unchanged. This
is accepted for the first version; normalization remains a follow-up.

The clean-upstream and safety-net-branch measurements are recorded in
`safety-net-baseline.md`. Framework/toolchain evidence for each upgrade stage
is kept in `spring-boot-3.5.md`, `java-25.md` and `spring-boot-4.1.md`.

## Validate a production-derived schema

`FreshInstallMigrationTest` exercises the test-only strict validation context
against an isolated fixture with a SELECT-only user. It disables the custom
migration initializer and scheduled writers. It is a mapping/schema check,
not a full boot of the deployed application.

To run the optional `ProductionDumpValidateTest`, provide an authorized,
sanitized **plain SQL** dump outside the repository. The test imports it into
a disposable MySQL container, then starts the validation context using a
SELECT-only account and compares schema metadata before and after. Choose an
image matching the source database; the default is `mysql:8.4.11`.

```bash
INATRACE_PRODUCTION_DUMP=/secure/path/sanitized.sql \
INATRACE_VALIDATION_MYSQL_IMAGE=mysql:8.4.11 \
  mvn -DargLine=-Dapi.version=1.43 -Dtest=ProductionDumpValidateTest test
```

Never point this test at a live database or commit the dump. A passing result
does not prove data correctness, production runtime configuration, or upgrade
compatibility by itself; validate the actual deployed version and migrations
in a separate isolated rehearsal.
