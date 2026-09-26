package com.abelium.inatrace.db.entities.stockorder;

import com.abelium.inatrace.api.types.Lengths;
import com.abelium.inatrace.db.base.TimestampEntity;
import com.abelium.inatrace.db.entities.codebook.MeasureUnitType;
import com.abelium.inatrace.db.entities.codebook.SemiProduct;
import com.abelium.inatrace.db.entities.common.User;
import com.abelium.inatrace.db.entities.common.UserCustomer;
import com.abelium.inatrace.db.entities.company.Company;
import com.abelium.inatrace.db.entities.company.CompanyCustomer;
import com.abelium.inatrace.db.entities.facility.Facility;
import com.abelium.inatrace.db.entities.payment.Payment;
import com.abelium.inatrace.db.entities.processingorder.ProcessingOrder;
import com.abelium.inatrace.db.entities.product.FinalProduct;
import com.abelium.inatrace.db.entities.productorder.ProductOrder;
import com.abelium.inatrace.db.entities.stockorder.enums.OrderType;
import com.abelium.inatrace.db.entities.stockorder.enums.PreferredWayOfPayment;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table
@NamedQueries({
	@NamedQuery(name = "StockOrder.getPurchaseOrderByIdAndType",
				query = "SELECT so FROM StockOrder so "
						+ "WHERE so.id = :stockOrderId "
						+ "AND so.orderType = 'PURCHASE_ORDER'"),
	@NamedQuery(name = "StockOrder.getStockOrdersByProcessingOrderId",
	            query = "SELECT so FROM StockOrder so "
			            + "WHERE so.processingOrder.id = :processingOrderId"),
	@NamedQuery(name = "StockOrder.getTopLevelStockOrdersForQrTag",
	            query = "SELECT so FROM StockOrder so WHERE so.qrCodeTag = :qrTag AND NOT EXISTS (SELECT t FROM Transaction t WHERE t.sourceStockOrder = so)")
})
public class StockOrder extends TimestampEntity {

	@Version
	private Long entityVersion;

	@ManyToOne(optional = false)
	private User createdBy;

	@ManyToOne
	private User updatedBy;

	@Column
	private String identifier;

	// Cooperative employee
	@Column
	private Long creatorId;

	// Farmer
	@ManyToOne
	private UserCustomer producerUserCustomer;

	// Farmer representative - collector
	@ManyToOne
	private UserCustomer representativeOfProducerUserCustomer;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "productionLocation_id", nullable = true)
	private StockOrderLocation productionLocation;

	// The company customer for which the stock order was placed
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "consumerCompanyCustomer_id", nullable = true)
	private CompanyCustomer consumerCompanyCustomer;

	// Set when this stock order represent a unit of quantity for a semi-product (used in processing)
	@ManyToOne
	private SemiProduct semiProduct;

	@Column
	private Boolean priceDeterminedLater;

	// Set when this stock order represents a uint of quantity for a final product (used in final processing)
	@ManyToOne
	private FinalProduct finalProduct;
	
	@ManyToOne
	private Facility facility;

	// The facility that is quoted for total quantity of the provided semi-product
	@ManyToOne
	private Facility quoteFacility;

	// The company of the quoted facility (this should be set automatically from the facility company)
	@ManyToOne
	private Company quoteCompany;

	@OneToMany(mappedBy = "stockOrder", cascade = CascadeType.ALL, orphanRemoval = true)
	private Set<Certification> certifications;

	// The required processing evidence fields values - the available values are sourced from the
	// selected Processing action definition;
	@OneToMany(mappedBy = "stockOrder", cascade = CascadeType.ALL, orphanRemoval = true)
	private Set<StockOrderPEFieldValue> processingEFValues;

	// The required processing evidence documents - the available values are sourced from the
	// selected Processing action definition;
	@OneToMany(mappedBy = "stockOrder", cascade = CascadeType.ALL, orphanRemoval = true)
	private Set<StockOrderPETypeValue> documentRequirements;

	// Activity proofs that were provided while creating or updating a purchase order
	@OneToMany(mappedBy = "stockOrder", cascade = CascadeType.ALL, orphanRemoval = true)
	private Set<StockOrderActivityProof> activityProofs;

	// A stock (purchase) order can be divided in many payments
	@OneToMany(mappedBy = "stockOrder", cascade = CascadeType.ALL, orphanRemoval = true)
	private Set<Payment> payments = new HashSet<>();

	@ManyToOne
	private Company company;
	
	@ManyToOne
	private MeasureUnitType measurementUnitType;

	// The product order that created this stock order (this stock order represents final product item with the requested quantity)
	@ManyToOne
	private ProductOrder productOrder;

	/**
	 * User entered Order ID - relevant for Quote orders.
	 * Can be set only when productOrder is not provided. If productOrder is set, then
	 * the user entered Order ID in the ProductOrder should be used.
 	 */
	@Column
	private String orderId;

	// Contains net quantity
	@Column
	private BigDecimal totalQuantity;

	// contains gross quantity
	@Column
	private BigDecimal totalGrossQuantity;
	
	@Column
	private BigDecimal fulfilledQuantity;

	@Column
	private BigDecimal availableQuantity;

	/**
	 * This should be saved as dated, without any time, so we don't have issues with different client timezones.
	 */
	@Column
	private LocalDate productionDate;

	/**
	 * This should be saved as dated, without any time, so we don't have issues with different client timezones.
	 */
	@Column
	private LocalDate deliveryTime;
	
	@Column
	private BigDecimal pricePerUnit;
	
	@Column
	private String currency;
	
	@Column
	private Boolean isPurchaseOrder;
	
	@Enumerated(EnumType.STRING)
	@Column(length = Lengths.ENUM)
	private OrderType orderType;

	@Column
	private String lotPrefix;

	@Column
	private String internalLotNumber;

	@Column
	private String comments;
	
	@Column
	private Boolean womenShare;

	@Column
	private Boolean requiredWomensCoffee;

	@Column
	private Boolean organic;

	@Column
	private BigDecimal cost;
	
	@Column
	private BigDecimal paid;

	@Column
	private BigDecimal balance;

	@Column
	private BigDecimal tare;

	@Column
	private BigDecimal damagedPriceDeduction;

	@Column
	private BigDecimal damagedWeightDeduction;

	@ManyToOne(fetch = FetchType.LAZY)
	private ProcessingOrder processingOrder;
	
	@Enumerated(EnumType.STRING)
	@Column(length = Lengths.ENUM)
	private PreferredWayOfPayment preferredWayOfPayment;
	
	@Column
	private Integer sacNumber;

	// Used in case where we have Quote order - the quote order is open when total quantity > fulfilled quantity
	@Column
	private Boolean isOpenOrder;

	// Used in processing order with action PROCESSING, FINAL_PROCESSING AND TRANSFER to denote that
	// the stock unit represented by this stock order is available in the facility
	@Column
	private Boolean isAvailable;

	/**
	 * This field denotes that the total quantity (output quantity) of this stock order is not in the expected range.
	 * This is relevant when this stock order was created from processing action type 'PROCESSING' without repacking.
	 * If the processing action has set expected output quantity per unit, then this field gets calculated when creating
	 * or updating the stock order.
	 */
	@Column
	private Boolean outQuantityNotInRange;

	// The price per unit specified in the final product order (global order)
	@Column
    private BigDecimal pricePerUnitForEndCustomer;

	// The currency for the price per unit in the final product order (global order)
	@Column
	private String currencyForEndCustomer;

	// Generated UUID tag to be used in the QR code for this stock order (all new stock orders created from this should inherit this tag)
	@Column
	private String qrCodeTag;

	// The final product for which the QR code tag is generated
	@ManyToOne(fetch = FetchType.LAZY)
	private FinalProduct qrCodeTagFinalProduct;

	// Generated ID provided by the client; This is used to group repacked stock orders when doing repacking with multiple outputs
	@Column
	private String repackedOriginStockOrderId;

	public User getCreatedBy() {
		return createdBy;
	}

	public void setCreatedBy(User createdBy) {
		this.createdBy = createdBy;
	}

	public User getUpdatedBy() {
		return updatedBy;
	}

	public void setUpdatedBy(User updatedBy) {
		this.updatedBy = updatedBy;
	}

	public String getIdentifier() {
		return identifier;
	}

	public void setIdentifier(String identifier) {
		this.identifier = identifier;
	}

	public Long getCreatorId() {
		return creatorId;
	}

	public void setCreatorId(Long creatorId) {
		this.creatorId = creatorId;
	}

	public UserCustomer getRepresentativeOfProducerUserCustomer() {
		return representativeOfProducerUserCustomer;
	}

	public void setRepresentativeOfProducerUserCustomer(UserCustomer representativeOfProducerCustomer) {
		this.representativeOfProducerUserCustomer = representativeOfProducerCustomer;
	}

	public UserCustomer getProducerUserCustomer() {
		return producerUserCustomer;
	}

	public void setProducerUserCustomer(UserCustomer producerUserCustomer) {
		this.producerUserCustomer = producerUserCustomer;
	}

	public Set<Certification> getCertifications() {
		if (certifications == null)
			certifications = new HashSet<>();
		return certifications;
	}

	public void setCertifications(Set<Certification> certifications) {
		this.certifications = certifications;
	}

	public CompanyCustomer getConsumerCompanyCustomer() {
		return consumerCompanyCustomer;
	}

	public void setConsumerCompanyCustomer(CompanyCustomer consumerCompanyCustomer) {
		this.consumerCompanyCustomer = consumerCompanyCustomer;
	}

	public SemiProduct getSemiProduct() {
		return semiProduct;
	}

	public void setSemiProduct(SemiProduct semiProduct) {
		this.semiProduct = semiProduct;
	}

	public Boolean getPriceDeterminedLater() {
		return priceDeterminedLater;
	}

	public void setPriceDeterminedLater(Boolean priceDeterminedLater) {
		this.priceDeterminedLater = priceDeterminedLater;
	}

	public FinalProduct getFinalProduct() {
		return finalProduct;
	}

	public void setFinalProduct(FinalProduct finalProduct) {
		this.finalProduct = finalProduct;
	}

	public Facility getFacility() {
		return facility;
	}

	public void setFacility(Facility facility) {
		this.facility = facility;
	}

	public Company getCompany() {
		return company;
	}

	public void setCompany(Company company) {
		this.company = company;
	}

	public ProductOrder getProductOrder() {
		return productOrder;
	}

	public void setProductOrder(ProductOrder productOrder) {
		this.productOrder = productOrder;
	}

	public String getOrderId() {
		return orderId;
	}

	public void setOrderId(String orderId) {
		this.orderId = orderId;
	}

	public MeasureUnitType getMeasurementUnitType() {
		return measurementUnitType;
	}

	public void setMeasurementUnitType(MeasureUnitType measurementUnitType) {
		this.measurementUnitType = measurementUnitType;
	}

	public BigDecimal getTotalQuantity() {
		return totalQuantity;
	}

	public void setTotalQuantity(BigDecimal totalQuantity) {
		this.totalQuantity = totalQuantity;
	}

	public BigDecimal getTotalGrossQuantity() {
		return totalGrossQuantity;
	}

	public void setTotalGrossQuantity(BigDecimal totalGrossQuantity) {
		this.totalGrossQuantity = totalGrossQuantity;
	}

	public BigDecimal getFulfilledQuantity() {
		return fulfilledQuantity;
	}

	public void setFulfilledQuantity(BigDecimal fulfilledQuantity) {
		this.fulfilledQuantity = fulfilledQuantity;
	}

	public BigDecimal getAvailableQuantity() {
		return availableQuantity;
	}

	public void setAvailableQuantity(BigDecimal availableQuantity) {
		this.availableQuantity = availableQuantity;
	}

	public Boolean getAvailable() {
		return isAvailable;
	}

	public void setAvailable(Boolean available) {
		isAvailable = available;
	}

	public Boolean getOutQuantityNotInRange() {
		return outQuantityNotInRange;
	}

	public void setOutQuantityNotInRange(Boolean outQuantityNotInRange) {
		this.outQuantityNotInRange = outQuantityNotInRange;
	}

	public LocalDate getProductionDate() {
		return productionDate;
	}

	public void setProductionDate(LocalDate productionDate) {
		this.productionDate = productionDate;
	}

	public LocalDate getDeliveryTime() {
		return deliveryTime;
	}

	public void setDeliveryTime(LocalDate deliveryTime) {
		this.deliveryTime = deliveryTime;
	}

	public Set<StockOrderPEFieldValue> getProcessingEFValues() {
		if (processingEFValues == null) {
			processingEFValues = new HashSet<>();
		}
		return processingEFValues;
	}

	public Set<StockOrderPETypeValue> getDocumentRequirements() {
		if (documentRequirements == null) {
			documentRequirements = new HashSet<>();
		}
		return documentRequirements;
	}

	public Set<StockOrderActivityProof> getActivityProofs() {
		if (activityProofs == null) {
			activityProofs = new HashSet<>();
		}
		return activityProofs;
	}

	public BigDecimal getPricePerUnit() {
		return pricePerUnit;
	}

	public void setPricePerUnit(BigDecimal pricePerUnit) {
		this.pricePerUnit = pricePerUnit;
	}

	public Set<Payment> getPayments() {
		return payments;
	}

	public void setPayments(Set<Payment> payments) {
		this.payments = payments;
	}

	public String getCurrency() {
		return currency;
	}

	public void setCurrency(String currency) {
		this.currency = currency;
	}

	public Boolean getPurchaseOrder() {
		return isPurchaseOrder;
	}

	public void setPurchaseOrder(Boolean purchaseOrder) {
		isPurchaseOrder = purchaseOrder;
	}

	public OrderType getOrderType() {
		return orderType;
	}

	public void setOrderType(OrderType orderType) {
		this.orderType = orderType;
	}

	public String getLotPrefix() {
		return lotPrefix;
	}

	public void setLotPrefix(String lotPrefix) {
		this.lotPrefix = lotPrefix;
	}

	public String getInternalLotNumber() {
		return internalLotNumber;
	}

	public void setInternalLotNumber(String internalLotNumber) {
		this.internalLotNumber = internalLotNumber;
	}

	public String getComments() {
		return comments;
	}

	public void setComments(String comments) {
		this.comments = comments;
	}

	public Boolean getWomenShare() {
		return womenShare;
	}

	public void setWomenShare(Boolean womenShare) {
		this.womenShare = womenShare;
	}

	public BigDecimal getCost() {
		return cost;
	}

	public void setCost(BigDecimal cost) {
		this.cost = cost;
	}

	public BigDecimal getPaid() {
		return paid;
	}

	public void setPaid(BigDecimal paid) {
		this.paid = paid;
	}

	public BigDecimal getBalance() {
		return balance;
	}

	public void setBalance(BigDecimal balance) {
		this.balance = balance;
	}

	public ProcessingOrder getProcessingOrder() {
		return processingOrder;
	}

	public void setProcessingOrder(ProcessingOrder processingOrder) {
		this.processingOrder = processingOrder;
	}

	public PreferredWayOfPayment getPreferredWayOfPayment() {
		return preferredWayOfPayment;
	}

	public void setPreferredWayOfPayment(PreferredWayOfPayment preferredWayOfPayment) {
		this.preferredWayOfPayment = preferredWayOfPayment;
	}

	public Integer getSacNumber() {
		return sacNumber;
	}

	public void setSacNumber(Integer sacNumber) {
		this.sacNumber = sacNumber;
	}

	public Boolean getIsOpenOrder() {
		return isOpenOrder;
	}

	public void setIsOpenOrder(Boolean isOpenOrder) {
		this.isOpenOrder = isOpenOrder;
	}

	public Facility getQuoteFacility() {
		return quoteFacility;
	}

	public void setQuoteFacility(Facility quoteFacility) {
		this.quoteFacility = quoteFacility;
	}

	public Company getQuoteCompany() {
		return quoteCompany;
	}

	public void setQuoteCompany(Company quoteCompany) {
		this.quoteCompany = quoteCompany;
	}

	public BigDecimal getPricePerUnitForEndCustomer() {
		return pricePerUnitForEndCustomer;
	}

	public void setPricePerUnitForEndCustomer(BigDecimal pricePerUnitForEndCustomer) {
		this.pricePerUnitForEndCustomer = pricePerUnitForEndCustomer;
	}

	public String getCurrencyForEndCustomer() {
		return currencyForEndCustomer;
	}

	public void setCurrencyForEndCustomer(String currencyForEndCustomer) {
		this.currencyForEndCustomer = currencyForEndCustomer;
	}

	public String getQrCodeTag() {
		return qrCodeTag;
	}

	public void setQrCodeTag(String qrCodeTag) {
		this.qrCodeTag = qrCodeTag;
	}

	public FinalProduct getQrCodeTagFinalProduct() {
		return qrCodeTagFinalProduct;
	}

	public void setQrCodeTagFinalProduct(FinalProduct qrCodeTagFinalProduct) {
		this.qrCodeTagFinalProduct = qrCodeTagFinalProduct;
	}

	public String getRepackedOriginStockOrderId() {
		return repackedOriginStockOrderId;
	}

	public void setRepackedOriginStockOrderId(String repackedOriginStockOrder) {
		this.repackedOriginStockOrderId = repackedOriginStockOrder;
	}

	public StockOrderLocation getProductionLocation() {
		return productionLocation;
	}

	public void setProductionLocation(StockOrderLocation productionLocation) {
		this.productionLocation = productionLocation;
	}

	public Boolean getRequiredWomensCoffee() {
		return requiredWomensCoffee;
	}

	public void setRequiredWomensCoffee(Boolean requiredWomensCoffee) {
		this.requiredWomensCoffee = requiredWomensCoffee;
	}

	public Boolean getOrganic() {
		return organic;
	}

	public void setOrganic(Boolean organic) {
		this.organic = organic;
	}

	public BigDecimal getTare() {
		return tare;
	}

	public void setTare(BigDecimal tare) {
		this.tare = tare;
	}

	public BigDecimal getDamagedPriceDeduction() {
		return damagedPriceDeduction;
	}

	public void setDamagedPriceDeduction(BigDecimal damagedPriceDeduction) {
		this.damagedPriceDeduction = damagedPriceDeduction;
	}

	public BigDecimal getDamagedWeightDeduction() {
		return damagedWeightDeduction;
	}

	public void setDamagedWeightDeduction(BigDecimal damagedWeightDeduction) {
		this.damagedWeightDeduction = damagedWeightDeduction;
	}
}
