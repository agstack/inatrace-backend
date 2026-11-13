# Farmer spreadsheet import and export

The farmer importer reads the first XLSX sheet. Row 5 (zero-based index 4)
contains headers; a nonblank cell in column Y enables a second product type.
Data begins on row 6. The first completely empty data row stops the scan.
The importer reads columns A–AG: identifiers and personal information (A–T),
farm and plant information (U–AC), and bank information (AD–AG). The company
must have at least one product type. The second-product path requires two.

The columns are: A company ID, B surname, C first name, D–O address
components, P country code, Q gender, R phone, S email, T smartphone,
U area unit, V total area, W–X first crop area/count, Y–Z second crop
area/count, AA organic, AB certified area, AC transition date and AD–AG
bank details. Country must match a stored country code; gender accepts `M`,
`F`, `N/A`, or `DIVERSE`. Surname, country and gender are required. Formula
and error cells in the identifier are rejected rather than evaluated. Date
cells use Excel's numeric representation. Text identifiers preserve leading
zeros; numeric identifiers cannot.

The importer accepts text or numeric cells for several identifiers. Numeric
identifiers are converted to whole-number strings, so leading zeros cannot be
recovered; text identifiers retain them. Numeric date cells are interpreted by
POI. A validation error in any scanned row prevents all valid rows from that
workbook being persisted. Duplicates already in the database are reported and
not added again. These are observations of the current implementation, not a
claim about every malformed workbook or every possible duplicate combination.

The export endpoint returns a ZIP with `farmers.xlsx` and `geodata.geojson`.
Its columns differ from the import layout; an import/export round trip is not
assumed. Tests parse the ZIP and workbook and compare cell values rather than
the XLSX bytes. Optional country and gender export behavior is related to #26;
spreadsheet geometry is separately tracked in #32.

`SpreadsheetPropertiesTest` uses jqwik 1.8.5 (Eclipse Public License 2.0) on
the existing JUnit Platform, with 50 attempts for numeric identifiers and
bounded synthetic text and date examples. The same version and replay path were
qualified in the local #98 work at `e7eb963`; that work is not merged here.
jqwik reports the seed and shrunk example;
replay data is kept under `target/jqwik-database`. These properties reopen real
POI workbooks and exercise the importer's identifier reader. The HTTP/MySQL
tests in `FarmerImportDocumentAuthorizationTest` cover one and two products,
an existing duplicate, selected invalid cells, empty-row stopping, uploader
authorization, and a ZIP/XLSX export. Each test removes the files it uploaded;
database changes roll back with the test transaction.

The tests cover selected examples, not every possible cell type, date,
duplicate arrangement or malformed workbook. No real customer files or
external services are used. `SpreadsheetImportGeneratedTest` uses five jqwik
tries per property with the real MockMvc importer and exporter: each generated case creates a Spring test
fixture, executes in its own transaction, removes its files and rolls back.
jqwik records seeds and shrinks failing identifiers. The committed-request test
uses separate transactions and removes only its own identified fixtures.

## Corrections and limitations

The non-XLSX upload error was corrected in backend PR #121; invalid workbook
formats now receive a controlled client error in the covered cases.

Additional regressions were reproduced on base `22ed157` and corrected in
this branch:

- `absentSurnameDoesNotCreateFarmer`: a missing surname was previously
  accepted, although a present blank cell was rejected as required. Both now
  produce a `REQUIRED` error at the correct row and column. The country and
  gender checks also report missing or blank cells as `REQUIRED`; missing
  country previously caused an internal server error.
- `duplicateRowsInSameWorkbookAreNotPersistedTwice`: two rows with different
  given names and the same company-internal identifier produce two farmers in the test
  transaction. The duplicate check now runs for each row after all rows pass
  validation and before that row is written. Later rows use the same persisted
  company/internal-ID or company/name/surname/city checks and database
  collation. No database uniqueness constraint or concurrent-import guarantee
  is introduced; already-existing duplicates are not modified.
- Names and surnames exceeding the 255-character database column limit now
  produce a row-level `INVALID_VALUE` error instead of reaching the database.
  Tests verify 255 and 256 accented characters for each field.
- A workbook with two product types is rejected with `INVALID_REQUEST` when
  the company has fewer than two configured, before any farmer is written.

Most integration tests use MockMvc within a test transaction and roll back
their data. `committedRequestsPreserveValidationAtomicity` commits its fixture,
then runs MockMvc requests with independently committed service transactions
and verifies state in new transactions. It checks no writes on a validation
error, rollback when a test-only service double fails after the first write,
and a successful committed import. The double is installed only for that request
and restored in `finally`. All rows are validated before any farmer is written,
and validation errors prevent imports.

The export check includes generated formula-like text, commas, quotes, Unicode
and newlines, the expected ZIP
entries, XLSX sheet names, selected headers, row count and an empty GeoJSON
feature collection. Date properties cover 50 generated calendar dates.
Generated cell properties exercise the cell readers; generated duplicate
identifiers also go through the HTTP importer. Reported errors and duplicate
matches use synthetic data; no existing production data is modified or
deduplicated. Farmer export is ZIP/XLSX/GeoJSON; a separate real deliveries CSV
endpoint is parsed with Apache Commons CSV and checks its header, quantity,
unit and company name including quoting and line breaks. Field lengths other
than names and surnames have not been
exhaustively validated by these tests.

Investigation confirmed that the preceding-day date is already stored in MySQL
when the JVM starts in America/Sao_Paulo and the application's `@PostConstruct`
later changes the default timezone to UTC. An isolated diagnostic checked the
raw SQL date, the JPA projection and the CSV date: starting the test JVM with
`-Duser.timezone=UTC` made all three equal the original 2025-06-17 date.
The Docker JVM now starts with `-Duser.timezone=UTC`. The application main method
also establishes UTC before invoking Spring, and Surefire sets UTC for tests.
The late `@PostConstruct` timezone change was removed. The regression permanently
checks the raw SQL date, JPA projection and CSV date, and passes with the usual
Maven command without an additional timezone argument. Existing stored dates
are not rewritten; this change prevents the startup mismatch for future writes.

```sh
mvn -q -DargLine=-Dapi.version=1.43 -Dtest=SpreadsheetPropertiesTest,SpreadsheetImportGeneratedTest,FarmerImportDocumentAuthorizationTest,MultiTenantIsolationTest,PoiWorkbookRoundTripTest test
mvn -q -DargLine=-Dapi.version=1.43 verify
```

On 2026-11-04 the full verification passed. The final focused suite passed
44 tests: its generated integration class took 15.37 seconds and the other
listed classes took about 4.6 seconds, excluding Maven and context startup.
The full verification preceded the final CSV header assertions; the focused
suite was rerun afterwards. Test-owned uploaded files are removed, ordinary
database fixtures roll back, and committed-test records are removed by ID.
