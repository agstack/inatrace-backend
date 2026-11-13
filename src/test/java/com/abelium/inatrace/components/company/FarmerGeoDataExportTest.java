package com.abelium.inatrace.components.company;

import com.abelium.inatrace.components.company.api.ApiPlot;
import com.abelium.inatrace.components.company.api.ApiPlotCoordinate;
import com.abelium.inatrace.components.company.api.ApiUserCustomer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * The GeoJSON part of the farmer export must survive plots created without drawing the boundary.
 * An empty coordinate list used to make {@code prepareFarmersGeoDataFile} call {@code get(0)} and
 * fail the whole export with an IndexOutOfBoundsException.
 */
class FarmerGeoDataExportTest {

	private final ObjectMapper mapper = new ObjectMapper();
	private final CompanyService companyService = new CompanyService();

	@Test
	@DisplayName("exports an empty feature collection when the plot has no coordinates")
	void skipsPlotsWithoutCoordinates() {

		ApiUserCustomer farmer = farmerWithPlot(List.of());

		byte[] geoJson = assertDoesNotThrow(
				() -> (byte[]) ReflectionTestUtils.invokeMethod(companyService, "prepareFarmersGeoDataFile", List.of(farmer)));

		JsonNode featureCollection = read(geoJson);
		assertNotNull(featureCollection);
		assertEquals("FeatureCollection", featureCollection.get("type").asText());
		assertEquals(0, featureCollection.get("features").size());
	}

	@Test
	@DisplayName("exports a point for a plot with fewer than three coordinates")
	void exportsPointForShortCoordinateList() {

		ApiUserCustomer farmer = farmerWithPlot(List.of(coordinate(46.5, 15.6), coordinate(46.6, 15.7)));

		byte[] geoJson = (byte[]) ReflectionTestUtils.invokeMethod(
				companyService, "prepareFarmersGeoDataFile", List.of(farmer));

		JsonNode feature = read(geoJson).get("features").get(0);
		assertEquals("Point", feature.get("geometry").get("type").asText());
		assertEquals(15.6, feature.get("geometry").get("coordinates").get(0).asDouble());
		assertEquals(46.5, feature.get("geometry").get("coordinates").get(1).asDouble());
	}

	@Test
	@DisplayName("exports a polygon for a plot with three or more coordinates")
	void exportsPolygonForLongCoordinateList() {

		ApiUserCustomer farmer = farmerWithPlot(List.of(
				coordinate(46.5, 15.6), coordinate(46.6, 15.7), coordinate(46.7, 15.8)));

		byte[] geoJson = (byte[]) ReflectionTestUtils.invokeMethod(
				companyService, "prepareFarmersGeoDataFile", List.of(farmer));

		JsonNode feature = read(geoJson).get("features").get(0);
		assertEquals("Polygon", feature.get("geometry").get("type").asText());
	}

	private ApiUserCustomer farmerWithPlot(List<ApiPlotCoordinate> coordinates) {

		ApiPlot plot = new ApiPlot();
		plot.setId(1L);
		plot.setCoordinates(new ArrayList<>(coordinates));

		ApiUserCustomer farmer = new ApiUserCustomer();
		farmer.setId(1L);
		farmer.setPlots(List.of(plot));

		return farmer;
	}

	private ApiPlotCoordinate coordinate(double latitude, double longitude) {

		ApiPlotCoordinate coordinate = new ApiPlotCoordinate();
		coordinate.setLatitude(latitude);
		coordinate.setLongitude(longitude);
		return coordinate;
	}

	private JsonNode read(byte[] geoJson) {

		try {
			return mapper.readTree(new String(geoJson));
		} catch (Exception e) {
			throw new IllegalStateException("Invalid GeoJSON", e);
		}
	}
}
