# Quantity property tests

## Tooling

The test-scoped dependency is `net.jqwik:jqwik:1.8.5`, published under the
Eclipse Public License 2.0 as stated in its Maven POM. Its
[1.8.5 guide](https://jqwik.net/docs/1.8.5/user-guide.html) specifies JUnit
Platform 1.10.2 or newer; this project uses Java 17, JUnit Platform 1.10.3 and
Surefire 3.2.5. Properties are discovered alongside Jupiter tests by Maven.

`QuantityPropertiesTest` uses 100 generated examples per property. jqwik
prints the seed and reduced example when a property fails. Its replay database
is `target/jqwik-database`, outside source control. `mvn clean` removes it, so
retain the reported seed and minimized values when reporting a failure.
Fast quantity generators use nonnegative integer cents within the bounds in the
test annotations and compare values with `BigDecimal.compareTo`; the persisted
examples use two fractional places. This is a tested input range, not a claim
that every database precision and scale is covered.

```bash
mvn -q -Dtest=QuantityPropertiesTest test
mvn -q -DargLine=-Dapi.version=1.43 -Dtest=StockConsumptionConcurrencyTest,TransactionTransitionConcurrencyTest test
mvn -q -DargLine=-Dapi.version=1.43 clean verify
```

The focused `YieldConversionRegressionTest` checks bag to kg, kg to bag and
bag to bag using the expected physical result. Before the correction, the first
and third examples failed; all three pass with the corrected calculation.
The latest `mvn -q -DargLine=-Dapi.version=1.43 clean verify` on the branch
rebased onto `upstream/main` passed 435 tests with zero failures, errors or
skips (2026-11-06). Focused timings: `QuantityPropertiesTest` 0.96 s,
`YieldConversionRegressionTest` 0.002 s, `StockConsumptionConcurrencyTest`
4.02 s, `TransactionTransitionConcurrencyTest` 4.17 s. It includes fractional
unit weights, missing-unit validation, and exact-exhaustion and
over-reservation controls.

An isolated negative control compared the service's result against an
intentionally incorrect addition in place of subtraction. Seed `98` produced
an original input of `21`, shrank it to `1`, and the same failure was replayed
using jqwik's stored `SAMPLE_FIRST` data. The temporary failing test was removed;
no production code was mutated. This establishes detection and replay for that
control, not a general mutation-testing result.

## Current evidence and limitations

The fast properties check new purchase and general-order balances, edits that
preserve used quantity, refusal below used quantity, decimal scale equivalence,
and the yield flag at both inclusive limits and one cent outside. The yield
examples use input and output weights from 1 to 60, yields from 0.1 to 1.0, and
independent integer rational arithmetic for the expected bounds and the input
mass cap. Bounds are rounded to two
decimal places using `HALF_UP`. Missing, zero and negative weights are rejected
with a validation error; they are not treated as convertible mass units.

Two existing MySQL-backed HTTP test classes each run 10 bounded cases generated
by jqwik with deterministic Java seeds `982026` and `982027`. The first verifies
two serial reservations, each resulting graph and rollback after an invalid
operation; the second verifies generated approvals and rejection/deletion
sequences without restoring the same reservation twice.
Existing tests also cover authorization and a later validation failure rolling
back inserted rows. These samples are sequential, not concurrency tests or an
exhaustive proof.

The migration seeds kg with weight 1 and a 60 kg bag with weight 60. The
frontend multiplies a quantity by its unit weight to get kg and divides by the
destination weight; it sends the selected stock quantity as transaction
`outputQuantity`. The old backend yield calculation divided that quantity by
both the input and output weights. The corrected calculation works in mass,
then divides by the output weight and rounds each bound to two decimals. This
avoids rounding an intermediate repeating decimal before computing the bounds.
An independently generated case with input `1.01`, input weight `50`, output
weight `12` and yield `0.5` showed intermediate rounding could move the upper
bound from `2.53` to `2.52`. A fixed regression checks that `2.53` is included
and `2.54` is outside the range.

An executable expected-value regression demonstrates the discrepancy: two 60 kg bags
contain 120 kg, and with 50% yield the expected output is 60 kg (within the
48–72 kg interval). The old backend marked 60 kg out of range. With input
`2.00` and weights 60 and 1, its two-decimal intermediate normalization is
`0.03`, yielding an interval of `0.01–0.02` kg. The regression asserts the
physically expected in-range result; it does not accept the old output. The
mass-based equation is consistent with the existing frontend calculation. The
frontend labels the field "Estimated output quantity per <input unit>" and
normalizes the target output back to the input unit, so the yield is a quantity
per input unit. Converting the expected range back to the input unit cancels
the weights and yields `inputQuantity × yield`, the same range the frontend
computes; the mass-based formula is its unit conversion. The two readings are
therefore equivalent for this calculation. The remaining difference is rounding
and aggregation (below). Units without a positive weight, such as the
seeded volume unit, cannot be converted by mass and now receive a validation
error in this yield calculation. The calculation updates the range flag when a
stock order is processed; it does not backfill previously stored flags. Rollout
should review affected configurations and historical records.

The frontend warning and backend flag do not currently have identical rounding
or aggregation semantics. For input `1.01`, weights `50` and `12`, yield `0.5`
and output `2.53`, the backend includes the rounded upper bound `2.53`, whereas
the frontend normalizes the output to the input unit, rounds the displayed total
to two places and compares it with an unrounded limit. In addition, the frontend
compares aggregated outputs while the backend evaluates each output stock order.
Aligning these requires a shared rule for multiple outputs as well as rounding.
The recorded decision is to keep the backend rule as the reference: each output
stock order is evaluated separately and the bounds are rounded HALF_UP to two
decimals. The frontend alignment is a separate follow-up; if the product prefers
an aggregate comparison, the backend flag and API shape must change instead.
This backend change does not claim to resolve that client discrepancy.
