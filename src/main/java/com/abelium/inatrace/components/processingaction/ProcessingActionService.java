package com.abelium.inatrace.components.processingaction;

import com.abelium.inatrace.api.ApiBaseEntity;
import com.abelium.inatrace.api.ApiPaginatedList;
import com.abelium.inatrace.api.ApiPaginatedRequest;
import com.abelium.inatrace.api.ApiStatus;
import com.abelium.inatrace.api.errors.ApiException;
import com.abelium.inatrace.components.codebook.processing_evidence_type.ProcessingEvidenceTypeService;
import com.abelium.inatrace.components.codebook.processingevidencefield.ProcessingEvidenceFieldService;
import com.abelium.inatrace.components.codebook.semiproduct.SemiProductService;
import com.abelium.inatrace.components.common.BaseService;
import com.abelium.inatrace.components.company.CompanyQueries;
import com.abelium.inatrace.components.facility.FacilityService;
import com.abelium.inatrace.components.facility.api.ApiFacility;
import com.abelium.inatrace.components.processingaction.api.ApiProcessingAction;
import com.abelium.inatrace.components.processingaction.api.ApiProcessingActionOutputSemiProduct;
import com.abelium.inatrace.components.product.FinalProductService;
import com.abelium.inatrace.components.value_chain.ValueChainService;
import com.abelium.inatrace.components.value_chain.api.ApiValueChain;
import com.abelium.inatrace.db.entities.codebook.SemiProduct;
import com.abelium.inatrace.db.entities.company.Company;
import com.abelium.inatrace.db.entities.processingaction.*;
import com.abelium.inatrace.db.entities.product.FinalProduct;
import com.abelium.inatrace.db.entities.value_chain.ProcessingActionValueChain;
import com.abelium.inatrace.db.entities.value_chain.ValueChain;
import com.abelium.inatrace.security.service.CustomUserDetails;
import com.abelium.inatrace.security.utils.PermissionsUtil;
import com.abelium.inatrace.tools.PaginationTools;
import com.abelium.inatrace.tools.Queries;
import com.abelium.inatrace.types.Language;
import com.abelium.inatrace.types.ProcessingActionType;
import jakarta.transaction.Transactional;
import org.apache.commons.lang3.BooleanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.torpedoquery.jakarta.jpa.OnGoingLogicalCondition;
import org.torpedoquery.jakarta.jpa.Torpedo;
import java.math.BigDecimal;
import java.util.stream.Collectors;

/**
 * Service for processing action entity.
 *
 * @author Rene Flores, Pece Adjievski, Sunesis d.o.o.
 */
@Lazy
@Service
public class ProcessingActionService extends BaseService {

	private final CompanyQueries companyQueries;

	private final ValueChainService valueChainService;

	private final SemiProductService semiProductService;

	private final FinalProductService finalProductService;

	private final ProcessingEvidenceTypeService processingEvidenceTypeService;

	private final ProcessingEvidenceFieldService processingEvidenceFieldService;

	private final FacilityService facilityService;

	@Autowired
	public ProcessingActionService(CompanyQueries companyQueries,
								   ValueChainService valueChainService,
	                               SemiProductService semiProductService,
								   FinalProductService finalProductService,
	                               ProcessingEvidenceTypeService processingEvidenceTypeService,
	                               ProcessingEvidenceFieldService processingEvidenceFieldService,
	                               FacilityService facilityService) {
		this.companyQueries = companyQueries;
		this.valueChainService = valueChainService;
		this.semiProductService = semiProductService;
		this.finalProductService = finalProductService;
		this.processingEvidenceTypeService = processingEvidenceTypeService;
		this.processingEvidenceFieldService = processingEvidenceFieldService;
		this.facilityService = facilityService;
	}

	public ApiProcessingAction getProcessingAction(Long id, CustomUserDetails user, Language language) throws ApiException {
		
		ProcessingAction processingAction = fetchProcessingAction(id);

		// The request user should be enrolled in the processing action owner company
		PermissionsUtil.checkUserIfCompanyEnrolled(processingAction.getCompany().getUsers().stream().toList(), user);

		return ProcessingActionMapper.toApiProcessingAction(processingAction, language);
	}

	public ApiProcessingAction getProcessingActionDetail(Long id, CustomUserDetails user, Language language) throws ApiException {

		ProcessingAction processingAction = fetchProcessingAction(id);

		// The request user should be enrolled in the processing action owner company
		PermissionsUtil.checkUserIfCompanyEnrolled(processingAction.getCompany().getUsers().stream().toList(), user);

		return ProcessingActionMapper.toApiProcessingActionDetail(processingAction, language);
	}

	@Transactional
	public ApiBaseEntity createOrUpdateProcessingAction(ApiProcessingAction apiProcessingAction, CustomUserDetails user) throws ApiException {

		ProcessingAction entity = apiProcessingAction.getId() != null
				? fetchProcessingAction(apiProcessingAction.getId())
				: new ProcessingAction();

		// Validate Processing action data
		validateProcessingAction(apiProcessingAction);

		if (entity.getId() != null) {
			// An existing action belongs to its persisted owner. A caller must not take it
			// over by supplying a company ID where they happen to be an admin.
			PermissionsUtil.checkUserIfCompanyEnrolledAndAdminOrSystemAdmin(
					entity.getCompany().getUsers().stream().toList(), user);
			if (!entity.getCompany().getId().equals(apiProcessingAction.getCompany().getId())) {
				throw new ApiException(ApiStatus.UNAUTHORIZED, "Processing action company cannot be changed");
			}
		}

		// Fetch owner company
		Company company = companyQueries.fetchCompany(apiProcessingAction.getCompany().getId());

		// Check that request user is enrolled in owner company
		PermissionsUtil.checkUserIfCompanyEnrolledAndAdminOrSystemAdmin(company.getUsers().stream().toList(), user);

		// Set processing action owner company
		entity.setCompany(company);

		// Set the value chain
		entity.setSortOrder(apiProcessingAction.getSortOrder());
		entity.setPrefix(apiProcessingAction.getPrefix());
		entity.setPublicTimelineLabel(apiProcessingAction.getPublicTimelineLabel());
		entity.setPublicTimelineLocation(apiProcessingAction.getPublicTimelineLocation());
		entity.setPublicTimelineIconType(apiProcessingAction.getPublicTimelineIconType());
		entity.setType(apiProcessingAction.getType());

		// Set semi-products and final products (depending on the Processing action type)
		setSemiAndFinalProducts(apiProcessingAction, entity);

		// Set the repack for output final product
		if (entity.getOutputFinalProduct() != null) {
			entity.setRepackedOutputFinalProducts(apiProcessingAction.getRepackedOutputFinalProducts());
			entity.setMaxOutputWeight(apiProcessingAction.getMaxOutputWeight());
		} else {
			entity.setRepackedOutputFinalProducts(null);
			entity.setMaxOutputWeight(null);
		}

		// If we have shipment or transfer, set the field denoting if we are dealing with final products
		if (ProcessingActionType.TRANSFER.equals(apiProcessingAction.getType()) ||
				ProcessingActionType.SHIPMENT.equals(apiProcessingAction.getType())) {
			entity.setFinalProductAction(BooleanUtils.toBooleanDefaultIfNull(apiProcessingAction.getFinalProductAction(), false));
		} else {
			entity.setFinalProductAction(Boolean.FALSE);
		}

		// Set the estimated output quantity per unit
		entity.setEstimatedOutputQuantityPerUnit(apiProcessingAction.getEstimatedOutputQuantityPerUnit());

		// Update the translations
		updateProcActionTranslations(apiProcessingAction, entity);
		
		// Create or update required processing evidence fields
		updateRequiredEvidenceFields(apiProcessingAction, entity);

		// Create or update required processing evidence types
		updateRequiredEvidenceTypes(apiProcessingAction, entity);

		// Create or update the supported facilities for this processing action (the facilities where this processing starts)
		updateSupportedFacilities(apiProcessingAction, entity);

		// Create or update the value-chains
		updateValueChains(apiProcessingAction, entity);

		// If action type is GENERATE_QR_CODE, set Final product for QR code
		if (ProcessingActionType.GENERATE_QR_CODE.equals(apiProcessingAction.getType())) {
			entity.setQrCodeForFinalProduct(
					finalProductService.fetchFinalProduct(apiProcessingAction.getQrCodeForFinalProduct().getId()));
		}

		if (entity.getId() == null) {
			em.persist(entity);
		}

		return new ApiBaseEntity(entity);
	}

	@Transactional
	public void deleteProcessingAction(Long id, CustomUserDetails user) throws ApiException {

		ProcessingAction processingAction = fetchProcessingAction(id);

		PermissionsUtil.checkUserIfCompanyEnrolledAndAdminOrSystemAdmin(processingAction.getCompany().getUsers().stream().toList(), user);

		em.remove(processingAction);
	}

	public ProcessingAction fetchProcessingAction(Long id) throws ApiException {

		ProcessingAction processingAction = Queries.get(em, ProcessingAction.class, id);
		if (processingAction == null) {
			throw new ApiException(ApiStatus.INVALID_REQUEST, "Invalid processing action ID");
		}
		return processingAction;
	}
	
	public ApiPaginatedList<ApiProcessingAction> listProcessingActionsByCompany(Long companyId,
																				CustomUserDetails user,
	                                                                            Language language,
	                                                                            ApiPaginatedRequest request,
	                                                                            ProcessingActionType actionType,
	                                                                            Boolean onlyFinalProducts) throws ApiException {

		// Check that request user is company enrolled
		Company company = companyQueries.fetchCompany(companyId);
		PermissionsUtil.checkUserIfCompanyEnrolled(company.getUsers().stream().toList(), user);

		return PaginationTools.createPaginatedResponse(em, request, () ->
						listProcessingActionsByCompanyQuery(companyId, language, actionType, onlyFinalProducts),
				processingAction -> ProcessingActionMapper.toApiProcessingAction(processingAction, language));
	}

	private ProcessingAction listProcessingActionsByCompanyQuery(Long companyId, Language language,
	                                                             ProcessingActionType actionType,
	                                                             Boolean onlyFinalProducts) {

		ProcessingAction processingActionProxy = Torpedo.from(ProcessingAction.class);
		ProcessingActionTranslation procActionTranslation = Torpedo.innerJoin(
				processingActionProxy.getProcessingActionTranslations());

		OnGoingLogicalCondition condition = Torpedo.condition();

		condition = condition.and(procActionTranslation.getLanguage()).eq(language);
		condition = condition.and(processingActionProxy.getCompany().getId()).eq(companyId);

		if (actionType != null) {
			condition = condition.and(processingActionProxy.getType()).eq(actionType);
		}

		if (BooleanUtils.isTrue(onlyFinalProducts)) {
			condition = condition.and(processingActionProxy.getInputFinalProduct()).isNotNull();
			condition = condition.and(processingActionProxy.getOutputFinalProduct()).isNotNull();
		}

		Torpedo.where(condition);

		Torpedo.orderBy(processingActionProxy.getId());
		Torpedo.orderBy(processingActionProxy.getSortOrder());

		return processingActionProxy;
	}

	private void validateProcessingAction(ApiProcessingAction apiProcessingAction) throws ApiException {

		// Validate value chain
		if (apiProcessingAction.getValueChains() == null || apiProcessingAction.getValueChains().isEmpty() ||
				apiProcessingAction.getValueChains().get(0) == null ||
				apiProcessingAction.getValueChains().get(0).getId() == null) {
			throw new ApiException(ApiStatus.INVALID_REQUEST, "At least one value chain is required");
		}

		// Validate action type
		if (apiProcessingAction.getType() == null) {
			throw new ApiException(ApiStatus.INVALID_REQUEST, "Action type is required");
		}

		// Validate company
		if (apiProcessingAction.getCompany() == null || apiProcessingAction.getCompany().getId() == null) {
			throw new ApiException(ApiStatus.INVALID_REQUEST, "Company is required");
		}

		// Validate that estimated quantity is not provided if we don't have processing action type 'PROCESSING'
		if (!apiProcessingAction.getType().equals(ProcessingActionType.PROCESSING) &&
				apiProcessingAction.getEstimatedOutputQuantityPerUnit() != null) {
			throw new ApiException(ApiStatus.INVALID_REQUEST,
					"Estimated output quantity cannot be provided when action is not 'PROCESSING'");
		}

		// If repack output for final product is selected, validate that maximum output quantity is provided
		if (BooleanUtils.isTrue(apiProcessingAction.getRepackedOutputFinalProducts())) {
			if (apiProcessingAction.getMaxOutputWeight() == null || apiProcessingAction.getMaxOutputWeight().compareTo(
					BigDecimal.ZERO) <= 0) {
				throw new ApiException(ApiStatus.INVALID_REQUEST, "Maximum output quantity is required when 'repackedOutputFinalProducts' is selected");
			}
		}

		switch (apiProcessingAction.getType()) {
			case PROCESSING:

				// Validate input semi-product
				if (apiProcessingAction.getInputSemiProduct() == null || apiProcessingAction.getInputSemiProduct().getId() == null) {
					throw new ApiException(ApiStatus.INVALID_REQUEST, "Input semi-product is required");
				}

				// Validate that at least one output semi-product is present
				if (apiProcessingAction.getOutputSemiProducts() == null || apiProcessingAction.getOutputSemiProducts().isEmpty()) {
					throw new ApiException(ApiStatus.INVALID_REQUEST, "At least one output semi-product is required");
				}
				break;

			case FINAL_PROCESSING:

				// Validate input semi-product
				if (apiProcessingAction.getInputSemiProduct() == null || apiProcessingAction.getInputSemiProduct().getId() == null) {
					throw new ApiException(ApiStatus.INVALID_REQUEST, "Input semi-product is required");
				}

				// Output should be in this case final product
				if (apiProcessingAction.getOutputFinalProduct() == null || apiProcessingAction.getOutputFinalProduct().getId() == null) {
					throw new ApiException(ApiStatus.INVALID_REQUEST, "Output final product is required");
				}
				break;

			case TRANSFER:
			case SHIPMENT:

				if (BooleanUtils.isTrue(apiProcessingAction.getFinalProductAction())) {

					// Should be provided input final product
					if (apiProcessingAction.getInputFinalProduct() == null || apiProcessingAction.getInputFinalProduct().getId() == null) {
						throw new ApiException(ApiStatus.INVALID_REQUEST, "Input final product is required");
					}

				} else {

					// Should be provided input semi-product
					if (apiProcessingAction.getInputSemiProduct() == null || apiProcessingAction.getInputSemiProduct().getId() == null) {
						throw new ApiException(ApiStatus.INVALID_REQUEST, "Input semi-product is required");
					}
				}
				break;
			case GENERATE_QR_CODE:

				// Validate input semi-product
				if (apiProcessingAction.getInputSemiProduct() == null || apiProcessingAction.getInputSemiProduct().getId() == null) {
					throw new ApiException(ApiStatus.INVALID_REQUEST, "Input semi-product is required");
				}

				// Validate that the Final product for which the QR code should be generated is provided
				if (apiProcessingAction.getQrCodeForFinalProduct() == null || apiProcessingAction.getQrCodeForFinalProduct().getId() == null) {
					throw new ApiException(ApiStatus.INVALID_REQUEST, "Final product for QR code is required");
				}
		}

		// Make sure English translation is present
		apiProcessingAction.getTranslations()
				.stream()
				.filter(t -> t != null
						&& Language.EN.equals(t.getLanguage())
						&& t.getDescription() != null
						&& t.getName() != null)
				.findFirst()
				.orElseThrow(() -> new ApiException(ApiStatus.INVALID_REQUEST, "English translation is required!"));
	}

	private void setSemiAndFinalProducts(ApiProcessingAction apiProcessingAction, ProcessingAction entity) throws ApiException {

		SemiProduct inputSemiProduct;

		FinalProduct inputFinalProduct;
		FinalProduct outputFinalProduct;

		switch (apiProcessingAction.getType()) {
			case PROCESSING:

				// Set the input semi-product
				inputSemiProduct = semiProductService.fetchSemiProduct(apiProcessingAction.getInputSemiProduct().getId());
				entity.setInputSemiProduct(inputSemiProduct);

				// Set the output semi-products
				entity.getOutputSemiProducts().clear();
				for (ApiProcessingActionOutputSemiProduct apiPaOSM : apiProcessingAction.getOutputSemiProducts()) {
					ProcessingActionOutputSemiProduct paOSM = new ProcessingActionOutputSemiProduct();
					paOSM.setProcessingAction(entity);
					paOSM.setOutputSemiProduct(semiProductService.fetchSemiProduct(apiPaOSM.getId()));
					paOSM.setRepackedOutput(apiPaOSM.getRepackedOutput());
					paOSM.setMaxOutputWeight(apiPaOSM.getMaxOutputWeight());
					entity.getOutputSemiProducts().add(paOSM);
				}

				// Clear input and output final products (if they were set from previous version)
				entity.setInputFinalProduct(null);
				entity.setOutputFinalProduct(null);

				break;

			case FINAL_PROCESSING:

				// Set the input semi-product
				inputSemiProduct = semiProductService.fetchSemiProduct(apiProcessingAction.getInputSemiProduct().getId());
				entity.setInputSemiProduct(inputSemiProduct);

				// Set the output final product
				outputFinalProduct = finalProductService.fetchFinalProduct(apiProcessingAction.getOutputFinalProduct().getId());
				entity.setOutputFinalProduct(outputFinalProduct);

				// Clear all output semi-product (if they were set from previous version)
				entity.getOutputSemiProducts().clear();

				break;

			case TRANSFER:
			case SHIPMENT:

				if (BooleanUtils.isTrue(apiProcessingAction.getFinalProductAction())) {

					// Set the input final product and set the output to be the same as the input
					inputFinalProduct = finalProductService.fetchFinalProduct(apiProcessingAction.getInputFinalProduct().getId());
					entity.setInputFinalProduct(inputFinalProduct);
					entity.setOutputFinalProduct(inputFinalProduct);

					// Clear all output semi-product (if they were set from previous version)
					entity.setInputSemiProduct(null);
					entity.getOutputSemiProducts().clear();
				} else {

					// Set the input semi-product and set the output to be the same as the input
					inputSemiProduct = semiProductService.fetchSemiProduct(apiProcessingAction.getInputSemiProduct().getId());
					entity.setInputSemiProduct(inputSemiProduct);

					entity.getOutputSemiProducts().clear();
					ProcessingActionOutputSemiProduct paOSM = new ProcessingActionOutputSemiProduct();
					paOSM.setProcessingAction(entity);
					paOSM.setOutputSemiProduct(inputSemiProduct);
					paOSM.setRepackedOutput(null);
					paOSM.setMaxOutputWeight(null);
					entity.getOutputSemiProducts().add(paOSM);

					// Clear input and output final products (if they were set from previous version)
					entity.setInputFinalProduct(null);
					entity.setOutputFinalProduct(null);
				}
				break;

			case GENERATE_QR_CODE:

				// Set the input and output semi-product (the output is the same with the input)
				inputSemiProduct = semiProductService.fetchSemiProduct(apiProcessingAction.getInputSemiProduct().getId());
				entity.setInputSemiProduct(inputSemiProduct);

				entity.getOutputSemiProducts().clear();
				ProcessingActionOutputSemiProduct paOSM = new ProcessingActionOutputSemiProduct();
				paOSM.setProcessingAction(entity);
				paOSM.setOutputSemiProduct(inputSemiProduct);
				paOSM.setRepackedOutput(null);
				paOSM.setMaxOutputWeight(null);
				entity.getOutputSemiProducts().add(paOSM);

				// Clear input and output final products (if they were set from previous version)
				entity.setInputFinalProduct(null);
				entity.setOutputFinalProduct(null);
		}
	}

	private void updateValueChains(ApiProcessingAction apiProcessingAction, ProcessingAction entity) throws ApiException {

		entity.getProcessingActionsValueChains().clear();

		for (ApiValueChain apiValueChain : apiProcessingAction.getValueChains()) {
			ProcessingActionValueChain processingActionValueChain = new ProcessingActionValueChain();
			ValueChain valueChain = valueChainService.fetchValueChain(apiValueChain.getId());
			processingActionValueChain.setProcessingAction(entity);
			processingActionValueChain.setValueChain(valueChain);
			entity.getProcessingActionsValueChains().add(processingActionValueChain);
		}

	}

	private void updateRequiredEvidenceFields(ApiProcessingAction apiProcessingAction, ProcessingAction entity) {

		// Delete requiredEvidenceFields that are not present in the request
		entity.getProcessingEvidenceFields().removeAll(
				entity.getProcessingEvidenceFields()
						.stream()
						.filter(field -> apiProcessingAction.getRequiredEvidenceFields()
								.stream()
								.noneMatch(ref -> ref.getId().equals(field.getProcessingEvidenceField().getId())))
						.collect(Collectors.toList())
		);

		// Update or add requiredEvidenceFields
		apiProcessingAction.getRequiredEvidenceFields().forEach(

				requiredEvidenceField -> {

					ProcessingActionPEF processingActionPEF = entity.getProcessingEvidenceFields()
							.stream()
							.filter(p -> p.getProcessingEvidenceField().getId().equals(requiredEvidenceField.getId()))
							.findFirst()
							.orElse(new ProcessingActionPEF());

					try {
						processingActionPEF.setProcessingEvidenceField(
								processingEvidenceFieldService.fetchProcessingEvidenceField(requiredEvidenceField.getId()));
						processingActionPEF.setMandatory(
								requiredEvidenceField.getMandatory() != null && requiredEvidenceField.getMandatory());
						processingActionPEF.setRequiredOnQuote(
								requiredEvidenceField.getRequiredOnQuote() != null && requiredEvidenceField.getRequiredOnQuote());
						processingActionPEF.setProcessingAction(entity);
					} catch (ApiException e) {
						e.printStackTrace();
					}
					entity.getProcessingEvidenceFields().add(processingActionPEF);
				}
		);
	}

	private void updateRequiredEvidenceTypes(ApiProcessingAction apiProcessingAction, ProcessingAction entity) {

		// Delete requiredDocumentTypes that are not present in the request
		entity.getRequiredDocumentTypes().removeAll(
				entity.getRequiredDocumentTypes()
						.stream()
						.filter(p -> apiProcessingAction.getRequiredDocumentTypes()
								.stream()
								.noneMatch(rdt -> rdt.getId().equals(p.getProcessingEvidenceType().getId())))
						.collect(Collectors.toList())
		);

		// Update or add requiredDocumentTypes
		apiProcessingAction.getRequiredDocumentTypes().forEach(

				requiredDocumentType -> {

					ProcessingActionPET processingActionProcessingEvidenceType = entity.getRequiredDocumentTypes()
							.stream()
							.filter(p -> p.getProcessingEvidenceType().getId().equals(requiredDocumentType.getId()))
							.findFirst()
							.orElse(new ProcessingActionPET());

					try {
						processingActionProcessingEvidenceType.setProcessingEvidenceType(
								processingEvidenceTypeService.fetchProcessingEvidenceType(requiredDocumentType.getId()));
						processingActionProcessingEvidenceType.setMandatory(
								requiredDocumentType.getMandatory() != null && requiredDocumentType.getMandatory());
						processingActionProcessingEvidenceType.setRequiredOnQuote(
								requiredDocumentType.getRequiredOnQuote() != null && requiredDocumentType.getRequiredOnQuote());
						processingActionProcessingEvidenceType.setRequiredOneOfGroupIdForQuote(requiredDocumentType.getRequiredOneOfGroupIdForQuote());
						processingActionProcessingEvidenceType.setProcessingAction(entity);
					} catch (ApiException e) {
						e.printStackTrace();
					}
					entity.getRequiredDocumentTypes().add(processingActionProcessingEvidenceType);
				}
		);
	}

	private void updateSupportedFacilities(ApiProcessingAction apiProcessingAction, ProcessingAction entity) throws ApiException {

		// Delete facilities that are not present
		entity.getProcessingActionFacilities().removeAll(
				entity.getProcessingActionFacilities()
						.stream()
						.filter(paf -> apiProcessingAction.getSupportedFacilities()
								.stream()
								.noneMatch(f -> f.getId().equals(paf.getFacility().getId())))
						.collect(Collectors.toList())
		);

		// Create newly added supported facilities
		for (ApiFacility supportedFacility : apiProcessingAction.getSupportedFacilities()) {
			ProcessingActionFacility processingActionFacility = entity.getProcessingActionFacilities()
					.stream()
					.filter(paf -> supportedFacility.getId().equals(paf.getFacility().getId()))
					.findAny()
					.orElse(new ProcessingActionFacility());

			if (processingActionFacility.getId() == null) {

				processingActionFacility.setFacility(facilityService.fetchFacility(supportedFacility.getId()));
				processingActionFacility.setProcessingAction(entity);

				entity.getProcessingActionFacilities().add(processingActionFacility);
			}
		}
	}

	private void updateProcActionTranslations(ApiProcessingAction apiProcessingAction, ProcessingAction entity) {

		// Remove translations that are not part of the request
		entity.getProcessingActionTranslations().removeAll(
				entity.getProcessingActionTranslations()
						.stream()
						.filter(translation -> apiProcessingAction.getTranslations()
								.stream()
								.noneMatch(t -> t.getLanguage().equals(translation.getLanguage())))
						.collect(Collectors.toList())
		);

		// Update or add translations
		apiProcessingAction.getTranslations().forEach(
				translation -> {
					ProcessingActionTranslation pat = entity.getProcessingActionTranslations()
							.stream()
							.filter(t -> t.getLanguage().equals(translation.getLanguage()))
							.findFirst()
							.orElse(new ProcessingActionTranslation());
					pat.setName(translation.getName());
					pat.setDescription(translation.getDescription());
					pat.setLanguage(translation.getLanguage());
					pat.setProcessingAction(entity);
					entity.getProcessingActionTranslations().add(pat);
				}
		);
	}
}
