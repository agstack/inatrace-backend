package com.abelium.inatrace.components.pub;

import com.abelium.inatrace.api.ApiDefaultResponse;
import com.abelium.inatrace.api.ApiPaginatedResponse;
import com.abelium.inatrace.api.ApiResponse;
import com.abelium.inatrace.api.errors.ApiException;
import com.abelium.inatrace.components.analytics.RequestLogService;
import com.abelium.inatrace.components.analytics.api.ApiLogRequest;
import com.abelium.inatrace.components.common.GlobalSettingsService;
import com.abelium.inatrace.components.common.api.ApiGlobalSettingsValue;
import com.abelium.inatrace.components.company.CompanyService;
import com.abelium.inatrace.components.product.ProductService;
import com.abelium.inatrace.components.product.api.*;
import com.abelium.inatrace.components.stockorder.StockOrderService;
import com.abelium.inatrace.components.stockorder.api.ApiQRTagPublic;
import com.abelium.inatrace.types.Language;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;

@RestController
@RequestMapping("/public")
public class PublicController {

	private final CompanyService companyService;

	private final ProductService productService;

	private final StockOrderService stockOrderService;

	private final RequestLogService requestLogService;

    private final GlobalSettingsService globalSettingsService;

	@Autowired
	public PublicController(CompanyService companyService,
	                        ProductService productService,
							StockOrderService stockOrderService,
	                        RequestLogService requestLogService,
	                        GlobalSettingsService globalSettingsService) {
		this.companyService = companyService;
		this.productService = productService;
		this.stockOrderService = stockOrderService;
		this.requestLogService = requestLogService;
		this.globalSettingsService = globalSettingsService;
	}
    
    @GetMapping(value = "/product/label/{uid}")
    @Operation(summary = "Get label with field values")
    public ApiResponse<ApiProductLabelValuesExtended> getPublicProductLabelValues(
			@Valid @Parameter(description = "Label uid", required = true) @PathVariable("uid") String uid) throws ApiException {
    	return new ApiResponse<>(productService.getProductLabelValuesPublic(uid));
    }

	@GetMapping(value = "/stock-order/{qrTag}")
	@Operation(summary = "Get public data for the Stock order with the given QR code tag")
	public ApiResponse<ApiQRTagPublic> getQRTagPublicData(
			@Valid @Parameter(description = "QR code tag", required = true) @PathVariable("qrTag") String qrTag,
			@Valid @Parameter(description = "Return aggregated history") @RequestParam(value = "withHistory", required = false) Boolean withHistory,
			@RequestHeader(value = "language", defaultValue = "EN", required = false) Language language) throws ApiException {
		return new ApiResponse<>(stockOrderService.getQRTagPublicData(qrTag, withHistory, language));
	}
    
    @GetMapping(value = "/product/knowledgeBlog/{id}")
    @Operation(summary = "Get knowledge blog by id")
    public ApiResponse<ApiKnowledgeBlog> getPublicProductKnowledgeBlog(
    		@Valid @Parameter(description = "id", required = true) @PathVariable("id") Long id) throws ApiException {
    	return new ApiResponse<>(productService.getKnowledgeBlogPublic(id));
    }

    @GetMapping(value = "/product/label_batch/{uid}/{number}")
    @Operation(summary = "Get batch by label and number")
    public ApiResponse<ApiProductLabelBatch> getPublicProductLabelBatch(
			@Valid @Parameter(description = "Label uid", required = true) @PathVariable("uid") String uid,
			@Valid @Parameter(description = "Batch number", required = true)  @PathVariable("number") String number) throws ApiException {
    	return new ApiResponse<>(productService.getProductLabelBatchPublic(uid, number));
    }
    
    @GetMapping(value = "/product/label/{uid}/verify_batch_authenticity")
    @Operation(summary = "Check batch by number and given date(s)")
    public ApiResponse<Boolean> checkPublicProductLabelBatchAuthenticity(HttpServletRequest servletRequest,
    		@Valid @Parameter(description = "Label uid", required = true) @PathVariable("uid") String uid,
			@Valid ApiProductLabelBatchCheckAuthenticity request) throws ApiException {
    	return new ApiResponse<>(productService.checkProductLabelBatchAuthenticityPublic(uid, request, servletRequest));
    }
    
    @GetMapping(value = "/product/label/{uid}/verify_batch_origin")
    @Operation(summary = "Get batch by label and number")
    public ApiResponse<List<ApiLocation>> checkPublicProductLabelBatchOrigin(HttpServletRequest servletRequest,
    		@Valid @Parameter(description = "Label uid", required = true) @PathVariable("uid") String uid,
			@Valid ApiProductLabelBatchCheckOrigin request) throws ApiException {
    	return new ApiResponse<>(productService.checkProductLabelBatchOriginPublic(uid, request, servletRequest));
    }

    @PostMapping(value = "/logRequest")
    @Operation(summary = "Write data to request log for analytics")
    public ApiDefaultResponse logPublicRequest(HttpServletRequest servletRequest,
    		@Valid @RequestBody ApiLogRequest request) throws ApiException {
    	requestLogService.log(servletRequest, request);
    	return new ApiDefaultResponse();
    }

    @GetMapping(value = "/document/{storageKey}", produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    @Operation(summary = "Returns file contents for given storage key")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    content = @Content(schema = @Schema(type = "string", format = "binary"))
            )
    })
    public ResponseEntity<byte[]> getPublicDocument(@Valid @PathVariable(value = "storageKey") String storageKey) throws ApiException {
        return productService.getPublicDocument(storageKey).toResponseEntity();
    }

    @GetMapping(value = "/image/{storageKey}", produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    @Operation(summary = "Returns image contents for given storage key")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    content = @Content(schema = @Schema(type = "string", format = "binary"))
            )
    })
    public ResponseEntity<byte[]> getPublicImage(@Valid @PathVariable(value = "storageKey") String storageKey) throws ApiException {
        return productService.getPublicImage(storageKey, null).toResponseEntity();
    }

    @GetMapping(value = "/image/{storageKey}/{size}", produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    @Operation(summary = "Returns image contents for given storage key")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    content = @Content(schema = @Schema(type = "string", format = "binary"))
            )
    })
    public ResponseEntity<byte[]> getPublicResizedImage(@Valid @PathVariable(value = "storageKey") String storageKey,
    		@Valid @PathVariable(value = "size", required = false) String size) throws ApiException {
        return productService.getPublicImage(storageKey, size).toResponseEntity();
    }
    
    @GetMapping(value = "/product/label/feedback/list/{labelUid}")
    @Operation(summary = "List feedback for a label uid")
    public ApiPaginatedResponse<ApiProductLabelFeedback> listProductLabelFeedbacks(
    		@Valid @Parameter(description = "Label id", required = true) @PathVariable("labelUid") String labelUid,
    		@Valid ApiListProductLabelFeedbackRequest request) throws ApiException {
    	return new ApiPaginatedResponse<>(productService.listProductLabelFeedbacks(labelUid, request));
    }    

    @PostMapping(value = "/product/label/feedback/{labelUid}")
    @Operation(summary = "Add a feedback to a label with a label uid")
    public ApiDefaultResponse addProductLabelFeedback(
    		@Valid @Parameter(description = "Label id", required = true) @PathVariable("labelUid") String labelUid,
    		@Valid @RequestBody ApiProductLabelFeedback request) throws ApiException {
    	productService.addProductLabelFeedback(labelUid, request);
    	return new ApiDefaultResponse();
    }    

    @Operation(summary = "Returns 'global settings' value")
    @GetMapping(value = "/globalSettings/{name}")
    public ApiResponse<ApiGlobalSettingsValue> getPublicGlobalSettings(@Valid @PathVariable(value = "name") String name) {
        return new ApiResponse<>(new ApiGlobalSettingsValue(globalSettingsService.getSettings(name, true)));
    }

}
