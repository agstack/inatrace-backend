package com.abelium.inatrace.components.company;

import com.abelium.inatrace.api.*;
import com.abelium.inatrace.api.errors.ApiException;
import com.abelium.inatrace.components.company.api.*;
import com.abelium.inatrace.components.company.types.CompanyAction;
import com.abelium.inatrace.components.product.api.ApiListCustomersRequest;
import com.abelium.inatrace.components.product.api.ApiProductType;
import com.abelium.inatrace.components.value_chain.api.ApiValueChain;
import com.abelium.inatrace.security.service.CustomUserDetails;
import com.abelium.inatrace.types.Language;
import com.abelium.inatrace.types.UserCustomerType;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import jakarta.validation.Valid;
import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/company")
public class CompanyController {

	private final CompanyService companyService;

    private final UserCustomerImportService userCustomerImportService;

	@Autowired
	public CompanyController(CompanyService companyService, UserCustomerImportService userCustomerImportService) {
		this.companyService = companyService;
        this.userCustomerImportService = userCustomerImportService;
	}

	@PostMapping(value = "/create")
    @PreAuthorize("hasAnyAuthority('SYSTEM_ADMIN', 'REGIONAL_ADMIN')")
    @Operation(summary = "Create a new company (with the logged-in user as company admin)")
    public ApiResponse<ApiBaseEntity> createCompany(@AuthenticationPrincipal CustomUserDetails authUser, @Valid @RequestBody ApiCompany request) throws ApiException {
		return new ApiResponse<>(companyService.createCompany(authUser.getUserId(), request));
    }
    
    @GetMapping(value = "/list")
    @Operation(summary = "Lists all companies for the logged-in user. Sorting: name or default")
    public ApiPaginatedResponse<ApiCompanyListResponse> listCompanies(@AuthenticationPrincipal CustomUserDetails authUser, 
    		@Valid ApiListCompaniesRequest request) {
    	return new ApiPaginatedResponse<>(companyService.listUserCompanies(authUser.getUserId(), request));
    }
    
    @GetMapping(value = "/admin/list")
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN')")
    @Operation(summary = "Lists all companies. Must be admin. Sorting: name or default")
    public ApiPaginatedResponse<ApiCompanyListResponse> listCompaniesAdmin(@Valid ApiListCompaniesRequest request) {
    	return new ApiPaginatedResponse<>(companyService.listCompanies(request));
    }    
    
    @GetMapping(value = "/profile/{id}")
    @Operation(summary = "Get all info about a company")
    public ApiResponse<ApiCompanyGet> getCompany(@AuthenticationPrincipal CustomUserDetails authUser, 
    		@Valid @Parameter(description = "Record id", required = true) @PathVariable("id") Long id,
    		@Valid @Parameter(description = "language") @RequestParam(value = "language", defaultValue = "EN") String language) throws ApiException {
    	return new ApiResponse<>(companyService.getCompany(authUser, id, Language.valueOf(language)));
    }

    @GetMapping(value = "/profile/{id}/onboardingState")
    @Operation(summary = "Get the company onboarding state")
    public ApiResponse<ApiCompanyOnboardingState> getCompanyOnboardingState(@AuthenticationPrincipal CustomUserDetails authUser,
                                                                            @PathVariable("id") Long id) throws ApiException {
        return new ApiResponse<>(companyService.getCompanyOnboardingState(authUser, id));
    }

    @GetMapping(value = "/profile/{id}/name")
    @Operation(summary = "Get the company name and abbreviation")
    public ApiResponse<ApiCompanyName> getCompanyName(
            @AuthenticationPrincipal CustomUserDetails authUser,
            @Valid @Parameter(description = "Record id", required = true) @PathVariable("id") Long id) throws ApiException {
        return new ApiResponse<>(companyService.getCompanyName(authUser, id));
    }

	@GetMapping("/profile/{id}/users")
	@Operation(summary ="Get all user for the company with the provided ID")
	public ApiResponse<List<ApiCompanyUser>> getCompanyUsers(
			@Valid @Parameter(description = "Company ID", required = true) @PathVariable("id") Long id,
            @AuthenticationPrincipal CustomUserDetails authUser) throws ApiException {

		return new ApiResponse<>(companyService.getCompanyUsers(id, authUser));
	}

    @PutMapping(value = "/profile")
    @Operation(summary = "Update company data")
    public ApiDefaultResponse updateCompany(@AuthenticationPrincipal CustomUserDetails authUser, @Valid @RequestBody ApiCompanyUpdate company) throws ApiException {
    	companyService.updateCompany(authUser, company);
    	return new ApiDefaultResponse();
    }
    
    @PostMapping(value = "/execute/{action}")
    @Operation(summary = "Execute company action. Must be an Company admin, System admin or Regional admin enrolled in this company",
               operationId = "executeCompanyAction")
    public ApiDefaultResponse executeAction(
            @AuthenticationPrincipal CustomUserDetails authUser,
            @Valid @RequestBody ApiCompanyActionRequest request,
    		@Valid @PathVariable(value = "action") CompanyAction action) throws ApiException {
        companyService.executeAction(authUser, request, action);
        return new ApiDefaultResponse();
    }

    @GetMapping(value = "/userCustomers/{id}")
    @Operation(summary = "Get user customer by id")
    public ApiResponse<ApiUserCustomer> getUserCustomer(
            @AuthenticationPrincipal CustomUserDetails authUser,
            @Valid @Parameter(description = "User customer ID", required = true) @PathVariable("id") Long id,
            @RequestHeader(value = "language", defaultValue = "EN", required = false) Language language) throws ApiException {

        return new ApiResponse<>(companyService.getUserCustomer(id, authUser, language));
    }

    @GetMapping(value = "/userCustomers/{companyId}/{type}")
    @Operation(summary = "Get list of user customers for given company ID and type")
    public ApiPaginatedResponse<ApiUserCustomer> getUserCustomersForCompanyAndType(
            @AuthenticationPrincipal CustomUserDetails authUser,
            @Valid @Parameter(description = "Company ID", required = true) @PathVariable("companyId") Long companyId,
            @Valid @Parameter(description = "Type of user customer (collector, farmer)") @PathVariable("type") UserCustomerType type,
            @RequestHeader(value = "language", defaultValue = "EN", required = false) Language language,
            @Valid ApiListFarmersRequest request) throws ApiException {

        return new ApiPaginatedResponse<>(companyService.getUserCustomersForCompanyAndType(companyId, type, request,
                authUser, language));
    }

    @GetMapping(value = "/userCustomers/{companyId}/plots")
    @Operation(summary = "Get all user customers plots that are part of the company with the provided ID")
    public ApiResponse<List<ApiPlot>> getUserCustomersPlotsForCompany(
            @AuthenticationPrincipal CustomUserDetails authUser,
            @Valid @Parameter(description = "Company ID", required = true) @PathVariable("companyId") Long companyId,
            @RequestHeader(value = "language", defaultValue = "EN", required = false) Language language) throws ApiException {
        return companyService.getUserCustomersPlotsForCompany(authUser, companyId, language);
    }

    @GetMapping(value = "/userCustomers/{companyId}/exportFarmerData", produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    @Operation(summary ="Export payments for provided company ID")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    content = @Content(schema = @Schema(type = "string", format = "binary"))
            )
    })
    public ResponseEntity<byte[]> exportFarmerDataByCompany(
            @AuthenticationPrincipal CustomUserDetails authUser,
            @Valid @Parameter(description = "Company ID", required = true) @PathVariable("companyId") Long companyId,
            @RequestHeader(value = "language", defaultValue = "EN", required = false) Language language
    ) throws ApiException {

        byte[] response;
        try {
            response = companyService.exportFarmerDataByCompany(authUser, companyId, language);
        } catch (IOException e) {
            throw new ApiException(ApiStatus.ERROR, "Error while exporting file!");
        }

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(response);
    }

    @PostMapping(value = "/userCustomers/add/{companyId}")
    @Operation(summary = "Add new user customer for given company ID")
    public ApiResponse<ApiUserCustomer> addUserCustomer(
            @AuthenticationPrincipal CustomUserDetails authUser,
            @Valid @Parameter(description = "Company ID", required = true) @PathVariable("companyId") Long companyId,
            @RequestHeader(value = "language", defaultValue = "EN", required = false) Language language,
            @Valid @RequestBody ApiUserCustomer request
    ) throws ApiException {
        return new ApiResponse<>(companyService.addUserCustomer(companyId, request, authUser, language));
    }

    @PutMapping(value = "/userCustomers/edit")
    @Operation(summary = "Update user customer with given ID")
    public ApiResponse<ApiUserCustomer> updateUserCustomer(
            @AuthenticationPrincipal CustomUserDetails authUser,
            @RequestHeader(value = "language", defaultValue = "EN", required = false) Language language,
            @Valid @RequestBody ApiUserCustomer request
    ) throws ApiException {
        return new ApiResponse<>(companyService.updateUserCustomer(request, authUser, language));
    }

    @GetMapping(value = "/userCustomers/{id}/exportGeoData", produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    @Operation(summary = "Export the Geo-data for the user customer with the provided ID")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    content = @Content(schema = @Schema(type = "string", format = "binary"))
            )
    })
    public ResponseEntity<byte[]> exportUserCustomerGeoData(
            @Valid @Parameter(description = "User customer ID", required = true) @PathVariable("id") Long id,
            @AuthenticationPrincipal CustomUserDetails authUser) throws ApiException {

        byte[] response = companyService.exportUserCustomerGeoData(authUser, id);

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(response);
    }

    @PostMapping(value = "/userCustomers/{id}/uploadGeoData")
    @Operation(summary = "Upload Geo-data for the user customer with the provided ID")
    public ApiDefaultResponse uploadUserCustomerGeoData(
            @AuthenticationPrincipal CustomUserDetails authUser,
            @Valid @Parameter(description = "User customer ID", required = true) @PathVariable("id") Long id,
            @RequestParam("file") MultipartFile file) throws ApiException {

        companyService.uploadUserCustomerGeoData(authUser, id, file);

        return new ApiDefaultResponse();
    }

    @DeleteMapping(value = "/userCustomers/{id}")
    @Operation(summary = "Delete user customer with given id")
    public ApiDefaultResponse deleteUserCustomer(
            @Valid @Parameter(description = "User customer ID", required = true) @PathVariable("id") Long id,
            @AuthenticationPrincipal CustomUserDetails authUser) throws ApiException {
        companyService.deleteUserCustomer(id, authUser);
        return new ApiDefaultResponse();
    }

    @PostMapping(value = "/userCustomers/{id}/plots/add")
    @Operation(summary = "Add new plot for the provided user customer")
    public ApiResponse<ApiPlot> createUserCustomerPlot(
            @AuthenticationPrincipal CustomUserDetails authUser,
            @Valid @Parameter(description = "User customer ID", required = true) @PathVariable("id") Long id,
            @RequestHeader(value = "language", defaultValue = "EN", required = false) Language language,
            @Valid @RequestBody ApiPlot request) throws ApiException {
        return new ApiResponse<>(companyService.createUserCustomerPlot(id, authUser, language, request));
    }

    @PostMapping(value = "/userCustomers/{id}/plots/{plotId}/updateGeoID")
    @Operation(summary = "Add new plot for the provided user customer")
    public ApiResponse<ApiPlot> refreshGeoIDForUserCustomerPlot(
            @AuthenticationPrincipal CustomUserDetails authUser,
            @Valid @Parameter(description = "User customer ID", required = true) @PathVariable("id") Long id,
            @Valid @Parameter(description = "Plot ID", required = true) @PathVariable("plotId") Long plotId,
            @RequestHeader(value = "language", defaultValue = "EN", required = false) Language language) throws ApiException {
        return new ApiResponse<>(companyService.refreshGeoIDForUserCustomerPlot(id, plotId, authUser, language));
    }

    @GetMapping(value = "/companyCustomers/list/{companyId}")
    @Operation(summary = "List company customers for company")
    public ApiPaginatedResponse<ApiCompanyCustomer> getCompanyCustomersList(
            @AuthenticationPrincipal CustomUserDetails authUser,
            @Valid @Parameter(description = "Company id", required = true) @PathVariable("companyId") Long companyId,
            @Valid ApiListCustomersRequest request) throws ApiException {
        return new ApiPaginatedResponse<>(companyService.listCompanyCustomers(authUser, companyId, request));
    }

    @GetMapping(value = "/companyCustomers/{id}")
    @Operation(summary = "Get company customer by ID")
    public ApiResponse<ApiCompanyCustomer> getCompanyCustomer(
            @AuthenticationPrincipal CustomUserDetails authUser,
            @Valid @Parameter(description = "Company customer ID", required = true) @PathVariable("id") Long companyCustomerId
    ) throws ApiException {
        return new ApiResponse<>(companyService.getCompanyCustomer(companyCustomerId, authUser));
    }

    @PostMapping(value = "/companyCustomers")
    @Operation(summary = "Create company customer")
    public ApiResponse<ApiCompanyCustomer> createCompanyCustomer(
            @AuthenticationPrincipal CustomUserDetails authUser,
            @Valid @RequestBody ApiCompanyCustomer apiCompanyCustomer) throws ApiException {
        return new ApiResponse<>(companyService.createCompanyCustomer(apiCompanyCustomer, authUser));
    }

    @PutMapping(value = "/companyCustomers")
    @Operation(summary = "Update company customer")
    public ApiResponse<ApiCompanyCustomer> updateCompanyCustomer(
            @AuthenticationPrincipal CustomUserDetails authUser,
            @Valid @RequestBody ApiCompanyCustomer apiCompanyCustomer) throws ApiException {
        return new ApiResponse<>(companyService.updateCompanyCustomer(apiCompanyCustomer, authUser));
    }

    @DeleteMapping(value = "/companyCustomers/{id}")
    @Operation(summary = "Delete company customer with ID")
    public ApiDefaultResponse deleteCompanyCustomer(
            @AuthenticationPrincipal CustomUserDetails authUser,
            @Valid @Parameter(description = "Company customer ID", required = true) @PathVariable("id") Long id) throws ApiException {
        companyService.deleteCompanyCustomer(id, authUser);
        return new ApiDefaultResponse();
    }

    @GetMapping(value = "/associations/{id}")
    @Operation(summary = "Get list of associations for the selected company with given ID")
    public ApiPaginatedResponse<ApiCompanyListResponse> getAssociations(
            @AuthenticationPrincipal CustomUserDetails authUser,
            @Valid @Parameter(description = "Company ID", required = true) @PathVariable("id") Long id,
            @Valid ApiPaginatedRequest request) throws ApiException {
        return new ApiPaginatedResponse<>(companyService.getAssociations(id, request, authUser));
    }

    @GetMapping(value = "/{id}/connected-companies")
    @Operation(summary = "Get list of connected companies for the company with the given ID")
    public ApiPaginatedResponse<ApiCompanyListResponse> getConnectedCompanies(
            @AuthenticationPrincipal CustomUserDetails authUser,
            @Valid @Parameter(description = "Company ID", required = true) @PathVariable("id") Long id,
            @Valid ApiPaginatedRequest request) throws ApiException {
        return new ApiPaginatedResponse<>(companyService.getConnectedCompanies(id, request, authUser));
    }

    @PostMapping(value = "/userCustomers/import/farmers/{companyId}/{documentId}")
    @PreAuthorize("hasAnyAuthority('SYSTEM_ADMIN', 'REGIONAL_ADMIN')")
    @Operation(summary = "Import farmers from an XLSX spreadsheet document")
    public ApiUserCustomerImportResponse importFarmersSpreadsheet(
            @AuthenticationPrincipal CustomUserDetails authUser,
            @Valid @Parameter(description = "Company ID", required = true) @PathVariable("companyId") Long companyId,
            @Valid @Parameter(description = "Document ID", required = true) @PathVariable("documentId") Long documentId,
            @RequestHeader(value = "language", defaultValue = "EN", required = false) Language language,
            @RequestParam(value = "koboSelections", required = false) List<String> koboSelections) throws ApiException {
        return userCustomerImportService.importFarmersSpreadsheet(companyId, documentId, authUser, language, koboSelections == null ? List.of() : koboSelections);
    }

    @PostMapping(value = "/userCustomers/import/farmers/{companyId}/{documentId}/confirm-plots")
    @PreAuthorize("hasAnyAuthority('SYSTEM_ADMIN', 'REGIONAL_ADMIN')")
    @Operation(summary = "Confirm imported plots for an existing farmer")
    public ApiDefaultResponse confirmImportedFarmerPlots(
            @AuthenticationPrincipal CustomUserDetails authUser,
            @PathVariable("companyId") Long companyId,
            @PathVariable("documentId") Long documentId,
            @Valid @RequestBody ApiUserCustomer selection,
            @RequestHeader(value = "language", defaultValue = "EN", required = false) Language language,
            @RequestParam(value = "koboSelections", required = false) List<String> koboSelections) throws ApiException {
        userCustomerImportService.confirmImportedFarmerPlots(companyId, documentId, selection, authUser, language, koboSelections == null ? List.of() : koboSelections);
        return new ApiDefaultResponse();
    }

    @PostMapping(value = "/userCustomers/import/farmers/{companyId}/{documentId}/create-separate")
    @PreAuthorize("hasAnyAuthority('SYSTEM_ADMIN', 'REGIONAL_ADMIN')")
    @Operation(summary = "Create a separate farmer from an imported duplicate")
    public ApiDefaultResponse createSeparateImportedFarmer(
            @AuthenticationPrincipal CustomUserDetails authUser,
            @PathVariable("companyId") Long companyId,
            @PathVariable("documentId") Long documentId,
            @Valid @RequestBody ApiUserCustomer selection,
            @RequestHeader(value = "language", defaultValue = "EN", required = false) Language language,
            @RequestParam(value = "koboSelections", required = false) List<String> koboSelections) throws ApiException {
        userCustomerImportService.createSeparateImportedFarmer(companyId, documentId, selection, authUser, language, koboSelections == null ? List.of() : koboSelections);
        return new ApiDefaultResponse();
    }

    @GetMapping(value = "/{id}/value-chains")
    @Operation(summary = "Get list of value chains for the company with the given ID")
    public ApiPaginatedResponse<ApiValueChain> getCompanyValueChains(
            @AuthenticationPrincipal CustomUserDetails authUser,
            @Valid @Parameter(description = "Company ID", required = true) @PathVariable("id") Long id,
            @Valid ApiPaginatedRequest request) throws ApiException {
        return new ApiPaginatedResponse<>(companyService.getCompanyValueChainList(id, request, authUser));
    }

    @GetMapping(value = "/{id}/product-types")
    @Operation(summary = "Get list of product types for the company with the given ID")
    public ApiPaginatedResponse<ApiProductType> getCompanyProductTypes(
            @AuthenticationPrincipal CustomUserDetails authUser,
            @Valid @Parameter(description = "Company ID", required = true) @PathVariable("id") Long id,
            @RequestHeader(value = "language", defaultValue = "EN", required = false) Language language,
            @Valid ApiPaginatedRequest request) throws ApiException {
        return new ApiPaginatedResponse<>(companyService.getCompanyProductTypesList(id, request, authUser, language));
    }

}
