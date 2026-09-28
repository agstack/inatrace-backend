package com.abelium.inatrace.components.stockorder.mappers;

import com.abelium.inatrace.components.codebook.measure_unit_type.MeasureUnitTypeMapper;
import com.abelium.inatrace.components.codebook.semiproduct.SemiProductMapper;
import com.abelium.inatrace.components.codebook.semiproduct.api.ApiSemiProduct;
import com.abelium.inatrace.components.common.mappers.ActivityProofMapper;
import com.abelium.inatrace.components.company.mappers.CompanyCustomerMapper;
import com.abelium.inatrace.components.company.mappers.CompanyMapper;
import com.abelium.inatrace.components.company.mappers.UserCustomerMapper;
import com.abelium.inatrace.components.facility.FacilityMapper;
import com.abelium.inatrace.components.payment.PaymentMapper;
import com.abelium.inatrace.components.processingorder.mappers.ProcessingOrderMapper;
import com.abelium.inatrace.components.product.ProductApiTools;
import com.abelium.inatrace.components.productorder.mappers.ProductOrderMapper;
import com.abelium.inatrace.components.stockorder.api.ApiStockOrder;
import com.abelium.inatrace.components.stockorder.api.ApiStockOrderEvidenceTypeValue;
import com.abelium.inatrace.components.user.mappers.UserMapper;
import com.abelium.inatrace.db.entities.stockorder.StockOrder;
import com.abelium.inatrace.db.entities.stockorder.StockOrderActivityProof;
import com.abelium.inatrace.types.Language;
import org.apache.commons.lang3.BooleanUtils;

import java.util.Objects;
import java.util.stream.Collectors;

public class StockOrderMapper {

    public static ApiStockOrder toApiStockOrderBase(StockOrder entity) {

        if(entity == null) {
            return null;
        }

        ApiStockOrder apiStockOrder = new ApiStockOrder();
        apiStockOrder.setId(entity.getId());
        apiStockOrder.setIdentifier(entity.getIdentifier());
        apiStockOrder.setLotPrefix(entity.getLotPrefix());
        apiStockOrder.setInternalLotNumber(setupInternalLotNumberForSacked(entity.getInternalLotNumber(), entity.getSacNumber()));
        apiStockOrder.setCurrency(entity.getCurrency());
        apiStockOrder.setPreferredWayOfPayment(entity.getPreferredWayOfPayment());
        apiStockOrder.setTotalQuantity(entity.getTotalQuantity());
        apiStockOrder.setFulfilledQuantity(entity.getFulfilledQuantity());
        apiStockOrder.setBalance(entity.getBalance());
        apiStockOrder.setOrderType(entity.getOrderType());
        apiStockOrder.setMeasureUnitType(
                MeasureUnitTypeMapper.toApiMeasureUnitType(entity.getMeasurementUnitType()));

        // If present map the QR code tag and the Final product for which the QR code tag was generated
        if (entity.getQrCodeTag() != null) {
            apiStockOrder.setQrCodeTag(entity.getQrCodeTag());
            apiStockOrder.setQrCodeTagFinalProduct(ProductApiTools.toApiFinalProductBase(entity.getQrCodeTagFinalProduct()));
        }

        // Map women share and organic only
        apiStockOrder.setWomenShare(entity.getWomenShare());
        apiStockOrder.setOrganic(entity.getOrganic());

        // Farmer
        apiStockOrder.setProducerUserCustomer(
                UserCustomerMapper.toApiUserCustomerBase(entity.getProducerUserCustomer()));

        // Collector
        apiStockOrder.setRepresentativeOfProducerUserCustomer(
                UserCustomerMapper.toApiUserCustomerBase(entity.getRepresentativeOfProducerUserCustomer()));

        return apiStockOrder;
    }

    public static ApiStockOrder toApiStockOrder(StockOrder entity, Long userId, Language language) {
        return toApiStockOrder(entity, userId, language, false);
    }

    public static ApiStockOrder toApiStockOrder(StockOrder entity, Long userId, Language language, Boolean withProcessingOrder) {

        if (entity == null) {
            return null;
        }

        ApiStockOrder apiStockOrder = new ApiStockOrder();
        apiStockOrder.setId(entity.getId());
        apiStockOrder.setIdentifier(entity.getIdentifier());
        apiStockOrder.setCreatedBy(UserMapper.toSimpleApiUser(entity.getCreatedBy()));
        apiStockOrder.setUpdatedBy(UserMapper.toSimpleApiUser(entity.getUpdatedBy()));
        apiStockOrder.setCreationTimestamp(entity.getCreationTimestamp());
        apiStockOrder.setUpdateTimestamp(entity.getUpdateTimestamp());
        apiStockOrder.setCreatorId(entity.getCreatorId());
        apiStockOrder.setProductionLocation(
                StockOrderLocationMapper.toApiStockOrderLocation(entity.getProductionLocation()));
        apiStockOrder.setRepresentativeOfProducerUserCustomer(
                UserCustomerMapper.toApiUserCustomer(entity.getRepresentativeOfProducerUserCustomer()));
        apiStockOrder.setProducerUserCustomer(
                UserCustomerMapper.toApiUserCustomer(entity.getProducerUserCustomer()));

        // Map the activity proofs
        if (!entity.getActivityProofs().isEmpty()) {
            apiStockOrder.setActivityProofs(entity.getActivityProofs().stream()
                    .map(StockOrderActivityProof::getActivityProof)
                    .filter(Objects::nonNull)
                    .map(activityProof -> ActivityProofMapper.toApiActivityProof(activityProof, userId))
                    .collect(Collectors.toList()));
        }

        // Map the instances (values) of processing evidence fields
        if (!entity.getProcessingEFValues().isEmpty()) {
            apiStockOrder.setRequiredEvidenceFieldValues(entity.getProcessingEFValues().stream()
                    .map(StockOrderEvidenceFieldValueMapper::toApiStockOrderEvidenceFieldValue)
                    .collect(Collectors.toList()));
        }

        // Map the instances (values) of processing evidence types (both required and other evidences)
        entity.getDocumentRequirements().forEach(stockOrderPETypeValue -> {

            ApiStockOrderEvidenceTypeValue apiEvidenceTypeValue = StockOrderEvidenceTypeValueMapper.toApiStockOrderEvidenceTypeValue(
                    stockOrderPETypeValue, userId, language);

            if (BooleanUtils.isTrue(stockOrderPETypeValue.getOtherEvidence())) {
                apiStockOrder.getOtherEvidenceDocuments().add(apiEvidenceTypeValue);
            } else {
                apiStockOrder.getRequiredEvidenceTypeValues().add(apiEvidenceTypeValue);
            }
        });

        // Map the semi-product that is represented by this stock order
        apiStockOrder.setSemiProduct(SemiProductMapper.toApiSemiProduct(entity.getSemiProduct(), ApiSemiProduct.class, language));

        apiStockOrder.setPriceDeterminedLater(entity.getPriceDeterminedLater());

        // Map the final product that is represented by this stock order
        apiStockOrder.setFinalProduct(ProductApiTools.toApiFinalProduct(entity.getFinalProduct()));

        // Set the facility and company of the stock order
        apiStockOrder.setFacility(FacilityMapper.toApiFacility(entity.getFacility(), language));
        apiStockOrder.setCompany(CompanyMapper.toApiCompanyBase(entity.getCompany()));

        // Set the measure unit of the stock order
        apiStockOrder.setMeasureUnitType(MeasureUnitTypeMapper.toApiMeasureUnitType(entity.getMeasurementUnitType()));

        // Set the quoted facility
        apiStockOrder.setQuoteFacility(FacilityMapper.toApiFacility(entity.getQuoteFacility(), language));

        // Set the quote company
        apiStockOrder.setQuoteCompany(CompanyMapper.toApiCompanyBase(entity.getQuoteCompany()));

        // Set the company customer for whom the stock order was created
        apiStockOrder.setConsumerCompanyCustomer(CompanyCustomerMapper.toApiCompanyCustomer(
                entity.getConsumerCompanyCustomer()));

        // Set the product order that triggered the creation of this stock order (if product order is not provided, set orderId from this Stock order)
        apiStockOrder.setProductOrder(ProductOrderMapper.toApiProductOrder(entity.getProductOrder(), language));
        if (apiStockOrder.getProductOrder() == null) {
            apiStockOrder.setOrderId(entity.getOrderId());
        }

        // Set the stock order quantities
        apiStockOrder.setTotalQuantity(entity.getTotalQuantity());
        apiStockOrder.setTotalGrossQuantity(entity.getTotalGrossQuantity());
        apiStockOrder.setFulfilledQuantity(entity.getFulfilledQuantity());
        apiStockOrder.setAvailableQuantity(entity.getAvailableQuantity());
        apiStockOrder.setTare(entity.getTare());
        apiStockOrder.setAvailable(entity.getAvailable());
        apiStockOrder.setOpenOrder(entity.getIsOpenOrder());
        apiStockOrder.setOutQuantityNotInRange(entity.getOutQuantityNotInRange());

        // Set dates
        apiStockOrder.setProductionDate(entity.getProductionDate());
        apiStockOrder.setDeliveryTime(entity.getDeliveryTime());

        // Set currency, prices and cost
        apiStockOrder.setCurrency(entity.getCurrency());
        apiStockOrder.setPricePerUnit(entity.getPricePerUnit());
        apiStockOrder.setDamagedPriceDeduction(entity.getDamagedPriceDeduction());
        apiStockOrder.setDamagedWeightDeduction(entity.getDamagedWeightDeduction());
        apiStockOrder.setCost(entity.getCost());
        apiStockOrder.setPaid(entity.getPaid());
        apiStockOrder.setBalance(entity.getBalance());
        apiStockOrder.setPreferredWayOfPayment(entity.getPreferredWayOfPayment());

        // Set identifiers and type of the stock order
        apiStockOrder.setOrderType(entity.getOrderType());
        apiStockOrder.setLotPrefix(entity.getLotPrefix());
        apiStockOrder.setInternalLotNumber(setupInternalLotNumberForSacked(entity.getInternalLotNumber(), entity.getSacNumber()));
        apiStockOrder.setSacNumber(entity.getSacNumber());
        apiStockOrder.setRepackedOriginStockOrderId(entity.getRepackedOriginStockOrderId());
        apiStockOrder.setPurchaseOrder(entity.getPurchaseOrder());

        // Set other data fields
        apiStockOrder.setComments(entity.getComments());
        apiStockOrder.setWomenShare(entity.getWomenShare());
        apiStockOrder.setOrganic(entity.getOrganic());

        // Set price and currency for end customer (used in Quote orders for final products)
        apiStockOrder.setPricePerUnitForEndCustomer(entity.getPricePerUnitForEndCustomer());
        apiStockOrder.setCurrencyForEndCustomer(entity.getCurrencyForEndCustomer());

        // Map the consumer company customer
        apiStockOrder.setConsumerCompanyCustomer(
                CompanyCustomerMapper.toApiCompanyCustomerBase(entity.getConsumerCompanyCustomer()));

        // Map the stock order QR code tag and the final product for which the QR code tag is generated
        apiStockOrder.setQrCodeTag(entity.getQrCodeTag());
        apiStockOrder.setQrCodeTagFinalProduct(ProductApiTools.toApiFinalProductBase(entity.getQrCodeTagFinalProduct()));

        // If requested mapping with Processing order, map the Processing order that created this stock order
        if (BooleanUtils.isTrue(withProcessingOrder)) {
            apiStockOrder.setProcessingOrder(ProcessingOrderMapper.toApiProcessingOrderBase(entity.getProcessingOrder()));
        }

        apiStockOrder.setPayments(entity.getPayments().stream().map(payment -> PaymentMapper.toApiPayment(payment, null)).collect(Collectors.toList()));

        return apiStockOrder;
    }

    public static ApiStockOrder toApiStockOrderHistoryItem(StockOrder entity, Language language) {

        if (entity == null) {
            return null;
        }

        ApiStockOrder apiStockOrder = new ApiStockOrder();
        apiStockOrder.setId(entity.getId());
        apiStockOrder.setIdentifier(entity.getIdentifier());
        apiStockOrder.setInternalLotNumber(setupInternalLotNumberForSacked(entity.getInternalLotNumber(), entity.getSacNumber()));
        apiStockOrder.setSacNumber(entity.getSacNumber());
        apiStockOrder.setProductionDate(entity.getProductionDate());
        apiStockOrder.setCreationTimestamp(entity.getCreationTimestamp());

        apiStockOrder.setTotalQuantity(entity.getTotalQuantity());
        apiStockOrder.setMeasureUnitType(
                MeasureUnitTypeMapper.toApiMeasureUnitType(entity.getMeasurementUnitType()));

        apiStockOrder.setFacility(FacilityMapper.toApiFacilityBase(entity.getFacility(), language));

        apiStockOrder.setWomenShare(entity.getWomenShare());
        apiStockOrder.setOrganic(entity.getOrganic());

        // Map payments
        apiStockOrder.setPayments(entity.getPayments().stream().map(payment -> PaymentMapper.toApiPayment(payment, null)).collect(Collectors.toList()));

        // Map the instances (values) of processing evidence fields
        if (!entity.getProcessingEFValues().isEmpty()) {
            apiStockOrder.setRequiredEvidenceFieldValues(entity.getProcessingEFValues().stream()
                    .map(StockOrderEvidenceFieldValueMapper::toApiStockOrderEvidenceFieldValue)
                    .collect(Collectors.toList()));
        }

        return apiStockOrder;
    }

    public static ApiStockOrder toApiStockOrderHistory(StockOrder entity, Language language) {

        ApiStockOrder apiStockOrder = toApiStockOrderHistoryItem(entity, language);

        if (apiStockOrder == null) {
            return null;
        }

        apiStockOrder.setUpdateTimestamp(entity.getUpdateTimestamp());
        apiStockOrder.setOrderType(entity.getOrderType());

        apiStockOrder.setFulfilledQuantity(entity.getFulfilledQuantity());
        apiStockOrder.setAvailableQuantity(entity.getAvailableQuantity());

        apiStockOrder.setComments(entity.getComments());
        apiStockOrder.setPurchaseOrder(entity.getPurchaseOrder());

        if (BooleanUtils.isTrue(entity.getPurchaseOrder())) {

            // Farmer
            apiStockOrder.setProducerUserCustomer(
                    UserCustomerMapper.toApiUserCustomerBase(entity.getProducerUserCustomer()));

            // Collector
            apiStockOrder.setRepresentativeOfProducerUserCustomer(
                    UserCustomerMapper.toApiUserCustomerBase(entity.getRepresentativeOfProducerUserCustomer()));

            apiStockOrder.setCurrency(entity.getCurrency());
            apiStockOrder.setCost(entity.getCost());
            apiStockOrder.setPaid(entity.getPaid());
            apiStockOrder.setBalance(entity.getBalance());
        }

        apiStockOrder.setSemiProduct(SemiProductMapper.toApiSemiProductBase(entity.getSemiProduct(), ApiSemiProduct.class, language));
        apiStockOrder.setFinalProduct(ProductApiTools.toApiFinalProductBase(entity.getFinalProduct()));

        return apiStockOrder;
    }

    private static String setupInternalLotNumberForSacked(String internalLotNumber, Integer sacNumber) {
        if(internalLotNumber == null || sacNumber == null) {
            return internalLotNumber;
        }
        return String.format("%s/%d", internalLotNumber, sacNumber);
    }

}
