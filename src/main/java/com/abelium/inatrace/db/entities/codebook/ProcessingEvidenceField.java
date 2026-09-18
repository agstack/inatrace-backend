package com.abelium.inatrace.db.entities.codebook;

import com.abelium.inatrace.api.types.Lengths;
import com.abelium.inatrace.db.base.TimestampEntity;
import com.abelium.inatrace.db.entities.value_chain.ValueChainProcessingEvidenceField;
import com.abelium.inatrace.types.ProcessingEvidenceFieldType;
import jakarta.persistence.*;

import java.util.HashSet;
import java.util.Set;

@Entity
@Table(indexes = { @Index(columnList = "label") })
@NamedQueries({
	@NamedQuery(name = "ProcessingEvidenceField.getProcessingEvidenceFieldsForValueChainIds",
	            query = "SELECT DISTINCT vcpef.processingEvidenceField FROM ValueChainProcessingEvidenceField vcpef "
			            + "WHERE vcpef.valueChain.id IN :valueChainIds"),
	@NamedQuery(name = "ProcessingEvidenceField.countProcessingEvidenceFieldsForValueChainIds",
	            query = "SELECT COUNT(DISTINCT vcpef.processingEvidenceField) FROM ValueChainProcessingEvidenceField vcpef "
			            + "WHERE vcpef.valueChain.id IN :valueChainIds")
})
public class ProcessingEvidenceField extends TimestampEntity {

	@Version
	private Long entityVersion;

	@Column(nullable = false)
	private String fieldName;

	@Column
	private String label;
	
	@Enumerated(EnumType.STRING)
	@Column(length = Lengths.ENUM, nullable = false)
	private ProcessingEvidenceFieldType type = ProcessingEvidenceFieldType.STRING;

	@OneToMany(mappedBy = "processingEvidenceField", orphanRemoval = true, cascade = CascadeType.ALL)
	private Set<ValueChainProcessingEvidenceField> valueChains;

	@OneToMany(mappedBy = "processingEvidenceField", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
	private Set<ProcessingEvidenceFieldTranslation> translations = new HashSet<>();

	public String getFieldName() {
		return fieldName;
	}

	public void setFieldName(String fieldName) {
		this.fieldName = fieldName;
	}

	public String getLabel() {
		return label;
	}

	public void setLabel(String label) {
		this.label = label;
	}

	public ProcessingEvidenceFieldType getType() {
		return type;
	}

	public void setType(ProcessingEvidenceFieldType type) {
		this.type = type;
	}

	public Set<ValueChainProcessingEvidenceField> getValueChains() {
		return valueChains;
	}

	public void setValueChains(Set<ValueChainProcessingEvidenceField> valueChains) {
		this.valueChains = valueChains;
	}

	public Set<ProcessingEvidenceFieldTranslation> getTranslations() {
		if (translations == null) {
			translations = new HashSet<>();
		}
		return translations;
	}

}
