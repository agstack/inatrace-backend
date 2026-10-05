# Supply Chain authorization matrix — #35

The current application registers **51** operations in the nine controllers
named by #35. This is a route inventory and work ledger, not a claim that all
51 are covered yet. Each row needs an entitled request with a real fixture, a
foreign-tenant request, and an anonymous request. Refused bodies must exclude
the fixture marker; refused writes must leave database state unchanged. A
`Pending` row is not counted as covered, even if its service appears to check
permission. `Partial` identifies an HTTP scenario still missing.

The tests use the shared MySQL Testcontainer through
`AbstractMySqlIntegrationTest`, `@SpringBootTest`, MockMvc with the real
filter chain, and `TokenService` access cookies. Beyco's successful outbound
requests use a loopback HTTP server; rejected requests must not reach it.

| Method | `/api` route | Expected authorization boundary | Evidence | Status |
|---|---|---|---|---|
| GET | `/api/chain/stock-order/{id}` | Company associated through product with stock owner | `StockOrderApiAuthTest#individualOrderAndHistoryAreScopedToAssociatedCompany` | Verified |
| GET | `/api/chain/stock-order/{id}/processing-order` | Company associated through product with stock owner | `StockOrderApiAuthTest#processingOrderReadFromStockOrderChecksTheStockOwnersProductConnection` | Verified |
| GET | `/api/chain/stock-order/list/facility/{facilityId}/available` | Company associated through product with facility owner | `StockOrderApiAuthTest#allStockListsCheckTheRequestedCompanyOrFacility` | Verified |
| GET | `/api/chain/stock-order/list/facility/{facilityId}` | Enrolled in facility owner company | `StockOrderApiAuthTest#allStockListsCheckTheRequestedCompanyOrFacility` | Verified |
| GET | `/api/chain/stock-order/list/company/{companyId}/orders-for-customers` | Enrolled in path company; nested facility/customer filters checked | `StockOrderApiAuthTest#allStockListsCheckTheRequestedCompanyOrFacility`; `#companyListsKeepNestedFarmerCustomerAndFacilityFiltersWithinPathCompany` | Verified |
| GET | `/api/chain/stock-order/list/company/{companyId}/quote-orders` | Enrolled in path quote company | `StockOrderApiAuthTest#quoteOrderListChecksEnrollmentBeforeReturningRows` | Verified |
| GET | `/api/chain/stock-order/list/company/{companyId}` | Enrolled in path company; farmer filters checked | `StockOrderApiAuthTest#allStockListsCheckTheRequestedCompanyOrFacility`; `#companyListsKeepNestedFarmerCustomerAndFacilityFiltersWithinPathCompany` | Verified |
| POST | `/api/chain/stock-order/bulk-purchase` | Enrolled in facility company; every farmer/order scoped | `StockOrderApiAuthTest#bulkPurchaseRejectsForeignTenantBeforeInsertingAnything`; `#bulkPurchaseWithMixedTenantFarmersWritesNoOrders` | Verified; mixed-farmer partial-write guard fixed here |
| PUT | `/api/chain/stock-order` | Owner of existing order and selected facility; nested IDs scoped | `StockOrderApiAuthTest#purchaseCreationAndUpdateRejectForeignTenantWithoutWrites`; `#ownerCannotAttachRivalFarmerToItsPurchase`; `#ownerCannotAttachRivalCustomerToItsStockOrder`; `#ownerCannotAttachRivalProductOrderToItsStockOrder` | Verified; nested farmer, customer and product-order guards fixed here |
| DELETE | `/api/chain/stock-order/{id}` | Company admin of stock owner; no stock/history mutation on denial | `StockOrderApiAuthTest#deleteRejectsForeignTenantAndLeavesOrderInPlace` | Verified |
| GET | `/api/chain/stock-order/{id}/aggregated-history` | Product-associated company before history construction | `StockOrderApiAuthTest#individualOrderAndHistoryAreScopedToAssociatedCompany` | Verified |
| GET | `/api/chain/stock-order/{id}/exportGeoData` | Product-associated company before GeoJSON bytes | `StockOrderApiAuthTest#geoJsonExportAuthorizesBeforeStreamingCoordinates` | Verified |
| GET | `/api/chain/stock-order/export/deliveries/company/{companyId}` | Enrolled in path company before XLSX bytes | `StockOrderApiAuthTest#deliveryExportAuthorizesBeforeWritingWorkbook` | Verified |
| GET | `/api/chain/processing-order/{id}` | Product-associated company of processing action owner | `StockOrderApiAuthTest#processingOrderReadAndDeleteAreTenantScoped` | Verified |
| PUT | `/api/chain/processing-order` | Authorized processing action and every input/output order | `StockOrderApiAuthTest#processingOrderCreationChecksActionAndOutputOwnerBeforeWriting`; `#companyCannotCreateProcessingWithForeignAction`; `#existingProcessingOrderCannotMoveToForeignAction`; `#processingOrderCannotTakeOverForeignOutputStockOrder`; `#processingOrderCannotCreateOutputAtForeignFacility`; `#processingOrderCannotUseForeignInputStockOrder`; `#ownerCanCreateProcessingWithItsOwnInputStockOrder` | Verified; action, input and output guards fixed here |
| DELETE | `/api/chain/processing-order/{id}` | Company admin of action owner; stock/history unchanged on denial | `StockOrderApiAuthTest#processingOrderReadAndDeleteAreTenantScoped` | Verified |
| GET | `/api/chain/processing-action/list/company/{id}` | Enrolled in path company | `ProcessingActionApiAuthTest#processingActionReadsAreTenantScoped` | Verified |
| GET | `/api/chain/processing-action/{id}` | Enrolled in action owner company | `ProcessingActionApiAuthTest#processingActionReadsAreTenantScoped` | Verified |
| GET | `/api/chain/processing-action/{id}/detail` | Enrolled in action owner company | `ProcessingActionApiAuthTest#processingActionReadsAreTenantScoped` | Verified |
| PUT | `/api/chain/processing-action` | Company admin of owner; existing row cannot move tenants | `ProcessingActionApiAuthTest#updateCannotTransferForeignActionToRequestersCompany`; `#foreignUserCannotCreateActionForAcme` | Verified; takeover guard fixed here |
| DELETE | `/api/chain/processing-action/{id}` | Company admin of action owner; row unchanged on denial | `ProcessingActionApiAuthTest#foreignDeleteLeavesActionAndTranslationsUntouched` | Verified |
| GET | `/api/chain/transaction/list/input/stock-order/{stockOrderId}` | Enrolled in stock owner company | `StockOrderApiAuthTest#inputTransactionListChecksStockOrderOwner` | Verified |
| PUT | `/api/chain/transaction/{id}/approve` | Enrolled in target quote stock owner; no quantity/status change on denial | `StockOrderApiAuthTest#approvingTransactionIsRestrictedToQuoteOwnerAndPreservesRefusedState` | Verified |
| PUT | `/api/chain/transaction/{id}/reject` | Enrolled in target quote stock owner; no quantity/status change on denial | `StockOrderApiAuthTest#rejectingTransactionIsRestrictedToQuoteOwnerAndPreservesRefusedState` | Verified |
| GET | `/api/chain/payment/{id}` | Enrolled in paying company | `PaymentApiAuthTest#paymentAndBulkPaymentByIdAreTenantScoped` | Verified |
| GET | `/api/chain/payment/bulk-payment/{id}` | Enrolled in bulk payment paying company | `PaymentApiAuthTest#paymentAndBulkPaymentByIdAreTenantScoped` | Verified |
| GET | `/api/chain/payment/list/company/{id}` | Enrolled in path company | `PaymentApiAuthTest#paymentListsCheckBothCompanyAndPurchaseEnrollment` | Verified |
| GET | `/api/chain/payment/list/purchase/{id}` | Enrolled in purchase stock owner | `PaymentApiAuthTest#paymentListsCheckBothCompanyAndPurchaseEnrollment` | Verified |
| GET | `/api/chain/payment/export/company/{id}` | Enrolled in path company before XLSX bytes | `PaymentApiAuthTest#paymentExportsCheckEnrollmentBeforeWritingWorkbook` | Verified |
| GET | `/api/chain/payment/list/bulk-payment/company/{id}` | Enrolled in path company | `PaymentApiAuthTest#bulkPaymentListChecksCompanyEnrollment` | Verified |
| GET | `/api/chain/payment/export/bulk-payment/company/{id}` | Enrolled in path company before XLSX bytes | `PaymentApiAuthTest#paymentExportsCheckEnrollmentBeforeWritingWorkbook` | Verified |
| PUT | `/api/chain/payment` | Existing paying company or new purchase owner; no payment/balance change on denial | `PaymentApiAuthTest#paymentUpdateChecksOwnerBeforeChangingStatus`; `#paymentCreationChecksPurchaseCompanyBeforeInserting` | Verified |
| POST | `/api/chain/payment/bulk-payment` | Enrolled in paying company; every child purchase scoped | `PaymentApiAuthTest#bulkPaymentCreationChecksPayingCompanyBeforeInserting`; `#bulkPaymentWithForeignChildWritesNoPayments` | Verified; mixed-child partial-write guard fixed here |
| DELETE | `/api/chain/payment/{id}` | Enrolled in paying company; payment/balance unchanged on denial | `PaymentApiAuthTest#rejectedDeleteLeavesPaymentUntouched` | Verified |
| GET | `/api/chain/facility/list/company/{id}/all` | Enrolled in path company | `FacilityApiAuthTest#facilityReadsAreScopedToOwningCompany` | Verified |
| GET | `/api/chain/facility/list/company/{id}` | Enrolled in path company | `FacilityApiAuthTest#facilityReadsAreScopedToOwningCompany` | Verified |
| GET | `/api/chain/facility/list/company/{id}/available-selling` | Enrolled in path company; product associations bound result | `FacilityApiAuthTest#availableSellingListChecksEnrollmentEvenWhenNoProductsAreConnected`; `#connectedPublicSellerIsVisibleButUnrelatedTenantIsNot` | Verified |
| GET | `/api/chain/facility/list/collecting/company/{id}` | Enrolled in path company | `FacilityApiAuthTest#facilityReadsAreScopedToOwningCompany` | Verified |
| GET | `/api/chain/facility/{id}` | Owner or product-connected company if public facility | `FacilityApiAuthTest#facilityReadsAreScopedToOwningCompany`; `#connectedPublicSellerIsVisibleButUnrelatedTenantIsNot` | Verified |
| GET | `/api/chain/facility/{id}/detail` | Enrolled in facility owner company | `FacilityApiAuthTest#facilityReadsAreScopedToOwningCompany` | Verified |
| PUT | `/api/chain/facility` | Company admin of existing/new facility owner; nested IDs scoped | `FacilityApiAuthTest#facilityUpdateAndCreationRejectForeignCompanyWithoutMutation`; `#facilityCannotAttachUnrelatedTenantsFinalProduct`; `#facilityCanAttachFinalProductFromConnectedValueChain` | Verified; final-product association guard fixed here |
| PUT | `/api/chain/facility/{id}/activate` | Company admin of facility owner; status unchanged on denial | `FacilityApiAuthTest#foreignUserCannotActivateOrDeactivateFacility` | Verified |
| PUT | `/api/chain/facility/{id}/deactivate` | Company admin of facility owner; status unchanged on denial | `FacilityApiAuthTest#foreignUserCannotActivateOrDeactivateFacility` | Verified |
| DELETE | `/api/chain/facility/{id}` | Company admin of facility owner; dependencies unchanged on denial | `FacilityApiAuthTest#rejectedFacilityDeletePreservesFacilityAndLocation`; `#ownerCannotDeleteFacilityWithStockHistory` | Verified |
| GET | `/api/chain/group-stock-order/list/facility/{facilityId}` | Enrolled in facility owner company before aggregation | `GroupStockOrderApiAuthTest` | Verified; missing guard fixed here |
| GET | `/api/chain/product-order/{id}` | Enrolled in order facility owner or system admin | `ProductOrderApiTest` (merged suite) | Verified |
| POST | `/api/chain/product-order` | Enrolled in order facility owner; customer/items scoped | `StockOrderApiAuthTest#productOrderCreationRejectsForeignTenantWithoutAnyOrderOrStockWrites`; `#productOrderCannotAttachCustomerFromAnotherTenant`; `#productOrderCannotCreateItsItemAtForeignFacility` | Verified; nested customer/facility guards fixed here |
| GET | `/api/chain/beyco-order/company/{companyId}/token` | Enrolled in company and integration enabled before outbound request | `BeycoOrderApiAuthTest#ownerCanUseBeycoTransportWhileStrangerCannotReachIt` | Verified; missing guard fixed here |
| GET | `/api/chain/beyco-order/company/{companyId}/token/refresh` | Enrolled in company and integration enabled before outbound request | `BeycoOrderApiAuthTest#ownerCanUseBeycoTransportWhileStrangerCannotReachIt` | Verified; missing guard fixed here |
| GET | `/api/chain/beyco-order/company/{companyId}/fields` | Enrolled in company; every requested stock ID owned by company | `BeycoOrderApiAuthTest#ownerCanReadFieldsForOwnStockOrder`; `#evenOwnerCannotMixForeignStockOrderIntoBeycoFields` | Verified; both missing guards fixed here |
| POST | `/api/chain/beyco-order/company/{companyId}/order` | Enrolled in company and integration enabled before outbound request | `BeycoOrderApiAuthTest#ownerCanUseBeycoTransportWhileStrangerCannotReachIt` | Verified; missing guard fixed here |

The payment list has two distinct service branches: company ID and purchase ID.
Both have a positive response containing `ACME-PAYMENT-RECEIPT-ONLY`, a 403
response without it for a rival-company JWT, and a 401 anonymous response.
Payment and bulk-payment XLSX controls open the workbook and inspect the
receipt cell; refused responses are checked for marker absence and do not
retain the binary content type. The group-stock test first failed with HTTP
200 and the Acme lot in the rival response, then passed after the service
guard was added. Beyco tests similarly reproduced both cross-tenant field
reads and mixed-company stock IDs before the fix.
