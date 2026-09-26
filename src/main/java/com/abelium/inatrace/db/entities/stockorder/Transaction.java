package com.abelium.inatrace.db.entities.stockorder;

import com.abelium.inatrace.api.types.Lengths;
import com.abelium.inatrace.db.base.TimestampEntity;
import com.abelium.inatrace.db.entities.codebook.MeasureUnitType;
import com.abelium.inatrace.db.entities.codebook.SemiProduct;
import com.abelium.inatrace.db.entities.company.Company;
import com.abelium.inatrace.db.entities.facility.Facility;
import com.abelium.inatrace.db.entities.processingorder.ProcessingOrder;
import com.abelium.inatrace.db.entities.product.FinalProduct;
import com.abelium.inatrace.db.entities.stockorder.enums.TransactionStatus;
import jakarta.persistence.*;
import java.math.BigDecimal;

@NamedQueries({
@NamedQuery(
		name = "Transaction.getOutputTransactionsByStockOrderId",
		query = "SELECT t FROM Transaction t WHERE t.sourceStockOrder.id = :stockOrderId")
})
@Entity
public class Transaction extends TimestampEntity {
	
	@Version
	private Long entityVersion;
	
	@ManyToOne
	private Company company;

	@Column
	private Long initiationUserId;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "sourceStockOrder_id", nullable = true)
	private StockOrder sourceStockOrder;

	@ManyToOne(fetch = FetchType.LAZY)
	private ProcessingOrder targetProcessingOrder;

	// Used for orders with action type 'PROCESSING'
	@ManyToOne
	private SemiProduct semiProduct;

	// Used for orders with action type 'FINAL_PROCESSING'
	@ManyToOne
	private FinalProduct finalProduct;
	
	@ManyToOne
	private Facility sourceFacility;

	@Column
	private Boolean isProcessing;
	
	@Enumerated(EnumType.STRING)
	@Column(length = Lengths.ENUM)
	private TransactionStatus status;
	
	@Column
	private Long shipmentId;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "inputMeasureUnitType_id", nullable = true)
	private MeasureUnitType inputMeasureUnitType;
	
	@Column
	private BigDecimal inputQuantity;

	@Column
	private BigDecimal outputQuantity;
	
	@Column
	private BigDecimal pricePerUnit;
	
	@Column
	private String currency;
	
	@Column
	private String rejectComment;

	public Company getCompany() {
		return company;
	}

	public void setCompany(Company company) {
		this.company = company;
	}

	public Long getInitiationUserId() {
		return initiationUserId;
	}

	public void setInitiationUserId(Long initiationUserId) {
		this.initiationUserId = initiationUserId;
	}

	public StockOrder getSourceStockOrder() {
		return sourceStockOrder;
	}

	public void setSourceStockOrder(StockOrder sourceStockOrder) {
		this.sourceStockOrder = sourceStockOrder;
	}

	public ProcessingOrder getTargetProcessingOrder() {
		return targetProcessingOrder;
	}

	public void setTargetProcessingOrder(ProcessingOrder targetProcessingOrder) {
		this.targetProcessingOrder = targetProcessingOrder;
	}

	public SemiProduct getSemiProduct() {
		return semiProduct;
	}

	public void setSemiProduct(SemiProduct semiProduct) {
		this.semiProduct = semiProduct;
	}

	public FinalProduct getFinalProduct() {
		return finalProduct;
	}

	public void setFinalProduct(FinalProduct finalProduct) {
		this.finalProduct = finalProduct;
	}

	public Facility getSourceFacility() {
		return sourceFacility;
	}

	public void setSourceFacility(Facility sourceFacility) {
		this.sourceFacility = sourceFacility;
	}

	public Boolean getIsProcessing() {
		return isProcessing;
	}

	public void setIsProcessing(Boolean isProcessing) {
		this.isProcessing = isProcessing;
	}

	public TransactionStatus getStatus() {
		return status;
	}

	public void setStatus(TransactionStatus status) {
		this.status = status;
	}

	public Long getShipmentId() {
		return shipmentId;
	}

	public void setShipmentId(Long shipmentId) {
		this.shipmentId = shipmentId;
	}

	public MeasureUnitType getInputMeasureUnitType() {
		return inputMeasureUnitType;
	}

	public void setInputMeasureUnitType(MeasureUnitType inputMeasureUnitType) {
		this.inputMeasureUnitType = inputMeasureUnitType;
	}

	public BigDecimal getInputQuantity() {
		return inputQuantity;
	}

	public void setInputQuantity(BigDecimal inputQuantity) {
		this.inputQuantity = inputQuantity;
	}

	public BigDecimal getOutputQuantity() {
		return outputQuantity;
	}

	public void setOutputQuantity(BigDecimal outputQuantity) {
		this.outputQuantity = outputQuantity;
	}

	public BigDecimal getPricePerUnit() {
		return pricePerUnit;
	}

	public void setPricePerUnit(BigDecimal pricePerUnit) {
		this.pricePerUnit = pricePerUnit;
	}

	public String getCurrency() {
		return currency;
	}

	public void setCurrency(String currency) {
		this.currency = currency;
	}

	public String getRejectComment() {
		return rejectComment;
	}

	public void setRejectComment(String rejectComment) {
		this.rejectComment = rejectComment;
	}
	
}
