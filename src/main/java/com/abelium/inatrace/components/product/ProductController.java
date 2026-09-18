package com.abelium.inatrace.components.product;

import com.abelium.inatrace.api.*;
import com.abelium.inatrace.api.errors.ApiException;
import com.abelium.inatrace.components.product.api.*;
import com.abelium.inatrace.components.product.types.ProductLabelAction;
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
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import jakarta.validation.Valid;
import java.util.List;

@RestController
@RequestMapping("/product")
public class ProductController {

	private final ProductService productService;

	private final ProductDocumentService productDocumentEngine;

	@Autowired
	public ProductController(ProductService productService, ProductDocumentService productDocumentEngine) {
		this.productService = productService;
		this.productDocumentEngine = productDocumentEngine;
	}

	@PostMapping(value = "/create")
    @Operation(summary = "Create a new product")
    public ApiResponse<ApiBaseEntity> createProduct(@AuthenticationPrincipal CustomUserDetails authUser, 
    		@Valid @RequestBody ApiProduct request) throws ApiException {
		return new ApiResponse<>(productService.createProduct(authUser, request));
    }
    
    @GetMapping(value = "/list")
    @Operation(summary = "Lists all products. Sorting: name or default")
    public ApiPaginatedResponse<ApiProductListResponse> listProducts(
            @AuthenticationPrincipal CustomUserDetails authUser,
    		@Valid ApiListProductsRequest request,
            @RequestHeader(value = "language", defaultValue = "EN", required = false) Language language) {
    	return new ApiPaginatedResponse<>(productService.listUserProducts1(authUser.getUserId(), request));
    }
    
    @GetMapping(value = "/admin/list")
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN')")
    @Operation(summary = "Lists all products. Must be admin. Sorting: name or default")
    public ApiPaginatedResponse<ApiProductListResponse> listProductsAdmin(@AuthenticationPrincipal CustomUserDetails authUser,
    		@Valid ApiListProductsRequest request) {
    	return new ApiPaginatedResponse<>(productService.listProducts(authUser.getUserId(), request));
    }
    
    @GetMapping(value = "/{id}")
    @Operation(summary = "Get all info about a product")
    public ApiResponse<ApiProduct> getProduct(@AuthenticationPrincipal CustomUserDetails authUser,
    		@Valid @Parameter(description = "Product id", required = true) @PathVariable("id") Long id,
    		@Valid @RequestParam(value = "includeLabels", defaultValue = "false") boolean includeLabels) throws ApiException {
    	return new ApiResponse<>(productService.getProduct(authUser, id, includeLabels));
    }
    
    @PutMapping(value = "/")
    @Operation(summary = "Update product data")
    public ApiDefaultResponse updateProduct(@AuthenticationPrincipal CustomUserDetails authUser,
    		@Valid @RequestBody ApiProduct product) throws ApiException {
    	productService.updateProduct(authUser, product);
    	return new ApiDefaultResponse();
    }
    
    @DeleteMapping(value = "/{id}")
    @Operation(summary = "Deletes a product")
    public ApiDefaultResponse deleteProduct(@AuthenticationPrincipal CustomUserDetails authUser,
    		@Valid @Parameter(description = "Product id", required = true)  @PathVariable("id") Long id) throws ApiException {
    	productService.deleteProduct(authUser, id);
    	return new ApiDefaultResponse();
    }

	@GetMapping(value = "/{id}/labels")
	@Operation(summary = "Get labels for product")
	public ApiResponse<List<ApiProductLabelBase>> getProductLabels(
			@AuthenticationPrincipal CustomUserDetails authUser,
			@Valid @Parameter(description = "Product id", required = true)  @PathVariable("id") Long id) throws ApiException {
		return new ApiResponse<>(productService.getProductLabels(authUser, id));
	}

    @PostMapping(value = "/label/create")
    @Operation(summary = "Create a new product label")
    public ApiResponse<ApiBaseEntity> createProductLabel(@AuthenticationPrincipal CustomUserDetails authUser,
    		@Valid @RequestBody ApiProductLabel request) throws ApiException {
		return new ApiResponse<>(productService.createProductLabel(authUser, request));
    }
    
    @GetMapping(value = "/label/{id}")
    @Operation(summary = "Get label data")
    public ApiResponse<ApiProductLabel> getProductLabel(@AuthenticationPrincipal CustomUserDetails authUser,
    		@Valid @Parameter(description = "Record id", required = true) @PathVariable("id") Long id) throws ApiException {
    	return new ApiResponse<>(productService.getProductLabel(authUser, id));
    }

    @PutMapping(value = "/label")
    @Operation(summary = "Update label data")
    public ApiDefaultResponse updateProductLabel(@AuthenticationPrincipal CustomUserDetails authUser,
    		@Valid @RequestBody ApiProductLabel request) throws ApiException {
    	return productService.updateProductLabel(authUser, request);
    }
    
    @DeleteMapping(value = "/label/{id}")
    @Operation(summary = "Deletes a product label")
    public ApiDefaultResponse deleteProductLabel(@AuthenticationPrincipal CustomUserDetails authUser,
    		@Valid @Parameter(description = "Label id", required = true)  @PathVariable("id") Long id) throws ApiException {
    	productService.deleteProductLabel(authUser, id);
    	return new ApiDefaultResponse();
    }

    @PutMapping(value = "/label/content")
    @Operation(summary = "Update label content")
    public ApiDefaultResponse updateProductLabelContent(@AuthenticationPrincipal CustomUserDetails authUser,
    		@Valid @RequestBody ApiProductLabelContent request) throws ApiException {
    	return productService.updateProductLabelContent(authUser, request);
    }
    
    @GetMapping(value = "/label/content/{id}")
    @Operation(summary = "Get label content")
    public ApiResponse<ApiProductLabelContent> getProductLabelContent(@AuthenticationPrincipal CustomUserDetails authUser,
    		@Valid @Parameter(description = "Label id", required = true)  @PathVariable("id") Long id) throws ApiException {
    	return new ApiResponse<>(productService.getProductLabelContent(authUser, id));
    }

    @GetMapping(value = "/label/{id}/documents")
    @Operation(summary = "Get selected company documents for product label")
    public ApiResponse<List<ApiProductLabelCompanyDocument>> getCompanyDocumentsForProductLabel(@AuthenticationPrincipal CustomUserDetails authUser,
            @Valid @Parameter(description = "Label id", required = true) @PathVariable("id") Long id) throws ApiException {
        return new ApiResponse<>(productService.getCompanyDocumentsForProductLabel(authUser, id));
    }

    @PutMapping(value = "/label/{id}/documents")
    @Operation(summary = "Update company document product label list")
    public ApiDefaultResponse updateCompanyDocumentsForProductLabel(@AuthenticationPrincipal CustomUserDetails authUser,
            @Valid @Parameter(description = "Label ID", required = true) @PathVariable("id") Long id,
            @Valid @Parameter(description = "Company document list", required = true) @RequestBody List<ApiProductLabelCompanyDocument> documents) throws ApiException {
        productService.updateCompanyDocumentsForProductLabel(authUser, id, documents);
        return new ApiDefaultResponse();
    }

    @PostMapping(value = "/label/execute/{action}")
    @Operation(summary = "Execute action", operationId = "executeProductAction")
    public ApiDefaultResponse executeAction(@AuthenticationPrincipal CustomUserDetails authUser,
    		@Valid @RequestBody ApiBaseEntity request, 
    		@Valid @PathVariable(value = "action") ProductLabelAction action) throws ApiException {
    	productService.executeAction(authUser, request, action);
    	return new ApiDefaultResponse();
    }
    
    @GetMapping(value = "/label/analytics/{uid}")
    @Operation(summary = "Get label data")
    public ApiResponse<ApiProductLabelAnalytics> getProductLabelAnalytics(@AuthenticationPrincipal CustomUserDetails authUser,
    		@Valid @Parameter(description = "Label uid", required = true) @PathVariable("uid") String uid) throws ApiException {
    	return new ApiResponse<>(productService.getProductLabelAnalytics(authUser, uid));
    }
    
    @PostMapping(value = "/label_batch/create")
    @Operation(summary = "Create a new product label batch")
    public ApiResponse<ApiBaseEntity> createProductLabelBatch(@AuthenticationPrincipal CustomUserDetails authUser,
    		@Valid @RequestBody ApiProductLabelBatch request) throws ApiException {
		return new ApiResponse<>(productService.createProductLabelBatch(authUser, request));
    }
    
    @GetMapping(value = "/label_batch/{id}")
    @Operation(summary = "Get label batch data")
    public ApiResponse<ApiProductLabelBatch> getProductLabelBatch(@AuthenticationPrincipal CustomUserDetails authUser,
    		@Valid @Parameter(description = "Batch id", required = true) @PathVariable("id") Long id) throws ApiException {
    	return new ApiResponse<>(productService.getProductLabelBatch(authUser, id));
    }

    @PutMapping(value = "/label_batch")
    @Operation(summary = "Update label batch data")
    public ApiDefaultResponse updateProductLabelBatch(@AuthenticationPrincipal CustomUserDetails authUser,
    		@Valid @RequestBody ApiProductLabelBatch request) throws ApiException {
    	return productService.updateProductLabelBatch(authUser, request);
    }
    
    @DeleteMapping(value = "/label_batch/{id}")
    @Operation(summary = "Deletes a product label batch")
    public ApiDefaultResponse deleteProductLabelBatch(@AuthenticationPrincipal CustomUserDetails authUser,
    		@Valid @Parameter(description = "Batch id", required = true)  @PathVariable("id") Long id) throws ApiException {
    	productService.deleteProductLabelBatch(authUser, id);
    	return new ApiDefaultResponse();
    }
    
    @GetMapping(value = "/label/{id}/batches")
    @Operation(summary = "Get label batches for specified label. Sorting: number or productionDate, expiryDate")
    public ApiPaginatedResponse<ApiProductLabelBatch> getProductLabelBatches(@AuthenticationPrincipal CustomUserDetails authUser,
    		@Valid @Parameter(description = "Label id", required = true) @PathVariable("id") Long id,
    		@Valid ApiListProductLabelBatchesRequest request) throws ApiException {
    	return new ApiPaginatedResponse<>(productService.listProductLabelBatches(authUser, id, request));
    }

    @GetMapping(value = "/label/{id}/instructions", produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    content = @Content(schema = @Schema(type = "string", format = "binary"))
            )
    })
    public ResponseEntity<byte[]> getProductLabelInstructions(@AuthenticationPrincipal CustomUserDetails authUser,
    		@Valid @Parameter(description = "Label id", required = true) @PathVariable("id") Long id) throws ApiException {
        return productDocumentEngine.createLabelsAndHowToUseInstructions(authUser, id).toResponseEntity();
    }
    
    @GetMapping(value = "/knowledgeBlog/list/{productId}")
    @Operation(summary = "Get knowledge blogs for a product")
    public ApiPaginatedResponse<ApiKnowledgeBlogBase> getProductKnowledgeBlogs(
    		@AuthenticationPrincipal CustomUserDetails authUser,
    		@Valid @Parameter(description = "Product id", required = true) @PathVariable("productId") Long productId,
    		@Valid ApiListKnowledgeBlogRequest request) throws ApiException {
    	return new ApiPaginatedResponse<>(productService.listKnowledgeBlogs(authUser, productId, request));
    }
    
    @GetMapping(value = "/knowledgeBlog/{id}")
    @Operation(summary = "Get knowledge blog by id")
    public ApiResponse<ApiKnowledgeBlog> getProductKnowledgeBlog(
    		@AuthenticationPrincipal CustomUserDetails authUser,
    		@Valid @Parameter(description = "id", required = true) @PathVariable("id") Long id) throws ApiException {
    	return new ApiResponse<>(productService.getKnowledgeBlog(authUser, id));
    }

    @PutMapping(value = "/knowledgeBlog")
    @Operation(summary = "Update knowledge blog")
    public ApiDefaultResponse updateProductKnowledgeBlog(
    		@AuthenticationPrincipal CustomUserDetails authUser,
    		@Valid @RequestBody ApiKnowledgeBlog request) throws ApiException {
    	productService.updateKnowledgeBlog(authUser, request);
    	return new ApiDefaultResponse();
    }
    
    @PostMapping(value = "/knowledgeBlog/{productId}")
    @Operation(summary = "Add knowledge blog to a product")
    public ApiDefaultResponse addProductKnowledgeBlog(
    		@AuthenticationPrincipal CustomUserDetails authUser,
    		@Valid @Parameter(description = "type", required = true) @PathVariable("productId") Long productId,
    		@Valid @RequestBody ApiKnowledgeBlog request) throws ApiException {
    	productService.addKnowledgeBlog(authUser, productId, request);
    	return new ApiDefaultResponse();
    }
    
    @DeleteMapping(value = "/label/feedback/{id}")
    @Operation(summary = "Deletes a product label feedback")
    public ApiDefaultResponse deleteProductLabelFeedback(
    		@AuthenticationPrincipal CustomUserDetails authUser,
    		@Valid @Parameter(description = "Feedback id", required = true) @PathVariable("id") Long id) throws ApiException {
    	productService.deleteProductLabelFeedback(authUser, id);
    	return new ApiDefaultResponse();
    }

    @GetMapping(value = "/{productId}/finalProduct/{finalProductId}")
    @Operation(summary = "Get final product by ID.")
    public ApiResponse<ApiFinalProduct> getFinalProduct(
            @AuthenticationPrincipal CustomUserDetails authUser,
            @Valid @Parameter(description = "Product ID", required = true) @PathVariable("productId") Long productId,
            @Valid @Parameter(description = "Final product ID", required = true) @PathVariable("finalProductId") Long finalProductId) throws ApiException {
        return new ApiResponse<>(productService.getFinalProduct(productId, finalProductId, authUser));
    }

	@GetMapping(value = "/{productId}/finalProduct/{finalProductId}/labels")
	@Operation(summary = "Get final product labels.")
	public ApiResponse<List<ApiProductLabelBase>> getFinalProductLabels(
            @AuthenticationPrincipal CustomUserDetails authUser,
			@Valid @Parameter(description = "Product ID", required = true) @PathVariable("productId") Long productId,
			@Valid @Parameter(description = "Final product ID", required = true) @PathVariable("finalProductId") Long finalProductId,
			@Valid @Parameter(description = "Also return the unpublished labels") @RequestParam(value = "returnUnpublished", required = false) Boolean returnUnpublished) throws ApiException {
		return new ApiResponse<>(productService.getFinalProductLabels(productId, finalProductId, returnUnpublished, authUser));
	}

    @GetMapping(value = "/{productId}/finalProduct/list")
    @Operation(summary = "Get final product list by product ID.")
    public ApiPaginatedResponse<ApiFinalProduct> getFinalProductList(
            @AuthenticationPrincipal CustomUserDetails authUser,
            @Valid @Parameter(description = "Product ID", required = true) @PathVariable("productId") Long productId,
            @Valid ApiPaginatedRequest request) throws ApiException {

        return new ApiPaginatedResponse<>(productService.getFinalProductList(
                request,
                new FinalProductQueryRequest(productId),
                authUser
        ));
    }

    @PutMapping(value = "/{productId}/finalProduct")
    @Operation(summary = "Create or update final product.")
    public ApiResponse<ApiBaseEntity> createOrUpdateFinalProduct(
            @AuthenticationPrincipal CustomUserDetails authUser,
            @Valid @Parameter(description = "Product ID", required = true) @PathVariable("productId") Long productId,
            @Valid @RequestBody ApiFinalProduct apiFinalProduct) throws ApiException {

        return new ApiResponse<>(productService.createOrUpdateFinalProduct(authUser, productId, apiFinalProduct));
    }

    @DeleteMapping(value = "/{productId}/finalProduct/{finalProductId}")
    @Operation(summary = "Delete a final product")
    public ApiDefaultResponse deleteFinalProduct(
            @AuthenticationPrincipal CustomUserDetails authUser,
            @Valid @Parameter(description = "Product ID", required = true) @PathVariable("productId") Long productId,
            @Valid @Parameter(description = "Final product ID", required = true) @PathVariable("finalProductId") Long finalProductId) throws ApiException {

        productService.deleteFinalProduct(productId, finalProductId, authUser);
        return new ApiDefaultResponse();
    }

}
