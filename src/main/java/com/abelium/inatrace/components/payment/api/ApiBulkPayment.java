package com.abelium.inatrace.components.payment.api;

import com.abelium.inatrace.api.ApiBaseEntity;
import com.abelium.inatrace.components.common.api.ApiActivityProof;
import com.abelium.inatrace.components.company.api.ApiCompany;
import com.abelium.inatrace.components.user.api.ApiUser;
import com.abelium.inatrace.db.entities.payment.PaymentPurposeType;
import com.abelium.inatrace.tools.converters.SimpleDateConverter;
import tools.jackson.databind.annotation.JsonDeserialize;
import tools.jackson.databind.annotation.JsonSerialize;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public class ApiBulkPayment extends ApiBaseEntity {
	
	@Schema(description = "Bulk payment created by")
	private ApiUser createdBy;

	@Schema(description = "Creation timestamp")
	@JsonDeserialize(using = SimpleDateConverter.Deserialize.class)
	private Instant creationTimestamp;

	@Schema(description = "Bulk payment currency")
	private String currency;

	@Schema(description = "Formal creation date")
	@JsonSerialize(converter = SimpleDateConverter.Serialize.class)
	@JsonDeserialize(using = SimpleDateConverter.Deserialize.class)
	private Instant formalCreationTime;
	
	@Schema(description = "Company that pays for the bulk payment")
	private ApiCompany payingCompany;
	
	@Schema(description = "Bulk payment drescription")
	private String paymentDescription;
	
	@Schema(description = "Bulk payment purpose type")
	private PaymentPurposeType paymentPurposeType;
	
	@Schema(description = "Bulk payment receipt number")
	private String receiptNumber;
	
	@Schema(description = "Bulk payment total amount")
	private BigDecimal totalAmount;
	
	@Schema(description = "Bulk payment additional cost")
	private BigDecimal additionalCost;
	
	@Schema(description = "Bulk payment additional cost description")
	private String additionalCostDescription;
	
	@Schema(description = "Bulk payment payments")
	private List<ApiPayment> payments = new ArrayList<>();
	
	@Schema(description = "Bulk payment additional proofs")
	private List<ApiActivityProof> additionalProofs = new ArrayList<>();

	public ApiUser getCreatedBy() {
		return createdBy;
	}

	public void setCreatedBy(ApiUser createdBy) {
		this.createdBy = createdBy;
	}

	public Instant getCreationTimestamp() {
		return creationTimestamp;
	}

	public void setCreationTimestamp(Instant creationTimestamp) {
		this.creationTimestamp = creationTimestamp;
	}

	public String getCurrency() {
		return currency;
	}

	public void setCurrency(String currency) {
		this.currency = currency;
	}

	public Instant getFormalCreationTime() {
		return formalCreationTime;
	}

	public void setFormalCreationTime(Instant formalCreationTime) {
		this.formalCreationTime = formalCreationTime;
	}

	public ApiCompany getPayingCompany() {
		return payingCompany;
	}

	public void setPayingCompany(ApiCompany payingCompany) {
		this.payingCompany = payingCompany;
	}

	public String getPaymentDescription() {
		return paymentDescription;
	}

	public void setPaymentDescription(String paymentDescription) {
		this.paymentDescription = paymentDescription;
	}

	public PaymentPurposeType getPaymentPurposeType() {
		return paymentPurposeType;
	}

	public void setPaymentPurposeType(PaymentPurposeType paymentPurposeType) {
		this.paymentPurposeType = paymentPurposeType;
	}

	public String getReceiptNumber() {
		return receiptNumber;
	}

	public void setReceiptNumber(String receiptNumber) {
		this.receiptNumber = receiptNumber;
	}

	public BigDecimal getTotalAmount() {
		return totalAmount;
	}

	public void setTotalAmount(BigDecimal totalAmount) {
		this.totalAmount = totalAmount;
	}

	public BigDecimal getAdditionalCost() {
		return additionalCost;
	}

	public void setAdditionalCost(BigDecimal additionalCost) {
		this.additionalCost = additionalCost;
	}

	public String getAdditionalCostDescription() {
		return additionalCostDescription;
	}

	public void setAdditionalCostDescription(String additionalCostDescription) {
		this.additionalCostDescription = additionalCostDescription;
	}

	public List<ApiPayment> getPayments() {
		return payments;
	}

	public void setPayments(List<ApiPayment> payments) {
		this.payments = payments;
	}

	public List<ApiActivityProof> getAdditionalProofs() {
		return additionalProofs;
	}

	public void setAdditionalProofs(List<ApiActivityProof> additionalProofs) {
		this.additionalProofs = additionalProofs;
	}
}
