package com.abelium.inatrace.components.value_chain.api;

import com.abelium.inatrace.api.ApiBaseEntity;
import com.abelium.inatrace.components.codebook.facility_type.api.ApiFacilityType;
import com.abelium.inatrace.components.codebook.measure_unit_type.api.ApiMeasureUnitType;
import com.abelium.inatrace.components.codebook.processing_evidence_type.api.ApiProcessingEvidenceType;
import com.abelium.inatrace.components.codebook.processingevidencefield.api.ApiProcessingEvidenceField;
import com.abelium.inatrace.components.codebook.semiproduct.api.ApiSemiProduct;
import com.abelium.inatrace.components.product.api.ApiProductType;
import com.abelium.inatrace.db.entities.value_chain.enums.ValueChainStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.validation.annotation.Validated;

import java.util.List;

/**
 * Value chain API model entity.
 *
 * @author Pece Adjievski, Sunesis d.o.o.
 */
@Validated
public class ApiValueChain extends ApiBaseEntity {

	@Schema(description = "name of the value chain")
	private String name;

	@Schema(description = "description of the value chain")
	private String description;

	@Schema(description = "value chain status")
	private ValueChainStatus valueChainStatus;

	@Schema(description = "List of supported facility types. Omit on update to preserve associations; send an empty list to clear them.")
	private List<ApiFacilityType> facilityTypes;

	@Schema(description = "List of supported measuring unit types. Omit on update to preserve associations; send an empty list to clear them.")
	private List<ApiMeasureUnitType> measureUnitTypes;

	@Schema(description = "List of supported processing evidence types. Omit on update to preserve associations; send an empty list to clear them.")
	private List<ApiProcessingEvidenceType> processingEvidenceTypes;

	@Schema(description = "List of supported processing evidence fields. Omit on update to preserve associations; send an empty list to clear them.")
	private List<ApiProcessingEvidenceField> processingEvidenceFields;

	@Schema(description = "List of supported semi-products. Omit on update to preserve associations; send an empty list to clear them.")
	private List<ApiSemiProduct> semiProducts;

	@Schema(description = "Selected product type")
	private ApiProductType productType;

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

	public List<ApiFacilityType> getFacilityTypes() {
		return facilityTypes;
	}

	public void setFacilityTypes(List<ApiFacilityType> facilityTypes) {
		this.facilityTypes = facilityTypes;
	}

	public List<ApiMeasureUnitType> getMeasureUnitTypes() {
		return measureUnitTypes;
	}

	public void setMeasureUnitTypes(List<ApiMeasureUnitType> measureUnitTypes) {
		this.measureUnitTypes = measureUnitTypes;
	}

	public List<ApiProcessingEvidenceType> getProcessingEvidenceTypes() {
		return processingEvidenceTypes;
	}

	public void setProcessingEvidenceTypes(List<ApiProcessingEvidenceType> processingEvidenceTypes) {
		this.processingEvidenceTypes = processingEvidenceTypes;
	}

	public List<ApiProcessingEvidenceField> getProcessingEvidenceFields() {
		return processingEvidenceFields;
	}

	public void setProcessingEvidenceFields(List<ApiProcessingEvidenceField> processingEvidenceFields) {
		this.processingEvidenceFields = processingEvidenceFields;
	}

	public List<ApiSemiProduct> getSemiProducts() {
		return semiProducts;
	}

	public void setSemiProducts(List<ApiSemiProduct> semiProducts) {
		this.semiProducts = semiProducts;
	}

	public ApiProductType getProductType() {
		return productType;
	}

	public void setProductType(ApiProductType productType) {
		this.productType = productType;
	}
}
