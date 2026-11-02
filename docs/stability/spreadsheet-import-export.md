# Farmer spreadsheet import and export

The farmer importer reads the first XLSX sheet. Row 5 (zero-based index 4)
contains headers; a nonblank cell in column Y enables a second product type.
Data begins on row 6. The first completely empty data row stops the scan.
The importer reads columns A–AG: identifiers and personal information (A–T),
farm and plant information (U–AC), and bank information (AD–AG). The company
must have at least one product type. The second-product path requires two.

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
bounded synthetic text examples. jqwik reports the seed and shrunk example;
replay data is kept under `target/jqwik-database`. These properties reopen real
POI workbooks and exercise the importer's identifier reader. The HTTP/MySQL
tests in `FarmerImportDocumentAuthorizationTest` cover one and two products,
an existing duplicate, selected invalid cells, empty-row stopping, uploader
authorization, and a ZIP/XLSX export. Each test removes the files it uploaded;
database changes roll back with the test transaction.

The tests cover selected examples, not every possible cell type, date,
duplicate arrangement or malformed workbook. No real customer files or
external services are used.

## Finding: non-XLSX upload returns an internal error

Uploading a non-XLSX file as a general document and submitting it to the farmer
import endpoint produces HTTP 500. Apache POI throws `NotOfficeXmlFileException`,
which reaches the generic error handler. The regression
`unreadableWorkbookDoesNotCreateFarmers` checks that no farmers were created and
expects a client error; it currently fails on the status. A production fix and
review of the response contract are separate work. Do not treat this case as a
passing validation result or claim that issue #102 is complete while it remains
unresolved.

```sh
mvn -q -DargLine=-Dapi.version=1.43 -Dtest=SpreadsheetPropertiesTest,FarmerImportDocumentAuthorizationTest,PoiWorkbookRoundTripTest test
mvn -q -DargLine=-Dapi.version=1.43 clean verify
```
