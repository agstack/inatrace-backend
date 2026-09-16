package com.abelium.inatrace.db.entities.codebook;

import com.abelium.inatrace.api.types.Lengths;
import com.abelium.inatrace.db.base.TimestampEntity;
import com.abelium.inatrace.db.enums.EvidenceType;
import jakarta.persistence.*;

import java.util.HashSet;
import java.util.Set;

/**
 * Codebook entity for processing evidences.
 *
 * @author Pece Adjievski, Sunesis d.o.o.
 */
@Entity
@NamedQueries({
		@NamedQuery(name = "ProcessingEvidenceType.getProcessingEvidenceTypesForValueChainIds",
		            query = "SELECT DISTINCT vcpet.processingEvidenceType FROM ValueChainProcEvidenceType vcpet WHERE vcpet.valueChain.id IN :valueChainIds"),
		@NamedQuery(name = "ProcessingEvidenceType.countProcessingEvidenceTypesForValueChainIds",
		            query = "SELECT COUNT(DISTINCT vcpet.processingEvidenceType) FROM ValueChainProcEvidenceType vcpet WHERE vcpet.valueChain.id IN :valueChainIds")
})
public class ProcessingEvidenceType extends TimestampEntity {

	/**
	 * Capitalized underscored string that defines the document requirement.
	 */
	@Column(nullable = false)
	private String code;

	/**
	 * Default english label.
	 */
	@Column(nullable = false)
	private String label;

	/**
	 * Type of evidence type. DOCUMENT is prescribed (date, type, document). FIELD is any other. Some others can be added.
	 */
	@Enumerated(EnumType.STRING)
	@Column(length = Lengths.ENUM)
	private EvidenceType type;

	/**
	 * Whether the evidence is of fairness type.
	 */
	@Column
	private Boolean fairness;

	/**
	 * Whether the evidence is of provenance type.
	 */
	@Column
	private Boolean provenance;

	/**
	 * Whether the evidence is of quality type.
	 */
	@Column
	private Boolean quality;

	@OneToMany(mappedBy = "processingEvidenceType", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
	private Set<ProcessingEvidenceTypeTranslation> translations;

	public String getCode() {
		return code;
	}

	public void setCode(String code) {
		this.code = code;
	}

	public String getLabel() {
		return label;
	}

	public void setLabel(String label) {
		this.label = label;
	}

	public EvidenceType getType() {
		return type;
	}

	public void setType(EvidenceType type) {
		this.type = type;
	}

	public Boolean getFairness() {
		return fairness;
	}

	public void setFairness(Boolean fairness) {
		this.fairness = fairness;
	}

	public Boolean getProvenance() {
		return provenance;
	}

	public void setProvenance(Boolean provenance) {
		this.provenance = provenance;
	}

	public Boolean getQuality() {
		return quality;
	}

	public void setQuality(Boolean quality) {
		this.quality = quality;
	}

	public Set<ProcessingEvidenceTypeTranslation> getTranslations() {
		if (translations == null) {
			translations = new HashSet<>();
		}
		return translations;
	}

}
