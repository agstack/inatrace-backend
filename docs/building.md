# Building

Producing a runnable jar and a Docker image.

To build an executable `jar` run the following command in the project root directory:

```
mvn clean install
```

The version comes from the `revision` property, `dev-SNAPSHOT` by default. CI sets it
from the release tag; to set it yourself, add `-Drevision=<version>`.

## Database

MySQL 8.4 is the default. New installations can use PostgreSQL instead by
setting:

```properties
INATrace.database.vendor = postgresql
INATrace.database.url = jdbc:postgresql://localhost:5432/inatrace
INATrace.database.dialect = com.abelium.inatrace.configuration.CustomPostgreSQLDialect
INATrace.database.auto-quote-keyword = true
```

The PostgreSQL database must be created with a UTF-8 (or ICU) locale. The
backend creates the `unaccent` extension on the first start when the database
user is allowed to create extensions; `unaccent` is used by the search
normalization. If the user cannot create extensions, run this once with a
superuser before starting INATrace:

```sql
CREATE EXTENSION IF NOT EXISTS unaccent;
```

Flyway reads `db/migrations/common` on both engines, plus
`db/migrations/mysql` or `db/migrations/postgresql`. There is no data migration
between engines: PostgreSQL is supported for new installations only, and
existing MySQL installations keep using the MySQL migration history.

The integration suite runs on MySQL by default. To run it on PostgreSQL:

```bash
INATRACE_TEST_DB=postgresql mvn -q -DargLine=-Dapi.version=1.43 clean verify
```

## Docker image

Official images are published to `ghcr.io/agstack/inatrace-backend` by CI. See
[RELEASE.md](../RELEASE.md) for the tags and the release process.

### Building

```
docker build --build-arg REVISION=<version> -t inatrace-backend .
```

The build compiles the project in a Maven container, so it needs neither Java nor Maven
on the host. The image is based on `eclipse-temurin:25-jre` and runs as a non-root user.

### Running

The image contains no configuration. Mount an `application.properties` based on
`src/main/resources/application.properties.template` at
`/app/config/application.properties`, and a volume for uploaded files at
`/data/storage`, with `INATrace.fileStorage.root = /data/storage`:

```
docker run -p 8080:8080 \
  -v ./application.properties:/app/config/application.properties:ro \
  -v inatrace-storage:/data/storage \
  inatrace-backend
```
