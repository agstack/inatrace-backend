# Building

Producing a runnable jar and a Docker image.

To build an executable `jar` run the following command in the project root directory:

```
mvn clean install
```

The version comes from the `revision` property, `dev-SNAPSHOT` by default. CI sets it
from the release tag; to set it yourself, add `-Drevision=<version>`.

## Docker image

Official images are published to `ghcr.io/agstack/inatrace-backend` by CI. See
[RELEASE.md](../RELEASE.md) for the tags and the release process.

### Building

```
docker build --build-arg REVISION=<version> -t inatrace-backend .
```

The build compiles the project in a Maven container, so it needs neither Java nor Maven
on the host. The image is based on `eclipse-temurin:17-jre` and runs as a non-root user.

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
