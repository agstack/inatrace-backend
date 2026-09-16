package com.abelium.inatrace.components.codebook.processingevidencefield;

import com.abelium.inatrace.api.*;
import com.abelium.inatrace.api.errors.ApiException;
import com.abelium.inatrace.components.codebook.processingevidencefield.api.ApiProcessingEvidenceField;
import com.abelium.inatrace.security.service.CustomUserDetails;
import com.abelium.inatrace.types.Language;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.util.List;

/**
 * REST controller for processing evidence field entity.
 *
 * @author Rene Flores, Sunesis d.o.o.
 */
@RestController
@RequestMapping("/chain/processing-evidence-field")
public class ProcessingEvidenceFieldController {

	private final ProcessingEvidenceFieldService processingEvidenceFieldService;

	@Autowired
	public ProcessingEvidenceFieldController(ProcessingEvidenceFieldService processingEvidenceFieldService) {
		this.processingEvidenceFieldService = processingEvidenceFieldService;
	}

	@GetMapping("list")
	@Operation(summary ="Get a paginated list of processing evidence fields.")
	public ApiPaginatedResponse<ApiProcessingEvidenceField> getProcessingEvidenceFieldList(
			@Valid ApiPaginatedRequest request,
			@RequestHeader(value = "language", defaultValue = "EN", required = false) Language language) {

		return new ApiPaginatedResponse<>(processingEvidenceFieldService.getProcessingEvidenceFieldList(request, language));
	}

	@GetMapping("list/by-value-chains")
	@Operation(summary ="Get a list of processing evidence fields by value chain ID list.")
	public ApiPaginatedResponse<ApiProcessingEvidenceField> listProcessingEvidenceFieldsByValueChains(
			@Parameter(description = "Value chain IDs", required = true) @RequestParam(value = "valueChainIds")
			List<Long> valueChainIds,
			@Valid ApiPaginatedRequest request,
			@RequestHeader(value = "language", defaultValue = "EN", required = false) Language language) {

		return new ApiPaginatedResponse<>(processingEvidenceFieldService.listProcessingEvidenceFieldsByValueChainList(valueChainIds, request, language));
	}
	
	@GetMapping("{id}")
	@Operation(summary ="Get a single processing evidence field with the provided ID.")
	public ApiResponse<ApiProcessingEvidenceField> getProcessingEvidenceField(
			@Valid @Parameter(description = "ProcessingEvidenceField ID", required = true) @PathVariable("id") Long id,
			@RequestHeader(value = "language", defaultValue = "EN", required = false) Language language) throws ApiException {

		return new ApiResponse<>(processingEvidenceFieldService.getProcessingEvidenceField(id, language));
	}

	@PutMapping
	@PreAuthorize("hasAnyAuthority('SYSTEM_ADMIN', 'REGIONAL_ADMIN')")
	@Operation(summary ="Create or update processing evidence field. If ID is provided, then the entity with the provided ID is updated.")
	public ApiResponse<ApiBaseEntity> createOrUpdateProcessingEvidenceField(@AuthenticationPrincipal CustomUserDetails authUser,
																			@Valid @RequestBody ApiProcessingEvidenceField apiProcessingEvidenceField) throws ApiException {

		return new ApiResponse<>(processingEvidenceFieldService.createOrUpdateProcessingEvidenceField(authUser, apiProcessingEvidenceField));
	}

	@DeleteMapping("{id}")
	@PreAuthorize("hasAuthority('SYSTEM_ADMIN')")
	@Operation(summary ="Deletes a processing evidence field with the provided ID.")
	public ApiDefaultResponse deleteProcessingEvidenceField(@Valid @Parameter(description = "ProcessingEvidenceField ID", required = true) @PathVariable("id") Long id) throws ApiException {

		processingEvidenceFieldService.deleteProcessingEvidenceField(id);
		return new ApiDefaultResponse();
	}
}
