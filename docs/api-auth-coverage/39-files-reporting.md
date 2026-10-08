# Files, settings and reporting — issue #39

Issue: [#39](https://github.com/agstack/inatrace-backend/issues/39).

`CommonFileApiAuthTest` and `MultiTenantIsolationTest` exercise the effective
routes through MockMvc, the real Spring Security filter chain, JWT cookies and
the shared MySQL Testcontainer. Fixtures use Acme and Rival as unrelated
tenants. The storage fixture creates actual temporary files and deletes every
file and image variant that it uploads, because the test transaction rolls
back database rows but not filesystem bytes.

`CommonController` has no company-scoped resource for uploads: its private
temporary storage key is the access capability. The test proves that the key
is opaque and bound to the authenticated user who received it; attachment of a
document to a tenant-owned resource is covered by the relevant domain area.

| Method | Route | Expected policy | Test evidence | Result |
|---|---|---|---|---|
| GET | `/api/common/countries` | Any authenticated user reads global catalog; anonymous is 401 | `countriesAndGlobalSettingsAreAuthenticatedGlobalReads` | Verified |
| POST | `/api/common/document` | Authenticated upload; anonymous upload creates no document row or file | `privateDocumentKeyIsUsableOnlyByTheUserWhoReceivedIt`, `rejectedUploadsDoNotCreateDocumentRowsOrFiles` | Verified |
| POST | `/api/common/image` | Authenticated image upload; invalid image creates no row or file | `privateImageAndItsResizedVariantAreUsableOnlyByTheUserWhoReceivedTheirKey`, `rejectedUploadsDoNotCreateDocumentRowsOrFiles` | Verified |
| GET | `/api/common/document/{storageKey}` | Only recipient of private temporary key downloads bytes; internal key and anonymous request are refused | `privateDocumentKeyIsUsableOnlyByTheUserWhoReceivedIt` | Verified |
| GET | `/api/common/image/{storageKey}` | Only recipient of private temporary key downloads original image | `privateImageAndItsResizedVariantAreUsableOnlyByTheUserWhoReceivedTheirKey` | Verified |
| GET | `/api/common/image/{storageKey}/{size}` | Only recipient of private temporary key downloads resized image | `privateImageAndItsResizedVariantAreUsableOnlyByTheUserWhoReceivedTheirKey` | Verified |
| GET | `/api/common/globalSettings/{name}` | Authenticated global read; not a cross-tenant resource; anonymous is 401 | `countriesAndGlobalSettingsAreAuthenticatedGlobalReads` | Verified |
| POST | `/api/common/globalSettings/{name}` | `SYSTEM_ADMIN` only; refused roles and anonymous request leave persisted value unchanged | `onlySystemAdminCanMutateGlobalSettingsWithoutChangingThemOnRefusal` | Verified |
| GET | `/api/dashboard/deliveries-aggregated-data` | Enrolled company user only; foreign tenant is 403, anonymous is 401 | `dashboardOfForeignCompanyIsRejected`, `ownCompanyDashboardIsReadable`, `dashboardEndpointsRejectAnonymousRequests` | Verified |
| GET | `/api/dashboard/deliveries-aggregated-data/export` | Enrolled company user only; foreign export has no delivery marker or file bytes | `deliveriesCsvExportOfForeignCompanyIsRejected`, `ownCompanyDeliveriesCsvIsExportable`, `dashboardEndpointsRejectAnonymousRequests` | Verified |
| POST | `/api/dashboard/processing-performance-data` | Enrolled company user only; foreign tenant is 403, anonymous is 401 | `processingPerformanceOfForeignCompanyIsRejected`, `ownCompanyProcessingPerformanceIsReadableAndExportable`, `dashboardEndpointsRejectAnonymousRequests` | Verified |
| POST | `/api/dashboard/processing-performance-data/export` | Enrolled company user only; foreign tenant is 403, anonymous is 401 | `processingPerformanceExportOfForeignCompanyIsRejected`, `ownCompanyProcessingPerformanceIsReadableAndExportable`, `dashboardEndpointsRejectAnonymousRequests` | Verified with finding below |

## Historical routes removed before this coverage

The issue count is 14, while Spring registers 12 effective operations. Commit
`b7088c9` removed `CommonCsvController` and these two historical routes:

| Historical method and route | Current replacement / evidence |
|---|---|
| POST `/api/chain/csv/payments/company/{id}` | `/api/chain/payment/export/company/{id}` — covered by #35 |
| POST `/api/chain/csv/purchases/company/{id}` | `/api/chain/stock-order/export/deliveries/company/{companyId}` — covered by #35 |

## Finding: optional evidence fields break a valid performance export

The performance export route returns HTTP 500 when `evidenceFields` is omitted
from an otherwise valid request. The calculation route accepts the same
optional field as absent. This is not an authorization bypass: tenant access
is checked before the failure. The current behavior is characterized by
`processingPerformanceExportWithoutEvidenceFieldsIsCharacterizedAsServerError`.

No production fix is included in this coverage branch. The diagnosis and
proposed regression fix are recorded in the workspace plan
`plans/issues-to-be-fixed/39-processing-performance-export-null-evidence-fields.md`.
