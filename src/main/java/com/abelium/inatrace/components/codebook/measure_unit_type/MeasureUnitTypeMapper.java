package com.abelium.inatrace.components.codebook.measure_unit_type;

import com.abelium.inatrace.components.codebook.measure_unit_type.api.ApiMeasureUnitType;
import com.abelium.inatrace.db.entities.codebook.MeasureUnitType;

import java.util.HashSet;
import java.util.Set;

/**
 * Mapper for MeasureUnitType entity.
 *
 * @author Pece Adjievski, Sunesis d.o.o.
 */
public final class MeasureUnitTypeMapper {

	private MeasureUnitTypeMapper() {
		throw new IllegalStateException("Utility class");
	}

	/**
	 * Mapping the base entity attributes - no associations are included.
	 *
	 * @param entity DB entity.
	 * @return API model entity.
	 */
	public static ApiMeasureUnitType toApiMeasureUnitTypeBase(MeasureUnitType entity) {
		if(entity == null) return null;
		ApiMeasureUnitType apiMeasureUnitType = new ApiMeasureUnitType();
		apiMeasureUnitType.setId(entity.getId());
		apiMeasureUnitType.setCode(entity.getCode());
		apiMeasureUnitType.setLabel(entity.getLabel());

		return apiMeasureUnitType;
	}

	/**
	 * Mapping of the base attributes and all the associations.
	 *
	 * @param entity DB entity.
	 * @return API model entity.
	 */
	public static ApiMeasureUnitType toApiMeasureUnitType(MeasureUnitType entity) {
		return toApiMeasureUnitType(entity, new HashSet<>());
	}

	/**
	 * Internal method with visited tracking to prevent infinite recursion.
	 *
	 * @param entity DB entity.
	 * @param visited Set of already processed entity IDs to prevent cycles.
	 * @return API model entity.
	 */
	private static ApiMeasureUnitType toApiMeasureUnitType(MeasureUnitType entity, Set<Long> visited) {
		if (entity == null) {
			return null;
		}

		// Prevent infinite recursion by checking if we've already processed this entity
		if (visited.contains(entity.getId())) {
			// Return a minimal version without the recursive relationship
			return toApiMeasureUnitTypeBase(entity);
		}

		// Mark this entity as being processed
		visited.add(entity.getId());

		ApiMeasureUnitType apiMeasureUnitType = MeasureUnitTypeMapper.toApiMeasureUnitTypeBase(entity);

		apiMeasureUnitType.setWeight(entity.getWeight());

		if (entity.getUnderlyingMeasurementUnitType() != null) {
			apiMeasureUnitType.setUnderlyingMeasurementUnitType(
					MeasureUnitTypeMapper.toApiMeasureUnitType(entity.getUnderlyingMeasurementUnitType(), visited));
		}

		return apiMeasureUnitType;
	}
}
