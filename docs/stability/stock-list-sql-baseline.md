# Stock list and dashboard SQL budgets

Measured on base revision `306a746` with MySQL 8.4.11, the test profile and
Hibernate second-level cache disabled. The synchronous MockMvc request and its
serialization are observed on the request thread, including authorization and
count queries. Setup is flushed and the persistence context cleared before each
sample. The observer does not retain SQL or parameter values and excludes other
threads; this is a statement count, not a measurement of rows scanned or latency.

| Request | Dataset | Page size | Observed statements | Budget |
| --- | ---: | ---: | ---: | ---: |
| Company purchase-order list | 5 | 5 | 19 | 23 |
| Company purchase-order list | 50 | 5 | 19 | 23 |
| Company purchase-order list | 50 | 10 | 19 | 23 |
| Deliveries dashboard, yearly | 5 | — | 3 | 5 |
| Deliveries dashboard, yearly | 50 | — | 3 | 5 |

The fixed-size page and dashboard may grow by at most two statements when
unseen records increase. Distinct farmers exercise relationship loading. The
test also measures a page of 50 and checks bounded growth compared to five.
Pagination, response content, authorization, and an unbounded-query control are
also checked. The deliveries export has separate query and content coverage in
the [export report](deliveries-export-queries.md).

Before batching, the same fixture required 39 statements for five returned
orders and 64 for ten. Hibernate batch fetching now groups reads of the four
stock-order collections and customer entities in batches of at most 64. It does
not join collections into the paginated query. A separate test checks that
distinct payment and evidence values remain on their respective orders.
Negative controls add real farmer reads after the measured requests and check
that thresholds reject the growth. The observer covers Hibernate-inspected SQL
on the request thread, not direct JDBC or asynchronous work. Other relations
and larger pages can incur additional queries; this is not a universal bound.

Run the focused tests with:

```sh
mvn -q -DargLine=-Dapi.version=1.43 -Dtest=StockOrderApiAuthTest test
```
