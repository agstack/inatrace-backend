package com.abelium.inatrace.db.entities.product;

import com.abelium.inatrace.api.types.Lengths;
import com.abelium.inatrace.db.entities.company.Company;
import com.abelium.inatrace.db.entities.value_chain.ValueChain;
import com.abelium.inatrace.types.ProductStatus;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;

import java.util.HashSet;
import java.util.Set;

@Entity
@Table(indexes = { @Index(columnList = "name") })
@NamedQueries({
		@NamedQuery(name = "Product.countCompanyCreatedProducts",
				    query = "SELECT COUNT(p) FROM Product p WHERE p.company.id = :companyId")
})
public class Product extends ProductContent {
	
	@Version
	private long entityVersion;
	
	/**
	 * product status
	 */
	@Enumerated(EnumType.STRING)
    @Column(nullable = false, length = Lengths.ENUM)
    private ProductStatus status = ProductStatus.DISABLED;
	
	/**
	 * origin - farmer location - Use the pins on the map to mark locations of farmers or suppliers and 
	 * indicate the number of farmers or suppliers you work with directly or indirectly at each location. 
	 */
    @OneToMany(mappedBy = "product", cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<ProductLocation> originLocations = new HashSet<>();
	
	/**
	 * company - owner
	 */
	@NotNull
	@ManyToOne(fetch = FetchType.LAZY)
	private Company company;

	/**
	 * value chain
	 */
	@NotNull
	@ManyToOne(fetch = FetchType.LAZY)
	private ValueChain valueChain;

	/**
	 * a list of other companies associated with this product (buyers, producers, ...) 
	 */
    @OneToMany(mappedBy = "product", cascade = CascadeType.ALL, orphanRemoval = true)
	private Set<ProductCompany> associatedCompanies = new HashSet<>();
    
	/**
	 * labels
	 */
	@OneToMany(fetch = FetchType.LAZY, mappedBy = "product")
	private Set<ProductLabel> labels = new HashSet<>();

	/**
	 * Data sharing agreements between the stakeholders on this product (value chain)
	 */
	@OneToMany(mappedBy = "product", cascade = CascadeType.ALL, orphanRemoval = true)
	private Set<ProductDataSharingAgreement> dataSharingAgreements;

	public Set<ProductLocation> getOriginLocations() {
		return originLocations;
	}

	public void setOriginLocations(Set<ProductLocation> originLocations) {
		this.originLocations = originLocations;
	}

	public Company getCompany() {
		return company;
	}

	public void setCompany(Company company) {
		this.company = company;
	}

	public ValueChain getValueChain() {
		return valueChain;
	}

	public void setValueChain(ValueChain valueChain) {
		this.valueChain = valueChain;
	}

	public Set<ProductCompany> getAssociatedCompanies() {
		return associatedCompanies;
	}

	public void setAssociatedCompanies(Set<ProductCompany> associatedCompanies) {
		this.associatedCompanies = associatedCompanies;
	}

	public ProductStatus getStatus() {
		return status;
	}

	public void setStatus(ProductStatus status) {
		this.status = status;
	}

	public Set<ProductLabel> getLabels() {
		return labels;
	}

	public void setLabels(Set<ProductLabel> labels) {
		this.labels = labels;
	}

	public Set<ProductDataSharingAgreement> getDataSharingAgreements() {
		if (dataSharingAgreements == null) {
			dataSharingAgreements = new HashSet<>();
		}
		return dataSharingAgreements;
	}

	public void setDataSharingAgreements(Set<ProductDataSharingAgreement> dataSharingAgreements) {
		this.dataSharingAgreements = dataSharingAgreements;
	}

}
