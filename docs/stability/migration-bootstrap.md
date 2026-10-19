# Fresh-install migration coverage

`FreshInstallMigrationTest` exercises the current fresh-install startup path on
an isolated, empty `mysql:8.4.11` Testcontainers database. It does not connect to
the developer's database or reuse the schema shared by ordinary API tests.

The test starts the application three times, closing each context and connection
pool before the next phase:

1. Hibernate creates the schema with `ddl-auto=update`, then the application's
   delayed Flyway initializer runs the real SQL and custom JPA migrations.
2. Hibernate starts with `ddl-auto=validate`. The test checks the effective
   schema action and compares the schema and migration history before and after
   startup; no automatic repair or additional migration is permitted.
3. A normal `ddl-auto=update` restart preserves the history, schema, seed data
   and previously created entity graph.

The test compares all SQL resource names in the migration directory and concrete
JPA migration classes against the reviewed
`src/test/resources/db/fresh-install-migrations.txt` inventory. It checks
each applied script, type, version and success flag in `schema_version`, treating
Flyway's baseline version `1` separately. Pending migrations are checked using
the same custom resolver as `JpaMigrationStrategy`, not the plain Flyway bean.
When adding or removing a migration, review and update the inventory alongside
the migration; do not reduce it to accommodate a discovery failure.

Country codes and names are compared to the actual `import/countries.csv`;
facility and measurement-unit seed values are checked explicitly. Selected
document and authentication-token columns, foreign keys and indexes are checked
in MySQL metadata. Two facilities share one location and reference the seeded
storage facility type, exercising persistence and the intended cardinality.

## Running

Docker must be available. From the backend root, on Docker versions requiring
an explicit Testcontainers API version:

```sh
mvn -q -DargLine=-Dapi.version=1.43 -Dtest=FreshInstallMigrationTest test
mvn -q -DargLine=-Dapi.version=1.43 clean verify
```

The normal Maven `verify` discovers this `*Test` through Surefire. The test logs
its duration and uses one dedicated MySQL container and three sequential Spring
contexts; the regular suite may use its own separate shared container and cached
contexts. On the local 2026-10-19 implementation run, the focused test method took
approximately 19 seconds; timing varies with image availability and hardware.

## Scope and limitations

This checks the supported hybrid Hibernate/Flyway startup. Because Hibernate
performs `update` before Flyway, the test does not prove that migrations alone
construct the complete schema. Hibernate's validation does not check every
constraint or index; selected metadata assertions supplement it, but are not an
exhaustive schema comparison.

The test configures an absolute path to the repository's real country CSV. It
does not verify that a packaged application can start from an arbitrary working
directory without that external resource; see
[issue #53](https://github.com/agstack/inatrace-backend/issues/53). The test
disables external currency refresh and mail using the existing test profile.
Upgrade coverage from a prior version is separate:
[issue #88](https://github.com/agstack/inatrace-backend/issues/88).
