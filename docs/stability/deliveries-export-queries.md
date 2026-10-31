# Deliveries export query growth

The authenticated deliveries XLSX endpoint previously reused the full stock
list mapper. That mapper reads activity proofs, evidence and payments for every
order, although the workbook does not use those collections. This made the
number of SQL statements grow with the number of exported orders.

The export now checks company enrollment using the same code as the stock list,
then selects only workbook fields for purchase orders belonging to that company,
ordered by ID descending and limited to 10,000. It deliberately omits the count
query, whose result was unused by the workbook. Semiproduct translations are
loaded separately in batches of at most 500 distinct IDs and resolved with the
existing mapper, preserving its language and missing-translation behavior. The
ordinary stock-list mapper and endpoint are unchanged.

## Evidence

On 2026-10-31, using backend base `19d0ea3`, Java 17, Hibernate 6.5.2 and a
disposable MySQL 8.4.11 Testcontainer, the same authenticated MockMvc export
reported the following number of Hibernate `StatementInspector` calls:

| Fixture | Before | After |
| --- | ---: | ---: |
| 5 orders, distinct farmers, one semiproduct | 39 | 5 |
| 50 orders, distinct farmers, one semiproduct | 264 | 5 |
| 500 orders, distinct farmers and semiproducts | Not measured | 5 |
| 500 orders, shared farmer, distinct semiproducts | Not measured | 5 |
| 501 orders, 501 distinct semiproducts | Not measured | 6 |
| 10,001 eligible orders, 501 distinct semiproducts | Not measured | 6 |

The before samples are historical measurements taken against the unchanged
`19d0ea3` tree; the repository does not contain a command or test that runs the
old implementation, so they are not a reproducible regression gate. The after
samples were captured on this implementation working tree. The after 5/50/500
fixtures include distinct semiproducts; the baseline fixtures shared one.
Three after requests at each of 5, 50 and 500 orders produced identical counts.
The optional large test is reproducible with the command below.

Translation query count grows with distinct semiproduct IDs, in batches of 500:
for the tested fixture, the observed total is four fixed statements plus
`ceil(distinctSemiproducts / 500)` translation-fetch statements. Thus 501
distinct semiproducts add one statement, not one per order; 10,000 distinct
semiproducts would require 20 translation batches (and 24 statements under the
same fixture shape). The 10,001-order test currently uses only two distinct
semiproducts, so it verifies the export limit, not the 10,000-distinct-product
case. These derived counts describe this fixture and do not account for extra
queries caused by other mapped associations or codebook relationships.
The 10,001-order case checked that exactly the newest 10,000 were exported.

The test-only observer is thread-local and retains no SQL text or parameters.
Session creation, fixture setup and persistence-context clearing happen outside
the measured synchronous MockMvc request; authentication, authorization and
the export are inside. The sample excludes JDBC calls outside Hibernate and work
on other threads. A Hibernate inspection is not a guarantee of a completed
database execution, row count, network round trip or production latency.

The regular fixture enforces a ceiling of eight inspected statements for up to
500 distinct semiproducts, allowing three statements above the observed five.
The separate 501-product control requires exactly one more statement. Those
bounds describe the declared fixture and do not claim every possible graph has
the same query count; other mapped associations and codebook relationships may
cause additional work. The translation query currently hydrates `SemiProduct`
entities and their mapped translations in each batch, so query growth is
bounded by the batch formula but total hydrated data and persistence-context
memory still grow with the number of distinct products. The XLSX writer also
builds an `XSSFWorkbook` in memory.

Elapsed times and `Runtime` heap deltas varied substantially between optional
runs (including a negative heap delta in one run). These measurements include
fixture creation, request and workbook inspection in elapsed time; heap delta
is sampled around the request without controlling GC and is not a peak. They
are not suitable as memory limits or production capacity estimates. No JFR or
fixed-heap memory profile has been captured, so this document makes no claim
that exporting 10,000 rows is memory-safe for every deployment configuration.

## Commands

```bash
mvn -q -DargLine=-Dapi.version=1.43 -Dtest=StockOrderApiAuthTest test
mvn -q -DargLine=-Dapi.version=1.43 -Ddelivery.export.large=true \
  '-Dtest=StockOrderApiAuthTest#deliveryExportCharacterizesWorkbookAndQueryGrowth' test
mvn -q -DargLine=-Dapi.version=1.43 clean verify
```

The large case is opt-in to keep its fixture and workbook out of ordinary test
runs. These are test-only synthetic records, rolled back with the test.
The export's existing 10,000-order cap, workbook semantics, authorization and
failure behavior for absent required relationships are intentionally unchanged.
Query-budget work for other endpoints remains separate in issue #105.

The final full verification on this branch passed 328 tests with no failures,
errors or skipped tests; the stock-order test class accounted for 38 tests.
