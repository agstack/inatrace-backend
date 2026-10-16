# Codebooks authorization matrix — issue #38

Source: [agstack/inatrace-backend#38](https://github.com/agstack/inatrace-backend/issues/38)

The issue describes 44 historical operations in nine controllers. The live
Spring MVC inventory contains 41. The three missing historical mappings were
removed in `b7088c9` and are accounted for in the campaign
[README](README.md): the generic currency list became enabled/disabled lists,
and each single-value-chain evidence list became a list-by-value-chains route.

`CodebookApiAuthTest` exercises the actual cookie/JWT filter chain with a
system administrator, regional administrator, ordinary enrolled company user,
and anonymous caller. It uses distinctive fixtures, verifies that denied
responses do not disclose them, and reloads records after regional edit
attempts.

## CurrencyController decision

The two `CurrencyController` routes are registered, authenticated, and
reachable through the real filter chain; the regression suite invokes both.
They remain part of the supported contract for this issue. A search of the
repository's open and closed issues found no tracked CurrencyController
deprecation or unreachable-endpoint work, so #38 does not create or assume a
deprecation decision. Any future removal must be proposed and tracked as a
separate API-contract change.

## Effective policy

| Route group | System admin | Regional admin | Ordinary company user | Anonymous | Regression coverage |
|---|---|---|---|---|---|
| All reads | allowed | allowed | allowed | 401 | `everyCurrentCodebookReadIsAvailableToAuthenticatedUsersOnly` |
| Facility, measure-unit, semi-product, processing-evidence, value-chain upserts | create/edit | create; existing-record edit rejected by service | 403 | 401 | `regionalAdminsCanCreateButCannotEditExistingRestrictedCodebooks` |
| Currency enable | allowed | allowed | 403 | 401 | `systemAdminsCanEditCodebooksAndPerformSystemOnlyStatusChanges` |
| Currency disable | allowed | 403 | 403 | 401 | `regionalAdminsCanCreateButCannotEditExistingRestrictedCodebooks`, `ordinaryCompanyUsersCannotModifyAnyCodebookAndResponsesDoNotLeakMarkers` |
| Value-chain enable/disable/delete | allowed | 403 | 403 | 401 | `systemAdminsCanEditCodebooksAndPerformSystemOnlyStatusChanges`, `regionalAdminsCanCreateButCannotEditExistingRestrictedCodebooks`, `ordinaryCompanyUsersCannotModifyAnyCodebookAndResponsesDoNotLeakMarkers` |
| Product-type create | allowed | allowed | 403 | 401 | `ordinaryCompanyUsersCannotModifyAnyCodebookAndResponsesDoNotLeakMarkers` |
| Product-type update/delete | allowed | 403 | 403 | 401 | `regionalAdminsCanCreateButCannotEditExistingRestrictedCodebooks`, `ordinaryCompanyUsersCannotModifyAnyCodebookAndResponsesDoNotLeakMarkers` |
| Other codebook deletes | allowed | 403 | 403 | 401 | `regionalAdminsCanCreateButCannotEditExistingRestrictedCodebooks`, `ordinaryCompanyUsersCannotModifyAnyCodebookAndResponsesDoNotLeakMarkers` |

The service-layer edit guard applies to facility types, measurement units,
semi-products, processing-evidence types, processing-evidence fields, and
value chains. It is intentionally characterized here rather than relaxed by
this test-only change.

## Registered route inventory

| Method | Route | Controller method | Expected access |
|---|---|---|---|
| GET | `/api/chain/currency-type/list/enabled` | `CurrencyTypeController#getEnabledCurrencyTypes` | authenticated read |
| GET | `/api/chain/currency-type/list/disabled` | `CurrencyTypeController#getDisabledCurrencyTypes` | authenticated read |
| GET | `/api/chain/currency/convert/{value}/{from}/to/{to}` | `CurrencyController#convert` | authenticated read |
| GET | `/api/chain/currency/convert/{value}/{from}/to/{to}/on/{date}` | `CurrencyController#convertAtDate` | authenticated read |
| GET | `/api/chain/facility-type/list` | `FacilityTypeController#getFacilityTypeList` | authenticated read |
| GET | `/api/chain/facility-type/{id}` | `FacilityTypeController#getFacilityType` | authenticated read |
| GET | `/api/chain/measure-unit-type/list` | `MeasureUnitTypeController#getMeasureUnitTypeList` | authenticated read |
| GET | `/api/chain/measure-unit-type/{id}` | `MeasureUnitTypeController#getMeasurementUnitType` | authenticated read |
| GET | `/api/chain/processing-evidence-field/list` | `ProcessingEvidenceFieldController#getProcessingEvidenceFieldList` | authenticated read |
| GET | `/api/chain/processing-evidence-field/list/by-value-chains` | `ProcessingEvidenceFieldController#listProcessingEvidenceFieldsByValueChains` | authenticated read |
| GET | `/api/chain/processing-evidence-field/{id}` | `ProcessingEvidenceFieldController#getProcessingEvidenceField` | authenticated read |
| GET | `/api/chain/processing-evidence-type/list` | `ProcessingEvidenceTypeController#getProcessingEvidenceTypeList` | authenticated read |
| GET | `/api/chain/processing-evidence-type/list/by-value-chains` | `ProcessingEvidenceTypeController#listProcessingEvidenceTypesByValueChains` | authenticated read |
| GET | `/api/chain/processing-evidence-type/{id}` | `ProcessingEvidenceTypeController#getProcessingEvidenceType` | authenticated read |
| GET | `/api/chain/semi-product/list` | `SemiProductController#getSemiProductList` | authenticated read |
| GET | `/api/chain/semi-product/list/by-value-chains` | `SemiProductController#getSemiProductListByValueChains` | authenticated read |
| GET | `/api/chain/semi-product/{id}` | `SemiProductController#getSemiProduct` | authenticated read |
| GET | `/api/chain/semi-product/{id}/detail` | `SemiProductController#getSemiProductDetails` | authenticated read |
| GET | `/api/chain/value-chain/list` | `ValueChainController#getValueChainList` | authenticated read |
| GET | `/api/chain/value-chain/{id}` | `ValueChainController#getValueChain` | authenticated read |
| GET | `/api/product-type` | `ProductTypeController#getProductTypes` | authenticated read |
| GET | `/api/product-type/{id}` | `ProductTypeController#getProductType` | authenticated read; see note below |
| PUT | `/api/chain/currency-type/{id}/enable` | `CurrencyTypeController#enableCurrency` | system/regional |
| PUT | `/api/chain/currency-type/{id}/disable` | `CurrencyTypeController#disableCurrency` | system only |
| PUT | `/api/chain/facility-type` | `FacilityTypeController#createOrUpdateFacilityType` | system; regional create only |
| PUT | `/api/chain/measure-unit-type` | `MeasureUnitTypeController#createOrUpdateMeasurementUnitType` | system; regional create only |
| PUT | `/api/chain/processing-evidence-field` | `ProcessingEvidenceFieldController#createOrUpdateProcessingEvidenceField` | system; regional create only |
| PUT | `/api/chain/processing-evidence-type` | `ProcessingEvidenceTypeController#createOrUpdateProcessingEvidenceType` | system; regional create only |
| PUT | `/api/chain/semi-product` | `SemiProductController#createOrUpdateSemiProduct` | system; regional create only |
| PUT | `/api/chain/value-chain` | `ValueChainController#createOrUpdateValueChain` | system; regional create only |
| PUT | `/api/product-type` | `ProductTypeController#updateProductType` | system only |
| POST | `/api/chain/value-chain/{id}/enable` | `ValueChainController#enableValueChain` | system only |
| POST | `/api/chain/value-chain/{id}/disable` | `ValueChainController#disableValueChain` | system only |
| POST | `/api/product-type` | `ProductTypeController#createProductType` | system/regional |
| DELETE | `/api/chain/facility-type/{id}` | `FacilityTypeController#deleteFacilityType` | system only |
| DELETE | `/api/chain/measure-unit-type/{id}` | `MeasureUnitTypeController#deleteMeasurementUnitType` | system only |
| DELETE | `/api/chain/processing-evidence-field/{id}` | `ProcessingEvidenceFieldController#deleteProcessingEvidenceField` | system only |
| DELETE | `/api/chain/processing-evidence-type/{id}` | `ProcessingEvidenceTypeController#deleteProcessingEvidenceType` | system only |
| DELETE | `/api/chain/semi-product/{id}` | `SemiProductController#deleteSemiProduct` | system only |
| DELETE | `/api/chain/value-chain/{id}` | `ValueChainController#deleteValueChain` | system only |
| DELETE | `/api/product-type/{id}` | `ProductTypeController#deleteProductType` | system only; see note below |

## Characterized contract finding

`ProductTypeController` maps detail and delete as `/{id}`, but binds the value
with `@RequestParam("id")` rather than `@PathVariable`. The regression test
therefore supplies both the path segment and the `id` query parameter to
exercise the currently deployed contract. This is documented behavior for the
authorization campaign, not a silent API correction; the binding mismatch
should be considered separately if the public API contract is to be repaired.
