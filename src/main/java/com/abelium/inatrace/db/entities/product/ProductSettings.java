package com.abelium.inatrace.db.entities.product;

import com.abelium.inatrace.api.types.Lengths;
import com.abelium.inatrace.db.base.BaseEntity;
import com.abelium.inatrace.db.entities.common.Document;
import com.abelium.inatrace.types.Language;
import jakarta.persistence.*;
import org.hibernate.Length;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.HashMap;
import java.util.Map;

@Entity
public class ProductSettings extends BaseEntity {

	/**
	 * cost breakdown
	 */
	@Column
	private Boolean costBreakdown;
	
	/**
	 * pricing transparency
	 */
	@ElementCollection(fetch = FetchType.EAGER)
    @MapKeyColumn(name = "ptKey")
    @Column(name = "ptValue")	
    @CollectionTable
    private Map<String, Double> pricingTransparency = new HashMap<>();
	
	/**
	 * increase in income - document
	 */
	@ManyToOne
	private Document incomeIncreaseDocument;

	/**
	 * increase in income - description
	 */
	@JdbcTypeCode(SqlTypes.LONG32VARCHAR)
	@Column(length = Length.LONG32)
	private String incomeIncreaseDescription;
	
	/**
	 * default language
	 */
	@Enumerated(EnumType.STRING)
    @Column(nullable = false, length = Lengths.ENUM)
	private Language language = Language.EN;
	
	/**
	 * GDPR text
	 */
	@JdbcTypeCode(SqlTypes.LONG32VARCHAR)
	@Column(length = Length.LONG32)
	private String gdprText;
	
	/**
	 * Privacy policy text
	 */
	@JdbcTypeCode(SqlTypes.LONG32VARCHAR)
	@Column(length = Length.LONG32)
	private String privacyPolicyText;
	
	/**
	 * Terms of use text
	 */
	@JdbcTypeCode(SqlTypes.LONG32VARCHAR)
	@Column(length = Length.LONG32)
	private String termsOfUseText;

	public Boolean getCostBreakdown() {
		return costBreakdown;
	}

	public void setCostBreakdown(Boolean costBreakdown) {
		this.costBreakdown = costBreakdown;
	}

	public Map<String, Double> getPricingTransparency() {
		return pricingTransparency;
	}

	public void setPricingTransparency(Map<String, Double> pricingTransparency) {
		this.pricingTransparency = pricingTransparency;
	}

	public Document getIncomeIncreaseDocument() {
		return incomeIncreaseDocument;
	}

	public void setIncomeIncreaseDocument(Document incomeIncreaseDocument) {
		this.incomeIncreaseDocument = incomeIncreaseDocument;
	}

	public String getIncomeIncreaseDescription() {
		return incomeIncreaseDescription;
	}

	public void setIncomeIncreaseDescription(String incomeIncreaseDescription) {
		this.incomeIncreaseDescription = incomeIncreaseDescription;
	}

	public Language getLanguage() {
		return language;
	}

	public void setLanguage(Language language) {
		this.language = language;
	}

	public String getGdprText() {
		return gdprText;
	}

	public void setGdprText(String gdprText) {
		this.gdprText = gdprText;
	}

	public String getPrivacyPolicyText() {
		return privacyPolicyText;
	}

	public void setPrivacyPolicyText(String privacyPolicyText) {
		this.privacyPolicyText = privacyPolicyText;
	}

	public String getTermsOfUseText() {
		return termsOfUseText;
	}

	public void setTermsOfUseText(String termsOfUseText) {
		this.termsOfUseText = termsOfUseText;
	}

	public ProductSettings copy() {
		ProductSettings s = new ProductSettings();
		s.setCostBreakdown(getCostBreakdown());
		s.setPricingTransparency(new HashMap<>(getPricingTransparency()));
		s.setIncomeIncreaseDescription(getIncomeIncreaseDescription());
		s.setIncomeIncreaseDocument(getIncomeIncreaseDocument());
		s.setLanguage(getLanguage());
		s.setGdprText(getGdprText());
		s.setPrivacyPolicyText(getPrivacyPolicyText());
		s.setTermsOfUseText(getTermsOfUseText());
		return s;
	}
}
