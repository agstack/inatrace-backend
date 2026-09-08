package com.abelium.inatrace.components.codebook.measure_unit_type;

import com.abelium.inatrace.components.codebook.measure_unit_type.api.ApiMeasureUnitType;
import com.abelium.inatrace.db.entities.codebook.MeasureUnitType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

import java.time.Duration;

/**
 * Mapping a measurement unit whose underlying-unit chain loops back on itself terminates.
 *
 * <p>Nothing stops such a loop being stored: {@code MeasureUnitTypeService.createOrUpdateMeasureUnitType}
 * fetches the underlying unit by id and assigns it with no cycle check, so a system administrator can
 * point a unit at itself, or at a unit that points back. The mapper then walks
 * {@code getUnderlyingMeasurementUnitType()} without remembering where it has been.
 *
 * <p>Against the unfixed mapper both tests below fail with {@link StackOverflowError}, which is what
 * the endpoint returns to the caller as a 500. The failure is a hard JVM error rather than an
 * exception, so it is asserted with a timeout guard as well.
 */
class MeasureUnitTypeMapperTest {

	/** {@code BaseEntity.setId} is protected, and these entities never reach a database here. */
	private static MeasureUnitType unit(long id, String code) {
		MeasureUnitType unit = new MeasureUnitType();
		unit.setCode(code);
		unit.setLabel(code);
		try {
			Field idField = Class.forName("com.abelium.inatrace.db.base.BaseEntity").getDeclaredField("id");
			idField.setAccessible(true);
			idField.set(unit, id);
		} catch (ReflectiveOperationException e) {
			throw new IllegalStateException("could not seed the entity id", e);
		}
		return unit;
	}

	@Test
	@DisplayName("A unit that is its own underlying unit maps without recursing forever")
	void selfReferencingUnitTerminates() {

		MeasureUnitType kilogram = unit(1L, "kg");
		kilogram.setUnderlyingMeasurementUnitType(kilogram);

		ApiMeasureUnitType mapped = assertTimeoutPreemptively(Duration.ofSeconds(5),
				() -> MeasureUnitTypeMapper.toApiMeasureUnitType(kilogram));

		assertNotNull(mapped);
		assertEquals("kg", mapped.getCode());
		// The second visit returns the base representation, which stops the walk.
		assertNotNull(mapped.getUnderlyingMeasurementUnitType());
		assertNull(mapped.getUnderlyingMeasurementUnitType().getUnderlyingMeasurementUnitType());
	}

	@Test
	@DisplayName("Two units pointing at each other map without recursing forever")
	void mutuallyReferencingUnitsTerminate() {

		MeasureUnitType sack = unit(1L, "sack");
		MeasureUnitType kilogram = unit(2L, "kg");
		sack.setUnderlyingMeasurementUnitType(kilogram);
		kilogram.setUnderlyingMeasurementUnitType(sack);

		ApiMeasureUnitType mapped = assertTimeoutPreemptively(Duration.ofSeconds(5),
				() -> MeasureUnitTypeMapper.toApiMeasureUnitType(sack));

		assertNotNull(mapped);
		assertEquals("sack", mapped.getCode());
		assertEquals("kg", mapped.getUnderlyingMeasurementUnitType().getCode());
		assertEquals("sack", mapped.getUnderlyingMeasurementUnitType()
				.getUnderlyingMeasurementUnitType().getCode());
		// Third hop is the base representation: the walk stops instead of looping.
		assertNull(mapped.getUnderlyingMeasurementUnitType()
				.getUnderlyingMeasurementUnitType().getUnderlyingMeasurementUnitType());
	}

	@Test
	@DisplayName("An ordinary chain still maps every level")
	void acyclicChainIsUnaffected() {

		MeasureUnitType gram = unit(3L, "g");
		MeasureUnitType kilogram = unit(2L, "kg");
		MeasureUnitType sack = unit(1L, "sack");
		sack.setUnderlyingMeasurementUnitType(kilogram);
		kilogram.setUnderlyingMeasurementUnitType(gram);

		ApiMeasureUnitType mapped = MeasureUnitTypeMapper.toApiMeasureUnitType(sack);

		assertEquals("sack", mapped.getCode());
		assertEquals("kg", mapped.getUnderlyingMeasurementUnitType().getCode());
		assertEquals("g", mapped.getUnderlyingMeasurementUnitType()
				.getUnderlyingMeasurementUnitType().getCode());
		assertNull(mapped.getUnderlyingMeasurementUnitType()
				.getUnderlyingMeasurementUnitType().getUnderlyingMeasurementUnitType());
	}
}
