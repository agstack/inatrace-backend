package com.abelium.inatrace.components.payment;

import com.abelium.inatrace.api.ApiBaseEntity;
import com.abelium.inatrace.api.ApiPaginatedList;
import com.abelium.inatrace.api.ApiPaginatedRequest;
import com.abelium.inatrace.api.ApiStatus;
import com.abelium.inatrace.api.errors.ApiException;
import com.abelium.inatrace.components.common.BaseService;
import com.abelium.inatrace.components.common.api.ApiActivityProof;
import com.abelium.inatrace.components.company.CompanyQueries;
import com.abelium.inatrace.components.payment.api.ApiBulkPayment;
import com.abelium.inatrace.components.payment.api.ApiPayment;
import com.abelium.inatrace.components.stockorder.StockOrderService;
import com.abelium.inatrace.components.user.UserService;
import com.abelium.inatrace.db.entities.common.ActivityProof;
import com.abelium.inatrace.db.entities.common.Document;
import com.abelium.inatrace.db.entities.common.User;
import com.abelium.inatrace.db.entities.company.Company;
import com.abelium.inatrace.db.entities.payment.*;
import com.abelium.inatrace.db.entities.stockorder.StockOrder;
import com.abelium.inatrace.db.entities.stockorder.enums.OrderType;
import com.abelium.inatrace.security.service.CustomUserDetails;
import com.abelium.inatrace.security.utils.PermissionsUtil;
import com.abelium.inatrace.tools.PaginationTools;
import com.abelium.inatrace.tools.Queries;
import com.abelium.inatrace.tools.QueryTools;
import com.abelium.inatrace.tools.TranslateTools;
import com.abelium.inatrace.types.Language;
import jakarta.transaction.Transactional;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.torpedoquery.jakarta.jpa.OnGoingLogicalCondition;
import org.torpedoquery.jakarta.jpa.Torpedo;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Service for payment entity.
 *
 * @author Rene Flores, Sunesis d.o.o.
 */
@Lazy
@Service
public class PaymentService extends BaseService {

	private final UserService userService;

	private final CompanyQueries companyQueries;

	private final StockOrderService stockOrderService;

	private final MessageSource messageSource;

	@Autowired
	public PaymentService(UserService userService,
	                      CompanyQueries companyQueries,
	                      StockOrderService stockOrderService,
	                      MessageSource messageSource) {
		this.userService = userService;
		this.companyQueries = companyQueries;
		this.stockOrderService = stockOrderService;
		this.messageSource = messageSource;
	}

	public ApiPayment getPayment(Long id, CustomUserDetails user) throws ApiException {

		Payment payment = fetchEntity(id, Payment.class);

		// Check that the request user is enrolled in the paying company (owner company)
		PermissionsUtil.checkUserIfCompanyEnrolled(payment.getPayingCompany().getUsers().stream().toList(), user);

		return PaymentMapper.toApiPayment(payment, user.getUserId());
	}

	public ApiBulkPayment getBulkPayment(Long id, CustomUserDetails user) throws ApiException {

		BulkPayment bulkPayment = fetchEntity(id, BulkPayment.class);

		// Check that the request user is enrolled in the paying company (bulk payment owner company)
		PermissionsUtil.checkUserIfCompanyEnrolled(bulkPayment.getPayingCompany().getUsers().stream().toList(), user);

		return BulkPaymentMapper.toApiBulkPayment(bulkPayment, user.getUserId());
	}

	public ApiPaginatedList<ApiPayment> getPaymentList(ApiPaginatedRequest request, PaymentQueryRequest queryRequest, CustomUserDetails user) throws ApiException {

		if (queryRequest.companyId == null && queryRequest.purchaseId == null) {
			throw new ApiException(ApiStatus.UNAUTHORIZED, "Company ID or Purchase ID is required!");
		}

		if (queryRequest.companyId != null) {
			Company company = companyQueries.fetchCompany(queryRequest.companyId);
			PermissionsUtil.checkUserIfCompanyEnrolled(company.getUsers().stream().toList(), user);
		} else {
			// Check that the stock order exists and also validate that request user is enrolled in stock order owner company
			StockOrder stockOrder = stockOrderService.fetchEntity(queryRequest.purchaseId, StockOrder.class);
			PermissionsUtil.checkUserIfCompanyEnrolled(stockOrder.getCompany().getUsers().stream().toList(), user);
		}

		return PaginationTools.createPaginatedResponse(em, request, () -> paymentQueryObject(
				request, queryRequest), payment -> PaymentMapper.toApiPayment(payment, user.getUserId()));
	}

	public byte[] exportPaymentsByCompany(CustomUserDetails authUser, Long companyId, Language language) throws IOException, ApiException {

		// Get the payments list
		ApiPaginatedRequest request = new ApiPaginatedRequest();
		request.setLimit(10000);

		List<ApiPayment> payments = getPaymentList(request, new PaymentQueryRequest(companyId), authUser).items;

		ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
		try (XSSFWorkbook workbook = new XSSFWorkbook()) {

			// Create date cell style
			CellStyle dateCellStyle = workbook.createCellStyle();
			dateCellStyle.setDataFormat((short) 14);

			// Create Excel sheet
			XSSFSheet sheet = workbook.createSheet(TranslateTools.getTranslatedValue(
					messageSource, "export.payments.sheet.name", language
			));

			// Prepare the header
			Row headerRow = sheet.createRow(0);
			headerRow.createCell(0, CellType.STRING).setCellValue(TranslateTools.getTranslatedValue(
					messageSource, "export.payments.column.paymentPurposeType.label", language
			));
			headerRow.createCell(1, CellType.STRING).setCellValue(TranslateTools.getTranslatedValue(
					messageSource, "export.payments.column.amount.label", language
			));
			headerRow.createCell(2, CellType.STRING).setCellValue(TranslateTools.getTranslatedValue(
					messageSource, "export.payments.column.currency.label", language
			));
			headerRow.createCell(3, CellType.STRING).setCellValue(TranslateTools.getTranslatedValue(
					messageSource, "export.payments.column.farmerName.label", language
			));
			headerRow.createCell(4, CellType.STRING).setCellValue(TranslateTools.getTranslatedValue(
					messageSource, "export.payments.column.companyName.label", language
			));
			headerRow.createCell(5, CellType.STRING).setCellValue(TranslateTools.getTranslatedValue(
					messageSource, "export.payments.column.deliveryDate.label", language
			));
			headerRow.createCell(6, CellType.STRING).setCellValue(TranslateTools.getTranslatedValue(
					messageSource, "export.payments.column.paymentDate.label", language
			));
			headerRow.createCell(7, CellType.STRING).setCellValue(TranslateTools.getTranslatedValue(
					messageSource, "export.payments.column.preferredWayOfPayment.label", language
			));
			headerRow.createCell(8, CellType.STRING).setCellValue(TranslateTools.getTranslatedValue(
					messageSource, "export.payments.column.receiptNumber.label", language
			));

			int rowNum = 1;
			for (ApiPayment apiPayment : payments) {

				Row row = sheet.createRow(rowNum++);

				// Create payment purpose type column
				row.createCell(0, CellType.STRING).setCellValue(TranslateTools.getTranslatedValue(
						messageSource, "export.payments.column.paymentPurposeType.value." + apiPayment.getPaymentPurposeType().toString(), language
				));
				// sheet.autoSizeColumn(0);

				// Create amount column
				row.createCell(1, CellType.NUMERIC).setCellValue(apiPayment.getAmount().doubleValue());
				// sheet.autoSizeColumn(1);

				// Create currency column
				row.createCell(2, CellType.STRING).setCellValue(apiPayment.getCurrency());
				// sheet.autoSizeColumn(2);

				// Create farmer name column (cell is populated only recipient user customer is of type FARMER)
				row.createCell(3, CellType.STRING);

				// Create recipient company column
				row.createCell(4, CellType.STRING);

				// Depending on the recipient type (farmer of company) create the appropriate column
				switch (apiPayment.getRecipientType()) {
					case USER_CUSTOMER:
						row.getCell(3).setCellValue(apiPayment.getRecipientUserCustomer().getName() + " " + apiPayment.getRecipientUserCustomer().getSurname());
						// sheet.autoSizeColumn(3);
						break;
					case COMPANY:
						row.getCell(4).setCellValue(apiPayment.getRecipientCompany().getName());
						// sheet.autoSizeColumn(4);
				}

				// Create delivery date column
				row.createCell(5, CellType.NUMERIC).setCellValue(apiPayment.getProductionDate());
				row.getCell(5).setCellStyle(dateCellStyle);
				// sheet.autoSizeColumn(5);

				// Create payment date column
				row.createCell(6, CellType.NUMERIC).setCellValue(apiPayment.getFormalCreationTime());
				row.getCell(6).setCellStyle(dateCellStyle);
				// sheet.autoSizeColumn(6);

				// Create preferred way of payment column
				row.createCell(7, CellType.STRING).setCellValue(TranslateTools.getTranslatedValue(
						messageSource, "export.payments.column.preferredWayOfPayment.value." + apiPayment.getPreferredWayOfPayment().toString(), language
				));
				// sheet.autoSizeColumn(7);

				// Create receipt number column
				row.createCell(8, CellType.STRING).setCellValue(apiPayment.getReceiptNumber());
				// sheet.autoSizeColumn(8);
			}

			workbook.write(byteArrayOutputStream);
		}

		return byteArrayOutputStream.toByteArray();
	}

	private Payment paymentQueryObject(ApiPaginatedRequest request, PaymentQueryRequest queryRequest) {

		Payment paymentProxy = Torpedo.from(Payment.class);
		OnGoingLogicalCondition condition = Torpedo.condition();

		// Applies only when fetching list by PurchaseID or CompanyID
		if (queryRequest.companyId != null) {
			condition = condition.and(paymentProxy.getStockOrder().getCompany().getId()).eq(queryRequest.companyId);
		} else if (queryRequest.purchaseId != null) {
			condition = condition.and(paymentProxy.getStockOrder().getId()).eq(queryRequest.purchaseId);
		}

		// Query parameter filters
		if(queryRequest.paymentStatus != null) {
			condition = condition.and(paymentProxy.getPaymentStatus()).eq(queryRequest.paymentStatus);
		}
		if(queryRequest.preferredWayOfPayment != null) {
			condition = condition.and(paymentProxy.getPreferredWayOfPayment()).eq(queryRequest.preferredWayOfPayment);
		}
		if(queryRequest.productionDateStart != null) {
			condition = condition.and(paymentProxy.getProductionDate()).gte(queryRequest.productionDateStart);
		}
		if(queryRequest.productionDateEnd != null) {
			condition = condition.and(paymentProxy.getProductionDate()).lte(queryRequest.productionDateEnd);
		}
		if(queryRequest.farmerName != null) { // Search by farmers name (query)
			condition = condition.and(paymentProxy.getRecipientUserCustomer()).isNotNull();
			OnGoingLogicalCondition likeName = Torpedo.condition(paymentProxy.getRecipientUserCustomer().getName()).like().any(queryRequest.farmerName);
			OnGoingLogicalCondition likeSurname = Torpedo.condition(paymentProxy.getRecipientUserCustomer().getSurname()).like().any(queryRequest.farmerName);
			condition = condition.and(Torpedo.condition(likeName.or(likeSurname)));
		}
		if (queryRequest.farmerId != null) {
			condition = condition.and(paymentProxy.getRecipientUserCustomer()).isNotNull();
			condition = condition.and(paymentProxy.getRecipientUserCustomer().getId()).eq(queryRequest.farmerId);
		}
		if (queryRequest.representativeOfRecipientUserCustomerId != null) {
			condition = condition.and(paymentProxy.getRepresentativeOfRecipientUserCustomer()).isNotNull();
			condition = condition.and(paymentProxy.getRepresentativeOfRecipientUserCustomer().getId())
					.eq(queryRequest.representativeOfRecipientUserCustomerId);
		}

		Torpedo.where(condition);

		if ("productionDate".equals(request.sortBy)) {
			QueryTools.orderBy(request.sort, paymentProxy.getProductionDate());
		} else {
			QueryTools.orderBy(request.sort, paymentProxy.getId());
		}

		return paymentProxy;
	}

	public ApiPaginatedList<ApiBulkPayment> listBulkPayments(ApiPaginatedRequest request,
															 PaymentQueryRequest queryRequest,
															 CustomUserDetails user) throws ApiException {

		if (queryRequest.companyId == null) {
			throw new ApiException(ApiStatus.UNAUTHORIZED, "Company ID is required");
		}

		Company company = companyQueries.fetchCompany(queryRequest.companyId);
		PermissionsUtil.checkUserIfCompanyEnrolled(company.getUsers().stream().toList(), user);

		return PaginationTools.createPaginatedResponse(em, request, () -> bulkPaymentQueryObject(
				request, queryRequest), bulkPayment -> BulkPaymentMapper.toApiBulkPaymentBase(bulkPayment, user.getUserId()));
	}

	public byte[] exportBulkPaymentsByCompany(CustomUserDetails authUser, Long companyId, Language language) throws IOException, ApiException {

		ApiPaginatedRequest request = new ApiPaginatedRequest();
		request.setLimit(10000);

		List<ApiBulkPayment> bulkPayments = listBulkPayments(request, new PaymentQueryRequest(companyId), authUser).items;

		ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
		try (XSSFWorkbook workbook = new XSSFWorkbook()) {

			// Create Excel sheet
			XSSFSheet sheet = workbook.createSheet(TranslateTools.getTranslatedValue(
					messageSource, "export.bulkPayments.sheet.name", language
			));

			// Prepare the header
			Row headerRow = sheet.createRow(0);
			headerRow.createCell(0, CellType.STRING).setCellValue(TranslateTools.getTranslatedValue(
					messageSource, "export.bulkPayments.column.paymentPurposeType.label", language
			));
			headerRow.createCell(1, CellType.STRING).setCellValue(TranslateTools.getTranslatedValue(
					messageSource, "export.bulkPayments.column.receiptNumber.label", language
			));
			headerRow.createCell(2, CellType.STRING).setCellValue(TranslateTools.getTranslatedValue(
					messageSource, "export.bulkPayments.column.totalAmount.label", language
			));
			headerRow.createCell(3, CellType.STRING).setCellValue(TranslateTools.getTranslatedValue(
					messageSource, "export.bulkPayments.column.currency.label", language
			));

			int rowNum = 1;
			for (ApiBulkPayment apiBulkPayment : bulkPayments) {

				Row row = sheet.createRow(rowNum++);

				// Create payment purpose type column
				row.createCell(0, CellType.STRING).setCellValue(TranslateTools.getTranslatedValue(
						messageSource, "export.payments.column.paymentPurposeType.value." + apiBulkPayment.getPaymentPurposeType().toString(), language
				));
				// sheet.autoSizeColumn(0);

				// Create receipt number column
				row.createCell(1, CellType.STRING).setCellValue(apiBulkPayment.getReceiptNumber());
				// sheet.autoSizeColumn(1);

				// Create total amount column
				row.createCell(2, CellType.NUMERIC).setCellValue(apiBulkPayment.getTotalAmount().doubleValue());
				// sheet.autoSizeColumn(2);

				// Create bulk payment currency column
				row.createCell(3, CellType.STRING).setCellValue(apiBulkPayment.getCurrency());
				// sheet.autoSizeColumn(3);
			}

			workbook.write(byteArrayOutputStream);
		}

		return byteArrayOutputStream.toByteArray();
	}

	private BulkPayment bulkPaymentQueryObject(ApiPaginatedRequest request, PaymentQueryRequest queryRequest) {

		BulkPayment bulkPaymentProxy = Torpedo.from(BulkPayment.class);
		OnGoingLogicalCondition condition = Torpedo.condition();

		if (queryRequest.companyId != null) {
			condition = condition.and(bulkPaymentProxy.getPayingCompany().getId()).eq(queryRequest.companyId);
		}

		Torpedo.where(condition);

		switch (request.sortBy) {
			case "receiptNumber": QueryTools.orderBy(request.sort, bulkPaymentProxy.getReceiptNumber()); break;
			case "totalAmount": QueryTools.orderBy(request.sort, bulkPaymentProxy.getTotalAmount()); break;
			case "paymentPurposeType": QueryTools.orderBy(request.sort, bulkPaymentProxy.getPaymentPurposeType()); break;
			default: QueryTools.orderBy(request.sort, bulkPaymentProxy.getId());
		}

		return bulkPaymentProxy;
	}

	@Transactional
	public ApiBaseEntity createOrUpdatePayment(ApiPayment apiPayment,
											   CustomUserDetails user,
											   boolean isPartOfBulkPayment) throws ApiException {

		Payment entity = fetchEntityOrElse(apiPayment.getId(), Payment.class, new Payment());
		User currentUser = userService.fetchUserById(user.getUserId());

		if (entity.getId() != null) {

			// Check if the request user is enrolled in the owner company
			PermissionsUtil.checkUserIfCompanyEnrolled(entity.getPayingCompany().getUsers().stream().toList(), user);

			// Do not allow update of fields other than the payment status
			// Also it is not allowed to set back the value to UNCONFIRMED
			if (apiPayment.getPaymentStatus() == PaymentStatus.CONFIRMED) {
				if (entity.getPaymentStatus() == PaymentStatus.UNCONFIRMED) {
					entity.setPaymentStatus(PaymentStatus.CONFIRMED);
				}
			}

			entity.setPaymentConfirmedAtTime(Instant.now());
			entity.setPaymentConfirmedByCompany(entity.getPayingCompany());
			entity.setPaymentConfirmedByUser(currentUser);
			entity.setUpdatedBy(currentUser);

		} else {

			if (apiPayment.getRecipientType() == null) {
				throw new ApiException(ApiStatus.VALIDATION_ERROR, "Recipient type is required");
			}

			StockOrder stockOrder = fetchEntity(apiPayment.getStockOrder().getId(), StockOrder.class);

			// Check that the request user is enrolled in the stock order owner company (the company that initiates the payment)
			PermissionsUtil.checkUserIfCompanyEnrolled(stockOrder.getCompany().getUsers().stream().toList(), user);

			if (stockOrder.getOrderType() != OrderType.PURCHASE_ORDER && stockOrder.getOrderType() != OrderType.GENERAL_ORDER) {
				throw new ApiException(ApiStatus.VALIDATION_ERROR, "Not a Purchase or Quote order");
			}

			// Verify document is provided, if payment purpose is FIRST_INSTALLMENT
			if (!isPartOfBulkPayment && apiPayment.getPaymentPurposeType() == PaymentPurposeType.FIRST_INSTALLMENT && apiPayment.getReceiptDocument() == null) {
				throw new ApiException(ApiStatus.VALIDATION_ERROR, "Receipt document has to be provided");
			}

			// Verify totalPaid (amount paid to the farmer) is not negative
			if (apiPayment.getAmount().compareTo(BigDecimal.ZERO) < 0) {
				throw new ApiException(ApiStatus.VALIDATION_ERROR, "Total amount paid cannot be negative");
			}

			// Check if recipient type is COMPANY that a recipient Company is provided
			if (apiPayment.getRecipientType() == RecipientType.COMPANY && apiPayment.getRecipientCompany() == null) {
				throw new ApiException(ApiStatus.VALIDATION_ERROR, "Recipient company is required when type is COMPANY");
			}

			// Check if recipient type is USER_CUSTOMER that a recipient user customer is provided
			if (apiPayment.getRecipientType() == RecipientType.USER_CUSTOMER && apiPayment.getRecipientUserCustomer() == null) {
				throw new ApiException(ApiStatus.VALIDATION_ERROR, "Recipient user customer is required when type is USER_CUSTOMER");
			}

			// Receipt document (note: Storage key needs to be unique)
			if (apiPayment.getReceiptDocument() != null) {
				entity.setReceiptDocument(fetchEntity(apiPayment.getReceiptDocument().getId(), Document.class));
			}

			// Values from StockOrder
			entity.setStockOrder(stockOrder);
			entity.setOrderReference(stockOrder.getIdentifier());
			entity.setProductionDate(stockOrder.getProductionDate());
			entity.setPayingCompany(stockOrder.getCompany()); // The company that is paying
			entity.setPurchased(stockOrder.getTotalQuantity());
			entity.setPreferredWayOfPayment(stockOrder.getPreferredWayOfPayment());
			entity.setRecipientUserCustomer(stockOrder.getProducerUserCustomer()); // Farmer
			entity.setRepresentativeOfRecipientUserCustomer(stockOrder.getRepresentativeOfProducerUserCustomer()); // Collector

			// Values from request
			entity.setReceiptDocumentType(apiPayment.getReceiptDocumentType());
			entity.setFormalCreationTime(apiPayment.getFormalCreationTime());
			entity.setPaymentPurposeType(apiPayment.getPaymentPurposeType());
			entity.setPaymentStatus(apiPayment.getPaymentStatus());
			entity.setPaymentType(apiPayment.getPaymentType());
			entity.setRecipientType(apiPayment.getRecipientType());
			entity.setReceiptNumber(apiPayment.getReceiptNumber());
			entity.setCurrency(apiPayment.getCurrency());

			entity.setTotalPaid(apiPayment.getAmount());
			entity.setAmount(apiPayment.getAmount());
			entity.setAmountPaidToTheCollector(BigDecimal.ZERO);

			// If this payment is for Quote order between two companies in the value chain
			if (apiPayment.getRecipientCompany() != null) {
				entity.setRecipientCompany(fetchEntityOrElse(apiPayment.getRecipientCompany().getId(), Company.class, null));
			}

			if (stockOrder.getOrderType() == OrderType.PURCHASE_ORDER && !Boolean.TRUE.equals(stockOrder.getPriceDeterminedLater())) {
				BigDecimal sumTotalPaid = stockOrder.getPayments().stream()
						.map(p -> p.getTotalPaid() != null ? p.getTotalPaid() : BigDecimal.ZERO)
						.reduce(BigDecimal.ZERO, BigDecimal::add);

				stockOrder.setBalance(stockOrder.getCost().subtract(sumTotalPaid).subtract(entity.getTotalPaid() != null ? entity.getTotalPaid() : BigDecimal.ZERO));
				stockOrder.setUpdatedBy(currentUser);
			}

			entity.setCreatedBy(currentUser);
			entity.setUpdatedBy(currentUser);
		}
		
		if (entity.getId() == null) {
			em.persist(entity);
		}

		return new ApiBaseEntity(entity);
	}


	@Transactional
	public ApiBaseEntity createBulkPayment(ApiBulkPayment apiBulkPayment, CustomUserDetails user) throws ApiException {

		// Bulk-payments cannot be updated
		if (apiBulkPayment.getId() != null) {
			throw new ApiException(ApiStatus.INVALID_REQUEST, "Bulk payment cannot be updated!");
		}

		// Required fields
		if (apiBulkPayment.getPayingCompany() == null || apiBulkPayment.getPayingCompany().getId() == null) {
			throw new ApiException(ApiStatus.INVALID_REQUEST, "Paying company ID has to be provided!");
		}

		if (apiBulkPayment.getPayments() == null || apiBulkPayment.getPayments().isEmpty()) {
			throw new ApiException(ApiStatus.INVALID_REQUEST, "At least one payment needs to be provided.");
		}

		if (apiBulkPayment.getPaymentDescription() == null) {
			throw new ApiException(ApiStatus.INVALID_REQUEST, "Payment description needs to be provided.");
		}

		if (apiBulkPayment.getAdditionalCost() != null && apiBulkPayment.getAdditionalCostDescription() == null) {
			throw new ApiException(ApiStatus.INVALID_REQUEST, "Additional cost description needs to be provided.");
		}

		if (apiBulkPayment.getReceiptNumber() == null) {
			throw new ApiException(ApiStatus.INVALID_REQUEST, "Recipient number is required.");
		}

		Company payingCompany = fetchEntity(apiBulkPayment.getPayingCompany().getId(), Company.class);

		// Check that the request user is enrolled in the paying company (the company that initiates the payment)
		PermissionsUtil.checkUserIfCompanyEnrolled(payingCompany.getUsers().stream().toList(), user);

		BulkPayment entity = new BulkPayment();

		entity.setCreatedBy(userService.fetchUserById(user.getUserId()));
		entity.setPayingCompany(payingCompany);

		entity.setPaymentDescription(apiBulkPayment.getPaymentDescription());
		entity.setPaymentPurposeType(apiBulkPayment.getPaymentPurposeType());
		entity.setReceiptNumber(apiBulkPayment.getReceiptNumber());
		entity.setTotalAmount(apiBulkPayment.getTotalAmount());
		entity.setAdditionalCost(apiBulkPayment.getAdditionalCost());
		entity.setAdditionalCostDescription(apiBulkPayment.getAdditionalCostDescription());
		entity.setCurrency(apiBulkPayment.getCurrency());
		entity.setFormalCreationTime(apiBulkPayment.getFormalCreationTime() != null
				? apiBulkPayment.getFormalCreationTime()
				: Instant.now());

		// Create payments
		for (ApiPayment apiPayment: apiBulkPayment.getPayments()) {

			Long insertedPaymentId = createOrUpdatePayment(apiPayment, user, true).getId();
			Payment payment = fetchEntity(insertedPaymentId, Payment.class);

			// Bi-directional mapping
			payment.setBulkPayment(entity);
			entity.getPayments().add(payment);
		}

		// Add activity proofs
		for (ApiActivityProof apiActivityProof : apiBulkPayment.getAdditionalProofs()) {

			Document activityProofDoc = fetchEntity(apiActivityProof.getDocument().getId(), Document.class);

			BulkPaymentActivityProof bulkPaymentActivityProof = new BulkPaymentActivityProof();
			bulkPaymentActivityProof.setBulkPayment(entity);
			ActivityProof activityProof = new ActivityProof();
			activityProof.setDocument(activityProofDoc);
			activityProof.setType(apiActivityProof.getType());
			activityProof.setFormalCreationDate(apiActivityProof.getFormalCreationDate());
			activityProof.setValidUntil(apiActivityProof.getValidUntil());
			em.persist(activityProof);
			bulkPaymentActivityProof.setActivityProof(activityProof);

			entity.getAdditionalProofs().add(bulkPaymentActivityProof);
		}

		if (entity.getId() == null) {
			em.persist(entity);
		}

		return new ApiBaseEntity(entity);
	}

	@Transactional
	public void deletePayment(Long id, CustomUserDetails user) throws ApiException {

		Payment payment = fetchEntity(id, Payment.class);

		// Check that the request user is enrolled in the paying company (the company that initiated the payment)
		PermissionsUtil.checkUserIfCompanyEnrolled(payment.getPayingCompany().getUsers().stream().toList(), user);

		User currentUser = userService.fetchUserById(user.getUserId());
		StockOrder stockOrder = payment.getStockOrder();
		if (stockOrder.getOrderType() == OrderType.PURCHASE_ORDER && !Boolean.TRUE.equals(stockOrder.getPriceDeterminedLater())) {
			BigDecimal sumTotalPaid = stockOrder.getPayments().stream()
					.map(p -> p.getTotalPaid() != null ? p.getTotalPaid() : BigDecimal.ZERO)
					.reduce(BigDecimal.ZERO, BigDecimal::add);

			stockOrder.setBalance(stockOrder.getCost().subtract(sumTotalPaid).add(payment.getTotalPaid() != null ? payment.getTotalPaid() : BigDecimal.ZERO));
			stockOrder.setUpdatedBy(currentUser);
		}

		// Remove mapping on both sides, otherwise it does not delete
		stockOrder.getPayments().removeIf(p -> p.getId().equals(payment.getId()));
		em.remove(payment);
	}

	private <E> E fetchEntity(Long id, Class<E> entityClass) throws ApiException {

		E entity = Queries.get(em, entityClass, id);
		if (entity == null) {
			throw new ApiException(ApiStatus.INVALID_REQUEST, "Invalid " + entityClass.getSimpleName() + " ID");
		}
		return entity;
	}

	private <E> E fetchEntityOrElse(Long id, Class<E> entityClass, E defaultValue) {
		E entity = Queries.get(em, entityClass, id);
		return entity == null ? defaultValue : entity;
	}

}
