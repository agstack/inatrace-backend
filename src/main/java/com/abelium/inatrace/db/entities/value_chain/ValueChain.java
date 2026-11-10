package com.abelium.inatrace.db.entities.value_chain;

import com.abelium.inatrace.api.types.Lengths;
import com.abelium.inatrace.db.base.TimestampEntity;
import com.abelium.inatrace.db.entities.codebook.ProductType;
import com.abelium.inatrace.db.entities.common.User;
import com.abelium.inatrace.db.entities.value_chain.enums.ValueChainStatus;
import jakarta.persistence.*;
import org.hibernate.Length;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.HashSet;
import java.util.Set;

/**
 * Value chain entity representing definition of generic value chains. This is used as a template when creating products.
 * It contains the relations with the supported facility types, measure unit types, processing
 * evidence types and semi products.
 *
 * @author Pece Adjievski, Sunesis d.o.o.
 */
@Entity
public class ValueChain extends TimestampEntity {

	@Column(nullable = false)
	private String name;

	@JdbcTypeCode(SqlTypes.LONG32VARCHAR)
	@Column(nullable = false, length = Length.LONG32)
	private String description;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = Lengths.ENUM)
	private ValueChainStatus valueChainStatus;

	/**
	 * Holds a list of supported facility types ba this value chain.
	 */
	@OneToMany(mappedBy = "valueChain", orphanRemoval = true, cascade = CascadeType.ALL)
	private Set<ValueChainFacilityType> facilityTypes;

	/**
	 * Holds a list of supported measuring unit types by this value chain.
	 */
	@OneToMany(mappedBy = "valueChain", orphanRemoval = true, cascade = CascadeType.ALL)
	private Set<ValueChainMeasureUnitType> measureUnitTypes;

	/**
	 * Holds a list of supported processing evidence by this value chain.
	 */
	@OneToMany(mappedBy = "valueChain", orphanRemoval = true, cascade = CascadeType.ALL)
	private Set<ValueChainProcEvidenceType> procEvidenceTypes;

	/**
	 * Holds a list of supported semi-products by this value chain.
	 */
	@OneToMany(mappedBy = "valueChain", orphanRemoval = true, cascade = CascadeType.ALL)
	private Set<ValueChainSemiProduct> semiProducts;
	
	/**
	 * Holds a list of supported processing evidence fields by this value chain.
	 */
	@OneToMany(mappedBy = "valueChain", orphanRemoval = true, cascade = CascadeType.ALL)
	private Set<ValueChainProcessingEvidenceField> processingEvidenceFields;

	@ManyToOne(optional = false, fetch = FetchType.LAZY)
	private User createdBy;

	@ManyToOne(fetch = FetchType.LAZY)
	private User updatedBy;

	/**
	 * Reference to the product type that this Value chain is based on.
	 */
	@ManyToOne(optional = false, fetch = FetchType.LAZY)
	private ProductType productType;

	@Version
	private long entityVersion;

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public String getDescription() {
		return description;
	}

	public void setDescription(String description) {
		this.description = description;
	}

	public ValueChainStatus getValueChainStatus() {
		return valueChainStatus;
	}

	public void setValueChainStatus(ValueChainStatus valueChainStatus) {
		this.valueChainStatus = valueChainStatus;
	}

	public Set<ValueChainFacilityType> getFacilityTypes() {
		if (facilityTypes == null) {
			facilityTypes = new HashSet<>();
		}
		return facilityTypes;
	}

	public void setFacilityTypes(Set<ValueChainFacilityType> facilityTypes) {
		this.facilityTypes = facilityTypes;
	}

	public Set<ValueChainMeasureUnitType> getMeasureUnitTypes() {
		if (measureUnitTypes == null) {
			measureUnitTypes = new HashSet<>();
		}
		return measureUnitTypes;
	}

	public void setMeasureUnitTypes(Set<ValueChainMeasureUnitType> measureUnitTypes) {
		this.measureUnitTypes = measureUnitTypes;
	}

	public Set<ValueChainSemiProduct> getSemiProducts() {
		if (semiProducts == null) {
			semiProducts = new HashSet<>();
		}
		return semiProducts;
	}

	public void setSemiProducts(Set<ValueChainSemiProduct> semiProducts) {
		this.semiProducts = semiProducts;
	}
	
	public Set<ValueChainProcessingEvidenceField> getProcessingEvidenceFields() {
		if (processingEvidenceFields == null) {
			processingEvidenceFields = new HashSet<>();
		}
		return processingEvidenceFields;
	}

	public void setProcessingEvidenceFields(Set<ValueChainProcessingEvidenceField> processingEvidenceFields) {
		this.processingEvidenceFields = processingEvidenceFields;
	}

	public Set<ValueChainProcEvidenceType> getProcEvidenceTypes() {
		if (procEvidenceTypes == null) {
			procEvidenceTypes = new HashSet<>();
		}
		return procEvidenceTypes;
	}

	public void setProcEvidenceTypes(Set<ValueChainProcEvidenceType> procEvidenceTypes) {
		this.procEvidenceTypes = procEvidenceTypes;
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

	public ProductType getProductType() {
		return productType;
	}

	public void setProductType(ProductType productType) {
		this.productType = productType;
	}

	public long getEntityVersion() {
		return entityVersion;
	}

	public void setEntityVersion(long entityVersion) {
		this.entityVersion = entityVersion;
	}

}
