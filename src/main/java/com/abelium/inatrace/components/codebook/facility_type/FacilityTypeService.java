package com.abelium.inatrace.components.codebook.facility_type;

import com.abelium.inatrace.api.ApiBaseEntity;
import com.abelium.inatrace.api.ApiPaginatedList;
import com.abelium.inatrace.api.ApiPaginatedRequest;
import com.abelium.inatrace.api.ApiStatus;
import com.abelium.inatrace.api.errors.ApiException;
import com.abelium.inatrace.components.codebook.facility_type.api.ApiFacilityType;
import com.abelium.inatrace.components.common.BaseService;
import com.abelium.inatrace.db.entities.codebook.FacilityType;
import com.abelium.inatrace.security.service.CustomUserDetails;
import com.abelium.inatrace.tools.PaginationTools;
import com.abelium.inatrace.tools.Queries;
import com.abelium.inatrace.tools.QueryTools;
import com.abelium.inatrace.types.UserRole;
import jakarta.transaction.Transactional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.torpedoquery.jakarta.jpa.Torpedo;
import java.util.List;

/**
 * Service for facility type entity.
 *
 * @author Pece Adjievski, Sunesis d.o.o.
 */
@Lazy
@Service
public class FacilityTypeService extends BaseService {

	/**
	 * Every entity attribute that references this entry (the foreign keys to its table, except its
	 * translations). A delete is refused while any of them does; CodebookDeletionTest checks this
	 * list against the database schema.
	 */
	public static final List<String> IN_USE_REFERENCES = List.of(
			"Facility.facilityType",
			"ValueChainFacilityType.facilityType");

	public ApiPaginatedList<ApiFacilityType> getFacilityTypeList(ApiPaginatedRequest request) {

		return PaginationTools.createPaginatedResponse(em, request, () -> facilityTypeQueryObject(request),
				FacilityTypeMapper::toApiFacilityType);
	}

	private FacilityType facilityTypeQueryObject(ApiPaginatedRequest request) {

		FacilityType facilityTypeProxy = Torpedo.from(FacilityType.class);

		switch (request.sortBy) {
			case "code":
				QueryTools.orderBy(request.sort, facilityTypeProxy.getCode());
				break;
			case "label":
				QueryTools.orderBy(request.sort, facilityTypeProxy.getLabel());
				break;
			default:
				QueryTools.orderBy(request.sort, facilityTypeProxy.getId());
		}

		return facilityTypeProxy;
	}

	public ApiFacilityType getFacilityType(Long id) throws ApiException {

		return FacilityTypeMapper.toApiFacilityType(fetchFacilityType(id));
	}

	@Transactional
	public ApiBaseEntity createOrUpdateFacilityType(CustomUserDetails authUser, ApiFacilityType apiFacilityType) throws ApiException {

		FacilityType entity;

		if (apiFacilityType.getId() != null) {

			// Editing is not permitted for Regional admin
			if (authUser.getUserRole() == UserRole.REGIONAL_ADMIN) {
				throw new ApiException(ApiStatus.UNAUTHORIZED, "Regional admin not authorized!");
			}

			entity = fetchFacilityType(apiFacilityType.getId());
		} else {

			entity = new FacilityType();
			entity.setCode(apiFacilityType.getCode());
		}
		entity.setLabel(apiFacilityType.getLabel());

		if (entity.getId() == null) {
			em.persist(entity);
		}

		return new ApiBaseEntity(entity);
	}

	// The in-use answer may come after the flush (Queries.removeUnreferenced): roll back the whole delete then.
	@Transactional(rollbackOn = ApiException.class)
	public void deleteFacilityType(Long id) throws ApiException {

		FacilityType facilityType = fetchFacilityType(id);
		if (Queries.isReferenced(em, id, IN_USE_REFERENCES)) {
			throw new ApiException(ApiStatus.VALIDATION_ERROR, "Cannot delete a facility type that is in use");
		}
		Queries.removeUnreferenced(em, facilityType, "Cannot delete a facility type that is in use");
	}

	public FacilityType fetchFacilityType(Long id) throws ApiException {

		FacilityType facilityType = Queries.get(em, FacilityType.class, id);
		if (facilityType == null) {
			throw new ApiException(ApiStatus.INVALID_REQUEST, "Invalid facility type ID");
		}

		return facilityType;
	}

}
