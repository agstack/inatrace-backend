package com.abelium.inatrace.components.stockorder;

import com.abelium.inatrace.api.ApiBaseEntity;
import com.abelium.inatrace.api.ApiPaginatedList;
import com.abelium.inatrace.api.ApiPaginatedRequest;
import com.abelium.inatrace.api.ApiStatus;
import com.abelium.inatrace.api.errors.ApiException;
import com.abelium.inatrace.components.codebook.measure_unit_type.MeasureUnitTypeMapper;
import com.abelium.inatrace.components.codebook.processing_evidence_type.ProcessingEvidenceTypeService;
import com.abelium.inatrace.components.codebook.processingevidencefield.ProcessingEvidenceFieldService;
import com.abelium.inatrace.components.codebook.semiproduct.SemiProductService;
import com.abelium.inatrace.components.common.BaseService;
import com.abelium.inatrace.components.common.api.ApiActivityProof;
import com.abelium.inatrace.components.common.api.ApiCertification;
import com.abelium.inatrace.components.company.CompanyApiTools;
import com.abelium.inatrace.components.company.CompanyQueries;
import com.abelium.inatrace.components.currencies.CurrencyService;
import com.abelium.inatrace.components.facility.FacilityService;
import com.abelium.inatrace.components.facility.api.ApiFacility;
import com.abelium.inatrace.components.payment.api.ApiPayment;
import com.abelium.inatrace.components.processingorder.api.ApiProcessingOrder;
import com.abelium.inatrace.components.processingorder.mappers.ProcessingOrderMapper;
import com.abelium.inatrace.components.product.FinalProductService;
import com.abelium.inatrace.components.stockorder.api.*;
import com.abelium.inatrace.components.stockorder.mappers.StockOrderEvidenceTypeValueMapper;
import com.abelium.inatrace.components.stockorder.mappers.StockOrderMapper;
import com.abelium.inatrace.components.transaction.api.ApiTransaction;
import com.abelium.inatrace.components.transaction.mappers.TransactionMapper;
import com.abelium.inatrace.db.entities.common.*;
import com.abelium.inatrace.db.entities.company.Company;
import com.abelium.inatrace.db.entities.company.CompanyCertification;
import com.abelium.inatrace.db.entities.company.CompanyCustomer;
import com.abelium.inatrace.db.entities.facility.Facility;
import com.abelium.inatrace.db.entities.payment.Payment;
import com.abelium.inatrace.db.entities.payment.PaymentPurposeType;
import com.abelium.inatrace.db.entities.processingaction.ProcessingAction;
import com.abelium.inatrace.db.entities.processingorder.ProcessingOrder;
import com.abelium.inatrace.db.entities.product.ProductCompany;
import com.abelium.inatrace.db.entities.productorder.ProductOrder;
import com.abelium.inatrace.db.entities.stockorder.*;
import com.abelium.inatrace.db.entities.stockorder.enums.OrderType;
import com.abelium.inatrace.db.entities.stockorder.enums.PreferredWayOfPayment;
import com.abelium.inatrace.db.entities.stockorder.enums.TransactionStatus;
import com.abelium.inatrace.security.service.CustomUserDetails;
import com.abelium.inatrace.security.utils.PermissionsUtil;
import com.abelium.inatrace.tools.PaginationTools;
import com.abelium.inatrace.tools.Queries;
import com.abelium.inatrace.tools.QueryTools;
import com.abelium.inatrace.tools.TranslateTools;
import com.abelium.inatrace.types.Language;
import com.abelium.inatrace.types.ProcessingActionType;
import com.abelium.inatrace.types.ProductCompanyType;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.persistence.TypedQuery;
import jakarta.transaction.Transactional;
import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.StringUtils;
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
import java.math.RoundingMode;
import java.time.ZoneOffset;
import java.util.*;
import java.util.stream.Collectors;

@Lazy
@Service
public class StockOrderService extends BaseService {

    private static final String CUPPING_SCORE_FIELD_NAME = "CUPPING_SCORE";
    private static final String CUPPING_FLAVOUR_FIELD_NAME = "CUPPING_FLAVOUR";
    private static final String ROASTING_PROFILE_FIELD_NAME = "ROASTING_PROFILE";

    private final FacilityService facilityService;

    private final ProcessingEvidenceFieldService procEvidenceFieldService;

    private final ProcessingEvidenceTypeService procEvidenceTypeService;

    private final SemiProductService semiProductService;

    private final FinalProductService finalProductService;

    private final CurrencyService currencyService;

    private final CompanyQueries companyQueries;

    private final MessageSource messageSource;

    @Autowired
    public StockOrderService(FacilityService facilityService,
                             ProcessingEvidenceFieldService procEvidenceFieldService,
                             ProcessingEvidenceTypeService procEvidenceTypeService,
                             SemiProductService semiProductService,
                             FinalProductService finalProductService,
                             CurrencyService currencyService,
                             CompanyQueries companyQueries,
                             MessageSource messageSource) {
        this.facilityService = facilityService;
        this.procEvidenceFieldService = procEvidenceFieldService;
        this.procEvidenceTypeService = procEvidenceTypeService;
        this.semiProductService = semiProductService;
        this.finalProductService = finalProductService;
        this.currencyService = currencyService;
        this.companyQueries = companyQueries;
        this.messageSource = messageSource;
    }

    public ApiStockOrder getStockOrder(long id, CustomUserDetails user, Language language, Boolean withProcessingOrder) throws ApiException {

        StockOrder stockOrder = fetchEntity(id, StockOrder.class);

        // Check that the request user is form a company which is connected to the company that owns the quote order (or is a user of that company)
        PermissionsUtil.checkUserIfConnectedWithProducts(companyQueries.fetchCompanyProducts(stockOrder.getCompany().getId()), user);

        return StockOrderMapper.toApiStockOrder(stockOrder, user.getUserId(), language, withProcessingOrder);
    }

    public ApiProcessingOrder getStockOrderProcessingOrder(long id, CustomUserDetails user, Language language) throws ApiException {

        StockOrder stockOrder = fetchEntity(id, StockOrder.class);

        // Check that the request user is form a company which is connected to the company that owns the quote order (or is a user of that company)
        PermissionsUtil.checkUserIfConnectedWithProducts(companyQueries.fetchCompanyProducts(stockOrder.getCompany().getId()), user);

        // If Stock order has no Processing order set, exit with exception
        if (stockOrder.getProcessingOrder() == null) {
            throw new ApiException(ApiStatus.INVALID_REQUEST, "The Stock order with ID: " + id + " doesn't have Processing order.");
        }

        return ProcessingOrderMapper.toApiProcessingOrder(stockOrder.getProcessingOrder(), language);
    }

    public ApiPaginatedList<ApiStockOrder> getAvailableStockOrderListForFacility(ApiPaginatedRequest request,
                                                                                 StockOrderQueryRequest queryRequest,
                                                                                 CustomUserDetails user,
                                                                                 Language language) throws ApiException {

        if (queryRequest.facilityId == null) {
            throw new ApiException(ApiStatus.UNAUTHORIZED, "Facility ID should be provided!");
        }

        // Get the owner company of the facility
        // Using this, we can get the company products and check if the user is enrolled in any of the connected companies
        Facility facility = facilityService.fetchFacility(queryRequest.facilityId);
        PermissionsUtil.checkUserIfConnectedWithProducts(companyQueries.fetchCompanyProducts(facility.getCompany().getId()), user);

        return PaginationTools.createPaginatedResponse(em, request,
                () -> stockOrderQueryObject(
                        request,
                        queryRequest
                ), stockOrder -> StockOrderMapper.toApiStockOrder(stockOrder, user.getUserId(), language));
    }

    public ApiPaginatedList<ApiStockOrder> getStockOrderListForCompany(ApiPaginatedRequest request,
                                                             StockOrderQueryRequest queryRequest,
                                                             CustomUserDetails user,
                                                             Language language) throws ApiException {

        if (queryRequest.companyId == null && queryRequest.quoteCompanyId == null && queryRequest.facilityId == null) {
            throw new ApiException(ApiStatus.UNAUTHORIZED, "Company ID or Facility ID should be provided!");
        }

        // If company or quote company ID is provided, validate that the request user is enrolled in this company
        if (queryRequest.companyId != null) {
            Company company = companyQueries.fetchCompany(queryRequest.companyId);
            PermissionsUtil.checkUserIfCompanyEnrolled(company.getUsers().stream().toList(), user);
        } else if (queryRequest.quoteCompanyId != null) {
            Company company = companyQueries.fetchCompany(queryRequest.quoteCompanyId);
            PermissionsUtil.checkUserIfCompanyEnrolled(company.getUsers().stream().toList(), user);
        } else {

            // If company ID is not provided, fetch the facility's company and validate user company enrollment
            Facility facility = facilityService.fetchFacility(queryRequest.facilityId);
            PermissionsUtil.checkUserIfCompanyEnrolled(facility.getCompany().getUsers().stream().toList(), user);
        }

        return PaginationTools.createPaginatedResponse(em, request,
                () -> stockOrderQueryObject(
                        request,
                        queryRequest
                ), stockOrder -> StockOrderMapper.toApiStockOrder(stockOrder, user.getUserId(), language));
    }

    private StockOrder stockOrderQueryObject(ApiPaginatedRequest request,
                                             StockOrderQueryRequest queryRequest) {

        StockOrder stockOrderProxy = Torpedo.from(StockOrder.class);
        OnGoingLogicalCondition condition = Torpedo.condition();

        if (queryRequest.companyId != null) {
            condition = condition.and(stockOrderProxy.getCompany().getId()).eq(queryRequest.companyId);
        }

        if (queryRequest.facilityId != null) {
            condition = condition.and(stockOrderProxy.getFacility().getId()).eq(queryRequest.facilityId);
        }

        // User for fetching quote orders (filter by Quote company)
        if (queryRequest.quoteCompanyId != null) {
            condition = condition.and(stockOrderProxy.getQuoteCompany().getId()).eq(queryRequest.quoteCompanyId);
        }

        // Used for fetching quote orders
        if (queryRequest.quoteFacilityId != null) {
            condition = condition.and(stockOrderProxy.getQuoteFacility().getId()).eq(queryRequest.quoteFacilityId);
        }

        // Used for fetching quote orders (filter by Quote facility)
        if (queryRequest.companyCustomerId != null) {
            condition = condition.and(stockOrderProxy.getConsumerCompanyCustomer()).isNotNull();
            condition = condition.and(stockOrderProxy.getConsumerCompanyCustomer().getId()).eq(queryRequest.companyCustomerId);
        }

        // Query parameter filters
        if (queryRequest.farmerId != null) {
            condition = condition.and(stockOrderProxy.getProducerUserCustomer()).isNotNull();
            condition = condition.and(stockOrderProxy.getProducerUserCustomer().getId()).eq(queryRequest.farmerId);
        }

        // Filer by collector (used in purchase orders)
        if (queryRequest.representativeOfProducerUserCustomerId != null) {
            condition = condition.and(stockOrderProxy.getRepresentativeOfProducerUserCustomer()).isNotNull();
            condition = condition.and(stockOrderProxy.getRepresentativeOfProducerUserCustomer().getId())
                    .eq(queryRequest.representativeOfProducerUserCustomerId);
        }

        // Used for filtering stock orders by semi-product
        if (queryRequest.semiProductId != null) {
            condition = condition.and(stockOrderProxy.getSemiProduct()).isNotNull();
            condition = condition.and(stockOrderProxy.getSemiProduct().getId()).eq(queryRequest.semiProductId);
        }

        // Used for filtering stock orders by final product
        if (queryRequest.finalProductId != null) {
            condition = condition.and(stockOrderProxy.getFinalProduct()).isNotNull();
            condition = condition.and(stockOrderProxy.getFinalProduct().getId()).eq(queryRequest.finalProductId);
        }

        if (queryRequest.isOpenBalanceOnly != null && queryRequest.isOpenBalanceOnly) {
            condition = condition.and(stockOrderProxy.getBalance()).isNotNull();
            condition = condition.and(stockOrderProxy.getBalance()).gt(BigDecimal.ZERO);
        }

        if (queryRequest.isPurchaseOrderOnly != null && queryRequest.isPurchaseOrderOnly) {
            condition = condition.and(stockOrderProxy.getOrderType()).eq(OrderType.PURCHASE_ORDER);
        }

        if (queryRequest.isWomenShare != null) {
            condition = condition.and(stockOrderProxy.getWomenShare()).eq(queryRequest.isWomenShare);
        }

        if (queryRequest.organicOnly != null) {
            condition = condition.and(stockOrderProxy.getOrganic()).eq(queryRequest.organicOnly);
        }

        // If LOT name is provided filter by LOT prefix or LOT name
        if (StringUtils.isNotBlank(queryRequest.internalLotName)) {
            OnGoingLogicalCondition likeInternalLotName = Torpedo.condition(stockOrderProxy.getInternalLotNumber()).like().any(queryRequest.internalLotName);
            OnGoingLogicalCondition likeLotPrefix = Torpedo.condition(stockOrderProxy.getLotPrefix()).like().any(queryRequest.internalLotName);
            condition = condition.and(Torpedo.condition(likeInternalLotName.or(likeLotPrefix)));
        }

        if (queryRequest.wayOfPayment != null) {
            condition = condition.and(stockOrderProxy.getPreferredWayOfPayment()).eq(queryRequest.wayOfPayment);
        }

        if (queryRequest.orderType != null) {
            condition = condition.and(stockOrderProxy.getOrderType()).eq(queryRequest.orderType);
        }

        if (queryRequest.productionDateStart != null) {
            condition = condition.and(stockOrderProxy.getProductionDate()).gte(queryRequest.productionDateStart);
        }

        if (queryRequest.productionDateEnd != null) {
            condition = condition.and(stockOrderProxy.getProductionDate()).lte(queryRequest.productionDateEnd);
        }

        // Search by farmers name (query)
        if (queryRequest.producerUserCustomerName != null) {
            condition = condition.and(stockOrderProxy.getProducerUserCustomer()).isNotNull();
            OnGoingLogicalCondition likeName = Torpedo.condition(stockOrderProxy.getProducerUserCustomer().getName()).like().any(queryRequest.producerUserCustomerName);
            OnGoingLogicalCondition likeSurname = Torpedo.condition(stockOrderProxy.getProducerUserCustomer().getSurname()).like().any(queryRequest.producerUserCustomerName);
            condition = condition.and(Torpedo.condition(likeName.or(likeSurname)));
        }

        // Get only stock orders that have available quantity
        if (BooleanUtils.isTrue(queryRequest.isAvailable)) {
            condition = condition.and(stockOrderProxy.getAvailableQuantity()).gt(BigDecimal.ZERO);
        }

        // Used for fetching open quote orders
        if (BooleanUtils.isTrue(queryRequest.isOpenOnly)) {
            condition = condition.and(stockOrderProxy.getIsOpenOrder()).eq(true);
        }

        Torpedo.where(condition);

        // Order by
        switch (request.sortBy) {
            case "date":
                QueryTools.orderBy(request.sort, stockOrderProxy.getProductionDate());
                break;
            case "deliveryTime":
                QueryTools.orderBy(request.sort, stockOrderProxy.getDeliveryTime());
                break;
            case "updateTimestamp":
                QueryTools.orderBy(request.sort, stockOrderProxy.getUpdateTimestamp());
                break;
            default:
                QueryTools.orderBy(request.sort, stockOrderProxy.getId());
        }

        return stockOrderProxy;
    }

    /**
     * Return the public data for the stock order(s) with the provided QR code tag. Multiple stock orders can contain the provided QR code tag.
     * @param qrTag The QR code tag for which public data is returned.
     *
     * @param language The user selected language.
     * @return The public data to be used by the public B2C page.
     */
    public ApiQRTagPublic getQRTagPublicData(String qrTag, Boolean withHistory, Language language) throws ApiException {

        ApiQRTagPublic apiQRTagPublic = new ApiQRTagPublic();

        // Get the top level Stock order for the provided QR tag (the Stock order that is
        // not used as an input transaction in other Processing orders)
        StockOrder topLevelStockOrder = em.createNamedQuery("StockOrder.getTopLevelStockOrdersForQrTag", StockOrder.class)
                .setParameter("qrTag", qrTag)
                .getResultList()
                .stream()
                .findFirst()
                .orElse(null);

        if (topLevelStockOrder != null) {

            // Get and set the orderId
            String orderId;
            if (topLevelStockOrder.getProductOrder() != null) {
                orderId = topLevelStockOrder.getProductOrder().getOrderId();
            } else {
                orderId = topLevelStockOrder.getOrderId();
            }

            apiQRTagPublic.setOrderId(orderId);
            apiQRTagPublic.setQrTag(topLevelStockOrder.getQrCodeTag());

            // Set the aggregated history (if requested from the API call)
            if (BooleanUtils.isTrue(withHistory)) {

                ApiHistoryTimeline historyTimeline = new ApiHistoryTimeline();
                apiQRTagPublic.setHistoryTimeline(historyTimeline);

                // Set holding company IDs that are participating in this Stock order chain
                Set<Long> participatingCompaniesIds = new HashSet<>();

                // Get the history for the selected Stock order
                ApiStockOrderHistory stockOrderHistory = getStockOrderAggregatedHistoryList(topLevelStockOrder.getId(), language, null, false);

                // Map the Stock order history timeline to the public QR code tag data
                historyTimeline.setItems(stockOrderHistory.getTimelineItems()
                        .stream()
                        .map(processing -> {

                            ApiHistoryTimelineItem historyTimelineItem = new ApiHistoryTimelineItem();

                            if (processing.getProcessingOrder() != null) {

                                ApiFacility apiFacility = processing.getProcessingOrder().getTargetStockOrders()
                                        .stream().findAny().map(ApiStockOrder::getFacility).orElse(null);

                                // Add the facility company ID in the participating companies IDs for this Sto order chain
                                if (apiFacility != null) {
                                    participatingCompaniesIds.add(apiFacility.getCompany().getId());
                                }

                                // If processing item has no public timeline label defined, skip that processing
                                if (StringUtils.isNotBlank(processing.getProcessingOrder().getProcessingAction().getPublicTimelineLabel())) {

                                    historyTimelineItem.setType(processing.getProcessingOrder().getProcessingAction().getType().toString());
                                    historyTimelineItem.setName(processing.getProcessingOrder().getProcessingAction().getName());
                                    historyTimelineItem.setDate(processing.getProcessingOrder().getProcessingDate());

                                    // Set the location name (facility name)
                                    if (apiFacility != null) {
                                        historyTimelineItem.setLocation(apiFacility.getName());
                                    }

                                    // Set the location (facility) coordinates
                                    if (apiFacility != null && apiFacility.getFacilityLocation() != null) {
                                        historyTimelineItem.setLatitude(apiFacility.getFacilityLocation().getLatitude());
                                        historyTimelineItem.setLongitude(apiFacility.getFacilityLocation().getLongitude());
                                    }
                                }

                                // If current processing contains data for cupping score, flavour and roasting profile, get this data
                                processing.getProcessingOrder().getTargetStockOrders().stream().findFirst().ifPresent(tStockOrder ->
                                        tStockOrder.getRequiredEvidenceFieldValues().forEach(evidenceField -> {

                                    // Find and set cupping score field
                                    if (StockOrderService.CUPPING_SCORE_FIELD_NAME.equals(evidenceField.getEvidenceFieldName()) &&
                                            StringUtils.isNotBlank(evidenceField.getStringValue()) &&
                                            apiQRTagPublic.getCuppingScore() == null) {

                                        apiQRTagPublic.setCuppingScore(new BigDecimal(evidenceField.getStringValue()));
                                    }

                                    // Find and set cupping flavour field
                                    if (StockOrderService.CUPPING_FLAVOUR_FIELD_NAME.equals(evidenceField.getEvidenceFieldName()) &&
                                            StringUtils.isNotBlank(evidenceField.getStringValue()) &&
                                            apiQRTagPublic.getCuppingFlavour() == null) {

                                        apiQRTagPublic.setCuppingFlavour(evidenceField.getStringValue());
                                    }

                                    // Find and set roasting profile field
                                    if (StockOrderService.ROASTING_PROFILE_FIELD_NAME.equals(evidenceField.getEvidenceFieldName()) &&
                                            StringUtils.isNotBlank(evidenceField.getStringValue()) &&
                                            apiQRTagPublic.getRoastingProfile() == null) {

                                        apiQRTagPublic.setRoastingProfile(evidenceField.getStringValue());
                                    }
                                }));

                            } else {

                                processing.getPurchaseOrders().stream().findFirst().ifPresent(po -> {
                                    historyTimelineItem.setDate(po.getProductionDate());
                                    historyTimelineItem.setLocation(po.getFacility().getName());

                                    apiQRTagPublic.setProducerName(po.getFacility().getCompany().getName());
                                });
                            }

                            return historyTimelineItem;
                        })
                        .filter(historyTimelineItem -> historyTimelineItem.getDate() != null)
                        .collect(Collectors.toList()));

                enumerateRepeatedEventNames(apiQRTagPublic.getHistoryTimeline().getItems());

                // Get list of producers for this product
                List<Company> producers = getProducers(topLevelStockOrder);

                // Get list of payments to producers
                List<ApiPayment> producerPayments = getProducerPayments(stockOrderHistory, producers);

                Map<Long, BigDecimal> stockOrderWeights = new HashMap<>();
                BigDecimal producersPaidUsd = BigDecimal.ZERO;

                for (ApiPayment apiPayment : producerPayments) {

                    // Add stock order weights only once
                    if (!stockOrderWeights.containsKey(apiPayment.getStockOrder().getId())) {
                        stockOrderWeights.put(apiPayment.getStockOrder().getId(), apiPayment.getStockOrder().getFulfilledQuantity().multiply(apiPayment.getStockOrder().getMeasureUnitType().getWeight()));
                    }

                    // Calculate price in USD at date
                    BigDecimal producerPriceUsd = currencyService.convertAtDate(
                            apiPayment.getCurrency(),
                            "USD",
                            apiPayment.getAmount(),
                            Date.from(apiPayment.getFormalCreationTime().atStartOfDay().toInstant(ZoneOffset.UTC))
                    );

                    // Accumulate payments
                    producersPaidUsd = producersPaidUsd.add(producerPriceUsd);
                }

                // Sum up order weights
                BigDecimal producersCoffeeKg = stockOrderWeights.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);

                apiQRTagPublic.setPriceToProducer(calculateProducerPayments(producersPaidUsd, producersCoffeeKg));
                apiQRTagPublic.setPriceToFarmer(calculateFarmerPayments(stockOrderHistory));

                // Get all the certificates from the participating companies
                if (!participatingCompaniesIds.isEmpty()) {
                    List<ApiCertification> apiCertificates = em.createNamedQuery("CompanyCertification.getCertificatesForCompanyIDs", CompanyCertification.class)
                            .setParameter("companyIDs", participatingCompaniesIds)
                            .getResultList()
                            .stream()
                            .map(cc -> CompanyApiTools.toApiCertification(null, cc))
                            .collect(Collectors.toList());
                    apiQRTagPublic.setCertificates(apiCertificates);
                }
            }
        }

        return apiQRTagPublic;
    }

    private List<ApiPayment> getProducerPayments(ApiStockOrderHistory stockOrderHistory, List<Company> producers) {
        return stockOrderHistory.getTimelineItems().stream().flatMap(apiStockOrderHistoryTimelineItem -> {
            if (apiStockOrderHistoryTimelineItem.getProcessingOrder() != null && apiStockOrderHistoryTimelineItem.getProcessingOrder().getTargetStockOrders() != null) {
                return apiStockOrderHistoryTimelineItem.getProcessingOrder().getTargetStockOrders().stream().flatMap(apiStockOrder -> {
                    if (apiStockOrder.getPayments() != null) {
                        return apiStockOrder.getPayments().stream().filter(apiPayment -> {
                            if (apiPayment.getRecipientCompany() != null) {
                                return producers.stream().anyMatch(company -> company.getId().equals(apiPayment.getRecipientCompany().getId()));
                            }
                            return false;
                        });
                    }
                    return null;
                });
            }
            return null;
        }).collect(Collectors.toList());
    }

    private List<Company> getProducers(StockOrder topLevelStockOrder) {

        // If the stock order doesn't have final product, it means there is no final processing done, and we cannot connect
        // the stock order with the companies from the product stakeholders
        if (topLevelStockOrder.getFinalProduct() == null) {
            return Collections.emptyList();
        }

        return topLevelStockOrder.getFinalProduct().getProduct().getAssociatedCompanies().stream()
                .filter(productCompany -> ProductCompanyType.PRODUCER.equals(productCompany.getType()))
                .map(ProductCompany::getCompany)
                .collect(Collectors.toList());
    }

    private BigDecimal calculateProducerPayments(BigDecimal paidEur, BigDecimal coffeeKg) {
        if (paidEur.equals(BigDecimal.ZERO) || coffeeKg.equals(BigDecimal.ZERO)) {
            return null;
        } else {
            return paidEur.divide(coffeeKg, 3, RoundingMode.HALF_UP);
        }
    }

    private BigDecimal calculateFarmerPayments(ApiStockOrderHistory stockOrderHistory) {
        List<ApiStockOrder> farmerStockOrders = stockOrderHistory.getTimelineItems().stream().flatMap(apiStockOrderHistoryTimelineItem -> {
            if (apiStockOrderHistoryTimelineItem.getPurchaseOrders() != null) {
                return apiStockOrderHistoryTimelineItem.getPurchaseOrders().stream();
            }
            return null;
        }).collect(Collectors.toList());

        // Calculating farmer payments
        if (farmersFullyPaid(farmerStockOrders)) {
            BigDecimal paidUsd = BigDecimal.ZERO;
            BigDecimal coffeeKg = BigDecimal.ZERO;

            for (ApiStockOrder apiStockOrder : farmerStockOrders) {
                for (ApiPayment apiPayment : apiStockOrder.getPayments()) {
                    paidUsd = paidUsd.add(
                            currencyService.convertAtDate(
                                    apiPayment.getCurrency(),
                                    "USD",
                                    apiPayment.getAmount(),
                                    Date.from(apiPayment.getFormalCreationTime().atStartOfDay().toInstant(ZoneOffset.UTC))
                            )
                    );
                }
                coffeeKg = coffeeKg.add(apiStockOrder.getFulfilledQuantity().multiply(apiStockOrder.getMeasureUnitType().getWeight()));
            }

            if (BigDecimal.ZERO.equals(coffeeKg)) {
                return null;
            }

            return paidUsd.divide(coffeeKg, 3, RoundingMode.HALF_UP);
        } else {
            return null;
        }
    }

    private boolean farmersFullyPaid(List<ApiStockOrder> apiStockOrderList) {
        for (ApiStockOrder apiStockOrder : apiStockOrderList) {
            if (apiStockOrder.getBalance().compareTo(BigDecimal.ZERO) > 0) {
                return false;
            }
        }
        return true;
    }

    private void enumerateRepeatedEventNames(List<ApiHistoryTimelineItem> events) {
        if (!events.isEmpty()) {
            List<Integer> repeated = calculateRepetitions(events);

            addStepCounters(events, repeated);
        }
    }

    private void addStepCounters(List<ApiHistoryTimelineItem> events, List<Integer> repeated) {
        int enumerateFrom = -1;
        // now iterate through events from back to front
        for (int i = events.size() - 1; i >= 0; i--) {
            int repetition = repeated.get(i);
            // if the current event is a repetition, select it as a counter reference
            if (enumerateFrom == -1 && repetition > 0) {
                enumerateFrom = repetition;
            }
            // if enumeration is in progress
            if (enumerateFrom != -1) {
                // add step enumeration
                events.get(i).setStep(enumerateFrom - repetition + 1);
                events.get(i).setSteps(enumerateFrom + 1);
                // if we reached the first element, stop the enumeration
                if (repetition == 0) {
                    enumerateFrom = -1;
                }
            }
        }
    }

    private List<Integer> calculateRepetitions(List<ApiHistoryTimelineItem> events) {
        // create array with counter for consecutive repeated event names
        List<Integer> repeated = new ArrayList<>(events.size());
        // first element cannot be repeated since it is first
        repeated.add(0);
        // iterate through events from front to back
        for (int i = 1; i < events.size(); i++) {
            if (events.get(i).getName() != null && events.get(i).getName().equals(events.get(i - 1).getName())) {
                // if current event is the same as previous, increase the repetition counter
                repeated.add(repeated.get(i - 1) + 1);
            } else {
                // else start from beginning
                repeated.add(0);
            }
        }
        return repeated;
    }

    /**
     * Returns the history (the chain of stock orders from top to bottom) for the provided Stock order ID.
     *
     * @param id The Stock order ID.
     * @param language User selected language.
     * @param withDetails Should the response contain Stock and Processing order details.
     */
    public ApiStockOrderHistory getStockOrderAggregatedHistoryList(Long id,
                                                                   Language language,
                                                                   CustomUserDetails user,
                                                                   boolean withDetails) throws ApiException {

        StockOrder stockOrder = fetchEntity(id, StockOrder.class);

        // If requested details, we have to check if request user is enrolled in one of the connected companies
        if (withDetails) {

            if (user == null) {
                throw new ApiException(ApiStatus.UNAUTHORIZED, "Request user is required!");
            }

            PermissionsUtil.checkUserIfConnectedWithProducts(companyQueries.fetchCompanyProducts(stockOrder.getCompany().getId()), user);
        }

        ApiStockOrderHistory stockOrderHistory = new ApiStockOrderHistory();

        // Recursively add history, starting from depth 0
        List<ApiStockOrderHistoryTimelineItem> historyTimeline;
        if (user != null) {
            historyTimeline = addNextAggregationLevels(0, stockOrder, language, user.getUserId());
        } else {
            historyTimeline = addNextAggregationLevels(0, stockOrder, language, null);
        }
        historyTimeline.sort(Comparator.comparingInt(ApiStockOrderHistoryTimelineItem::getDepth));

        // Prepare aggregated Purchase orders (group all into single history timeline item)
        ApiStockOrderHistoryTimelineItem aggregatedPurchaseOrders = new ApiStockOrderHistoryTimelineItem();

        stockOrderHistory.setTimelineItems(historyTimeline.stream().filter(timelineItem -> {
            if (timelineItem.getStockOrder() != null) {
                aggregatedPurchaseOrders.getPurchaseOrders().add(timelineItem.getStockOrder());
                return false;
            }
            return true;
        }).collect(Collectors.toList()));
        stockOrderHistory.getTimelineItems().add(aggregatedPurchaseOrders);

        // If details are not request end the processing (we don't need the Stock and Processing order data including input and output transactions)
        if (!withDetails) {
            return stockOrderHistory;
        }

        // Set the Stock order and Processing order for which the history is calculated (including the input transaction for the Processing order)
        stockOrderHistory.setStockOrder(StockOrderMapper.toApiStockOrderHistory(stockOrder, language));
        stockOrderHistory.setProcessingOrder(ProcessingOrderMapper.toApiProcessingOrderHistory(stockOrder.getProcessingOrder(), language));

        if (stockOrder.getProcessingOrder() != null) {

            // Set the input transactions for the Processing order
            stockOrderHistory.getProcessingOrder().setInputTransactions(
                    stockOrder.getProcessingOrder().getInputTransactions().stream()
                            .map(TransactionMapper::toApiTransactionHistory).collect(Collectors.toList()));

            // Set the sibling Stock orders
            stockOrderHistory.getProcessingOrder().setTargetStockOrders(
                    stockOrder.getProcessingOrder().getTargetStockOrders().stream().map(tSO -> {
                        ApiStockOrder apiStockOrder = new ApiStockOrder();
                        apiStockOrder.setId(tSO.getId());
                        apiStockOrder.setTotalQuantity(tSO.getTotalQuantity());
                        apiStockOrder.setMeasureUnitType(
                                MeasureUnitTypeMapper.toApiMeasureUnitTypeBase(tSO.getMeasurementUnitType()));
                        return apiStockOrder;
                    }).collect(Collectors.toList()));
        }

        // Set the output transaction for the chosen Stock order (the transactions where this Stock order was used as an input)
        List<Transaction> outputTransactions = findOutputTransactions(stockOrder);
        stockOrderHistory.setOutputTransactions(outputTransactions.stream().map(transaction -> {
            ApiTransaction apiTransaction = TransactionMapper.toApiTransactionHistory(transaction);
            transaction.getTargetProcessingOrder().getTargetStockOrders().stream().findAny().ifPresent(tSO -> {
                ApiStockOrder apiTSO = new ApiStockOrder();
                apiTSO.setId(tSO.getId());
                apiTransaction.setTargetStockOrder(apiTSO);
            });
            return apiTransaction;
        }).collect(Collectors.toList()));

        return stockOrderHistory;
    }

    private List<Transaction> findOutputTransactions(StockOrder stockOrder) {

        // Fetch the transactions where the provided Stock order was used as an input Stock order
        TypedQuery<Transaction> getTransactionsBySourceStockOrderQuery = em
                .createNamedQuery("Transaction.getOutputTransactionsByStockOrderId", Transaction.class)
                .setParameter("stockOrderId", stockOrder.getId());

        return getTransactionsBySourceStockOrderQuery.getResultList();
    }

    /***
     * Recursively adds next stock aggregation history list.
     *
     * @param currentDepth - aggregated history depth level
     * @param stockOrder - current stockOrder entity for searching next child nodes
     * @param language - language
     *
     * @return returns aggregated history list for next levels
     *
     */
    private List<ApiStockOrderHistoryTimelineItem> addNextAggregationLevels(int currentDepth,
                                                                StockOrder stockOrder, Language language, Long userId) {

        if (stockOrder != null) {

            List<ApiStockOrderHistoryTimelineItem> historyTimeline = new ArrayList<>();

            ApiStockOrderHistoryTimelineItem nextHistory = new ApiStockOrderHistoryTimelineItem();

            // If we have Processing order defined, we are dealing with orders that involve
            // processing (in the other case we have Purchase order - special case)
            if (stockOrder.getProcessingOrder() != null && !stockOrder.getProcessingOrder().getInputTransactions().isEmpty()) {

                // Get the Stock order that were used as input when executing the Processing order
                List<StockOrder> inputStockOrders = stockOrder.getProcessingOrder().getInputTransactions()
                        .stream()
                        .filter(transaction -> !transaction.getStatus().equals(TransactionStatus.CANCELED))
                        .map(Transaction::getSourceStockOrder)
                        .collect(Collectors.toList());

                // Get the Stock orders that were created when executing the Processing order
                List<ApiStockOrder> targetStockOrders = stockOrder.getProcessingOrder().getTargetStockOrders()
                        .stream()
                        .map(siblingOrder -> StockOrderMapper.toApiStockOrderHistoryItem(siblingOrder, language))
                        .collect(Collectors.toList());

                nextHistory.setProcessingOrder(
                        ProcessingOrderMapper.toApiProcessingOrderHistory(stockOrder.getProcessingOrder(), language));
                nextHistory.getProcessingOrder().setTargetStockOrders(targetStockOrders);
                nextHistory.setDepth(currentDepth);

                // Set the required and other evidence documents (applicable only if requesting by logged-in user)
                if (userId != null) {
                    stockOrder.getProcessingOrder().getTargetStockOrders().stream().findAny().ifPresent(so -> {
                        so.getDocumentRequirements().forEach(docRequirement -> {
                            if (BooleanUtils.isTrue(docRequirement.getOtherEvidence())) {
                                nextHistory.getOtherEvidenceDocuments().add(
                                        StockOrderEvidenceTypeValueMapper.toApiStockOrderEvidenceTypeValue(docRequirement, userId, language));
                            } else {
                                nextHistory.getRequiredEvidenceDocuments().add(
                                        StockOrderEvidenceTypeValueMapper.toApiStockOrderEvidenceTypeValue(docRequirement, userId, language));
                            }
                        });
                    });
                }

                if (!inputStockOrders.isEmpty()) {
                    // next recursion for every child element
                    if (inputStockOrders.get(0).getSacNumber() != null) {

                        // if sac number present, proceed with only first item leaves
                        historyTimeline.addAll(addNextAggregationLevels(currentDepth + 1, inputStockOrders.get(0), language, userId));
                    } else if (OrderType.TRANSFER_ORDER.equals(inputStockOrders.get(0).getOrderType())) {

                        // if transfer order, go through with first item leaves
                        historyTimeline.addAll(addNextAggregationLevels(currentDepth + 1, inputStockOrders.get(0), language, userId));
                    } else {

                        // proceed recursion with all leaves
                        inputStockOrders.forEach(sourceOrder -> historyTimeline.addAll(addNextAggregationLevels(currentDepth + 1, sourceOrder, language, userId)));
                    }
                }

            } else {

                nextHistory.setStockOrder(StockOrderMapper.toApiStockOrderHistory(stockOrder, language));
                nextHistory.setDepth(currentDepth);
            }

            historyTimeline.add(nextHistory);

            return historyTimeline;

        } else {
            return new ArrayList<>();
        }
    }

    @Transactional
    public ApiPurchaseOrder createPurchaseBulkOrder(ApiPurchaseOrder apiPurchaseOrder, CustomUserDetails user) throws ApiException {

        // Validation
        if (apiPurchaseOrder == null) {
            throw new ApiException(ApiStatus.INVALID_REQUEST, "ApiPurchaseOrder needs to be provided!");
        }
        if (apiPurchaseOrder.getFarmers() == null || apiPurchaseOrder.getFarmers().isEmpty()){
            throw new ApiException(ApiStatus.INVALID_REQUEST, "Farmers needs to be provided!");
        }
        if (apiPurchaseOrder.getFacility() == null) {
            throw new ApiException(ApiStatus.INVALID_REQUEST, "Facility needs to be provided!");
        }

        Facility facility = facilityService.fetchFacility(apiPurchaseOrder.getFacility().id);
        PermissionsUtil.checkUserIfCompanyEnrolled(facility.getCompany().getUsers().stream().toList(), user);

        // Update stocks of type Purchase, one by one
        for (ApiPurchaseOrderFarmer farmer : apiPurchaseOrder.getFarmers()) {

            // convert to ApiStockOrder of type PURCHASE_ORDER
            ApiStockOrder apiStockOrder = convertPurchaseOrderFarmerToStockOrder(apiPurchaseOrder, farmer);

            // write to db
            ApiBaseEntity apiBaseEntity = createOrUpdateStockOrder(apiStockOrder, user, null);

            // set Id in response
            farmer.setId(apiBaseEntity.getId());
        }

        return apiPurchaseOrder;
    }

    private ApiStockOrder convertPurchaseOrderFarmerToStockOrder(ApiPurchaseOrder apiPurchaseOrder, ApiPurchaseOrderFarmer farmer){

        ApiStockOrder apiStockOrder = new ApiStockOrder();

        if (apiPurchaseOrder != null) {

            apiStockOrder.setOrderType(OrderType.PURCHASE_ORDER);

            // copy common attributes
            apiStockOrder.setCreatorId(apiPurchaseOrder.getCreatorId());
            apiStockOrder.setDeliveryTime(apiPurchaseOrder.getDeliveryTime());
            apiStockOrder.setFacility(apiPurchaseOrder.getFacility());
            apiStockOrder.setPreferredWayOfPayment(apiPurchaseOrder.getPreferredWayOfPayment());
            apiStockOrder.setRepresentativeOfProducerUserCustomer(apiPurchaseOrder.getRepresentativeOfProducerUserCustomer());
            apiStockOrder.setProductionDate(apiPurchaseOrder.getProductionDate());
            apiStockOrder.setCurrency(apiPurchaseOrder.getCurrency());
            apiStockOrder.setActivityProofs(apiPurchaseOrder.getActivityProofs());

            // copy farmer attributes
            apiStockOrder.setIdentifier(farmer.getIdentifier());
            apiStockOrder.setOrganic(farmer.getOrganic());
            apiStockOrder.setDamagedPriceDeduction(farmer.getDamagedPriceDeduction());
            apiStockOrder.setDamagedWeightDeduction(farmer.getDamagedWeightDeduction());
            apiStockOrder.setSemiProduct(farmer.getSemiProduct());
            apiStockOrder.setTare(farmer.getTare());
            apiStockOrder.setPricePerUnit(farmer.getPricePerUnit());
            apiStockOrder.setCost(farmer.getCost());
            apiStockOrder.setBalance(farmer.getBalance());
            apiStockOrder.setProducerUserCustomer(farmer.getProducerUserCustomer());
            apiStockOrder.setWomenShare(farmer.getWomenShare());
            apiStockOrder.setAvailableQuantity(farmer.getAvailableQuantity());
            apiStockOrder.setFulfilledQuantity(farmer.getFulfilledQuantity());
            apiStockOrder.setTotalQuantity(farmer.getTotalQuantity());
            apiStockOrder.setTotalGrossQuantity(farmer.getTotalGrossQuantity());
        }

        return apiStockOrder;
    }

    @Transactional
    public ApiBaseEntity createOrUpdateQuoteStockOrder(ApiStockOrder apiStockOrder,
                                                       CustomUserDetails user,
                                                       ProcessingOrder processingOrder) throws ApiException {

        // Check that the request user is form a company which is connected to the company that owns the quote order (or is a user of that company)
        if (apiStockOrder.getCompany() != null && apiStockOrder.getCompany().getId() != null) {
            PermissionsUtil.checkUserIfConnectedWithProducts(companyQueries.fetchCompanyProducts(apiStockOrder.getCompany().getId()), user);
        } else {
            // When creating a new Quote order, the underlying stock order has not yet set company
            PermissionsUtil.checkUserIfConnectedWithProducts(companyQueries.fetchCompanyProducts(processingOrder.getProcessingAction().getCompany().getId()), user);
        }

        return createOrUpdateStockOrder(apiStockOrder, user, processingOrder, false);
    }

    @Transactional
    public ApiBaseEntity createOrUpdateStockOrder(ApiStockOrder apiStockOrder,
                                                  CustomUserDetails user,
                                                  ProcessingOrder processingOrder) throws ApiException {
        return createOrUpdateStockOrder(apiStockOrder, user, processingOrder, true);
    }

    @Transactional
    public ApiBaseEntity createOrUpdateStockOrder(ApiStockOrder apiStockOrder,
                                                  CustomUserDetails user,
                                                  ProcessingOrder processingOrder,
                                                  boolean checkCompanyEnrolment) throws ApiException {

        StockOrder entity;

        if (apiStockOrder.getId() != null) {
            entity = fetchEntity(apiStockOrder.getId(), StockOrder.class);
            entity.setUpdatedBy(fetchEntity(user.getUserId(), User.class));

        } else {
            entity = new StockOrder();
            entity.setCreatedBy(fetchEntity(user.getUserId(), User.class));
            entity.setCreatorId(apiStockOrder.getCreatorId());
        }

        // Facility needs to be provided
        if (apiStockOrder.getFacility() == null) {
            throw new ApiException(ApiStatus.INVALID_REQUEST, "Facility needs to be provided!");
        }

        // Semi-product or Final product needs to be provided
        if (apiStockOrder.getSemiProduct() == null && apiStockOrder.getFinalProduct() == null) {
            throw new ApiException(ApiStatus.INVALID_REQUEST, "Semi-product or Final product needs to be provided!");
        }

        // Fetch the facility and check that the request user is enrolled in the facility's company
        Facility facility = facilityService.fetchFacility(apiStockOrder.getFacility().getId());

        // In some cases we don't need to check if request user is enrolled in company due to already
        // executed checks (approve/reject quote order transaction, etc.)
        if (checkCompanyEnrolment) {
            PermissionsUtil.checkUserIfCompanyEnrolled(facility.getCompany().getUsers().stream().toList(), user);
        }

        entity.setOrderType(apiStockOrder.getOrderType());
        entity.setFacility(facility);
        entity.setCompany(entity.getFacility().getCompany());
        entity.setIdentifier(apiStockOrder.getIdentifier());
        entity.setPreferredWayOfPayment(apiStockOrder.getPreferredWayOfPayment());
        entity.setSacNumber(apiStockOrder.getSacNumber());
        entity.setRepackedOriginStockOrderId(apiStockOrder.getRepackedOriginStockOrderId());
        entity.setProductionDate(apiStockOrder.getProductionDate());
        entity.setDeliveryTime(apiStockOrder.getDeliveryTime());
        entity.setComments(apiStockOrder.getComments());

        // Set the internal LOT name and the LOT prefix if we have Processing order provided
        entity.setInternalLotNumber(apiStockOrder.getInternalLotNumber());
        if (processingOrder != null) {
            entity.setLotPrefix(processingOrder.getProcessingAction().getPrefix());
        }

        // If provided set the Product order - this is provided when this Stock order was created as part of a Product order (batch Quote orders for final products)
        if (apiStockOrder.getProductOrder() != null && apiStockOrder.getProductOrder().getId() != null) {
            entity.setProductOrder(fetchEntity(apiStockOrder.getProductOrder().getId(), ProductOrder.class));
        }

        // Set price per unit for end customer (and currency) - this is used when placing Quote order for final products for an end customer
        entity.setPricePerUnitForEndCustomer(apiStockOrder.getPricePerUnitForEndCustomer());
        entity.setCurrencyForEndCustomer(apiStockOrder.getCurrencyForEndCustomer());

        // Set the consumer company customer (used in Quote orders for final products)
        if (apiStockOrder.getConsumerCompanyCustomer() != null && apiStockOrder.getConsumerCompanyCustomer().getId() != null) {
            entity.setConsumerCompanyCustomer(fetchEntity(apiStockOrder.getConsumerCompanyCustomer().getId(), CompanyCustomer.class));
        }

        // Set semi-product (used in processing)
        if (apiStockOrder.getSemiProduct() != null) {
            entity.setSemiProduct(semiProductService.fetchSemiProduct(apiStockOrder.getSemiProduct().getId()));
        }

        // Set final product (used in final product processing)
        if (apiStockOrder.getFinalProduct() != null) {
            entity.setFinalProduct(finalProductService.fetchFinalProduct(apiStockOrder.getFinalProduct().getId()));
        }

        entity.setPriceDeterminedLater(apiStockOrder.getPriceDeterminedLater() == null ? Boolean.FALSE : apiStockOrder.getPriceDeterminedLater());
        entity.setOrganic(apiStockOrder.getOrganic());
        entity.setTare(apiStockOrder.getTare());
        entity.setWomenShare(apiStockOrder.getWomenShare());
        entity.setDamagedPriceDeduction(apiStockOrder.getDamagedPriceDeduction());
        entity.setDamagedWeightDeduction(apiStockOrder.getDamagedWeightDeduction());
        entity.setCurrency(apiStockOrder.getCurrency());

        // Calculate the quantities for this stock order accommodating all different cases of stock orders
        calculateQuantities(apiStockOrder, entity, processingOrder, null);

        // Set the measure unit from the contained semi-product
        if (entity.getSemiProduct() != null) {
            entity.setMeasurementUnitType(entity.getSemiProduct().getMeasurementUnitType());
        }

        // Set the measure unit from the contained final product
        if (entity.getFinalProduct() != null) {
            entity.setMeasurementUnitType(entity.getFinalProduct().getMeasurementUnitType());
        }

        // If entity is new and processing order is provided with processing action type 'GENERATE_QR_CODE', generate a new QR code tag for this stock order
        generateStockOrderQRCodeTag(entity, processingOrder);

        Long previousLocationId = entity.getProductionLocation() == null
                ? null : entity.getProductionLocation().getId();

        // Production location
        ApiStockOrderLocation apiProdLocation = apiStockOrder.getProductionLocation();
        if (apiProdLocation != null) {

            StockOrderLocation stockOrderLocation;
            if (apiProdLocation.getId() == null) {
                stockOrderLocation = new StockOrderLocation();
                em.persist(stockOrderLocation);
            } else {
                stockOrderLocation = fetchEntity(apiProdLocation.getId(), StockOrderLocation.class);
                Long sameCompanyReferences = em.createQuery(
                                "SELECT COUNT(so) FROM StockOrder so WHERE so.productionLocation.id = :locationId " +
                                        "AND so.company.id = :companyId", Long.class)
                        .setParameter("locationId", stockOrderLocation.getId())
                        .setParameter("companyId", entity.getCompany().getId())
                        .getSingleResult();
                if (sameCompanyReferences == 0) {
                    throw new ApiException(ApiStatus.UNAUTHORIZED, "Production location does not belong to this company");
                }

                Long allReferences = em.createQuery(
                                "SELECT COUNT(so) FROM StockOrder so WHERE so.productionLocation.id = :locationId",
                                Long.class)
                        .setParameter("locationId", stockOrderLocation.getId())
                        .getSingleResult();
                boolean locationChanged = !Objects.equals(stockOrderLocation.getLatitude(), apiProdLocation.getLatitude())
                        || !Objects.equals(stockOrderLocation.getLongitude(), apiProdLocation.getLongitude())
                        || !Objects.equals(stockOrderLocation.getNumberOfFarmers(), apiProdLocation.getNumberOfFarmers())
                        || !Objects.equals(stockOrderLocation.getPinName(), apiProdLocation.getPinName());
                boolean usedByAnotherOrder = allReferences > (Objects.equals(previousLocationId,
                        stockOrderLocation.getId()) ? 1 : 0);
                if (locationChanged && usedByAnotherOrder) {
                    StockOrderLocation copy = new StockOrderLocation();
                    copy.setAddress(stockOrderLocation.getAddress());
                    stockOrderLocation = copy;
                    em.persist(stockOrderLocation);
                }
            }

            stockOrderLocation.setLatitude(apiProdLocation.getLatitude());
            stockOrderLocation.setLongitude(apiProdLocation.getLongitude());
            stockOrderLocation.setNumberOfFarmers(apiProdLocation.getNumberOfFarmers());
            stockOrderLocation.setPinName(apiProdLocation.getPinName());
            entity.setProductionLocation(stockOrderLocation);
        }

        switch (apiStockOrder.getOrderType()) {
            case PURCHASE_ORDER:

                // On purchase order, Total quantity is calculated by total gross quantity and tare
                if (apiStockOrder.getTare() != null) {
                    apiStockOrder.setTotalQuantity(apiStockOrder.getTotalGrossQuantity().subtract(apiStockOrder.getTare()));
                } else {
                    apiStockOrder.setTotalQuantity(apiStockOrder.getTotalGrossQuantity());
                }
                if (apiStockOrder.getDamagedWeightDeduction() != null) {
                    apiStockOrder.setTotalQuantity(apiStockOrder.getTotalQuantity().subtract(apiStockOrder.getDamagedWeightDeduction()));
                }
                entity.setTotalQuantity(apiStockOrder.getTotalQuantity());
                entity.setTotalGrossQuantity(apiStockOrder.getTotalGrossQuantity());

                // Required
                if (apiStockOrder.getProducerUserCustomer() == null) {
                    throw new ApiException(ApiStatus.INVALID_REQUEST, "Producer user customer is required for purchase orders!");
                }

                if (apiStockOrder.getTotalQuantity() == null) {
                    throw new ApiException(ApiStatus.VALIDATION_ERROR, "Total quantity needs to be provided!");
                }

                if (apiStockOrder.getPricePerUnit() == null && !Boolean.TRUE.equals(apiStockOrder.getPriceDeterminedLater())) {
                    throw new ApiException(ApiStatus.VALIDATION_ERROR, "Price per unit needs to be provided!");
                }

                entity.setPricePerUnit(apiStockOrder.getPricePerUnit());
                BigDecimal pricePerUnitReduced = entity.getPricePerUnit();
                // if pay later remove payable but keep balance
                if (entity.getDamagedPriceDeduction() != null){
                    pricePerUnitReduced = entity.getPricePerUnit().subtract(entity.getDamagedPriceDeduction());
                }
                if (!Boolean.TRUE.equals(apiStockOrder.getPriceDeterminedLater())) {
                    entity.setCost(pricePerUnitReduced.multiply(entity.getTotalQuantity()));

                    if (processingOrder == null) {
                        entity.setBalance(calculateBalanceForPurchaseOrder(entity));
                    } else if (entity.getId() == null){
                        entity.setBalance(entity.getCost());
                    }
                    entity.setPaid(entity.getCost().subtract(entity.getBalance()));
                } else {
                    entity.setCost(null);
                    entity.setBalance(null);
                    entity.setPaid(null);
                }

                entity.setProducerUserCustomer(fetchEntity(apiStockOrder.getProducerUserCustomer().getId(), UserCustomer.class));
                entity.setPurchaseOrder(true);

                // Optional
                if(apiStockOrder.getRepresentativeOfProducerUserCustomer() != null)
                    entity.setRepresentativeOfProducerUserCustomer(fetchEntity(apiStockOrder.getRepresentativeOfProducerUserCustomer().getId(), UserCustomer.class));

                // Replace the links; their ActivityProof rows are cleaned up after the links are flushed.
                Set<Long> previousProofIds = entity.getActivityProofs().stream()
                        .map(link -> link.getActivityProof().getId())
                        .filter(Objects::nonNull)
                        .collect(Collectors.toSet());
                entity.getActivityProofs().clear();

                for (ApiActivityProof apiAP : apiStockOrder.getActivityProofs()) {

                    Document activityProofDoc = fetchEntity(apiAP.getDocument().getId(), Document.class);

                    StockOrderActivityProof stockOrderActivityProof = new StockOrderActivityProof();
                    stockOrderActivityProof.setStockOrder(entity);
                    ActivityProof activityProof = new ActivityProof();
                    activityProof.setDocument(activityProofDoc);
                    activityProof.setFormalCreationDate(apiAP.getFormalCreationDate());
                    activityProof.setType(apiAP.getType());
                    activityProof.setValidUntil(apiAP.getValidUntil());
                    em.persist(activityProof);
                    stockOrderActivityProof.setActivityProof(activityProof);

                    entity.getActivityProofs().add(stockOrderActivityProof);
                }

                if (!previousProofIds.isEmpty()) {
                    em.flush();
                    previousProofIds.forEach(this::removeUnreferencedActivityProof);
                }

                break;
            case GENERAL_ORDER:

                // Set the quote facility and quote company
                entity.setQuoteFacility(facilityService.fetchFacility(apiStockOrder.getQuoteFacility().getId()));
                entity.setQuoteCompany(entity.getQuoteFacility().getCompany());

            case TRANSFER_ORDER:
            case PROCESSING_ORDER:

                // Create/update processing evidence fields instances (values)
                createOrUpdateEvidenceFieldValues(apiStockOrder.getRequiredEvidenceFieldValues(), entity);

                // Create/update processing evidence types instances (values) - both required and other evidences
                entity.getDocumentRequirements().clear();
                createOrUpdateEvidenceTypeValues(apiStockOrder.getRequiredEvidenceTypeValues(), entity, false);
                createOrUpdateEvidenceTypeValues(apiStockOrder.getOtherEvidenceDocuments(), entity, true);
        }

        if (entity.getId() == null) {
            em.persist(entity);
        }

        if (previousLocationId != null && !Objects.equals(previousLocationId,
                entity.getProductionLocation() == null ? null : entity.getProductionLocation().getId())) {
            em.flush();
            removeUnreferencedProductionLocation(previousLocationId);
        }

        return new ApiBaseEntity(entity);
    }

    public void calculateQuantities(ApiStockOrder apiStockOrder, StockOrder stockOrder, ProcessingOrder processingOrder, Long newInputTransactionId) throws ApiException{

        if (apiStockOrder.getOrderType() == null) {
            throw new ApiException(ApiStatus.INVALID_REQUEST, "OrderType needs to be provided!");
        }
        if (apiStockOrder.getTotalQuantity() == null) {
            throw new ApiException(ApiStatus.INVALID_REQUEST, "Total quantity cannot be null!");
        }
        if (apiStockOrder.getFulfilledQuantity() == null) {
            throw new ApiException(ApiStatus.INVALID_REQUEST, "Fulfilled quantity cannot be null!");
        }

        BigDecimal lastUsedQuantity = (stockOrder.getFulfilledQuantity() != null && stockOrder.getAvailableQuantity() != null)
                ? stockOrder.getFulfilledQuantity().subtract(stockOrder.getAvailableQuantity())
                : null;

        stockOrder.setTotalQuantity(apiStockOrder.getTotalQuantity());

        switch (apiStockOrder.getOrderType()) {
            case PURCHASE_ORDER:
            case PROCESSING_ORDER:
            case TRANSFER_ORDER:

                stockOrder.setFulfilledQuantity(stockOrder.getTotalQuantity());

                // For new StockOrders set available quantity to total quantity
                if (stockOrder.getId() == null) {
                    stockOrder.setAvailableQuantity(stockOrder.getFulfilledQuantity());
                }

                break;

            case GENERAL_ORDER:

                // Applies only for new Quote StockOrders
                if (stockOrder.getId() == null) {
                    stockOrder.setFulfilledQuantity(apiStockOrder.getFulfilledQuantity());
                    stockOrder.setAvailableQuantity(apiStockOrder.getAvailableQuantity());
                }
        }

        // If existing entity and processing order is provided edit the quantities
        if (stockOrder.getId() != null) {
            if (processingOrder != null) {

                // Calculate quantities based on input transactions
                List<Transaction> inputTxs = processingOrder.getInputTransactions().stream().toList();
                Transaction newInputTransaction = newInputTransactionId != null ? inputTxs
                        .stream()
                        .filter(t -> t.getId().equals(newInputTransactionId)).findAny()
                        .orElse(null) : null;

                if (apiStockOrder.getOrderType() == OrderType.GENERAL_ORDER) {

                    if (!processingOrder.getTargetStockOrders().contains(stockOrder)) {

                        // Quote order is part of another StockOrder (as source)
                        stockOrder.setAvailableQuantity(stockOrder.getAvailableQuantity()
                                .subtract(calculateUsedQuantity(inputTxs, stockOrder.getId())));
                        stockOrder.setFulfilledQuantity(calculateFulfilledQuantity(inputTxs, stockOrder.getId()));

                    } else {
                        // When updating quote order itself
                        stockOrder.setFulfilledQuantity(calculateFulfilledQuantity(inputTxs, null));
                    }

                } else {

                    if (newInputTransaction != null) {

                        stockOrder.setAvailableQuantity(stockOrder.getAvailableQuantity().subtract(newInputTransaction.getOutputQuantity()));

                    } else {

                        stockOrder.setAvailableQuantity(stockOrder.getFulfilledQuantity()
                                .subtract(lastUsedQuantity != null ? lastUsedQuantity : BigDecimal.ZERO)
                                .subtract(calculateUsedQuantity(inputTxs, stockOrder.getId())));
                    }
                }

            } else if (lastUsedQuantity != null) {
                stockOrder.setAvailableQuantity(apiStockOrder.getFulfilledQuantity().subtract(lastUsedQuantity));
            }
        }

        // Calculate if the total quantity of this stock order is within the expected range
        if (processingOrder != null) {

            ProcessingAction procAction = processingOrder.getProcessingAction();
            BigDecimal expectedTotalQuantityPerUnit = procAction.getEstimatedOutputQuantityPerUnit();

            if (procAction.getType().equals(ProcessingActionType.PROCESSING) &&
                    BooleanUtils.isFalse(procAction.getRepackedOutputFinalProducts()) && expectedTotalQuantityPerUnit != null) {

                // Calculate the total input quantity (summed up input transactions from the processing order)
                List<Transaction> inputTxs = processingOrder.getInputTransactions().stream().toList();
                BigDecimal totalInputQuantity = inputTxs.stream()
                        .filter(t -> !t.getStatus().equals(TransactionStatus.CANCELED))
                        .map(Transaction::getOutputQuantity)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);

                BigDecimal inputUnitWeight = procAction.getInputSemiProduct().getMeasurementUnitType().getWeight();
                BigDecimal outputUnitWeight = stockOrder.getSemiProduct().getMeasurementUnitType().getWeight();

                // Calculate the input quantity normalized in the output measuring unit (important when we have different input and output measure units)
                BigDecimal totalInputQuantityNormalized = totalInputQuantity.divide(inputUnitWeight,
                        RoundingMode.HALF_UP).divide(outputUnitWeight, RoundingMode.HALF_UP);

                // Calculate the expected total output quantity
                BigDecimal totalExpectedOutQuantity = totalInputQuantityNormalized.multiply(expectedTotalQuantityPerUnit);

                // Calculate the expected total output quantity allowed range (+/- 20% of the total expected output quantity)
                BigDecimal outQuantityRangeLow = totalExpectedOutQuantity
                        .multiply(new BigDecimal("0.8"))
                        .setScale(2, RoundingMode.HALF_UP);
                BigDecimal outQuantityRangeHigh = totalExpectedOutQuantity
                        .multiply(new BigDecimal("1.2"))
                        .min(totalInputQuantityNormalized)
                        .setScale(2, RoundingMode.HALF_UP);

                // Check if the total output quantity set in the stock order is within the allowed range
                BigDecimal totalQuantity = stockOrder.getTotalQuantity();
                stockOrder.setOutQuantityNotInRange(!(totalQuantity.compareTo(outQuantityRangeLow) >= 0 &&
                        totalQuantity.compareTo(outQuantityRangeHigh) <= 0));
            } else {

                stockOrder.setOutQuantityNotInRange(null);
            }
        }

        // Validate quantities
        if (stockOrder.getAvailableQuantity() != null) {

            if (stockOrder.getAvailableQuantity().compareTo(BigDecimal.ZERO) < 0) {
                throw new ApiException(ApiStatus.VALIDATION_ERROR, "Available quantity resulted in negative number.");
            }

            if (stockOrder.getFulfilledQuantity() != null) {
                if (stockOrder.getFulfilledQuantity().compareTo(stockOrder.getAvailableQuantity()) < 0) {
                    throw new ApiException(ApiStatus.VALIDATION_ERROR, "Available quantity (" + stockOrder.getAvailableQuantity()
                            + ") cannot be bigger then fulfilled quantity (" + stockOrder.getFulfilledQuantity() + ").");
                }
                if (stockOrder.getFulfilledQuantity().compareTo(stockOrder.getTotalQuantity()) > 0) {
                    throw new ApiException(ApiStatus.VALIDATION_ERROR, "Fulfilled quantity (" + stockOrder.getFulfilledQuantity()
                            + ") cannot be bigger then total quantity (" + stockOrder.getTotalQuantity() + ").");
                }
            }
        }

        stockOrder.setAvailable(stockOrder.getAvailableQuantity() != null && stockOrder.getAvailableQuantity().compareTo(BigDecimal.ZERO) > 0);
        stockOrder.setIsOpenOrder(stockOrder.getOrderType() == OrderType.GENERAL_ORDER && stockOrder.getTotalQuantity().compareTo(stockOrder.getFulfilledQuantity()) > 0);
    }

    @Transactional
    public void deleteStockOrder(Long id, CustomUserDetails user) throws ApiException {

        StockOrder stockOrder = fetchEntity(id, StockOrder.class);

        PermissionsUtil.checkUserIfCompanyEnrolledAndAdminOrSystemAdmin(stockOrder.getCompany().getUsers().stream().toList(), user);

        Set<Long> proofIds = stockOrder.getActivityProofs().stream()
                .map(link -> link.getActivityProof().getId())
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Long locationId = stockOrder.getProductionLocation() == null
                ? null : stockOrder.getProductionLocation().getId();

        em.remove(stockOrder);
        em.flush();
        proofIds.forEach(this::removeUnreferencedActivityProof);
        if (locationId != null) {
            removeUnreferencedProductionLocation(locationId);
        }
    }

    private void removeUnreferencedActivityProof(Long proofId) {
        Long stockLinks = em.createQuery(
                        "SELECT COUNT(link) FROM StockOrderActivityProof link WHERE link.activityProof.id = :proofId",
                        Long.class)
                .setParameter("proofId", proofId)
                .getSingleResult();
        Long bulkLinks = em.createQuery(
                        "SELECT COUNT(link) FROM BulkPaymentActivityProof link WHERE link.activityProof.id = :proofId",
                        Long.class)
                .setParameter("proofId", proofId)
                .getSingleResult();
        if (stockLinks == 0 && bulkLinks == 0) {
            ActivityProof proof = em.find(ActivityProof.class, proofId);
            if (proof != null) {
                em.remove(proof);
            }
        }
    }

    private void removeUnreferencedProductionLocation(Long locationId) {
        Long references = em.createQuery(
                        "SELECT COUNT(so) FROM StockOrder so WHERE so.productionLocation.id = :locationId",
                        Long.class)
                .setParameter("locationId", locationId)
                .getSingleResult();
        if (references == 0) {
            StockOrderLocation location = em.find(StockOrderLocation.class, locationId);
            if (location != null) {
                em.remove(location);
            }
        }
    }

    public <E> E fetchEntity(Long id, Class<E> entityClass) throws ApiException {

        E entity = Queries.get(em, entityClass, id);
        if (entity == null) {
            throw new ApiException(ApiStatus.INVALID_REQUEST, "Invalid " + entityClass.getSimpleName() + " ID");
        }
        return entity;
    }

    private BigDecimal calculateBalanceForPurchaseOrder(StockOrder stockOrder) {

        if (stockOrder.getOrderType() != OrderType.PURCHASE_ORDER || stockOrder.getCost() == null)
            return null;

        List<Payment> paymentList = em.createNamedQuery("Payment.listPaymentsByPurchaseId", Payment.class)
                .setParameter("purchaseId", stockOrder.getId())
                .getResultList();

        BigDecimal balance = stockOrder.getCost();
        for (Payment payment : paymentList) {

            if (payment.getPaymentPurposeType() == PaymentPurposeType.FIRST_INSTALLMENT) {
                balance = balance.subtract(payment.getAmount());

                if (payment.getPreferredWayOfPayment() != PreferredWayOfPayment.CASH_VIA_COLLECTOR) {
                    balance = balance.subtract(payment.getAmountPaidToTheCollector());
                }
            }
        }
        return balance;
    }

    private BigDecimal calculateFulfilledQuantity(List<Transaction> inputTransactions, Long stockOrderId){
        if (inputTransactions.isEmpty()) {
            return BigDecimal.ZERO;
        }
        return inputTransactions.stream()
                .filter(t -> !t.getStatus().equals(TransactionStatus.CANCELED))
                .filter(t -> stockOrderId == null || stockOrderId.equals(t.getSourceStockOrder().getId()))
                .map(Transaction::getInputQuantity)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private BigDecimal calculateUsedQuantity(List<Transaction> inputTransactions, Long stockOrderId) {

        if (stockOrderId == null || inputTransactions.isEmpty()) {
            return BigDecimal.ZERO;
        }

        return inputTransactions.stream()
                .filter(t -> t.getSourceStockOrder() != null
                        && stockOrderId.equals(t.getSourceStockOrder().getId())
                        && !t.getStatus().equals(TransactionStatus.CANCELED))
                .map(Transaction::getOutputQuantity)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private void createOrUpdateEvidenceFieldValues(List<ApiStockOrderEvidenceFieldValue> apiEvidenceFieldValues, StockOrder entity) throws ApiException {

        entity.getProcessingEFValues().clear();

        for (ApiStockOrderEvidenceFieldValue apiEFV : apiEvidenceFieldValues) {

            StockOrderPEFieldValue stockOrderPEFieldValue = new StockOrderPEFieldValue();
            stockOrderPEFieldValue.setStockOrder(entity);
            stockOrderPEFieldValue.setProcessingEvidenceField(
                    procEvidenceFieldService.fetchProcessingEvidenceField(apiEFV.getEvidenceFieldId()));
            stockOrderPEFieldValue.setBooleanValue(apiEFV.getBooleanValue());
            stockOrderPEFieldValue.setStringValue(apiEFV.getStringValue());
            stockOrderPEFieldValue.setInstantValue(apiEFV.getDateValue());
            stockOrderPEFieldValue.setNumericValue(apiEFV.getNumericValue());
            stockOrderPEFieldValue.setStringValue(apiEFV.getStringValue());

            entity.getProcessingEFValues().add(stockOrderPEFieldValue);
        }
    }

    private void createOrUpdateEvidenceTypeValues(List<ApiStockOrderEvidenceTypeValue> apiEvidenceTypeValues, StockOrder entity, boolean otherEvidence) throws ApiException {

        for (ApiStockOrderEvidenceTypeValue apiETV : apiEvidenceTypeValues) {

            StockOrderPETypeValue stockOrderPETypeValue = new StockOrderPETypeValue();
            stockOrderPETypeValue.setStockOrder(entity);
            stockOrderPETypeValue.setProcessingEvidenceType(
                    procEvidenceTypeService.fetchProcessingEvidenceType(apiETV.getEvidenceTypeId()));
            stockOrderPETypeValue.setDate(apiETV.getDate());
            stockOrderPETypeValue.setDocument(fetchEntity(apiETV.getDocument().getId(), Document.class));
            stockOrderPETypeValue.setOtherEvidence(otherEvidence);

            entity.getDocumentRequirements().add(stockOrderPETypeValue);
        }
    }

    private void generateStockOrderQRCodeTag(StockOrder entity, ProcessingOrder processingOrder) {

        if (entity.getId() == null && processingOrder != null && processingOrder.getProcessingAction().getType() == ProcessingActionType.GENERATE_QR_CODE) {
            entity.setQrCodeTag(UUID.randomUUID().toString());
            entity.setQrCodeTagFinalProduct(processingOrder.getProcessingAction().getQrCodeForFinalProduct());
        }
    }

    public byte[] createGeoJsonFromDeliveries(List<ApiStockOrderHistoryTimelineItem> timelineItems) throws ApiException {

        // Read all userCustomers, from timeline item, and set ids in a Set
        Set<Long> customerIds = new HashSet<>();
        for (ApiStockOrderHistoryTimelineItem timelineItem: timelineItems) {
            timelineItem.getPurchaseOrders().forEach(po -> {
                if (po.getProducerUserCustomer() != null) {
                    customerIds.add(po.getProducerUserCustomer().getId());
                }
            });
        }

        if (customerIds.isEmpty()) {
            return new byte[0];
        }

        // Get all plots for given farmers
        Plot pcProxy = Torpedo.from(Plot.class);
        Torpedo.where(pcProxy.getFarmer().getId()).in(customerIds);
        List<Plot> plotList = Torpedo.select(pcProxy).list(em);

        // Convert plot values into GeoJson coordinates
        ObjectMapper mapper = new ObjectMapper();
        
        // FeatureCollection, combines Multipoint feature and Multipolygon feature
        ObjectNode rootFeatureCollection = mapper.createObjectNode();
        rootFeatureCollection.set("type", mapper.convertValue("FeatureCollection", JsonNode.class));
        
        // Multi points
        ObjectNode rootMultiPoint = mapper.createObjectNode();
        rootMultiPoint.set("type", mapper.convertValue("Feature", JsonNode.class));
        
        ObjectNode geometryMultiPoint = mapper.createObjectNode();
        geometryMultiPoint.set("type", mapper.convertValue("MultiPoint", JsonNode.class));
        rootMultiPoint.set("geometry", geometryMultiPoint);

        List<List<Double>> pointCoordinatesList = new ArrayList<>();
        plotList.forEach(plot -> {
            // if point (less than 3 coordinates)
            if (!plot.getCoordinates().isEmpty() && plot.getCoordinates().size() < 3) {
                plot.getCoordinates().forEach(plotCoordinate -> {
                    List<Double> cList = new ArrayList<>();
                    cList.add(plotCoordinate.getLongitude());
                    cList.add(plotCoordinate.getLatitude());
                    pointCoordinatesList.add(cList);
                });
            }
        });

        geometryMultiPoint.set("coordinates", mapper.convertValue(pointCoordinatesList, JsonNode.class));
        rootMultiPoint.set("properties",mapper.createObjectNode());
        
        // Multi polygon
        ObjectNode rootMultiPolygon = mapper.createObjectNode();
        rootMultiPolygon.set("type", mapper.convertValue("Feature", JsonNode.class));

        ObjectNode geometryMultiPolygon = mapper.createObjectNode();
        geometryMultiPolygon.set("type", mapper.convertValue("MultiPolygon", JsonNode.class));
        rootMultiPolygon.set("geometry", geometryMultiPolygon);

        List<List<List<List<Double>>>> plotCoordinatesList = new ArrayList<>();
        plotList.forEach(plot -> {
            // if polygon (more than 3 coordinates)
            if (!plot.getCoordinates().isEmpty() && plot.getCoordinates().size() > 2) {
                List<List<List<Double>>> plotCoordinatesListArray = new ArrayList<>();
                List<List<Double>> coordinatesList = new ArrayList<>();

                plot.getCoordinates().forEach(plotCoordinate -> {
                    List<Double> cList = new ArrayList<>();
                    cList.add(plotCoordinate.getLongitude());
                    cList.add(plotCoordinate.getLatitude());
                    coordinatesList.add(cList);
                });

                // polygons must follow right-hand rule (counter-clockwise)
                if (!isPolygonCounterClockwise(coordinatesList)) {
                    Collections.reverse(coordinatesList);
                }

                if (!coordinatesList.get(0).equals(coordinatesList.get(coordinatesList.size() - 1))) {
                    // add first as last
                    coordinatesList.add(coordinatesList.get(0));
                }
                plotCoordinatesListArray.add(coordinatesList);
                plotCoordinatesList.add(plotCoordinatesListArray);
            }
        });

        geometryMultiPolygon.set("coordinates", mapper.convertValue(plotCoordinatesList, JsonNode.class));

        rootMultiPolygon.set("properties",mapper.createObjectNode());
        
        // combine MultiPolygon and Multipoint features
        List<ObjectNode> featureList = new ArrayList<>();
        featureList.add(rootMultiPoint);
        featureList.add(rootMultiPolygon);
        
        rootFeatureCollection.set("features", mapper.convertValue(featureList, JsonNode.class));

        try {
           return mapper.writer().writeValueAsBytes(rootFeatureCollection);
        } catch (JsonProcessingException e) {
            logger.error("Error while generating geoJson file", e);
            throw new ApiException(ApiStatus.ERROR, "Error while generating geojson file");
        }
    }

    public byte[] exportDeliveriesByCompany(CustomUserDetails authUser, Long companyId, Language language) throws IOException, ApiException {

        // Get the deliveries list (PURCHASE_ORDER stock orders)
        ApiPaginatedRequest request = new ApiPaginatedRequest();
        request.setLimit(10000);

        // Prepare the query request for deliveries
        StockOrderQueryRequest queryRequest = new StockOrderQueryRequest(
                companyId,
                null,
                null,
                null,
                null,
                true,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null
        );

        List<ApiStockOrder> apiDeliveries = getStockOrderListForCompany(request, queryRequest, authUser, language).items;

        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {

            // Create date cell style
            CellStyle dateCellStyle = workbook.createCellStyle();
            dateCellStyle.setDataFormat((short) 14);

            // Create Excel sheet
            XSSFSheet sheet = workbook.createSheet(TranslateTools.getTranslatedValue(
                    messageSource, "export.deliveries.sheet.name", language
            ));

            // Prepare the header
            Row headerRow = sheet.createRow(0);
            headerRow.createCell(0, CellType.STRING).setCellValue(TranslateTools.getTranslatedValue(
                    messageSource, "export.deliveries.column.deliveryDate.label", language
            ));
            headerRow.createCell(1, CellType.STRING).setCellValue(TranslateTools.getTranslatedValue(
                    messageSource, "export.deliveries.column.identifier.label", language
            ));
            headerRow.createCell(2, CellType.STRING).setCellValue(TranslateTools.getTranslatedValue(
                    messageSource, "export.deliveries.column.farmer.label", language
            ));
            headerRow.createCell(3, CellType.STRING).setCellValue(TranslateTools.getTranslatedValue(
                    messageSource, "export.deliveries.column.semiProduct.label", language
            ));
            headerRow.createCell(4, CellType.STRING).setCellValue(TranslateTools.getTranslatedValue(
                    messageSource, "export.deliveries.column.quantity.label", language
            ));
            headerRow.createCell(5, CellType.STRING).setCellValue(TranslateTools.getTranslatedValue(
                    messageSource, "export.deliveries.column.totalGrossQuantity.label", language
            ));
            headerRow.createCell(6, CellType.STRING).setCellValue(TranslateTools.getTranslatedValue(
                    messageSource, "export.deliveries.column.tare.label", language
            ));
            headerRow.createCell(7, CellType.STRING).setCellValue(TranslateTools.getTranslatedValue(
                    messageSource, "export.deliveries.column.damagedWeightDeduction.label", language
            ));
            headerRow.createCell(8, CellType.STRING).setCellValue(TranslateTools.getTranslatedValue(
                    messageSource, "export.deliveries.column.unit.label", language
            ));
            headerRow.createCell(9, CellType.STRING).setCellValue(TranslateTools.getTranslatedValue(
                    messageSource, "export.deliveries.column.pricePerUnit.label", language
            ));
            headerRow.createCell(10, CellType.STRING).setCellValue(TranslateTools.getTranslatedValue(
                    messageSource, "export.deliveries.column.damagedPriceDeduction.label", language
            ));
            headerRow.createCell(11, CellType.STRING).setCellValue(TranslateTools.getTranslatedValue(
                    messageSource, "export.deliveries.column.payable.label", language
            ));
            headerRow.createCell(12, CellType.STRING).setCellValue(TranslateTools.getTranslatedValue(
                    messageSource, "export.deliveries.column.balance.label", language
            ));
            headerRow.createCell(13, CellType.STRING).setCellValue(TranslateTools.getTranslatedValue(
                    messageSource, "export.deliveries.column.currency.label", language
            ));
            headerRow.createCell(14, CellType.STRING).setCellValue(TranslateTools.getTranslatedValue(
                    messageSource, "export.deliveries.column.preferredWayOfPayment.label", language
            ));

            int rowNum = 1;
            for (ApiStockOrder apiDelivery : apiDeliveries) {

                Row row = sheet.createRow(rowNum++);

                // Create delivery date column
                row.createCell(0, CellType.NUMERIC).setCellValue(apiDelivery.getProductionDate());
                row.getCell(0).setCellStyle(dateCellStyle);
                // sheet.autoSizeColumn(0);

                // Create column identifier
                row.createCell(1, CellType.STRING).setCellValue(apiDelivery.getIdentifier());
                // sheet.autoSizeColumn(1);

                // Create farmer column
                row.createCell(2, CellType.STRING).setCellValue(apiDelivery.getProducerUserCustomer().getName() + " " + apiDelivery.getProducerUserCustomer().getSurname());
                // sheet.autoSizeColumn(2);

                // Create semi-product column
                row.createCell(3, CellType.STRING).setCellValue(apiDelivery.getSemiProduct().getName());
                // sheet.autoSizeColumn(3);

                // Create quantity column
                row.createCell(4, CellType.NUMERIC).setCellValue(apiDelivery.getTotalQuantity().doubleValue());
                // sheet.autoSizeColumn(4);

                // Create gross quantity column
                row.createCell(5, CellType.NUMERIC);
                if (apiDelivery.getTotalGrossQuantity() != null) {
                    row.getCell(5).setCellValue(apiDelivery.getTotalGrossQuantity().doubleValue());
                }
                // sheet.autoSizeColumn(5);

                // Create tare column
                row.createCell(6, CellType.NUMERIC);
                if (apiDelivery.getTare() != null) {
                    row.getCell(6).setCellValue(apiDelivery.getTare().doubleValue());
                }
                // sheet.autoSizeColumn(6);

                // Create damaged weight deduction column
                row.createCell(7, CellType.NUMERIC);
                if (apiDelivery.getDamagedWeightDeduction() != null) {
                    row.getCell(7).setCellValue(apiDelivery.getDamagedWeightDeduction().doubleValue());
                }
                // sheet.autoSizeColumn(7);

                // Create unit column
                row.createCell(8, CellType.STRING).setCellValue(apiDelivery.getMeasureUnitType().getLabel());
                // sheet.autoSizeColumn(8);

                // Create price per unit column
                row.createCell(9, CellType.NUMERIC);
                if (apiDelivery.getPricePerUnit() != null) {
                    row.getCell(9).setCellValue(apiDelivery.getPricePerUnit().doubleValue());
                }
                // sheet.autoSizeColumn(9);

                // Create damaged price deduction column
                row.createCell(10, CellType.NUMERIC);
                if (apiDelivery.getDamagedPriceDeduction() != null) {
                    row.getCell(10).setCellValue(apiDelivery.getDamagedPriceDeduction().doubleValue());
                }
                // sheet.autoSizeColumn(10);

                // Create payable column
                row.createCell(11, CellType.NUMERIC);
                if (apiDelivery.getCost() != null) {
                    row.getCell(11).setCellValue(apiDelivery.getCost().doubleValue());
                }
                // sheet.autoSizeColumn(11);

                // Create balance column
                row.createCell(12, CellType.NUMERIC);
                if (apiDelivery.getBalance() != null) {
                    row.getCell(12).setCellValue(apiDelivery.getBalance().doubleValue());
                }
                // sheet.autoSizeColumn(12);

                // Create currency column
                row.createCell(13, CellType.STRING).setCellValue(apiDelivery.getCurrency());
                // sheet.autoSizeColumn(13);

                // Create preferred way of payment column
                row.createCell(14, CellType.STRING).setCellValue(TranslateTools.getTranslatedValue(
                        messageSource, "export.payments.column.preferredWayOfPayment.value." + apiDelivery.getPreferredWayOfPayment().toString(), language
                ));
                // sheet.autoSizeColumn(14);
            }

            workbook.write(byteArrayOutputStream);
        }

        return byteArrayOutputStream.toByteArray();
    }

    private boolean isPolygonCounterClockwise(List<List<Double>> coordinatesList) {
        // calculate area
        double areaSum = 0.0;
        for (int i = 0; i < coordinatesList.size(); i++){
            if (i == coordinatesList.size() -1) {
                // last coordinate calc
                areaSum += (coordinatesList.get(0).get(0) - coordinatesList.get(i).get(0)) *
                        (coordinatesList.get(0).get(1) + coordinatesList.get(i).get(1));
            } else {
                areaSum += (coordinatesList.get(i + 1).get(0) - coordinatesList.get(i).get(0)) *
                        (coordinatesList.get(i + 1).get(1) + coordinatesList.get(i).get(1));
            }
        }
        // if areaSum is < 0, polygon order is counter-clockwise
        return areaSum < 0;
    }
}
