package com.abelium.inatrace.components.value_chain;

import com.abelium.inatrace.components.codebook.facility_type.FacilityTypeMapper;
import com.abelium.inatrace.components.codebook.measure_unit_type.MeasureUnitTypeMapper;
import com.abelium.inatrace.components.codebook.processing_evidence_type.ProcessingEvidenceTypeMapper;
import com.abelium.inatrace.components.codebook.processingevidencefield.ProcessingEvidenceFieldMapper;
import com.abelium.inatrace.components.codebook.semiproduct.SemiProductMapper;
import com.abelium.inatrace.components.product.ProductTypeMapper;
import com.abelium.inatrace.components.value_chain.api.ApiValueChain;
import com.abelium.inatrace.db.entities.value_chain.ValueChain;
import com.abelium.inatrace.types.Language;

import java.util.stream.Collectors;

/**
 * Mapper for value chain entity.
 *
 * @author Pece Adjievski, Sunesis d.o.o.
 */
public final class ValueChainMapper {

	private ValueChainMapper () {
		throw new IllegalStateException("Utility class");
	}

	/**
	 * Mapping {@link ValueChain} into {@link ApiValueChain}. This method maps only the basic attributes - collections are not mapped.
	 * @param entity DB entity.
	 * @return API model entity.
	 */
	public static ApiValueChain toApiValueChainBase(ValueChain entity) {

		if (entity == null) {
			return null;
		}

		ApiValueChain apiValueChain = new ApiValueChain();
		apiValueChain.setId(entity.getId());
		apiValueChain.setName(entity.getName());
		apiValueChain.setDescription(entity.getDescription());
		apiValueChain.setValueChainStatus(entity.getValueChainStatus());

		return apiValueChain;
	}

	/**
	 * Mapping {@link ValueChain} into {@link ApiValueChain}. This method maps the basic attributes and all the linked collections.
	 * @param entity DB entity.
	 * @return API model entity.
	 */
	public static ApiValueChain toApiValueChain(ValueChain entity, Language language) {

		ApiValueChain apiValueChain = ValueChainMapper.toApiValueChainBase(entity);

		if (apiValueChain == null) {
			return null;
		}

		// Map facility types
		apiValueChain.setFacilityTypes(entity.getFacilityTypes().stream()
				.map(vcFacilityType -> FacilityTypeMapper.toApiFacilityType(vcFacilityType.getFacilityType()))
				.collect(Collectors.toList()));

		// Map measure unit types
		apiValueChain.setMeasureUnitTypes(entity.getMeasureUnitTypes().stream()
				.map(vcMeasureUnitType -> MeasureUnitTypeMapper.toApiMeasureUnitTypeBase(
						vcMeasureUnitType.getMeasureUnitType())).collect(Collectors.toList()));

		// Map processing evidence types
		apiValueChain.setProcessingEvidenceTypes(entity.getProcEvidenceTypes().stream()
				.map(vcProcEvidenceType -> ProcessingEvidenceTypeMapper.toApiProcessingEvidenceTypeBase(
						vcProcEvidenceType.getProcessingEvidenceType(), language)).collect(Collectors.toList()));

		// Map processing evidence fields
		apiValueChain.setProcessingEvidenceFields(entity.getProcessingEvidenceFields().stream()
				.map(vcProcEvidenceField -> ProcessingEvidenceFieldMapper.toApiProcessingEvidenceField(
						vcProcEvidenceField.getProcessingEvidenceField(), language)).collect(Collectors.toList()));

		// Map semi-products
		apiValueChain.setSemiProducts(entity.getSemiProducts().stream()
				.map(vcSemiProduct -> SemiProductMapper.toValueChainApiSemiProduct(vcSemiProduct.getSemiProduct(), language))
				.collect(Collectors.toList()));

		// Map product type
		if (entity.getProductType() != null) {
			apiValueChain.setProductType(ProductTypeMapper.toApiProductType(entity.getProductType(), language));
		}

		return apiValueChain;
	}

}
