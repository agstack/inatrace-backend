package com.abelium.inatrace.db.entities.payment;

import com.abelium.inatrace.api.types.Lengths;
import com.abelium.inatrace.db.base.TimestampEntity;
import com.abelium.inatrace.db.entities.common.Document;
import com.abelium.inatrace.db.entities.common.User;
import com.abelium.inatrace.db.entities.common.UserCustomer;
import com.abelium.inatrace.db.entities.company.Company;
import com.abelium.inatrace.db.entities.stockorder.StockOrder;
import com.abelium.inatrace.db.entities.stockorder.enums.PreferredWayOfPayment;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table
@NamedQueries({
	@NamedQuery(name = "Payment.listPaymentsByPurchaseId", 
				query = "SELECT p FROM Payment p "
						+ "INNER JOIN p.stockOrder so "
						+ "WHERE so.id = :purchaseId"),
	@NamedQuery(name = "Payment.countPaymentsByPurchaseId",
	            query = "SELECT COUNT(p) FROM Payment p "
	            		+ "INNER JOIN p.stockOrder so "
						+ "WHERE so.id = :purchaseId"),
	@NamedQuery(name = "Payment.listPaymentsByCompanyId", 
				query = "SELECT p FROM Payment p "
						+ "INNER JOIN p.stockOrder so "
						+ "INNER JOIN so.company c "
						+ "WHERE c.id = :companyId"),
	@NamedQuery(name = "Payment.countPaymentsByCompanyId",
    			query = "SELECT COUNT(p) FROM Payment p "
    					+ "INNER JOIN p.stockOrder so "
						+ "INNER JOIN so.company c "
						+ "WHERE c.id = :companyId")
})
public class Payment extends TimestampEntity {

	@Version
	private Long entityVersion;
	
	@Column
	private String orderReference; // stock order identifier

	@ManyToOne(optional = false)
	private User createdBy; // logged-in user
	
	@ManyToOne
	private User updatedBy;
	
	@Enumerated(EnumType.STRING)
	@Column(length = Lengths.ENUM)
	private PaymentType paymentType;

	@Column
	private String currency;
	
	@Column
	private BigDecimal purchased; // stock order quantity

	// The amount paid in the specified currency (paid to farmer of paid to a recipient company for Quote order)
	@Column
	private BigDecimal amount;
	
	@Column
	private BigDecimal amountPaidToTheCollector; // set to 0 by default
	
	@Column
	private BigDecimal totalPaid;
	
	@ManyToOne
	private StockOrder stockOrder; // stock order to which the payment(s) belong to
	
	@ManyToOne
	private Company payingCompany; // company who is paying - logged-in user
	
	@ManyToOne
	private Company recipientCompany; // the company that is receiving the payment (payment for quote orders)
	
	@ManyToOne
	private UserCustomer recipientUserCustomer; // farmer
	
	@ManyToOne
	private UserCustomer representativeOfRecipientUserCustomer; // collector
	
	@Enumerated(EnumType.STRING)
	@Column(length = Lengths.ENUM)
	private RecipientType recipientType;
	
	@Column
	private String receiptNumber; // defined by user

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "receiptDocument_id", nullable = true)
	private Document receiptDocument; // document info
	
	@Enumerated(EnumType.STRING)
	@Column(length = Lengths.ENUM)
	private ReceiptDocumentType receiptDocumentType; // purchase sheet, receipt
	
	@ManyToOne(fetch = FetchType.LAZY)
	private BulkPayment bulkPayment;
	
	@Enumerated(EnumType.STRING)
	@Column(length = Lengths.ENUM)
	private PaymentPurposeType paymentPurposeType; // advanced, cherry, member bonus, af women premium, invoice payment
	
	@Enumerated(EnumType.STRING)
	@Column(length = Lengths.ENUM)
	private PaymentStatus paymentStatus; // unconfirmed, confirmed
	
	@ManyToOne
	private User paymentConfirmedByUser; // user logged-in
	
	@ManyToOne
	private Company paymentConfirmedByCompany; // user's company who's logged-in
    
    @Column
    private Instant paymentConfirmedAtTime;

    @Column
	private LocalDate formalCreationTime;

	@Enumerated(EnumType.STRING)
	@Column(length = Lengths.ENUM)
	private PreferredWayOfPayment preferredWayOfPayment;
	
	@Column
    private LocalDate productionDate;
	
	public String getOrderReference() {
		return orderReference;
	}

	public void setOrderReference(String orderReference) {
		this.orderReference = orderReference;
	}

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

	public PaymentType getPaymentType() {
		return paymentType;
	}

	public void setPaymentType(PaymentType paymentType) {
		this.paymentType = paymentType;
	}

	public String getCurrency() {
		return currency;
	}

	public void setCurrency(String currency) {
		this.currency = currency;
	}

	public BigDecimal getAmount() {
		return amount;
	}

	public void setAmount(BigDecimal amount) {
		this.amount = amount;
	}

	public BigDecimal getAmountPaidToTheCollector() {
		return amountPaidToTheCollector;
	}

	public void setAmountPaidToTheCollector(BigDecimal amountPaidToTheCollector) {
		this.amountPaidToTheCollector = amountPaidToTheCollector;
	}
	
	public BigDecimal getPurchased() {
		return purchased;
	}

	public void setPurchased(BigDecimal purchased) {
		this.purchased = purchased;
	}

	public BigDecimal getTotalPaid() {
		return totalPaid;
	}

	public void setTotalPaid(BigDecimal totalPaid) {
		this.totalPaid = totalPaid;
	}

	public StockOrder getStockOrder() {
		return stockOrder;
	}

	public void setStockOrder(StockOrder stockOrder) {
		this.stockOrder = stockOrder;
	}

	public Company getPayingCompany() {
		return payingCompany;
	}

	public void setPayingCompany(Company payingCompany) {
		this.payingCompany = payingCompany;
	}

	public Company getRecipientCompany() {
		return recipientCompany;
	}

	public void setRecipientCompany(Company recipientCompany) {
		this.recipientCompany = recipientCompany;
	}

	public UserCustomer getRecipientUserCustomer() {
		return recipientUserCustomer;
	}

	public void setRecipientUserCustomer(UserCustomer recipientUserCustomer) {
		this.recipientUserCustomer = recipientUserCustomer;
	}

	public UserCustomer getRepresentativeOfRecipientUserCustomer() {
		return representativeOfRecipientUserCustomer;
	}

	public void setRepresentativeOfRecipientUserCustomer(UserCustomer representativeOfRecipientUserCustomer) {
		this.representativeOfRecipientUserCustomer = representativeOfRecipientUserCustomer;
	}

	public RecipientType getRecipientType() {
		return recipientType;
	}

	public void setRecipientType(RecipientType recipientType) {
		this.recipientType = recipientType;
	}

	public String getReceiptNumber() {
		return receiptNumber;
	}

	public void setReceiptNumber(String receiptNumber) {
		this.receiptNumber = receiptNumber;
	}

	public Document getReceiptDocument() {
		return receiptDocument;
	}

	public void setReceiptDocument(Document receiptDocument) {
		this.receiptDocument = receiptDocument;
	}

	public ReceiptDocumentType getReceiptDocumentType() {
		return receiptDocumentType;
	}

	public void setReceiptDocumentType(ReceiptDocumentType receiptDocumentType) {
		this.receiptDocumentType = receiptDocumentType;
	}

	public Long getEntityVersion() {
		return entityVersion;
	}

	public void setEntityVersion(Long entityVersion) {
		this.entityVersion = entityVersion;
	}

	public BulkPayment getBulkPayment() {
		return bulkPayment;
	}

	public void setBulkPayment(BulkPayment bulkPayment) {
		this.bulkPayment = bulkPayment;
	}

	public PaymentPurposeType getPaymentPurposeType() {
		return paymentPurposeType;
	}

	public void setPaymentPurposeType(PaymentPurposeType paymentPurporseType) {
		this.paymentPurposeType = paymentPurporseType;
	}

	public PaymentStatus getPaymentStatus() {
		return paymentStatus;
	}

	public void setPaymentStatus(PaymentStatus paymentStatus) {
		this.paymentStatus = paymentStatus;
	}

	public User getPaymentConfirmedByUser() {
		return paymentConfirmedByUser;
	}

	public void setPaymentConfirmedByUser(User paymentConfirmedByUser) {
		this.paymentConfirmedByUser = paymentConfirmedByUser;
	}

	public Company getPaymentConfirmedByCompany() {
		return paymentConfirmedByCompany;
	}

	public void setPaymentConfirmedByCompany(Company paymentConfirmedByCompany) {
		this.paymentConfirmedByCompany = paymentConfirmedByCompany;
	}

	public Instant getPaymentConfirmedAtTime() {
		return paymentConfirmedAtTime;
	}

	public void setPaymentConfirmedAtTime(Instant paymentConfirmedAtTime) {
		this.paymentConfirmedAtTime = paymentConfirmedAtTime;
	}

	public LocalDate getFormalCreationTime() {
		return formalCreationTime;
	}

	public void setFormalCreationTime(LocalDate formalCreationTime) {
		this.formalCreationTime = formalCreationTime;
	}

	public PreferredWayOfPayment getPreferredWayOfPayment() {
		return preferredWayOfPayment;
	}

	public void setPreferredWayOfPayment(PreferredWayOfPayment preferredWayOfPayment) {
		this.preferredWayOfPayment = preferredWayOfPayment;
	}

	public LocalDate getProductionDate() {
		return productionDate;
	}

	public void setProductionDate(LocalDate productionDate) {
		this.productionDate = productionDate;
	}
	
}
