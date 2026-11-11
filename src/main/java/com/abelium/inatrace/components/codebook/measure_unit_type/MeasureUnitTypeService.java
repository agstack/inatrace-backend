package com.abelium.inatrace.components.codebook.measure_unit_type;

import com.abelium.inatrace.api.ApiBaseEntity;
import com.abelium.inatrace.api.ApiPaginatedList;
import com.abelium.inatrace.api.ApiPaginatedRequest;
import com.abelium.inatrace.api.ApiStatus;
import com.abelium.inatrace.api.errors.ApiException;
import com.abelium.inatrace.components.codebook.measure_unit_type.api.ApiMeasureUnitType;
import com.abelium.inatrace.components.common.BaseService;
import com.abelium.inatrace.db.entities.codebook.MeasureUnitType;
import com.abelium.inatrace.security.service.CustomUserDetails;
import com.abelium.inatrace.tools.PaginationTools;
import com.abelium.inatrace.tools.Queries;
import com.abelium.inatrace.tools.QueryTools;
import com.abelium.inatrace.types.UserRole;
import jakarta.transaction.Transactional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.torpedoquery.jakarta.jpa.Torpedo;

import java.util.Objects;
import java.util.List;

/**
 * Service for measure unit type entity.
 *
 * @author Pece Adjievski, Sunesis d.o.o.
 */
@Lazy
@Service
public class MeasureUnitTypeService extends BaseService {

	/**
	 * Every entity attribute that references this entry (the foreign keys to its table, except its
	 * translations). A delete is refused while any of them does; CodebookDeletionTest checks this
	 * list against the database schema.
	 */
	public static final List<String> IN_USE_REFERENCES = List.of(
			"MeasureUnitType.underlyingMeasurementUnitType",
			"SemiProduct.measurementUnitType",
			"FinalProduct.measurementUnitType",
			"ValueChainMeasureUnitType.measureUnitType",
			"StockOrder.measurementUnitType",
			"Transaction.inputMeasureUnitType");

	public ApiPaginatedList<ApiMeasureUnitType> getMeasureUnitTypeList(ApiPaginatedRequest request) {

		return PaginationTools.createPaginatedResponse(em, request, () -> mUnitListQueryObject(request),
				MeasureUnitTypeMapper::toApiMeasureUnitType);
	}

	private MeasureUnitType mUnitListQueryObject(ApiPaginatedRequest request) {

		MeasureUnitType measureUnitTypeProxy = Torpedo.from(MeasureUnitType.class);

		switch (request.sortBy) {
			case "code":
				QueryTools.orderBy(request.sort, measureUnitTypeProxy.getCode());
				break;
			case "label":
				QueryTools.orderBy(request.sort, measureUnitTypeProxy.getLabel());
				break;
			default:
				QueryTools.orderBy(request.sort, measureUnitTypeProxy.getId());
		}

		return measureUnitTypeProxy;
	}

	public ApiMeasureUnitType getMeasureUnitType(Long id) throws ApiException {

		return MeasureUnitTypeMapper.toApiMeasureUnitType(fetchMeasureUnitType(id));
	}

	@Transactional
	public ApiBaseEntity createOrUpdateMeasureUnitType(CustomUserDetails authUser, ApiMeasureUnitType apiMeasureUnitType) throws ApiException {

		MeasureUnitType entity;

		if (apiMeasureUnitType.getId() != null) {

			// Editing is not permitted for Regional admin
			if (authUser.getUserRole() == UserRole.REGIONAL_ADMIN) {
				throw new ApiException(ApiStatus.UNAUTHORIZED, "Regional admin not authorized!");
			}

			entity = fetchMeasureUnitType(apiMeasureUnitType.getId());
		} else {

			entity = new MeasureUnitType();
			entity.setCode(apiMeasureUnitType.getCode());
		}
		entity.setLabel(apiMeasureUnitType.getLabel());
		entity.setWeight(apiMeasureUnitType.getWeight());

		if (apiMeasureUnitType.getUnderlyingMeasurementUnitType() != null &&
				apiMeasureUnitType.getUnderlyingMeasurementUnitType().getId() != null &&
				(entity.getUnderlyingMeasurementUnitType() == null ||
						!Objects.equals(entity.getUnderlyingMeasurementUnitType().getId(),
								apiMeasureUnitType.getUnderlyingMeasurementUnitType().getId()))) {

			MeasureUnitType underlyingMeasurementUnitType = fetchMeasureUnitType(
					apiMeasureUnitType.getUnderlyingMeasurementUnitType().getId());
			entity.setUnderlyingMeasurementUnitType(underlyingMeasurementUnitType);

		} else if (apiMeasureUnitType.getUnderlyingMeasurementUnitType() == null || apiMeasureUnitType.getUnderlyingMeasurementUnitType().getId() == null) {
			entity.setUnderlyingMeasurementUnitType(null);
		}

		if (entity.getId() == null) {
			em.persist(entity);
		}

		return new ApiBaseEntity(entity);
	}

	// The in-use answer may come after the flush (Queries.removeUnreferenced): roll back the whole delete then.
	@Transactional(rollbackOn = ApiException.class)
	public void deleteMeasureUnitType(Long id) throws ApiException {

		MeasureUnitType measureUnitType = fetchMeasureUnitType(id);
		if (Queries.isReferenced(em, id, IN_USE_REFERENCES)) {
			throw new ApiException(ApiStatus.VALIDATION_ERROR, "Cannot delete a measure unit type that is in use");
		}
		Queries.removeUnreferenced(em, measureUnitType, "Cannot delete a measure unit type that is in use");
	}

	public MeasureUnitType fetchMeasureUnitType(Long id) throws ApiException {

		MeasureUnitType measureUnitType = Queries.get(em, MeasureUnitType.class, id);
		if (measureUnitType == null) {
			throw new ApiException(ApiStatus.INVALID_REQUEST, "Invalid measure unit type ID");
		}

		return measureUnitType;
	}

}
