package com.abelium.inatrace.components.dashboard;

import com.abelium.inatrace.api.ApiResponse;
import com.abelium.inatrace.api.ApiStatus;
import com.abelium.inatrace.api.errors.ApiException;
import com.abelium.inatrace.components.dashboard.api.*;
import com.abelium.inatrace.security.service.CustomUserDetails;
import com.abelium.inatrace.types.Language;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.io.IOException;
import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/dashboard")
public class DashboardController {

    private final DashboardService dashboardService;

    @Autowired
    public DashboardController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    @GetMapping("deliveries-aggregated-data")
    public ApiResponse<ApiDeliveriesTotal> getDeliveriesAggregatedData(
            @Valid @Parameter(description = "Company ID", required = true) @RequestParam("companyId") Long companyId,
            @Valid @Parameter(description = "Facility IDs") @RequestParam(value = "facilityIds", required = false) List<Long> facilityIds,
            @Valid @Parameter(description = "Semi-product ID") @RequestParam(value = "semiProductId", required = false) Long semiProductId,
            @Valid @Parameter(description = "Farmer (UserCustomer) ID") @RequestParam(value = "farmerId", required = false) Long farmerId,
            @Valid @Parameter(description = "Collector (Representative of producer UserCustomer) ID") @RequestParam(value = "collectorId", required = false) Long collectorId,
            @Valid @Parameter(description = "Is women share") @RequestParam(value = "isWomenShare", required = false) Boolean isWomenShare,
            @Valid @Parameter(description = "Organic only") @RequestParam(value = "organicOnly", required = false) Boolean organicOnly,
            @Valid @Parameter(description = "Price determined later") @RequestParam(value = "priceDeterminedLater", required = false) Boolean priceDeterminedLater,
            @Valid @Parameter(description = "Production date range start") @RequestParam(value = "productionDateStart", required = false) LocalDate productionDateStart,
            @Valid @Parameter(description = "Production date range end") @RequestParam(value = "productionDateEnd", required = false) LocalDate productionDateEnd,
            @Valid @Parameter(description = "Aggregation type", required = true) @RequestParam(value = "aggregationType") ApiAggregationTimeUnit aggregationType,
            @AuthenticationPrincipal CustomUserDetails authUser
    ) throws ApiException {
        return new ApiResponse<>(dashboardService.getDeliveriesAggregatedData(
                aggregationType,
                new ApiDeliveriesQueryRequest(
                        companyId,
                        facilityIds,
                        farmerId,
                        collectorId,
                        semiProductId,
                        isWomenShare,
                        organicOnly,
                        priceDeterminedLater,
                        productionDateStart,
                        productionDateEnd
                ), authUser));
    }

    @GetMapping(value = "deliveries-aggregated-data/export", produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    content = @Content(schema = @Schema(type = "string", format = "binary"))
            )
    })
    public ResponseEntity<byte[]> exportDeliveriesData(
            @Valid @Parameter(description = "Company ID", required = true) @RequestParam("companyId") Long companyId,
            @Valid @Parameter(description = "Facility IDs") @RequestParam(value = "facilityIds", required = false) List<Long> facilityIds,
            @Valid @Parameter(description = "Semi-product ID") @RequestParam(value = "semiProductId", required = false) Long semiProductId,
            @Valid @Parameter(description = "Farmer (UserCustomer) ID") @RequestParam(value = "farmerId", required = false) Long farmerId,
            @Valid @Parameter(description = "Collector (Representative of producer UserCustomer) ID") @RequestParam(value = "collectorId", required = false) Long collectorId,
            @Valid @Parameter(description = "Is women share") @RequestParam(value = "isWomenShare", required = false) Boolean isWomenShare,
            @Valid @Parameter(description = "Organic only") @RequestParam(value = "organicOnly", required = false) Boolean organicOnly,
            @Valid @Parameter(description = "Price determined later") @RequestParam(value = "priceDeterminedLater", required = false) Boolean priceDeterminedLater,
            @Valid @Parameter(description = "Production date range start") @RequestParam(value = "productionDateStart", required = false) LocalDate productionDateStart,
            @Valid @Parameter(description = "Production date range end") @RequestParam(value = "productionDateEnd", required = false) LocalDate productionDateEnd,
            @Valid @Parameter(description = "Aggregation type", required = true) @RequestParam(value = "aggregationType") ApiAggregationTimeUnit aggregationType,
            @Valid @Parameter(description = "Export type", required = true) @RequestParam(value = "exportType") ApiExportType exportType,
            @RequestHeader(value = "language", defaultValue = "EN", required = false) Language language,
            @AuthenticationPrincipal CustomUserDetails authUser
    ) throws ApiException {

        byte[] response = null;

        ApiDeliveriesQueryRequest request = new ApiDeliveriesQueryRequest(
                companyId,
                facilityIds,
                farmerId,
                collectorId,
                semiProductId,
                isWomenShare,
                organicOnly,
                priceDeterminedLater,
                productionDateStart,
                productionDateEnd
        );

        ApiDeliveriesTotal total = dashboardService.getDeliveriesAggregatedData(aggregationType, request, authUser);

        try {
            switch (exportType) {
                case CSV:
                    response = dashboardService.convertDeliveryDataToCsv(total, request, language);
                    break;
                case PDF:
                    response = dashboardService.convertDeliveryDataToPDF(total, request, language);
                    break;
                case EXCEL:
                    response = dashboardService.convertDeliveryDataToExcel(total, request, language);
                    break;
                default:
                    break;
            }
        } catch (IOException e) {
            throw new ApiException(ApiStatus.ERROR, "Error while exporting file!");
        }

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(response);
    }

    @PostMapping(value = "processing-performance-data")
    @Operation(summary ="Calculates processing performance data")
    public ApiResponse<ApiProcessingPerformanceTotal> calculateProcessingPerformanceData(
            @Valid @RequestBody ApiProcessingPerformanceRequest processingPerformanceRequest,
            @AuthenticationPrincipal CustomUserDetails authUser
    ) throws ApiException {
        return new ApiResponse<>(dashboardService.calculateProcessingPerformanceData(processingPerformanceRequest, authUser));
    }

    @PostMapping(value = "processing-performance-data/export", produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    @Operation(summary ="Exports processing performance data to the requested format")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    content = @Content(schema = @Schema(type = "string", format = "binary"))
            )
    })
    public ResponseEntity<byte[]> exportProcessingPerformanceData(
            @Valid @RequestBody ApiProcessingPerformanceRequest processingPerformanceRequest,
            @RequestHeader(value = "language", defaultValue = "EN", required = false) Language language,
            @AuthenticationPrincipal CustomUserDetails authUser
    ) throws ApiException {

        byte[] response = null;

        ApiProcessingPerformanceTotal total = dashboardService.calculateProcessingPerformanceData(
                processingPerformanceRequest, authUser);

        try {
            switch (processingPerformanceRequest.getExportType()) {
                case CSV:
                    response = dashboardService.convertPerformanceDataToCsv(total, processingPerformanceRequest, language);
                    break;
                case PDF:
                    response = dashboardService.convertPerformanceDataToPDF(total, processingPerformanceRequest, language);
                    break;
                case EXCEL:
                    response = dashboardService.convertPerformanceDataToExcel(total, processingPerformanceRequest, language);
                    break;
                default:
                    break;
            }
        } catch (IOException e) {
            throw new ApiException(ApiStatus.ERROR, "Error while exporting file!");
        }

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(response);
    }
}
