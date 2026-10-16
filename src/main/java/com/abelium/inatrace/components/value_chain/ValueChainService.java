package com.abelium.inatrace.components.value_chain;

import com.abelium.inatrace.api.ApiBaseEntity;
import com.abelium.inatrace.api.ApiPaginatedList;
import com.abelium.inatrace.api.ApiStatus;
import com.abelium.inatrace.api.errors.ApiException;
import com.abelium.inatrace.components.codebook.facility_type.FacilityTypeService;
import com.abelium.inatrace.components.codebook.facility_type.api.ApiFacilityType;
import com.abelium.inatrace.components.codebook.measure_unit_type.MeasureUnitTypeService;
import com.abelium.inatrace.components.codebook.measure_unit_type.api.ApiMeasureUnitType;
import com.abelium.inatrace.components.codebook.processing_evidence_type.ProcessingEvidenceTypeService;
import com.abelium.inatrace.components.codebook.processing_evidence_type.api.ApiProcessingEvidenceType;
import com.abelium.inatrace.components.codebook.processingevidencefield.ProcessingEvidenceFieldService;
import com.abelium.inatrace.components.codebook.processingevidencefield.api.ApiProcessingEvidenceField;
import com.abelium.inatrace.components.codebook.semiproduct.SemiProductService;
import com.abelium.inatrace.components.codebook.semiproduct.api.ApiSemiProduct;
import com.abelium.inatrace.components.common.BaseService;
import com.abelium.inatrace.components.user.UserService;
import com.abelium.inatrace.components.value_chain.api.ApiValueChain;
import com.abelium.inatrace.components.value_chain.api.ApiValueChainListRequest;
import com.abelium.inatrace.db.entities.codebook.*;
import com.abelium.inatrace.db.entities.common.User;
import com.abelium.inatrace.db.entities.value_chain.*;
import com.abelium.inatrace.db.entities.value_chain.enums.ValueChainStatus;
import com.abelium.inatrace.security.service.CustomUserDetails;
import com.abelium.inatrace.tools.PaginationTools;
import com.abelium.inatrace.tools.Queries;
import com.abelium.inatrace.tools.QueryTools;
import com.abelium.inatrace.types.Language;
import com.abelium.inatrace.types.UserRole;
import jakarta.transaction.Transactional;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.torpedoquery.jakarta.jpa.OnGoingLogicalCondition;
import org.torpedoquery.jakarta.jpa.Torpedo;

import java.util.Map;
import java.util.stream.Collectors;

/**
 * Service for value chain entity.
 *
 * @author Pece Adjievski, Sunesis d.o.o.
 */
@Lazy
@Service
public class ValueChainService extends BaseService {

	private final UserService userService;
	private final FacilityTypeService facilityTypeService;
	private final MeasureUnitTypeService measureUnitTypeService;
	private final ProcessingEvidenceTypeService procEvidenceTypeService;
	private final ProcessingEvidenceFieldService procEvidenceFieldService;
	private final SemiProductService semiProductService;

	@Autowired
	public ValueChainService(UserService userService,
	                         FacilityTypeService facilityTypeService,
	                         MeasureUnitTypeService measureUnitTypeService,
	                         ProcessingEvidenceTypeService procEvidenceTypeService,
	                         ProcessingEvidenceFieldService procEvidenceFieldService,
	                         SemiProductService semiProductService) {
		this.userService = userService;
		this.facilityTypeService = facilityTypeService;
		this.measureUnitTypeService = measureUnitTypeService;
		this.procEvidenceTypeService = procEvidenceTypeService;
		this.procEvidenceFieldService = procEvidenceFieldService;
		this.semiProductService = semiProductService;
	}

	public ApiPaginatedList<ApiValueChain> getValueChainList(ApiValueChainListRequest request) {

		return PaginationTools.createPaginatedResponse(em, request, () -> valueChainQueryObject(request),
				ValueChainMapper::toApiValueChainBase);
	}

	private ValueChain valueChainQueryObject(ApiValueChainListRequest request) {

		ValueChain valueChainProxy = Torpedo.from(ValueChain.class);

		OnGoingLogicalCondition condition = Torpedo.condition();

		if (StringUtils.isNotBlank(request.getName())) {
			condition = condition.and(valueChainProxy.getName()).like().any(request.getName());
		}
		if (request.getValueChainStatus() != null) {
			condition = condition.and(valueChainProxy.getValueChainStatus()).eq(request.getValueChainStatus());
		}
		if (request.getProductTypeId() != null) {
			condition = condition.and(valueChainProxy.getProductType().getId()).eq(request.getProductTypeId());
		}

		Torpedo.where(condition);

		switch (request.sortBy) {
			case "name":
				QueryTools.orderBy(request.sort, valueChainProxy.getName());
				break;
			case "description":
				QueryTools.orderBy(request.sort, valueChainProxy.getDescription());
				break;
			default:
				QueryTools.orderBy(request.sort, valueChainProxy.getId());
		}

		return valueChainProxy;
	}

	public ApiValueChain getValueChain(Long id, Language language) throws ApiException {

		ValueChain valueChain = fetchValueChain(id);

		return ValueChainMapper.toApiValueChain(valueChain, language);
	}

	@Transactional
	public ApiBaseEntity createOrUpdateValueChain(CustomUserDetails authUser, ApiValueChain apiValueChain) throws ApiException {

		User user = userService.fetchUserById(authUser.getUserId());
		ValueChain entity;

		if (apiValueChain.getId() != null) {

			// Editing is not permitted for Regional admin
			if (authUser.getUserRole() == UserRole.REGIONAL_ADMIN) {
				throw new ApiException(ApiStatus.UNAUTHORIZED, "Regional admin not authorized!");
			}

			entity = fetchValueChain(apiValueChain.getId());

			if (entity.getValueChainStatus().equals(ValueChainStatus.DISABLED)) {
				throw new ApiException(ApiStatus.INVALID_REQUEST,
						"Selected value chain is disabled, editing is not permitted");
			}

			entity.setUpdatedBy(user);
		} else {
			entity = new ValueChain();
			entity.setCreatedBy(user);
			entity.setValueChainStatus(ValueChainStatus.ENABLED);
		}

		entity.setName(apiValueChain.getName());
		entity.setDescription(apiValueChain.getDescription());

		// Update facility type association
		updateVCFacilityTypes(entity, apiValueChain);

		// Update measuring unit types
		updateVCMeasureUnitTypes(entity, apiValueChain);

		// Update processing evidence types
		updateVCProcEvidenceTypes(entity, apiValueChain);

		// Update processing evidence fields
		updateVCProcEvidenceFields(entity, apiValueChain);

		// Update semi-products
		updateVCSemiProducts(entity, apiValueChain);

		// Update product type
		updateProductType(entity, apiValueChain);

		if (entity.getId() == null) {
			em.persist(entity);
		}

		return new ApiBaseEntity(entity);
	}

	@Transactional
	public ApiBaseEntity enableValueChain(Long id) throws ApiException {

		ValueChain valueChain = fetchValueChain(id);
		valueChain.setValueChainStatus(ValueChainStatus.ENABLED);

		return new ApiBaseEntity(valueChain);
	}

	@Transactional
	public ApiBaseEntity disableValueChain(Long id) throws ApiException {

		ValueChain valueChain = fetchValueChain(id);
		valueChain.setValueChainStatus(ValueChainStatus.DISABLED);

		return new ApiBaseEntity(valueChain);
	}

	@Transactional
	public void deleteValueChain(Long id) throws ApiException {

		ValueChain valueChain = fetchValueChain(id);
		em.remove(valueChain);
	}

	public ValueChain fetchValueChain(Long id) throws ApiException {

		ValueChain valueChain = Queries.get(em, ValueChain.class, id);
		if (valueChain == null) {
			throw new ApiException(ApiStatus.INVALID_REQUEST, "Invalid value chain ID");
		}

		return valueChain;
	}

	private void updateVCFacilityTypes(ValueChain entity, ApiValueChain apiValueChain) throws ApiException {
		if (apiValueChain.getFacilityTypes() == null) {
			return;
		}

		Map<Long, ValueChainFacilityType> currentVCFacilities = entity.getFacilityTypes().stream().collect(
				Collectors.toMap(vcFT -> vcFT.getFacilityType().getId(), vcFT -> vcFT));
		for (ApiFacilityType apiFacilityType : apiValueChain.getFacilityTypes()) {
			ValueChainFacilityType vcFT = currentVCFacilities.get(apiFacilityType.getId());
			if (vcFT == null) {
				FacilityType facilityType = facilityTypeService.fetchFacilityType(apiFacilityType.getId());
				entity.getFacilityTypes().add(new ValueChainFacilityType(entity, facilityType));
			} else {
				currentVCFacilities.remove(apiFacilityType.getId());
			}
		}
		currentVCFacilities.values().forEach(vcFT -> entity.getFacilityTypes().remove(vcFT));
	}

	private void updateVCMeasureUnitTypes(ValueChain entity, ApiValueChain apiValueChain) throws ApiException {
		if (apiValueChain.getMeasureUnitTypes() == null) {
			return;
		}

		Map<Long, ValueChainMeasureUnitType> currentVCMeasureUnits = entity.getMeasureUnitTypes().stream().collect(
				Collectors.toMap(vcMUT -> vcMUT.getMeasureUnitType().getId(), vcMUT -> vcMUT));
		for (ApiMeasureUnitType apiMeasureUnitType : apiValueChain.getMeasureUnitTypes()) {
			ValueChainMeasureUnitType vcMUT = currentVCMeasureUnits.get(apiMeasureUnitType.getId());
			if (vcMUT == null) {
				MeasureUnitType measureUnitType = measureUnitTypeService.fetchMeasureUnitType(
						apiMeasureUnitType.getId());
				entity.getMeasureUnitTypes().add(new ValueChainMeasureUnitType(entity, measureUnitType));
			} else {
				currentVCMeasureUnits.remove(apiMeasureUnitType.getId());
			}
		}
		currentVCMeasureUnits.values().forEach(vcMUT -> entity.getMeasureUnitTypes().remove(vcMUT));
	}

	private void updateVCProcEvidenceTypes(ValueChain entity, ApiValueChain apiValueChain) throws ApiException {
		if (apiValueChain.getProcessingEvidenceTypes() == null) {
			return;
		}

		Map<Long, ValueChainProcEvidenceType> currentVCProcEvidenceTypes = entity.getProcEvidenceTypes().stream()
				.collect(Collectors.toMap(vcPET -> vcPET.getProcessingEvidenceType().getId(), vcPET -> vcPET));
		for (ApiProcessingEvidenceType apiProcessingEvidenceType : apiValueChain.getProcessingEvidenceTypes()) {
			ValueChainProcEvidenceType vcPET = currentVCProcEvidenceTypes.get(apiProcessingEvidenceType.getId());
			if (vcPET == null) {
				ProcessingEvidenceType processingEvidenceType = procEvidenceTypeService.fetchProcessingEvidenceType(
						apiProcessingEvidenceType.getId());
				entity.getProcEvidenceTypes().add(new ValueChainProcEvidenceType(entity, processingEvidenceType));
			} else {
				currentVCProcEvidenceTypes.remove(apiProcessingEvidenceType.getId());
			}
		}
		currentVCProcEvidenceTypes.values().forEach(vcPET -> entity.getProcEvidenceTypes().remove(vcPET));
	}

	private void updateVCProcEvidenceFields(ValueChain entity, ApiValueChain apiValueChain) throws ApiException {
		if (apiValueChain.getProcessingEvidenceFields() == null) {
			return;
		}

		Map<Long, ValueChainProcessingEvidenceField> currentVCProcEvidenceFields = entity.getProcessingEvidenceFields().stream()
				.collect(Collectors.toMap(vcPEF -> vcPEF.getProcessingEvidenceField().getId(), vcPEF -> vcPEF));
		for (ApiProcessingEvidenceField apiProcessingEvidenceField : apiValueChain.getProcessingEvidenceFields()) {
			ValueChainProcessingEvidenceField vcPEF = currentVCProcEvidenceFields.get(apiProcessingEvidenceField.getId());
			if (vcPEF == null) {
				ProcessingEvidenceField processingEvidenceField = procEvidenceFieldService.fetchProcessingEvidenceField(
						apiProcessingEvidenceField.getId());
				entity.getProcessingEvidenceFields().add(new ValueChainProcessingEvidenceField(entity, processingEvidenceField));
			} else {
				currentVCProcEvidenceFields.remove(apiProcessingEvidenceField.getId());
			}
		}
		currentVCProcEvidenceFields.values().forEach(vcPEF -> entity.getProcessingEvidenceFields().remove(vcPEF));
	}

	private void updateVCSemiProducts(ValueChain entity, ApiValueChain apiValueChain) throws ApiException {
		if (apiValueChain.getSemiProducts() == null) {
			return;
		}

		Map<Long, ValueChainSemiProduct> currentVCSemiProducts = entity.getSemiProducts().stream()
				.collect(Collectors.toMap(vcSP -> vcSP.getSemiProduct().getId(), vcSP -> vcSP));
		for (ApiSemiProduct apiSemiProduct : apiValueChain.getSemiProducts()) {
			ValueChainSemiProduct vcSP = currentVCSemiProducts.get(apiSemiProduct.getId());
			if (vcSP == null) {
				SemiProduct semiProduct = semiProductService.fetchSemiProduct(apiSemiProduct.getId());
				entity.getSemiProducts().add(new ValueChainSemiProduct(entity, semiProduct));
			} else {
				currentVCSemiProducts.remove(apiSemiProduct.getId());
			}
		}
		currentVCSemiProducts.values().forEach(vcSP -> entity.getSemiProducts().remove(vcSP));
	}

	private void updateProductType(ValueChain entity, ApiValueChain apiValueChain) throws ApiException {

		if (apiValueChain.getProductType() == null || apiValueChain.getProductType().getId() == null) {
			throw new ApiException(ApiStatus.INVALID_REQUEST, "Product type id must be specified");
		}
		ProductType productType = em.find(ProductType.class, apiValueChain.getProductType().getId());
		if (productType == null) {
			throw new ApiException(ApiStatus.INVALID_REQUEST, "Product type with given id does not exist");
		}

		entity.setProductType(productType);
	}

}
