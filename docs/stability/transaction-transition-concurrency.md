# Transaction transition concurrency coverage

`TransactionTransitionConcurrencyTest` executes the approval, rejection and
processing-order deletion endpoints through a running HTTP server with real
access-token cookies and MySQL transactions. It deliberately avoids an
enclosing test transaction: each request uses the server transaction that a
client would use in production.

## Accounting oracle

The fixture begins with a source order totaling 10, with 3 already reserved by
a pending input transaction. Its source availability is therefore 7. The quote
output starts at zero available quantity.

| Final state | Source available | Quote output available |
| --- | ---: | ---: |
| Pending | 7 | 0 |
| Executed | 7 | 3 |
| Canceled | 10 | 0 |
| Deleted processing order | 10 | absent |

The distinct-unit control uses an input quantity of 3 and a source reservation
of 2. Rejecting it restores the source from 8 to 10, proving that restoration
uses `Transaction.outputQuantity`, the quantity originally reserved from the
source, rather than the target input quantity.

## Controls and concurrent scenarios

Sequential controls cover approval, rejection, a second transition refusal,
canceled-then-delete, direct deletion of a pending order, distinct units, and
the two deletion guards: an output already used by another transaction and an
output with a payment.

The suite runs two authenticated HTTP requests in separate workers. A
test-only Hibernate `POST_LOAD` observer pauses each selected worker only after
it has loaded the same `Transaction` from the real persistence path. It records
thread, status, version and transaction activity, and the test requires two
distinct active transactions to have read the expected state/version before
releasing them. The observer is registered only by this test and does not alter
production behavior.

| Scenario | Expected final state | Per-request reconciliation |
| --- | --- | --- |
| approve / approve | EXECUTED, source 7, target 3 | Exactly one successful approval; a successful approval must leave EXECUTED. |
| reject / reject | CANCELED, source 10, target 0 | Exactly one successful rejection; a successful rejection must leave CANCELED. |
| approve / reject | One permitted state | Exactly one success and its labelled effect is present. |
| reject / delete | CANCELED graph or removed graph, source 10 | A successful delete must remove the graph; a successful reject must be canceled or subsequently removed by delete. |
| delete / delete | Removed graph, source 10 | Exactly one successful delete; no target, transaction or processing order remains. |
| reject already canceled / delete | Removed graph, source 10 | Rejection is refused and deletion cannot restore stock twice. |

Final assertions reload source, target, transaction and processing-order rows
in a new transaction; they verify status, quantities, ownership and graph
foreign keys. They also verify that outputs protected by a dependent
transaction or payment and the dependent row itself survive a rejected delete.
They do not trust entities cached by the request that issued the transition.
For `reject/delete`, a separate test-only `POST_UPDATE` observer records the
actual transition to `CANCELED`: a successful reject must have emitted that
state update even when a later successful delete removes the graph. This keeps
a no-op 2xx from satisfying the scenario.

The current API may return a business `400`, an optimistic/deadlock `500`, or
a future `409` to the losing concurrent request. `500` is accepted only when
the real controller advice received an optimistic-lock/deadlock exception;
the number of such diagnostics must cover the number of `500` responses.
Authentication, authorization and unrelated server failures fail the test. Any
policy change from the existing error body needs separate compatibility review
for frontend and mobile clients.

Fixtures have unique IDs and `@AfterEach` deletes only records created by the
scenario, in dependency order. The test therefore shares the normal
`AbstractMySqlIntegrationTest` MySQL container safely with the rest of the
suite.

## Run

```bash
mvn -q -DargLine=-Dapi.version=1.43 \
  -Dtest=TransactionTransitionConcurrencyTest test
mvn -q -DargLine=-Dapi.version=1.43 clean verify
```

On 2026-10-20 the focused suite completed 10 tests with no failures, and the
complete suite completed 261 tests with no failures or errors.
