package com.abelium.inatrace.components.codebook.processingevidencefield;

import com.abelium.inatrace.api.ApiBaseEntity;
import com.abelium.inatrace.api.ApiPaginatedList;
import com.abelium.inatrace.api.ApiPaginatedRequest;
import com.abelium.inatrace.api.ApiStatus;
import com.abelium.inatrace.api.errors.ApiException;
import com.abelium.inatrace.components.codebook.processingevidencefield.api.ApiProcessingEvidenceField;
import com.abelium.inatrace.components.common.BaseService;
import com.abelium.inatrace.db.entities.codebook.ProcessingEvidenceField;
import com.abelium.inatrace.db.entities.codebook.ProcessingEvidenceFieldTranslation;
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
 * Service for processing evidence field entity.
 *
 * @author Rene Flores, Sunesis d.o.o.
 */
@Lazy
@Service
public class ProcessingEvidenceFieldService extends BaseService {

	public ApiPaginatedList<ApiProcessingEvidenceField> getProcessingEvidenceFieldList(ApiPaginatedRequest request, Language language) {

		return PaginationTools.createPaginatedResponse(em, request, () -> processingEvidenceFieldQueryObject(request), processingEvidenceField -> ProcessingEvidenceFieldMapper.toApiProcessingEvidenceFieldDetails(processingEvidenceField, language));
	}

	private ProcessingEvidenceField processingEvidenceFieldQueryObject(ApiPaginatedRequest request) {

		ProcessingEvidenceField processingEvidenceFieldProxy = Torpedo.from(ProcessingEvidenceField.class);
		if ("label".equals(request.sortBy)) {
			QueryTools.orderBy(request.sort, processingEvidenceFieldProxy.getLabel());
		} else {
			QueryTools.orderBy(request.sort, processingEvidenceFieldProxy.getId());
		}

		return processingEvidenceFieldProxy;
	}

	public ApiProcessingEvidenceField getProcessingEvidenceField(Long id, Language language) throws ApiException {
		return ProcessingEvidenceFieldMapper.toApiProcessingEvidenceField(fetchProcessingEvidenceField(id), language);
	}

	@Transactional
	public ApiBaseEntity createOrUpdateProcessingEvidenceField(CustomUserDetails authUser,
															   ApiProcessingEvidenceField apiProcessingEvidenceField) throws ApiException {

		ProcessingEvidenceField entity;

		if (apiProcessingEvidenceField.getId() != null) {

			// Editing is not permitted for Regional admin
			if (authUser.getUserRole() == UserRole.REGIONAL_ADMIN) {
				throw new ApiException(ApiStatus.UNAUTHORIZED, "Regional admin not authorized!");
			}
			entity = fetchProcessingEvidenceField(apiProcessingEvidenceField.getId());
		} else {
			entity = new ProcessingEvidenceField();
		}

		// ApiProcessingEvidenceField object
		entity.setFieldName(apiProcessingEvidenceField.getFieldName());
		entity.setLabel(apiProcessingEvidenceField.getLabel());
		entity.setType(apiProcessingEvidenceField.getType());

		// Remove translation not in request
		entity.getTranslations().removeIf(processingEvidenceFieldTranslation -> apiProcessingEvidenceField
				.getTranslations()
				.stream()
				.noneMatch(apiProcessingEvidenceFieldTranslation -> processingEvidenceFieldTranslation
						.getLanguage()
						.equals(apiProcessingEvidenceFieldTranslation.getLanguage())
			)
		);

		// Add or update existing
		apiProcessingEvidenceField.getTranslations().forEach(apiProcessingEvidenceFieldTranslation -> {
			ProcessingEvidenceFieldTranslation translation = entity.getTranslations().stream().filter(processingEvidenceFieldTranslation -> processingEvidenceFieldTranslation
					.getLanguage()
					.equals(apiProcessingEvidenceFieldTranslation.getLanguage()))
					.findFirst()
					.orElse(new ProcessingEvidenceFieldTranslation());
			translation.setLabel(apiProcessingEvidenceFieldTranslation.getLabel());
			translation.setLanguage(apiProcessingEvidenceFieldTranslation.getLanguage());
			translation.setProcessingEvidenceField(entity);
			entity.getTranslations().add(translation);
		});
		
		if (entity.getId() == null) {
			em.persist(entity);
		}

		return new ApiBaseEntity(entity);
	}

	@Transactional
	public void deleteProcessingEvidenceField(Long id) throws ApiException {

		ProcessingEvidenceField processingEvidenceField = fetchProcessingEvidenceField(id);
		em.remove(processingEvidenceField);
	}

	public ProcessingEvidenceField fetchProcessingEvidenceField(Long id) throws ApiException {

		ProcessingEvidenceField processingEvidenceField = Queries.get(em, ProcessingEvidenceField.class, id);
		if (processingEvidenceField == null) {
			throw new ApiException(ApiStatus.INVALID_REQUEST, "Invalid processing evidence field ID");
		}
		return processingEvidenceField;
	}
	
	public ApiPaginatedList<ApiProcessingEvidenceField> listProcessingEvidenceFieldsByValueChainList(
			List<Long> valueChainIds, ApiPaginatedRequest request, Language language) {

		TypedQuery<ProcessingEvidenceField> processingEvidenceFieldsQuery = em.createNamedQuery(
						"ProcessingEvidenceField.getProcessingEvidenceFieldsForValueChainIds", ProcessingEvidenceField.class)
				.setParameter("valueChainIds", valueChainIds)
				.setFirstResult(request.getOffset())
				.setMaxResults(request.getLimit());

		List<ProcessingEvidenceField> processingEvidenceFields = processingEvidenceFieldsQuery.getResultList();

		Long count = em.createNamedQuery("ProcessingEvidenceField.countProcessingEvidenceFieldsForValueChainIds", Long.class)
				.setParameter("valueChainIds", valueChainIds)
				.getSingleResult();

		return new ApiPaginatedList<>(
				processingEvidenceFields
						.stream()
						.map(processingEvidenceField -> ProcessingEvidenceFieldMapper.toApiProcessingEvidenceFieldDetails(processingEvidenceField, language))
						.collect(Collectors.toList()), count);
	}
	
}
