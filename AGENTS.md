# AGENTS.md

Instructions for coding agents working in this repository. Humans: the same rules
apply; `README.md` and `docs/` are the place to start.

## What this is

The INATrace Java backend: Spring Boot 3.3 on Java 17, Maven, MySQL 8.4. It serves
the web frontend (`inatrace-frontend`) and the mobile app. Read `docs/` before
changing behaviour; `TECHNICAL_DOCUMENTATION.md` is background only and not current.

## Build and test

```bash
mvn clean install            # jar; -Drevision=<version> to set the version
mvn test                     # whole suite
mvn test -Dtest=SomeTest     # one class; fails if the name matches no test
```

- Integration tests extend `AbstractMySqlIntegrationTest` (`src/test/java/.../support/`),
  which starts one `mysql:8.4.11` Testcontainer per Maven JVM. **Docker must be
  running.** In that shared context the schema is built from the entity model
  (`ddl-auto = update`) and Flyway points at an empty location, so the migrations don't
  run there. The install and upgrade tests run real migrations on their own:
  `FreshInstallMigrationTest` (Hibernate-then-Flyway bootstrap on an empty MySQL, checked
  against `src/test/resources/db/fresh-install-migrations.txt`) and the `*MigrationTest`
  classes under `db/entities/`.
- `ApiRouteInventoryTest` compares `docs/api-auth-coverage/routes.tsv` against the routes
  Spring registers. Adding, removing or renaming an endpoint fails it until the TSV is
  updated.
- Run the test class you touched before claiming a change works, and say which classes
  you ran. Don't assert a 500 as expected behaviour; fix the cause or map it to a client
  error.

## Changing code

- Keep the style of the file you are in: comment density, naming, `ApiException` with an
  `ApiStatus` for client errors, `em` (EntityManager) access through the service layer.
- A new or changed endpoint needs, in the same commit: the route in `routes.tsv`, a row in
  the matching `docs/api-auth-coverage/<area>.md` matrix (who may call it, and which test
  proves it), and an authorization test.
- Deleting entities: check every foreign key that references them (`grep -r "ManyToOne"`
  for the entity) and clean up or reject with a client error. Don't rely on
  `CascadeType.ALL` on shared relationships.
- User-supplied bytes (uploads) are the client's fault when they don't parse: map every
  failure to open them to `ApiStatus.INVALID_REQUEST`, not to a 500.
- Paginated list endpoints default to `limit = 100` (`ApiPaginatedRequest`); callers that
  need everything must pass a limit.
- Backend and frontend ship separately. A response-shape change must keep the currently
  deployed frontend working, or the PR must say which frontend PR it needs and why they
  have to deploy together.

## Documentation

`docs/` is published to <https://docs.agstack.org/> as-is. Update the page a change makes
wrong in the same commit. Don't write run-specific numbers (test counts, timings) into
`docs/`; they go stale on the next commit.

## Commits and pull requests

- Conventional Commits: `<type>: <summary>`, lowercase, imperative, no trailing period
  (`fix:`, `feat:`, `test:`, `docs:`, `refactor:`, `chore:`, `ci:`, `build:`).
- One concern per PR. Reference the issue (`(#123)`) in the subject when there is one.
- No AI attribution: no `Co-Authored-By: Claude …` trailers, no "Generated with …" or
  "Prepared with LLM assistance" in commits, PR descriptions, issues or docs.
- Don't merge, rebase or push shared branches unless asked.
