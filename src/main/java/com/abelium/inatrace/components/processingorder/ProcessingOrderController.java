package com.abelium.inatrace.components.processingorder;

import com.abelium.inatrace.api.ApiBaseEntity;
import com.abelium.inatrace.api.ApiDefaultResponse;
import com.abelium.inatrace.api.ApiResponse;
import com.abelium.inatrace.api.errors.ApiError;
import com.abelium.inatrace.api.errors.ApiException;
import com.abelium.inatrace.components.processingorder.api.ApiProcessingOrder;
import com.abelium.inatrace.security.service.CustomUserDetails;
import com.abelium.inatrace.types.Language;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;

@RestController
@RequestMapping("chain/processing-order")
public class ProcessingOrderController {

    private final ProcessingOrderService processingOrderService;

    @Autowired
    public ProcessingOrderController(ProcessingOrderService processingOrderService) {
        this.processingOrderService = processingOrderService;
    }

    @GetMapping("{id}")
    @Operation(summary ="Get a single processing order with the provided ID.")
    public ApiResponse<ApiProcessingOrder> getProcessingOrder(
            @Valid @Parameter(description = "ProcessingOrder ID", required = true) @PathVariable("id") Long id,
            @AuthenticationPrincipal CustomUserDetails authUser,
            @RequestHeader(value = "language", defaultValue = "EN", required = false) Language language) throws ApiException {

        return new ApiResponse<>(processingOrderService.getProcessingOrder(id, authUser, language));
    }

    @PutMapping
    @Operation(summary ="Create or update processing order. If the ID is provided, then the entity with the provided ID is updated.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "OK"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "The input company has no product association. In that case status is "
                            + "PRODUCT_ASSOCIATION_REQUIRED and errorDetails contains the company ID as a string. "
                            + "Other invalid requests return status VALIDATION_ERROR or INVALID_REQUEST.",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "403",
                    description = "The user is not authorized for the processing action or the input stock order.",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    public ApiResponse<ApiBaseEntity> createOrUpdateProcessingOrder(
            @Valid @RequestBody ApiProcessingOrder apiProcessingOrder,
            @AuthenticationPrincipal CustomUserDetails authUser,
            @RequestHeader(value = "language", defaultValue = "EN", required = false) Language language) throws ApiException {

        return new ApiResponse<>(processingOrderService.createOrUpdateProcessingOrder(apiProcessingOrder, authUser, language));
    }

    @DeleteMapping("{id}")
    @Operation(summary ="Deletes a processing order with the provided ID.")
    public ApiDefaultResponse deleteProcessingOrder(
            @Valid @Parameter(description = "ProcessingOrder ID", required = true) @PathVariable("id") Long id,
            @AuthenticationPrincipal CustomUserDetails authUser) throws ApiException {

        processingOrderService.deleteProcessingOrder(id, authUser);
        return new ApiDefaultResponse();
    }

}
