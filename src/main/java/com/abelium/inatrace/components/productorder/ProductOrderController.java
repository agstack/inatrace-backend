package com.abelium.inatrace.components.productorder;

import com.abelium.inatrace.api.ApiBaseEntity;
import com.abelium.inatrace.api.ApiResponse;
import com.abelium.inatrace.api.errors.ApiException;
import com.abelium.inatrace.components.productorder.api.ApiProductOrder;
import com.abelium.inatrace.security.service.CustomUserDetails;
import com.abelium.inatrace.types.Language;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;

/**
 * REST controller for Product order entity.
 *
 * @author Pece Adjievski, Sunesis d.o.o.
 */
@RestController
@RequestMapping("/chain/product-order")
public class ProductOrderController {

	private final ProductOrderService productOrderService;

	@Autowired
	public ProductOrderController(ProductOrderService productOrderService) {
		this.productOrderService = productOrderService;
	}

	@GetMapping("{id}")
	@Operation(summary = "Get a single product order with the provided ID.")
	public ApiResponse<ApiProductOrder> getProductOrder(
			@Valid @Parameter(description = "Product order ID", required = true) @PathVariable("id") Long id,
			@AuthenticationPrincipal CustomUserDetails authUser,
			@RequestHeader(value = "language", defaultValue = "EN", required = false) Language language) throws ApiException {

		return new ApiResponse<>(productOrderService.getProductOrder(id, authUser, language));
	}

	@PostMapping
	@Operation(summary = "Create product order.")
	public ApiResponse<ApiBaseEntity> createProductOrder(
			@Valid @RequestBody ApiProductOrder apiProductOrder,
			@AuthenticationPrincipal CustomUserDetails authUser,
			@RequestHeader(value = "language", defaultValue = "EN", required = false) Language language) throws ApiException {

		return new ApiResponse<>(productOrderService.createProductOrder(apiProductOrder, authUser, language));
	}

}
