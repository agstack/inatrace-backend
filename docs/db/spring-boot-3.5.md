# Spring Boot 3.5.16 migration notes (issue #147)

Measured on 10 November 2026 on branch `chore/spring-boot-3.5-147`, based on
the safety-net commit `7cf3dae9`. The application remains on Java 17.0.20.1.

## Resolved versions

- Spring Boot `3.5.16` (Spring Framework `6.2.19`, Spring Security `6.5.11`)
- Hibernate ORM `6.6.53.Final`
- Jackson `2.21.4`
- Flyway `11.7.2` (`flyway-core` and `flyway-mysql`)
- MySQL Connector/J `9.7.0`
- Testcontainers `1.21.4` (explicit override kept)
- springdoc `2.8.17`
- JUnit Jupiter `5.12.2`

## Verification

```bash
JAVA_HOME=/opt/jdk-17.0.20.1+1 PATH=/opt/jdk-17.0.20.1+1/bin:$PATH \
  mvn -q -DargLine=-Dapi.version=1.43 clean verify
```

- **419 tests, 0 failures, 1 skipped** (the optional production-dump test).
- 103 s total; `FreshInstallMigrationTest` 25.74 s and
  `DatabaseUpgradeTest` 13.45 s.
- `git diff --check` clean.
- Docker image built and started with Java 17:
  `sha256:bf30b92a70718af233609ed827a51cb313a2a86a24315c0263501e266059b574`.

## Schema diff

The Hibernate 6.6 schema snapshot differs only in the **column order** of
`CompanyCustomer.location` (the same columns and types are present). Hibernate
orders the embedded address attributes alphabetically; the reviewed snapshot
was updated with `-Dschema.snapshot.update=true`. No column was added, removed,
renamed or retyped.

## OpenAPI diff

springdoc 2.8.17 defaults to OpenAPI 3.1. To preserve the existing 3.0.1
contract, `springdoc.api-docs.version=OPENAPI_3_0` was added to
`application.properties.template` and the test profile. With that setting the
reviewed diff is limited to six additions of `"format": "email"` to e-mail
properties; no operation, field, type, required value or enum changed.

`docs/api-contract/openapi.json` was regenerated with
`-Dapi.contract.update=true` after reviewing that diff.

## Compatibility fixes required by the upgrade

- `JpaMigrationExecutor` now returns `List<Results>` as required by Flyway 11.
- `RestTemplateConfig` passes the two additional request-factory providers
  required by Boot 3.5 to `restTemplateBuilderConfigurer`.
- `Address` is no longer both `@Embeddable` and `@MappedSuperclass`; Hibernate
  6.6 rejects that combination.
- `GeoAddress` no longer extends `Address`; it is a separate embeddable with
  the same address attributes plus coordinates. Hibernate 6.6 no longer allows
  the same embeddable class with different property mappings.
- `CommonApiTools` has explicit overloads for `Address` and `GeoAddress`.
- `ProductService` now removes product-label batches, feedback and final-product
  links through managed entities instead of bulk DML, avoiding stale managed
  references when the label is deleted under Hibernate 6.6.
- Test assertions use `JsonCompareMode.STRICT` and `JsonNode.properties()` to
  avoid newly deprecated APIs.

## Remaining deprecations

- POI `XWPFRun.getFontSize()` in `DocxDocumentCreator` (pre-existing).
- Boot 3.5 deprecates `@SpyBean`; migration to `@MockitoSpyBean` is planned for
  the Spring Boot 4 stage, as documented in the plan.

## Pending

- The `edge` image strategy must be confirmed before integrating stages 2/3
  into `main`; publishing an image is not a production deployment.
- The platform `backend.properties` must include
  `springdoc.api-docs.version=OPENAPI_3_0` if it overrides the template.
