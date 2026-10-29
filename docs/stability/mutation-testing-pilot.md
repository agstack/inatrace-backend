# Targeted mutation-testing pilot

This opt-in pilot for [#104](https://github.com/agstack/inatrace-backend/issues/104) checks whether selected existing tests detect bytecode mutations. PIT does not change Java source files. The small selection is not a project-wide mutation score or a guarantee that authorization, payments or other business rules are fully covered.

## Tooling and scope

- `org.pitest:pitest-maven:1.18.2` and `org.pitest:pitest-junit5-plugin:1.2.2`, both Apache License 2.0 according to their Maven POMs. PIT's [JUnit plugin documentation](https://github.com/pitest/pitest-junit5-plugin) describes the integration and version compatibility.
- Java 17.0.20.1, JUnit Platform 1.10.3, Spring Boot 3.3, Testcontainers 1.21.4 and MySQL 8.4.11; backend base `02ef12d` plus these uncommitted test/profile changes.
- One PIT worker; `-Dapi.version` is forwarded explicitly to child JVMs. A child argument file was inspected and contained `-Dapi.version=1.43`. During a facility run, one MySQL container and one Ryuk container were observed; this is not a claim about every process/container over the entire run.
- PIT's default mutators, with `CLASSLIMIT` 30 for the mapper and 3 for each service. The limit bounds runtime but samples only a small subset; the chosen mutations can change as PIT, source or configuration changes. No global percentage threshold is configured.
- No service code, REST contract, authorization or ordinary `verify` configuration is changed. The profile does not attach PIT to the default lifecycle. The open-source PIT run emits a warning that the optional Arcmutate Spring plugin is absent; actual selected Spring tests nevertheless executed and detected the mutations listed below.

## Commands

Run from `inatrace-backend`; Docker is required for the service pilots. `-Dapi.version=1.43` is the override needed for the local Docker environment and can be changed when appropriate. Run each command separately; the plugin does not run merely by enabling the profile.

```bash
mvn -Pmutation -Dapi.version=1.43 test-compile org.pitest:pitest-maven:mutationCoverage

mvn -Pmutation -Dapi.version=1.43 \
  -Dmutation.targetClass=com.abelium.inatrace.components.facility.FacilityService \
  -Dmutation.targetTest=com.abelium.inatrace.components.facility.FacilityApiAuthTest \
  '-Dmutation.features=+CLASSLIMIT(limit[3])' \
  -Dmutation.reportDir=target/pit-reports/facility \
  test-compile org.pitest:pitest-maven:mutationCoverage

mvn -Pmutation -Dapi.version=1.43 \
  -Dmutation.targetClass=com.abelium.inatrace.components.payment.PaymentService \
  -Dmutation.targetTest=com.abelium.inatrace.components.payment.PaymentApiAuthTest \
  '-Dmutation.features=+CLASSLIMIT(limit[3])' \
  -Dmutation.reportDir=target/pit-reports/payment \
  test-compile org.pitest:pitest-maven:mutationCoverage
```

Reports are generated under `target/pit-reports/mapper`, `facility` and `payment` as HTML and XML; each rerun replaces that target's report and `mvn clean` removes them. For a different target, override the target class, test class, feature limit and report directory together. Never use a shared database or real credentials for these tests.

## Results and survivor review

After the test changes, repeated bounded runs gave the same classifications:

| Target | Generated | Killed | Survived | No coverage | Timeout / errors | PIT analysis time |
| --- | ---: | ---: | ---: | ---: | ---: | --- |
| MeasureUnitTypeMapper | 13 | 13 | 0 | 0 | 0 / 0 | <1 s |
| FacilityService | 3 | 3 | 0 | 0 | 0 / 0 | 31 s |
| PaymentService | 3 | 3 | 0 | 0 | 0 / 0 | 31 s |

The times are PIT's own analysis times, excluding Maven startup and dependency downloads. There were no non-viable, memory-error, run-error or not-started results in these selections. Test coverage by PIT applies only to the selected classes and is not equivalent to the project's JaCoCo coverage.

Initially, 3 of 13 mapper mutations survived: removing the mapped ID, label or weight setters. `acyclicChainIsUnaffected` now asserts all three fields with a non-null weight; the same mutations are killed.

Initially, 1 of 3 facility mutations survived: forcing the product-association predicate at `FacilityService:130` to false. The existing positive fixture also owned the product, so it could pass without the association. The fixture now uses a product owned by another company but associated with the facility company. `facilityCanAttachFinalProductFromConnectedValueChain` kills that mutation. The sampled removed enrollment check is killed by `facilityReadsAreScopedToOwningCompany`; the sampled conditional at line 193 is killed by `facilityUpdateAndCreationRejectForeignCompanyWithoutMutation`.

Initially, 2 of 3 payment mutations survived: removing `Payment.setUpdatedBy` on creation (`PaymentService:480`) and omitting the bulk-export currency cell (`:344`). The creation test now reloads the record and checks the updating user; the workbook test checks currency and amount. The corresponding mutations are killed by `paymentCreationChecksPurchaseCompanyBeforeInserting` and `paymentExportsCheckEnrollmentBeforeWritingWorkbook`. The third, replacing a list conversion with null, is killed by `paymentListsCheckBothCompanyAndPurchaseEnrollment`.

The selected tests improved without changing production behavior. These observations do not prove the omitted mutants are safe, that all rules are protected, or that every mutation is meaningful. The broader codebook/product deletion gaps remain tracked in [#79](https://github.com/agstack/inatrace-backend/issues/79) and [#80](https://github.com/agstack/inatrace-backend/issues/80); this pilot does not depend on or replace them. Testcontainers workers create their own JVM-local resources, so expand the target or worker count only after measuring resource use and runtime.

The normal build was verified separately with `mvn -q -DargLine=-Dapi.version=1.43 clean verify`: 291 tests, no failures, errors or skipped tests. This was run against the stated base before the upstream `main` advanced; rerun verification after rebasing onto the eventual PR base.
