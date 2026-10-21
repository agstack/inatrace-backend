# Stock consumption concurrency evidence

Issue: [#89](https://github.com/agstack/inatrace-backend/issues/89)

`StockConsumptionConcurrencyTest` exercises `PUT /api/chain/processing-order`
through a running Spring Boot server and the shared MySQL Testcontainers instance.
Its setup is committed before requests begin; it does not wrap the test in a
transaction. Each request has a real access-token cookie and its own HTTP
worker, so security, transaction boundaries and persistence are all exercised.
Every fixture is removed by its own identifiers in `@AfterEach`, in dependency
order; it does not reset or truncate the shared Testcontainers database.

## Deterministic overlap

For same-source races, a test-only Mockito spy delegates to the real
`StockOrderService.calculateQuantities` method. It stops only requests for the
scenario source immediately before the source quantity is changed, after the
source was loaded. Latches confirm all workers reached that point before they
are released. The spy is part of the test context only; no production timing
hook, endpoint, retry or lock policy is introduced. The gate records the Tomcat
worker identity and the private optimistic-lock version of the loaded source,
then releases only after the expected distinct workers observed the same version.

## Covered outcomes

| Scenario | Required persisted outcome |
| --- | --- |
| Two 70-unit reservations from 100 | Exactly one successful graph; available quantity 30; one input transaction and output; no partial loser rows. |
| Four 30-unit competing reservations from 100 | At least one valid request succeeds; total committed input never exceeds 100; every successful response has exactly one input, processing order and output. |
| Sequential 70-unit reservations from 200 | Both requests succeed; available quantity 60 and committed input 140. |
| Concurrent 70-unit reservations from 200 | One or both requests may commit under optimistic locking; every successful request has one complete graph, every loser has none, and committed input/output/source balance reconcile. |
| Source edit (100 to 120) versus a 30-unit reservation | Isolated and both serial controls are established. The concurrent result must be edit only (120 available), consumption only (100 total, 70 available), or the serial combined result (120 total, 90 available). No mixed state is accepted. |
| Independent 70-unit reservations from two 100-unit sources | Both requests succeed; each source ends at 30 and only its own graph changes. |
| Invalid SHIPMENT target after input processing starts | The identity-backed processing order and transaction inserts are observed before the target-type guard; the rejected request leaves source quantity, processing orders, transactions and outputs unchanged. |
| PROCESSING yield with configured yield 0.5 | A 100-unit input debits 100; outputs 50 and 39 exercise in- and out-of-range behavior. A second output unit with weight 2 normalizes the expected in-range quantity to 25. |

All post-request assertions use a new transaction. Scenario-wide queries count
`ProcessingOrder` and output `StockOrder` rows directly by the fixture action
IDs, so an orphaned order/output cannot be hidden by the absence of a linked
input transaction. Per-source queries additionally verify links and quantities.
Each processing request carries a unique output identifier: every `2xx` response
must have exactly that output, its processing order and one matching input from
the expected source and quantity; every non-`2xx` response must leave no output
with its identifier. The scenario-wide counts additionally catch an empty
processing order that cannot be reached from an output.
Decimal comparisons are by numeric value rather than scale.
Foreign-company refusal remains covered by `StockOrderApiAuthTest`; this class
does not duplicate that sequential authorization matrix to construct its
committed, same-tenant concurrency fixtures.

## Current HTTP finding

The database invariant holds under the forced stale-write races: MySQL and
Hibernate roll back the losing transaction, so no negative availability or
partial processing graph remains. The losing request is currently surfaced as a
server error (for example a MySQL deadlock or Hibernate stale-object failure),
not a clean `409 Conflict`. The test deliberately characterizes this result; it
does not normalize it or add a retry. A future user-facing conflict-policy
change is tracked separately in [#92](https://github.com/agstack/inatrace-backend/issues/92).
The concurrency assertions accept only success, the currently observed 500, or
a future 409 conflict; they reject accidental validation or authorization responses.

Run the focused coverage with:

```bash
mvn -q -DargLine=-Dapi.version=1.43 -Dtest=StockConsumptionConcurrencyTest test
```
