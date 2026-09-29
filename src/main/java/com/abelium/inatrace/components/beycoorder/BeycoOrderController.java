package com.abelium.inatrace.components.beycoorder;

import com.abelium.inatrace.api.ApiResponse;
import com.abelium.inatrace.api.errors.ApiException;
import com.abelium.inatrace.components.beycoorder.api.ApiBeycoOrderFields;
import com.abelium.inatrace.components.beycoorder.api.ApiBeycoTokenResponse;
import com.abelium.inatrace.security.service.CustomUserDetails;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.util.List;

@RestController
@RequestMapping("/chain/beyco-order")
public class BeycoOrderController {

    private final BeycoOrderService beycoOrderService;

    @Autowired
    public BeycoOrderController(
            BeycoOrderService beycoOrderService
    ) {
        this.beycoOrderService = beycoOrderService;
    }

    @GetMapping("/company/{companyId}/token")
    @Operation(summary = "Get OAuth2 token for Beyco integration")
    public ApiResponse<ApiBeycoTokenResponse> getToken(
            @Parameter(description = "Authorization code from Beyco OAuth2", required = true) @RequestParam(value = "authCode") String authCode,
            @Parameter(description = "ID of company", required = true) @PathVariable(value = "companyId") Long companyId,
            @AuthenticationPrincipal CustomUserDetails authUser
    ) throws ApiException {
        return new ApiResponse<>(beycoOrderService.getBeycoAuthToken(authCode, companyId, authUser));
    }

    @GetMapping("/company/{companyId}/token/refresh")
    @Operation(summary = "Refresh expired token")
    public ApiResponse<ApiBeycoTokenResponse> refreshToken(
            @Parameter(description = "Refresh token", required = true) @RequestHeader(value = "X-Beyco-Refresh-Token") String refreshToken,
            @Parameter(description = "ID of company", required = true) @PathVariable(value = "companyId") Long companyId,
            @AuthenticationPrincipal CustomUserDetails authUser
    ) throws ApiException {
        return new ApiResponse<>(beycoOrderService.refreshBeycoAuthToken(refreshToken, companyId, authUser));
    }

    @GetMapping("/company/{companyId}/fields")
    @Operation(summary = "Get list of fields necessary for Beyco order for selected Stock Orders")
    public ApiResponse<ApiBeycoOrderFields> getBeycoOrderFieldsForSelectedStockOrders(
            @Parameter(description = "ID's of selected stock orders", required = true) @RequestParam(value = "id") List<Long> stockOrderIds,
            @Parameter(description = "ID of company", required = true) @PathVariable(value = "companyId") Long companyId,
            @AuthenticationPrincipal CustomUserDetails authUser
    ) throws ApiException {
        return new ApiResponse<>(beycoOrderService.getBeycoOrderFieldList(stockOrderIds, companyId, authUser));
    }

    @PostMapping("/company/{companyId}/order")
    @Operation(summary = "Send order to Beyco")
    public ApiResponse<Object> sendBeycoOrder(
            @Valid @Parameter(description = "Beyco offer", required = true) @RequestBody ApiBeycoOrderFields beycoOrder,
            @Parameter(description = "JWT token", required = true) @RequestHeader(value = "X-Beyco-Token") String token,
            @Parameter(description = "ID of company", required = true) @PathVariable(value = "companyId") Long companyId,
            @AuthenticationPrincipal CustomUserDetails authUser
    ) throws ApiException {
        return new ApiResponse<>(this.beycoOrderService.sendBeycoOrder(beycoOrder, token, companyId, authUser));
    }

}
