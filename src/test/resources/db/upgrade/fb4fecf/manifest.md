# Reviewed synthetic upgrade fixture: `fb4fecf` to target reference `19d0ea3`

The test pulls the MySQL image by the index digest in `manifest.properties`,
not by the mutable tag alone. The history signature hashes every row ordered by
`installed_rank`: rank, version, description, type, script, checksum and
success joined with `|`, one row per LF, encoded as UTF-8. A negative control
alters the final row and verifies that the signature changes.

`source-fixture.sql` is a full MySQL export of the schema and `schema_version`
history produced by the pinned backend source revision
`fb4fecf87c97c0ae65994850446a73bceb99d201`. It is not a hand-written partial
schema and does not fabricate Flyway rows or migration checksums.

## Generation provenance

The source revision was checked out in an isolated worktree and built with
Java 17.0.20.1, Maven 3.9.9, Spring Boot 3.3.3 and `mysql:8.4.11`. Its normal
Hibernate-then-Flyway boot created 97 tables and recorded the baseline plus 41
historical migrations through `2023.06.15.10.04`; the 20 SQL migrations retain
their source-produced checksums. After that boot, a small, deterministic graph
was inserted solely for this test: company/user membership, facility/location,
stock source and output, processing order, linked transaction, document and
authentication token. The 28 single-column unique indexes named by
`V2026_09_23_12_00__Drop_Legacy_OneToOne_Unique_Indexes` were then added as the
explicit synthetic legacy condition that migration must repair; a clean source
boot no longer creates them, while installations upgraded from the former
one-to-one mapping can retain them. The values are synthetic
`upgrade-fixture-*` markers.

Consequently, **only the schema and Flyway history are source-produced**. The
data graph and legacy indexes are controlled synthetic test conditions layered
on that genuine source database. They are intentionally retained because they
exercise the historical constraint-removal and data-preservation paths; they do
not claim to be a byte-for-byte customer database or a clean-source output.

## Reproduction procedure

Use an isolated detached worktree at the pinned source revision and a disposable
database created from `mysql:8.4.11@sha256:0744ee5ef89ce6ccfa13de3e579fe6b9e27f93dd70da9c06d2c908b1b193fb8d`.
The following procedure intentionally uses only local, disposable paths and a
local MySQL defaults file; it must never point at a shared or production server:

```bash
git worktree add --detach /tmp/inatrace-backend-source-88 \
  fb4fecf87c97c0ae65994850446a73bceb99d201
(
  cd /tmp/inatrace-backend-source-88
  mvn -q -DskipTests package
  java -jar target/coffee-backend-*.jar \
    --spring.config.additional-location=file:/tmp/inatrace-source-88.properties
)
# Wait for that unchanged source boot to record baseline + 41 rows, stop it,
# then apply only the reviewed upgrade-fixture-* graph and the 28 legacy indexes
# to the disposable database through mysql --defaults-extra-file=<local-cnf>.
mysqldump --no-tablespaces --single-transaction --skip-comments \
  --skip-dump-date --set-charset --routines --events --triggers \
  --databases <isolated-upgrade-database> > source-fixture.sql
sha256sum source-fixture.sql
```

Run the source application's normal boot unchanged with Java 17.0.20.1, Maven
3.9.9, UTF-8/`utf8mb4`, UTC and the naming strategies listed in
`manifest.properties`. The reviewed deterministic direct-SQL graph is accepted
for this schema-upgrade fixture: it tests target preservation, rather than the
old application's create-flow. After Flyway reports the baseline plus 41
successful source migrations, insert only the reviewed `upgrade-fixture-*`
graph and the 28 documented legacy indexes before running the recorded export.

Credentials, temporary datasource flags and local paths are deliberately not
recorded. The canonical detailed migration inventory is the source-produced
`schema_version` INSERT in `source-fixture.sql`; it includes every version,
type, description and checksum, while `source.history.sha256` makes accidental
history edits detectable.

The database was exported with `mysqldump --no-tablespaces
--single-transaction --skip-comments --skip-dump-date --set-charset --routines
--events --triggers`. Its SHA-256, and a canonical SHA-256 over the source
`schema_version` content, are recorded in `manifest.properties` and checked by
`DatabaseUpgradeTest`. No customer data, passwords, usable sessions, or
external credentials are included.

Regenerate only after deliberately changing the pinned source contract: repeat
the source preflight, inspect its schema/history before export, update this
manifest and review the SQL diff. Do not replace this fixture with a production
dump or make historical migrations pass by editing their contents.
