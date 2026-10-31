# Database upgrade path

`DatabaseUpgradeTest` covers one reviewed, synthetic upgrade path from
`fb4fecf87c97c0ae65994850446a73bceb99d201` to the target reference revision
`19d0ea3b119e78713720a9a4f2b81e6c3deceb24`, which includes migration
`2026.10.21.12.00`. The test starts the application compiled from the current
checkout, not a separate checkout of that SHA. The target revision records the
reference for this reviewed upgrade path; a run on another revision exercises
that revision's code and does not, by itself, prove it matches the reference.

The fixture is source-produced rather than a production dump: its 97-table
schema and `schema_version` export were made by the pinned source revision,
including its authentic baseline, 41 historical migration rows and SQL
checksums. After that source boot, the reviewed fixture adds only stable
`upgrade-fixture-*` graph data and the 28 historical one-to-one unique indexes
that installations upgraded from an older mapping can retain. Those data and
legacy indexes are deliberate synthetic conditions, not claims that they were
created by a clean source boot. The test runs in one disposable
`mysql:8.4.11` Testcontainers database pinned to the manifest's image digest.
It is not a certification of every
released deployment or of backup/restore compatibility.

The target application is started three times against the same database:

1. `ddl-auto=update` applies the normal Hibernate-then-Flyway bootstrap;
2. `ddl-auto=validate` proves the upgraded schema requires no repair;
3. `ddl-auto=update` proves a normal restart does not repeat migration effects.

Before the current application starts, the test verifies the source table
count, exact historical migration history and checksums, pre-2026 structures,
and the representative stock/processing graph. It snapshots tables, columns,
indexes, foreign keys and fixture markers, then permits only the reviewed
Hibernate/Flyway delta: the document uploader column, its canonical named
foreign key (after the cleanup migration removes any Hibernate duplicate), the
intended indexes, and the removal of all 28 obsolete unique indexes. The target preserves the graph
through update, validate and restart; verifies the document-uploader
column/FK/index and authentication-token expiration index; and performs current
JPA writes for shared facility locations, stock, and processing metadata. The
effective SQL plus custom-JPA Flyway resolver rejects pending migrations and
validates the historical SQL checksums against current migration resources. A
negative control changes a historical SQL checksum and verifies that validation
fails. The four custom JPA delta migrations are checked by version, type and
script, with null checksums: the application's JPA resolver does not provide
checksums for their contents. This test does not claim checksum protection for
those Java migration bodies. The full source history is hashed as ordered rows,
with a negative control covering the last row; it is not limited by MySQL's
`GROUP_CONCAT` length.

Run it from `inatrace-backend/`:

```bash
mvn -q -DargLine=-Dapi.version=1.43 -Dtest=DatabaseUpgradeTest test
```

The reviewed source/target metadata is in
`src/test/resources/db/upgrade/fb4fecf/manifest.properties`; explanatory
provenance is kept beside it in `manifest.md`. The manifest records the pinned
image digest, source toolchain and naming strategies; `source-fixture.sql`
contains the exact detailed migration inventory and checksums used by the test.
Regenerating or expanding the fixture requires a preflight of the pinned source
revision on the declared MySQL image and review of the resulting SQL and
SHA-256. Do not replace it with customer data or modify historical migrations
to make a new fixture boot.
