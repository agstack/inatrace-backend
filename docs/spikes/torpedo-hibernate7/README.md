# TorpedoQuery compatibility probe

This isolated experiment is not part of the application build and does not
change its dependencies. It uses Java 25, Spring Boot dependency management
4.1.2, Hibernate 7.4.13.Final, TorpedoQuery Jakarta 1.0.1 and MySQL 8.4.11.
On 10 November 2026 it compiled and passed against a disposable MySQL
container for equality filtering, count, ordered pagination, relationship
navigation and the project's `GROUP_CONCAT` dialect registration.

Run with Docker available:

```sh
JAVA_HOME=/usr/lib/jvm/java-25-openjdk-amd64 \
PATH=/usr/lib/jvm/java-25-openjdk-amd64/bin:$PATH \
  mvn -f docs/spikes/torpedo-hibernate7/pom.xml -Dtest=TorpedoProbe test
```

Verdict: this sample provides a conditional go for continuing the staged
upgrade; it is not evidence that all application queries or the application
itself are compatible. In particular, the application does not compile on
Boot 4 by changing its parent alone: Flyway and other changed APIs still need
the migration in issue #149. Test the application's actual query inventory,
SQL semantics and integrations before approving that upgrade. The probe
creates and drops its own schema; it never connects to a production database.
