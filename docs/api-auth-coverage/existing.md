# Existing suites — route-level baseline for #34

This table covers the 45 registered operations in `UserController` and
`CompanyController` at commit `018a7f8`. It cites representative HTTP test
methods from the merged suites. **Partial** means at least one relevant HTTP
case exists; it does not claim that all positive, denied, anonymous, leakage,
and persisted-state scenarios required by #34 are present. **Gap** means no
HTTP case was identified in the merged suites inspected here. The five
`AuthenticationTokenValidityTest` cases exercise token validity without
calling an API route, so they complement this table but cannot fill an HTTP
operation row. This first pass found **29 partial routes and 16 gaps** among
the 45 operations; it does not change the parent issue's acceptance criteria.

The main issue describes these areas as covered because suites exist. This
audit does not silently mark an entire controller covered on that basis.
Before closing #34, each partial row needs its missing scenarios identified
and each gap needs a test or an explicit policy-based exception. The campaign
owner can schedule those additions in a separate #34 follow-up PR so the five
sub-issue PRs remain focused.

| Method | Registered route | Handler | Area | Existing HTTP evidence | Status |
|---|---|---|---|---|---|
| `DELETE` | `/api/company/companyCustomers/{id}` | `CompanyController#deleteCompanyCustomer` | Companies | No HTTP case identified in the merged suites | Gap |
| `DELETE` | `/api/company/userCustomers/{id}` | `CompanyController#deleteUserCustomer` | Farmers/plots | FarmerAndPlotApiTest#foreignFarmerCannotBeDeleted | Partial |
| `GET` | `/api/company/admin/list` | `CompanyController#listCompaniesAdmin` | Companies | CompanyApiTest#adminListShowsAllCompaniesToSystemAdmin; #adminListRefusesPlainUser | Partial |
| `GET` | `/api/company/associations/{id}` | `CompanyController#getAssociations` | Companies | No HTTP case identified in the merged suites | Gap |
| `GET` | `/api/company/companyCustomers/list/{companyId}` | `CompanyController#getCompanyCustomersList` | Companies | No HTTP case identified in the merged suites | Gap |
| `GET` | `/api/company/companyCustomers/{id}` | `CompanyController#getCompanyCustomer` | Companies | No HTTP case identified in the merged suites | Gap |
| `GET` | `/api/company/list` | `CompanyController#listCompanies` | Companies | CompanyApiTest#listReturnsOnlyOwnCompanies | Partial |
| `GET` | `/api/company/profile/{id}` | `CompanyController#getCompany` | Companies | CompanyApiTest#getCompanyRefusesUnrelatedUser; #getCompanyAllowsEnrolledUser | Partial |
| `GET` | `/api/company/profile/{id}/name` | `CompanyController#getCompanyName` | Companies | CompanyApiTest#getCompanyNameRefusesUnrelatedUser; #getCompanyNameAllowsEnrolledUser | Partial |
| `GET` | `/api/company/profile/{id}/onboardingState` | `CompanyController#getCompanyOnboardingState` | Companies | No HTTP case identified in the merged suites | Gap |
| `GET` | `/api/company/profile/{id}/users` | `CompanyController#getCompanyUsers` | Companies | CompanyApiTest#getCompanyUsersRefusesUnrelatedUser; #getCompanyUsersListsEnrolledUsers | Partial |
| `GET` | `/api/company/userCustomers/{companyId}/exportFarmerData` | `CompanyController#exportFarmerDataByCompany` | Farmers/plots | FarmerAndPlotApiTest#ownCompanyFarmerExportIsDownloadable; #foreignCompanyFarmerExportIsRejected | Partial |
| `GET` | `/api/company/userCustomers/{companyId}/plots` | `CompanyController#getUserCustomersPlotsForCompany` | Farmers/plots | FarmerAndPlotApiTest#ownCompanyPlotsAreReadable; #foreignCompanyPlotsAreRejected | Partial |
| `GET` | `/api/company/userCustomers/{companyId}/{type}` | `CompanyController#getUserCustomersForCompanyAndType` | Farmers/plots | FarmerAndPlotApiTest#ownCompanyFarmerListIsReadable; #foreignCompanyFarmerListIsRejected | Partial |
| `GET` | `/api/company/userCustomers/{id}` | `CompanyController#getUserCustomer` | Farmers/plots | FarmerAndPlotApiTest#ownFarmerIsReadable; #foreignFarmerByIdIsRejected | Partial |
| `GET` | `/api/company/userCustomers/{id}/exportGeoData` | `CompanyController#exportUserCustomerGeoData` | Farmers/plots | FarmerAndPlotApiTest#ownFarmerGeoDataIsExportable; #foreignFarmerGeoDataExportIsRejected | Partial |
| `GET` | `/api/company/{id}/connected-companies` | `CompanyController#getConnectedCompanies` | Companies | No HTTP case identified in the merged suites | Gap |
| `GET` | `/api/company/{id}/product-types` | `CompanyController#getCompanyProductTypes` | Companies | No HTTP case identified in the merged suites | Gap |
| `GET` | `/api/company/{id}/value-chains` | `CompanyController#getCompanyValueChains` | Companies | No HTTP case identified in the merged suites | Gap |
| `GET` | `/api/user/admin/list` | `UserController#adminListUsers` | Auth/Users | UserAuthApiTest#plainUserCannotListAllUsers; #systemAdminCanListAllUsers | Partial |
| `GET` | `/api/user/admin/profile/{id}` | `UserController#getProfileForAdmin` | Auth/Users | No HTTP case identified in the merged suites | Gap |
| `GET` | `/api/user/list` | `UserController#listUsers` | Auth/Users | No HTTP case identified in the merged suites | Gap |
| `GET` | `/api/user/profile` | `UserController#getProfileForUser` | Auth/Users | UserAuthApiTest#profileRequiresAuthentication; #accessCookieOpensProfile | Partial |
| `GET` | `/api/user/regional-admin/list` | `UserController#regionalAdminListUsers` | Auth/Users | UserAuthApiTest#plainUserCannotListRegionalUsers | Partial |
| `POST` | `/api/company/companyCustomers` | `CompanyController#createCompanyCustomer` | Companies | No HTTP case identified in the merged suites | Gap |
| `POST` | `/api/company/create` | `CompanyController#createCompany` | Companies | CompanyApiTest#createRequiresAuthentication; #systemAdminCanCreateCompany | Partial |
| `POST` | `/api/company/execute/{action}` | `CompanyController#executeAction` | Companies | CompanyApiTest#systemAdminCanActivateCompany; #unrelatedPlainUserCannotExecuteAction | Partial |
| `POST` | `/api/company/userCustomers/add/{companyId}` | `CompanyController#addUserCustomer` | Farmers/plots | FarmerAndPlotApiTest#farmerCannotBeAddedToForeignCompany | Partial |
| `POST` | `/api/company/userCustomers/import/farmers/{companyId}/{documentId}` | `CompanyController#importFarmersSpreadsheet` | Farmers/plots | No HTTP case identified in the merged suites | Gap |
| `POST` | `/api/company/userCustomers/{id}/plots/add` | `CompanyController#createUserCustomerPlot` | Farmers/plots | FarmerAndPlotApiTest#plotCanBeAddedToOwnFarmer; #plotCannotBeAddedToForeignFarmer | Partial |
| `POST` | `/api/company/userCustomers/{id}/plots/{plotId}/updateGeoID` | `CompanyController#refreshGeoIDForUserCustomerPlot` | Farmers/plots | FarmerAndPlotApiTest#geoIdRefreshOnForeignPlotIsRejected | Partial |
| `POST` | `/api/company/userCustomers/{id}/uploadGeoData` | `CompanyController#uploadUserCustomerGeoData` | Farmers/plots | FarmerAndPlotApiTest#foreignFarmerGeoDataUploadIsRejected | Partial |
| `POST` | `/api/user/admin/execute/{action}` | `UserController#activateUser` | Auth/Users | No HTTP case identified in the merged suites | Gap |
| `POST` | `/api/user/confirm_email` | `UserController#confirmEmail` | Auth/Users | UserAuthApiTest#confirmEmailAdvancesStatus; #confirmEmailRejectsUnknownToken | Partial |
| `POST` | `/api/user/login` | `UserController#login` | Auth/Users | UserAuthApiTest#loginIssuesBothCookies; #loginRejectsWrongPassword | Partial |
| `POST` | `/api/user/logout` | `UserController#logout` | Auth/Users | UserAuthApiTest#logoutClearsCookies | Partial |
| `POST` | `/api/user/refresh_authentication` | `UserController#refreshAuthentication` | Auth/Users | UserAuthApiTest#refreshIssuesNewAccessCookie; #refreshWithoutCookieIsRejected | Partial |
| `POST` | `/api/user/register` | `UserController#createUser` | Auth/Users | UserAuthApiTest#registerCreatesAnInactiveAccount; #registerRejectsIncompleteBody | Partial |
| `POST` | `/api/user/request_reset_password` | `UserController#requestResetPassword` | Auth/Users | UserAuthApiTest#resetRequestStoresAToken; #resetRequestForUnknownEmailRevealsNothing | Partial |
| `POST` | `/api/user/reset_password` | `UserController#resetPassword` | Auth/Users | UserAuthApiTest#resetWithValidTokenChangesThePassword; PasswordResetApprovalTest#resetDoesNotStartASessionForAnUnapprovedAccount | Partial |
| `PUT` | `/api/company/companyCustomers` | `CompanyController#updateCompanyCustomer` | Companies | No HTTP case identified in the merged suites | Gap |
| `PUT` | `/api/company/profile` | `CompanyController#updateCompany` | Companies | CompanyApiTest#updateAllowsCompanyAdmin; #updateRefusesUnrelatedUser | Partial |
| `PUT` | `/api/company/userCustomers/edit` | `CompanyController#updateUserCustomer` | Farmers/plots | FarmerAndPlotApiTest#foreignFarmerCannotBeEdited | Partial |
| `PUT` | `/api/user/admin/profile` | `UserController#adminUpdateProfile` | Auth/Users | No HTTP case identified in the merged suites | Gap |
| `PUT` | `/api/user/profile` | `UserController#updateProfile` | Auth/Users | No HTTP case identified in the merged suites | Gap |
