package com.abelium.inatrace.components.payment;

import com.abelium.inatrace.components.common.CommonApiTools;
import com.abelium.inatrace.components.company.mappers.CompanyMapper;
import com.abelium.inatrace.components.company.mappers.UserCustomerMapper;
import com.abelium.inatrace.components.payment.api.ApiPayment;
import com.abelium.inatrace.components.stockorder.mappers.StockOrderMapper;
import com.abelium.inatrace.components.user.mappers.UserMapper;
import com.abelium.inatrace.db.entities.payment.Payment;

/**
 * Mapper for Payment entity.
 *
 * @author Rene Flores, Sunesis d.o.o.
 */
public final class PaymentMapper {

	private PaymentMapper() {
		throw new IllegalStateException("Utility class");
	}

	public static ApiPayment toApiPayment(Payment entity, Long userId) {

		// Simplest apiPayment object
		ApiPayment apiPayment = new ApiPayment();
		apiPayment.setId(entity.getId());
		apiPayment.setUpdatedTimestamp(entity.getUpdateTimestamp());
		apiPayment.setCreatedBy(UserMapper.toSimpleApiUser(entity.getCreatedBy()));
		apiPayment.setUpdatedBy(UserMapper.toSimpleApiUser(entity.getUpdatedBy()));
		apiPayment.setPaymentType(entity.getPaymentType());
		apiPayment.setCurrency(entity.getCurrency());
		apiPayment.setAmount(entity.getAmount());
		apiPayment.setAmountPaidToTheCollector(entity.getAmountPaidToTheCollector());
		apiPayment.setRecipientType(entity.getRecipientType());
		apiPayment.setReceiptNumber(entity.getReceiptNumber());
		apiPayment.setReceiptDocumentType(entity.getReceiptDocumentType());
		apiPayment.setPaymentPurposeType(entity.getPaymentPurposeType());
		apiPayment.setPaymentStatus(entity.getPaymentStatus());
		apiPayment.setPaymentConfirmedAtTime(entity.getPaymentConfirmedAtTime());
		apiPayment.setFormalCreationTime(entity.getFormalCreationTime());
		apiPayment.setPreferredWayOfPayment(entity.getPreferredWayOfPayment());
		apiPayment.setProductionDate(entity.getProductionDate());
		apiPayment.setRecipientUserCustomer(UserCustomerMapper.toApiUserCustomerBase(entity.getRecipientUserCustomer()));
		apiPayment.setRepresentativeOfRecipientUserCustomer(UserCustomerMapper.toApiUserCustomerBase(entity.getRepresentativeOfRecipientUserCustomer()));
		apiPayment.setRecipientCompany(CompanyMapper.toApiCompanyBase(entity.getRecipientCompany()));
		apiPayment.setPayingCompany(CompanyMapper.toApiCompanyBase(entity.getPayingCompany()));
		apiPayment.setPaymentConfirmedByUser(UserMapper.toSimpleApiUser(entity.getPaymentConfirmedByUser()));
		apiPayment.setStockOrder(StockOrderMapper.toApiStockOrderBase(entity.getStockOrder()));
		apiPayment.setPurchased(entity.getPurchased().intValue());

		// A receipt is never public. Without a user (history built for exports and public
		// traceability) it is left out instead of being registered as a public storage key,
		// which StorageKeyCache refuses without an explicit public scope.
		if (entity.getReceiptDocument() != null && userId != null) {
			apiPayment.setReceiptDocument(CommonApiTools.toApiDocument(entity.getReceiptDocument(), userId));
		}

		return apiPayment;
	}
}
