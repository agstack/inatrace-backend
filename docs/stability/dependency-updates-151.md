# Dependency updates after Spring Boot 4.1.2 (#151)

Single branch, both batches: `chore/dependency-updates-151` (base `c83566ca`,
PR #156). The original plan split the work in two batches; the team chose to
keep everything in one branch/PR.

## Versions

| Dependency | Before | After |
|---|---:|---:|
| `org.apache.commons:commons-csv` | 1.8 | **1.14.1** |
| `org.apache.tika:tika-core` | 2.9.2 | **3.3.3** |
| `commons-beanutils:commons-beanutils` | 1.9.4 | **1.11.0** |
| `com.google.guava:guava` | 33.3.0-jre | **33.8.0-jre** |
| `commons-io:commons-io` | 2.16.1 | **2.22.0** |
| `org.apache.pdfbox:pdfbox` | 3.0.3 | **3.0.8** |
| `com.google.zxing:core` / `javase` | 3.5.3 | **3.5.4** |
| `com.maxmind.geoip2:geoip2` | 4.2.1 | **4.4.0** |
| `com.auth0:java-jwt` | 4.4.0 | **4.6.2** |
| `org.apache.poi:poi` | 5.3.0 | **5.5.1** |
| `org.apache.poi:poi-ooxml` | 5.3.0 | **5.5.1** |
| `org.apache.poi:poi-ooxml-full` | 5.2.3 (transitive) | **5.5.1** |
| `fr.opensagres.xdocreport:...converter.pdf` | 2.1.0 | **2.2.0** |

Decisions:

- `tika-core` goes to **3.3.3** and not 2.9.4 because `GHSA-f58c-gq56-vjjf`
  (XXE) is only fixed from 3.2.2. The project only calls `Tika.detect(byte[])`
  (`CommonService.uploadImage`, `MailUtils.addInlineImage`), stable across 2.x
  and 3.x.
- Tika 3.3.3 requires `commons-io` 2.22.0; POI 5.3.0 requested 2.16.1. The
  explicit pin stays and its comment was updated.
- `poi-ooxml-full` is declared explicitly at 5.5.1 because xdocreport 2.2.0
  still brings 5.2.3 transitively; mixing schema versions with POI 5.5.1
  classes fails at runtime.
- BOM-managed versions were not touched.

Out of scope: POI 6.0.0, Tika 4.2.0, GeoIP2 5.2.0 (majors) and
`mapbox-sdk-turf` (only a pre-release exists).

## Automated checks

| Check | Result |
|---|---|
| `mvn -q -DargLine=-Dapi.version=1.43 clean verify` (Java 25) | **431 tests, 0 failures, 0 errors, 1 skipped** |
| `OpenApiContractTest` | green, contract stays 3.0.1 |
| `mvn dependency:analyze` | no new unused non-starter dependency |
| `mvn dependency:tree` | `poi`, `poi-ooxml`, `poi-ooxml-lite`, `poi-ooxml-full` all 5.5.1; `xdocreport` 2.2.0 |
| `mvn versions:display-dependency-updates` | only majors and the mapbox pre-release remain |

## Runtime validation (fresh `inatrace_149`, `ddl-auto=validate`)

Artifacts were captured before and after each restart with the same requests and
compared structurally (CSV lines, XLSX cells, PDF pages/text, JSON stable
fields). Batch B was also compared against the batch A build in isolation.

| Flow | Endpoint | Result |
|---|---|---|
| Dashboard deliveries (JSON + CSV/Excel/PDF) | `GET /api/dashboard/deliveries-aggregated-data[/export]` | 200, artifacts identical |
| Processing performance (JSON + CSV/Excel/PDF) | `POST /api/dashboard/processing-performance-data[/export]` | 200, artifacts identical |
| Farmer export (EN) | `GET /api/company/userCustomers/{id}/exportFarmerData` | 200, ZIP (`farmers.xlsx` + `geodata.geojson`) identical |
| Farmer export (DE) | same, `language: DE` | **500 before and after** — pre-existing bug, fix pending in PR #145 |
| Deliveries export | `GET /api/chain/stock-order/export/deliveries/company/{id}` | 200, identical |
| Payments and bulk payments export | `GET /api/chain/payment/export/{,bulk-payment/}company/{id}` | 200, identical |
| Label instructions PDF (POI + xdocreport + PDFBox + zxing) | `GET /api/product/label/{id}/instructions` | 200, 1 page, same text, QR decoded: `http://localhost:4200/q/8263f524…` |
| Label content (beanutils) | `GET /api/product/label/content/{id}` | 200, identical |
| Public label and batch verification | `GET /api/public/product/label/{uid}[…]` | 200, identical |
| Public QR tag | `GET /api/public/stock-order/{qrTag}` (with and without history) | 200, identical |
| Analytics (GeoIP2) | `GET /api/product/label/analytics/{uid}`, `POST /api/public/logRequest` | 200, identical |
| Image upload (Tika) | `POST /api/common/image` | valid PNG 200; fake PNG 400 |
| Document upload/download | `POST /api/common/document`, `GET /api/common/{image,document}/{key}` | 200, downloaded bytes match the upload |
| Auth (JWT) | login, refresh, invalid token | 200 / 200 / 401 |

Frontend (Angular, Playwright): dashboard deliveries and processing performance
exported in CSV, Excel and PDF; farmer export ZIP; deliveries XLSX; payments
XLSX. All downloads produced valid files. The public label page could not be
opened: the frontend router does not serve `/q/{uuid}` (nor `/q-cd/{uuid}`).

Mobile: the installed debug build starts and renders, but it was left in guest
mode and the adb-driven login could not be completed reliably in this round, so
the authenticated mobile flow was not re-run. The mobile client is unchanged;
login, public image keys and farmer endpoints are covered by the API and web
checks.

## Test fixtures used locally

The label instructions endpoint depends on two deployment assets that are not
in the repository: `INATrace_labels_text.docx` and `INATrace-labele-o-A4.pdf`.
Minimal fixtures were generated with LibreOffice and placed in the configured
`INATrace.documents.root` only to exercise the pipeline; they are not committed.

## Gap closure (10/11/2026)

The first runtime pass left four paths untested. They were closed afterwards:

| Gap | Result |
|---|---|
| XLSX import (POI parsing) | Template imported with `successful: 1`; farmer created and deleted afterwards. Parsing, validation and persistence work with POI 5.5.1 |
| Resized image variants | `GET /api/common/image/{key}/SMALL` 200 (3,829 B) and `/LARGE` 200 (12,029 B) |
| Public image keys | Covered by `PublicLabelApiTest` in the suite (`/public/image/{key}`, `/SMALL` and private-key rejection) |
| GeoIP2 ingestion | With a MaxMind test database, `X-Forwarded-For: 81.2.69.142` persisted `United Kingdom / London` in `RequestLog` |
| `POST /api/public/logRequest` | 403 without the analytics token (expected), 200 with it; row persisted |
| Logout | 200, and the next authenticated call returns 401 |
| Frontend unit tests | `npm run test:ci`: **138 SUCCESS** |
| Mobile unit tests | `npx jest --ci`: **8 suites, 32 tests, all pass** |

Pre-existing behaviour found while closing the gaps, **confirmed on the old
build as well** (same NPE, Guava 33.3.0 line numbers): a `PUT /api/product`
carrying `photo.storageKey` returns 500. It is unrelated to this batch and was
not fixed here.

Mobile authenticated flow: the login screen opens through the deep link
(`myapp://login`) and the username field accepts input, but the password field
did not accept adb-injected text (`input text` and key events), so the
authenticated flow could not be completed. The mobile client itself is unchanged
by this batch.

## Follow-ups

- German farmer export 500 (PR #145 still open) — not caused by this change.
- Dashboard deliveries export throws NPE when an aggregated bucket has no
  production date (`DashboardService.createDateColumn`); test data was completed
  to capture the artifacts. Worth a separate issue.
- Public label route used by the QR (`/q/{uuid}`) is not served by the frontend
  router.
- `itext` 2.1.7 (transitive from xdocreport) still carries
  `GHSA-86p9-x5pw-94qx`; replacing it with OpenPDF is a separate change.
