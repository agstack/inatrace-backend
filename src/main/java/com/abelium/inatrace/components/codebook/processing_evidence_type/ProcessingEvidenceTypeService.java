package com.abelium.inatrace.components.codebook.processing_evidence_type;

import com.abelium.inatrace.api.ApiBaseEntity;
import com.abelium.inatrace.api.ApiPaginatedList;
import com.abelium.inatrace.api.ApiPaginatedRequest;
import com.abelium.inatrace.api.ApiStatus;
import com.abelium.inatrace.api.errors.ApiException;
import com.abelium.inatrace.components.codebook.processing_evidence_type.api.ApiProcessingEvidenceType;
import com.abelium.inatrace.components.common.BaseService;
import com.abelium.inatrace.db.entities.codebook.ProcessingEvidenceType;
import com.abelium.inatrace.db.entities.codebook.ProcessingEvidenceTypeTranslation;
import com.abelium.inatrace.security.service.CustomUserDetails;
import com.abelium.inatrace.tools.PaginationTools;
import com.abelium.inatrace.tools.Queries;
import com.abelium.inatrace.tools.QueryTools;
import com.abelium.inatrace.types.Language;
import com.abelium.inatrace.types.UserRole;
import jakarta.persistence.TypedQuery;
import jakarta.transaction.Transactional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.torpedoquery.jakarta.jpa.Torpedo;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Service for processing evidence type entity.
 *
 * @author Pece Adjievski, Sunesis d.o.o.
 */
@Lazy
@Service
public class ProcessingEvidenceTypeService extends BaseService {

	public ApiPaginatedList<ApiProcessingEvidenceType> getProcEvidenceTypeList(ApiPaginatedRequest request, Language language) {

		return PaginationTools.createPaginatedResponse(em, request, () -> procEvidenceTypeQueryObject(request),
				processingEvidenceType -> ProcessingEvidenceTypeMapper.toApiProcessingEvidenceType(processingEvidenceType, language));
	}

	private ProcessingEvidenceType procEvidenceTypeQueryObject(ApiPaginatedRequest request) {

		ProcessingEvidenceType processingEvidenceType = Torpedo.from(ProcessingEvidenceType.class);

		switch (request.sortBy) {
			case "code":
				QueryTools.orderBy(request.sort, processingEvidenceType.getCode());
				break;
			case "label":
				QueryTools.orderBy(request.sort, processingEvidenceType.getLabel());
				break;
			default:
				QueryTools.orderBy(request.sort, processingEvidenceType.getId());
		}

		return processingEvidenceType;
	}

	public ApiProcessingEvidenceType getProcessingEvidenceType(Long id, Language language) throws ApiException {

		return ProcessingEvidenceTypeMapper.toApiProcessingEvidenceType(fetchProcessingEvidenceType(id), language);
	}

	@Transactional
	public ApiBaseEntity createOrUpdateProcessingEvidenceType(CustomUserDetails authUser,
															  ApiProcessingEvidenceType apiProcessingEvidenceType) throws ApiException {

		ProcessingEvidenceType entity;

		if (apiProcessingEvidenceType.getId() != null) {

			// Editing is not permitted for Regional admin
			if (authUser.getUserRole() == UserRole.REGIONAL_ADMIN) {
				throw new ApiException(ApiStatus.UNAUTHORIZED, "Regional admin not authorized!");
			}

			entity = fetchProcessingEvidenceType(apiProcessingEvidenceType.getId());
		} else {

			entity = new ProcessingEvidenceType();
			entity.setCode(apiProcessingEvidenceType.getCode());
		}
		entity.setLabel(apiProcessingEvidenceType.getLabel());

		entity.setType(apiProcessingEvidenceType.getType());
		entity.setFairness(apiProcessingEvidenceType.getFairness());
		entity.setProvenance(apiProcessingEvidenceType.getProvenance());
		entity.setQuality(apiProcessingEvidenceType.getQuality());

		// Remove translation if not in request
		entity.getTranslations().removeIf(processingEvidenceTypeTranslation -> apiProcessingEvidenceType
				.getTranslations()
				.stream()
				.noneMatch(apiProcessingEvidenceTypeTranslation -> processingEvidenceTypeTranslation
						.getLanguage()
						.equals(apiProcessingEvidenceTypeTranslation.getLanguage())));

		// Add or edit
		apiProcessingEvidenceType.getTranslations().forEach(apiProcessingEvidenceTypeTranslation -> {
			ProcessingEvidenceTypeTranslation translation = entity.getTranslations().stream().filter(processingEvidenceTypeTranslation -> processingEvidenceTypeTranslation
					.getLanguage()
					.equals(apiProcessingEvidenceTypeTranslation.getLanguage()))
					.findFirst()
					.orElse(new ProcessingEvidenceTypeTranslation());
			translation.setLabel(apiProcessingEvidenceTypeTranslation.getLabel());
			translation.setLanguage(apiProcessingEvidenceTypeTranslation.getLanguage());
			translation.setProcessingEvidenceType(entity);
			entity.getTranslations().add(translation);
		});

		if (entity.getId() == null) {
			em.persist(entity);
		}

		return new ApiBaseEntity(entity);
	}

	@Transactional
	public void deleteProcessingEvidenceType(Long id) throws ApiException {

		ProcessingEvidenceType processingEvidenceType = fetchProcessingEvidenceType(id);
		em.remove(processingEvidenceType);
	}

	public ProcessingEvidenceType fetchProcessingEvidenceType(Long id) throws ApiException {

		ProcessingEvidenceType processingEvidenceType = Queries.get(em, ProcessingEvidenceType.class, id);
		if (processingEvidenceType == null) {
			throw new ApiException(ApiStatus.INVALID_REQUEST, "Invalid processing evidence type ID");
		}

		return processingEvidenceType;
	}

	public ApiPaginatedList<ApiProcessingEvidenceType> listProcessingEvidenceTypesByValueChainList(
			List<Long> valueChainIds, ApiPaginatedRequest request, Language language) {

		TypedQuery<ProcessingEvidenceType> processingEvidenceTypesQuery =
				em.createNamedQuery("ProcessingEvidenceType.getProcessingEvidenceTypesForValueChainIds", ProcessingEvidenceType.class)
						.setParameter("valueChainIds", valueChainIds)
						.setFirstResult(request.getOffset())
						.setMaxResults(request.getLimit());

		List<ProcessingEvidenceType> processingEvidenceTypes = processingEvidenceTypesQuery.getResultList();

		Long count = em.createNamedQuery("ProcessingEvidenceType.countProcessingEvidenceTypesForValueChainIds", Long.class)
				.setParameter("valueChainIds", valueChainIds)
				.getSingleResult();

		return new ApiPaginatedList<>(
				processingEvidenceTypes
						.stream()
						.map(processingEvidenceType -> ProcessingEvidenceTypeMapper.toApiProcessingEvidenceTypeBase(processingEvidenceType, language))
						.collect(Collectors.toList()), count);
	}
}
