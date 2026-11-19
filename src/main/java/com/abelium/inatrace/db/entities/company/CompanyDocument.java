package com.abelium.inatrace.db.entities.company;

import com.abelium.inatrace.api.types.Lengths;
import com.abelium.inatrace.db.base.BaseEntity;
import com.abelium.inatrace.db.entities.common.Document;
import com.abelium.inatrace.db.entities.product.ProductLabelCompanyDocument;
import com.abelium.inatrace.types.CompanyDocumentCategory;
import com.abelium.inatrace.types.CompanyDocumentType;
import jakarta.persistence.*;
import org.hibernate.Length;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.util.HashSet;
import java.util.Set;

@Entity
public class CompanyDocument extends BaseEntity {

	/**
	 * Company this document applies to
	 */
	@ManyToOne(fetch = FetchType.LAZY)
	private Company company;
	
	/**
	 * Company translation this document applies to
	 */
	@ManyToOne(fetch = FetchType.LAZY)
	private CompanyTranslation companyTranslation;
	
    /**
     * Document type (link, file)
     */
    @Enumerated(EnumType.STRING)
    @Column(length = Lengths.ENUM)
    private CompanyDocumentType type = CompanyDocumentType.LINK;	

    /**
     * Document category
     */
    @Enumerated(EnumType.STRING)
    @Column(length = Lengths.ENUM)
    private CompanyDocumentCategory category;	

	/**
	 * Name of this document
	 */
	@Column(length = Lengths.DEFAULT)
	private String name;
    
	/**
	 * Description of this document
	 */
	@Column(length = Lengths.DEFAULT)
	private String description;
	
	/**
	 * Quote (long text)
	 */
	@JdbcTypeCode(SqlTypes.LONG32VARCHAR)
	@Column(length = Length.LONG32)
	private String quote;

	/**
	 * Link
	 */
	@Column(length = Lengths.DEFAULT)
	private String link;	
	
	/**
	 * Document attached to the company
	 */
	@ManyToOne
	private Document document;

	@OneToMany(mappedBy = "companyDocumentId")
	@OnDelete(action = OnDeleteAction.CASCADE)
	private Set<ProductLabelCompanyDocument> productLabels = new HashSet<>();

	public Company getCompany() {
		return company;
	}

	public void setCompany(Company company) {
		this.company = company;
	}

	public CompanyTranslation getCompanyTranslation() {
		return companyTranslation;
	}

	public void setCompanyTranslation(CompanyTranslation companyTranslation) {
		this.companyTranslation = companyTranslation;
	}

	public CompanyDocumentType getType() {
		return type;
	}

	public void setType(CompanyDocumentType type) {
		this.type = type;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public String getQuote() {
		return quote;
	}

	public void setQuote(String quote) {
		this.quote = quote;
	}

	public CompanyDocumentCategory getCategory() {
		return category;
	}

	public void setCategory(CompanyDocumentCategory category) {
		this.category = category;
	}

	public String getDescription() {
		return description;
	}

	public void setDescription(String description) {
		this.description = description;
	}

	public String getLink() {
		return link;
	}

	public void setLink(String link) {
		this.link = link;
	}

	public Document getDocument() {
		return document;
	}

	public void setDocument(Document document) {
		this.document = document;
	}

	public Set<ProductLabelCompanyDocument> getProductLabels() {
		return productLabels;
	}

	public void setProductLabels(Set<ProductLabelCompanyDocument> productLabels) {
		this.productLabels = productLabels;
	}

}
