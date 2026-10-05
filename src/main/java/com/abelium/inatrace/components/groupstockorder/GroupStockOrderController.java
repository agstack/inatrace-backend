package com.abelium.inatrace.components.groupstockorder;

import com.abelium.inatrace.api.ApiPaginatedRequest;
import com.abelium.inatrace.api.ApiPaginatedResponse;
import com.abelium.inatrace.api.errors.ApiException;
import com.abelium.inatrace.components.groupstockorder.api.ApiGroupStockOrder;
import com.abelium.inatrace.security.service.CustomUserDetails;
import com.abelium.inatrace.types.Language;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/chain/group-stock-order")
public class GroupStockOrderController {

    private final GroupStockOrderService groupStockOrderService;

    @Autowired
    public GroupStockOrderController(GroupStockOrderService groupStockOrderService) {
        this.groupStockOrderService = groupStockOrderService;
    }

    @GetMapping("/list/facility/{facilityId}")
    @Operation(summary = "Get a paginated list of grouped stock orders.")
    public ApiPaginatedResponse<ApiGroupStockOrder> getGroupedStockOrderList(
            @Valid ApiPaginatedRequest request,
            @Valid @Parameter(description = "Facility ID", required = true) @PathVariable Long facilityId,
            @Valid @Parameter(description = "Available orders only") @RequestParam(value = "availableOnly", required = false) Boolean availableOnly,
            @Valid @Parameter(description = "Is purchase orders only") @RequestParam(value = "isPurchaseOrderOnly", required = false) Boolean isPurchaseOrderOnly,
            @Valid @Parameter(description = "Semi-product ID") @RequestParam(value = "semiProductId", required = false) Long semiProductId,
            @AuthenticationPrincipal CustomUserDetails authUser,
            @RequestHeader(value = "language", defaultValue = "EN", required = false) Language language
    ) throws ApiException {
        return new ApiPaginatedResponse<>(this.groupStockOrderService.getGroupedStockOrderList(
                request,
                new GroupStockOrderQueryRequest(
                        facilityId,
                        availableOnly,
                        isPurchaseOrderOnly,
                        semiProductId
                ),
                language,
                authUser
        ));
    }

}
