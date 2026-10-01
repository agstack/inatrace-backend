# Building

Producing a runnable jar and a Docker image.

To build an executable `jar` run the following command in the project root directory:

```
mvn clean install
```

## Docker image

### Base

`eclipse-temurin:17-jre`

Since only the major version is specified, the build process will always pull the latest minor and patch versions automatically.

### Command syntax

To build and tag a Docker image run `docker-build.sh` in the project root directory. The script runs `mvn clean package` then builds and tags a Docker image with the resulting `jar`.

```
./docker-build.sh <repo name (local or remote)> <tag> [push]
```

### Example

```
./docker-build.sh inatrace-be 2.4.0 push
```
