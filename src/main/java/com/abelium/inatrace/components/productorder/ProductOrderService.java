package com.abelium.inatrace.components.productorder;

import com.abelium.inatrace.api.ApiBaseEntity;
import com.abelium.inatrace.api.ApiStatus;
import com.abelium.inatrace.api.errors.ApiException;
import com.abelium.inatrace.components.common.BaseService;
import com.abelium.inatrace.components.company.CompanyService;
import com.abelium.inatrace.components.facility.FacilityService;
import com.abelium.inatrace.components.processingorder.ProcessingOrderService;
import com.abelium.inatrace.components.processingorder.api.ApiProcessingOrder;
import com.abelium.inatrace.components.productorder.api.ApiProductOrder;
import com.abelium.inatrace.components.productorder.mappers.ProductOrderMapper;
import com.abelium.inatrace.components.stockorder.api.ApiStockOrder;
import com.abelium.inatrace.db.entities.facility.Facility;
import com.abelium.inatrace.db.entities.productorder.ProductOrder;
import com.abelium.inatrace.db.entities.stockorder.enums.OrderType;
import com.abelium.inatrace.security.service.CustomUserDetails;
import com.abelium.inatrace.security.utils.PermissionsUtil;
import com.abelium.inatrace.types.Language;
import com.abelium.inatrace.types.UserRole;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import jakarta.transaction.Transactional;
import java.math.BigDecimal;

/**
 * Service for Product order entity.
 *
 * @author Pece Adjievski, Sunesis d.o.o.
 */
@Service
@Lazy
public class ProductOrderService extends BaseService {

	private final ProcessingOrderService processingOrderService;

	private final FacilityService facilityService;

	private final CompanyService companyService;

	@Autowired
	public ProductOrderService(ProcessingOrderService processingOrderService,
	                           FacilityService facilityService,
	                           CompanyService companyService) {
		this.processingOrderService = processingOrderService;
		this.facilityService = facilityService;
		this.companyService = companyService;
	}

	public ApiProductOrder getProductOrder(Long id, CustomUserDetails user, Language language) throws ApiException {

		ProductOrder productOrder = fetchProductOrder(id);

		// The order is readable by the company that owns the facility it was placed at - the same
		// company createProductOrder checks enrolment against before it will accept the order.
		PermissionsUtil.checkUserIfCompanyEnrolledOrSystemAdmin(
				productOrder.getFacility().getCompany().getUsers().stream().toList(), user);

		return ProductOrderMapper.toApiProductOrder(productOrder, language);
	}

	@Transactional
	public ApiBaseEntity createProductOrder(ApiProductOrder apiProductOrder, CustomUserDetails user, Language language) throws ApiException {

		// Validate input data
		if (StringUtils.isBlank(apiProductOrder.getOrderId())) {
			throw new ApiException(ApiStatus.VALIDATION_ERROR, "Order ID is required");
		}
		if (apiProductOrder.getDeliveryDeadline() == null) {
			throw new ApiException(ApiStatus.VALIDATION_ERROR, "Delivery deadline is requried");
		}
		if (apiProductOrder.getFacility() == null || apiProductOrder.getFacility().getId() == null) {
			throw new ApiException(ApiStatus.VALIDATION_ERROR, "Facility is required");
		}
		if (apiProductOrder.getCustomer() == null || apiProductOrder.getCustomer().getId() == null) {
			throw new ApiException(ApiStatus.VALIDATION_ERROR, "Customer is required");
		}
		if (CollectionUtils.isEmpty(apiProductOrder.getItems())) {
			throw new ApiException(ApiStatus.VALIDATION_ERROR, "At least one Stock order is requried");
		}

		Facility facility = facilityService.fetchFacility(apiProductOrder.getFacility().getId());
		if (
				user.getUserRole() != UserRole.SYSTEM_ADMIN &&
				facility.getCompany().getUsers().stream().noneMatch(cu -> cu.getUser().getId().equals(user.getUserId()))) {
			throw new ApiException(ApiStatus.AUTH_ERROR, "User is not enrolled in owner company");
		}

		// Prepare the Product order entity
		ProductOrder productOrder = new ProductOrder();
		productOrder.setOrderId(apiProductOrder.getOrderId());
		productOrder.setDeliveryDeadline(apiProductOrder.getDeliveryDeadline());
		productOrder.setRequiredOrganic(apiProductOrder.getRequiredOrganic());
		productOrder.setRequiredWomensOnly(apiProductOrder.getRequiredWomensOnly());
		productOrder.setFacility(facility);
		productOrder.setCustomer(companyService.fetchCompanyCustomer(apiProductOrder.getCustomer().getId()));

		em.persist(productOrder);

		// Prepare target stock order and target processing order for every item in the Product order
		for (ApiStockOrder orderItem : apiProductOrder.getItems()) {

			if (orderItem.getProcessingOrder() == null) {
				throw new ApiException(ApiStatus.VALIDATION_ERROR, "Processing order is required");
			}
			if (orderItem.getProcessingOrder().getProcessingAction() == null ||
					orderItem.getProcessingOrder().getProcessingAction().getId() == null) {
				throw new ApiException(ApiStatus.VALIDATION_ERROR, "Processing action is required");
			}

			ApiStockOrder targetStockOrder = new ApiStockOrder();

			// Set the persisted Product order in the target stokc order to be created
			targetStockOrder.setProductOrder(new ApiProductOrder());
			targetStockOrder.getProductOrder().setId(productOrder.getId());

			// Set the other fields
			targetStockOrder.setDeliveryTime(orderItem.getDeliveryTime());
			targetStockOrder.setCreatorId(orderItem.getCreatorId());
			targetStockOrder.setFinalProduct(orderItem.getFinalProduct());
			targetStockOrder.setFacility(orderItem.getFacility());
			targetStockOrder.setQuoteFacility(orderItem.getQuoteFacility());
			targetStockOrder.setFulfilledQuantity(BigDecimal.ZERO);
			targetStockOrder.setAvailableQuantity(BigDecimal.ZERO);
			targetStockOrder.setTotalQuantity(orderItem.getTotalQuantity());
			targetStockOrder.setProductionDate(orderItem.getProductionDate());
			targetStockOrder.setOrderType(OrderType.GENERAL_ORDER);
			targetStockOrder.setInternalLotNumber(orderItem.getInternalLotNumber());
			targetStockOrder.setCurrency(orderItem.getCurrency());
			targetStockOrder.setCurrencyForEndCustomer(orderItem.getCurrencyForEndCustomer());
			targetStockOrder.setPricePerUnitForEndCustomer(orderItem.getPricePerUnitForEndCustomer());

			// Set the consumer company customer
			targetStockOrder.setConsumerCompanyCustomer(orderItem.getConsumerCompanyCustomer());

			// Prepare the Processing order data (the Processing order data is contained inside the Stock order from the API request)
			ApiProcessingOrder targetProcessingOrder = new ApiProcessingOrder();
			targetProcessingOrder.setProcessingAction(orderItem.getProcessingOrder().getProcessingAction());
			targetProcessingOrder.setInitiatorUserId(orderItem.getProcessingOrder().getInitiatorUserId());
			targetProcessingOrder.getTargetStockOrders().add(targetStockOrder);

			// Create the processing order
			processingOrderService.createOrUpdateProcessingOrder(targetProcessingOrder, user, language);
		}

		return new ApiBaseEntity(productOrder);
	}

	private ProductOrder fetchProductOrder(Long id) throws ApiException {

		ProductOrder productOrder = em.find(ProductOrder.class, id);
		if (productOrder == null) {
			throw new ApiException(ApiStatus.INVALID_REQUEST, "Invalid Product order ID");
		}

		return productOrder;
	}

}
