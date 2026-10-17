package com.abelium.inatrace.components.company;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers every geo data format the farmer import accepts. The geoshape cases use real values
 * taken from a KoboToolbox collection, which is the kind of data that motivated multi-format
 * support in the first place.
 */
class GeoDataParserTest {

	/** First plot of a real collection, exactly as the collection tool wrote it (abridged). */
	private static final String REAL_GEOSHAPE =
			"5.1717367 10.2352433 1267.1000000000001 1.45;"
					+ "5.1718067 10.235235 1267.8 1.3;"
					+ "5.1719302 10.2352027 1267.0 1.3;"
					+ "5.1720846 10.2351663 1262.1000000000001 1.5;"
					+ "5.1717367 10.2352433 1267.1000000000001 1.45";

	// ------------------------------------------------------------------ absent

	@Test
	void blankCell_isAbsentGeodata_notAnError() {
		assertTrue(GeoDataParser.parse(null, null).isEmpty());
		assertTrue(GeoDataParser.parse("", null).isEmpty());
		assertTrue(GeoDataParser.parse("   ", null).isEmpty());
	}

	// ------------------------------------------------------------------ WKT

	@Test
	void validPolygon_isParsed() {
		List<GeoDataParser.ParsedPlot> plots =
				GeoDataParser.parse("POLYGON((5.17 10.23, 5.18 10.24, 5.19 10.25))", null);

		assertEquals(1, plots.size());
		GeoDataParser.ParsedPlot plot = plots.get(0);
		assertEquals(GeoDataParser.GeoDataType.POLYGON, plot.getType());
		assertEquals(3, plot.getPoints().size());
		assertEquals(5.17, plot.getPoints().get(0)[0]);
		assertEquals(10.23, plot.getPoints().get(0)[1]);
		assertNull(plot.getLabel());
	}

	@Test
	void validPoint_isParsed() {
		List<GeoDataParser.ParsedPlot> plots = GeoDataParser.parse("POINT(5.17 10.23)", null);

		assertEquals(1, plots.size());
		assertEquals(GeoDataParser.GeoDataType.POINT, plots.get(0).getType());
		assertEquals(1, plots.get(0).getPoints().size());
		assertEquals(5.17, plots.get(0).getPoints().get(0)[0]);
		assertEquals(10.23, plots.get(0).getPoints().get(0)[1]);
	}

	@Test
	void multiPolygon_becomesOnePlotPerPolygon() {
		List<GeoDataParser.ParsedPlot> plots = GeoDataParser.parse(
				"MULTIPOLYGON(((5.17 10.23, 5.18 10.24, 5.19 10.25)),((6.17 11.23, 6.18 11.24, 6.19 11.25)))",
				null);

		assertEquals(2, plots.size());
		assertEquals(5.17, plots.get(0).getPoints().get(0)[0]);
		assertEquals(6.17, plots.get(1).getPoints().get(0)[0]);
	}

	@Test
	void polygonHoles_areDropped() {
		List<GeoDataParser.ParsedPlot> plots = GeoDataParser.parse(
				"POLYGON((5.10 10.10, 5.90 10.10, 5.90 10.90, 5.10 10.90),(5.40 10.40, 5.60 10.40, 5.60 10.60))",
				null);

		assertEquals(1, plots.size());
		assertEquals(4, plots.get(0).getPoints().size());
	}

	@Test
	void malformedShape_isRejected() {
		// Missing closing parenthesis
		assertThrows(IllegalArgumentException.class, () -> GeoDataParser.parse("POLYGON((1 2, 3 4, 5 6)", null));
		// Non-numeric token
		assertThrows(IllegalArgumentException.class, () -> GeoDataParser.parse("POLYGON((1 2, a b, 5 6))", null));
		// Three numbers in one pair
		assertThrows(IllegalArgumentException.class, () -> GeoDataParser.parse("POLYGON((1 2 3, 4 5, 6 7))", null));
	}

	@Test
	void outOfRangeCoordinates_areRejected() {
		assertThrows(IllegalArgumentException.class, () -> GeoDataParser.parse("POINT(95 10.23)", null));
		assertThrows(IllegalArgumentException.class, () -> GeoDataParser.parse("POINT(5.17 190)", null));
		assertThrows(IllegalArgumentException.class,
				() -> GeoDataParser.parse("POLYGON((95 10, 5 11, 6 12))", null));
	}

	@Test
	void pointWithMoreThanOneCoordinatePair_isRejected() {
		assertThrows(IllegalArgumentException.class, () -> GeoDataParser.parse("POINT(1 2, 3 4)", null));
	}

	// ------------------------------------------------------------------ WKT axis order

	@Test
	void standardLonLatWkt_isDetectedFromTheDeclaredCountry() {
		// QGIS/PostGIS order: longitude first. Read as lat/lon this plot would sit west of Cameroon.
		List<GeoDataParser.ParsedPlot> plots = GeoDataParser.parse(
				"POLYGON((10.2352433 5.1717367, 10.235235 5.1718067, 10.2352027 5.1719302))", "CM");

		assertEquals(5.1717367, plots.get(0).getPoints().get(0)[0], 1e-9, "latitude");
		assertEquals(10.2352433, plots.get(0).getPoints().get(0)[1], 1e-9, "longitude");
	}

	@Test
	void documentedLatLonWkt_isKeptWhenItFitsTheCountry() {
		List<GeoDataParser.ParsedPlot> plots = GeoDataParser.parse(
				"POLYGON((5.1717367 10.2352433, 5.1718067 10.235235, 5.1719302 10.2352027))", "CM");

		assertEquals(5.1717367, plots.get(0).getPoints().get(0)[0], 1e-9, "latitude");
		assertEquals(10.2352433, plots.get(0).getPoints().get(0)[1], 1e-9, "longitude");
	}

	@Test
	void withoutAKnownCountry_theDocumentedLatLonOrderIsKept() {
		// No country, so no evidence to flip on: 10.23 stays the latitude.
		List<GeoDataParser.ParsedPlot> plots = GeoDataParser.parse(
				"POLYGON((10.2352433 5.1717367, 10.235235 5.1718067, 10.2352027 5.1719302))", "ZZ");

		assertEquals(10.2352433, plots.get(0).getPoints().get(0)[0], 1e-9, "latitude");
	}

	// ------------------------------------------------------------------ ODK / Kobo geoshape

	@Test
	void realKoboGeoshape_isParsedAsAPolygon() {
		List<GeoDataParser.ParsedPlot> plots = GeoDataParser.parse(REAL_GEOSHAPE, "CM");

		assertEquals(1, plots.size());
		GeoDataParser.ParsedPlot plot = plots.get(0);
		assertEquals(GeoDataParser.GeoDataType.POLYGON, plot.getType());
		assertEquals(5, plot.getPoints().size(), "the closing vertex is kept");
		assertEquals(5.1717367, plot.getPoints().get(0)[0]);
		assertEquals(10.2352433, plot.getPoints().get(0)[1]);
	}

	@Test
	void geoshapeAltitudeAndAccuracy_areDiscarded() {
		List<GeoDataParser.ParsedPlot> plots = GeoDataParser.parse(REAL_GEOSHAPE, "CM");

		for (double[] point : plots.get(0).getPoints()) {
			assertEquals(2, point.length, "only latitude and longitude are kept");
		}
	}

	@Test
	void geoshapeWithoutAltitudeOrAccuracy_isAccepted() {
		List<GeoDataParser.ParsedPlot> plots =
				GeoDataParser.parse("5.17 10.23;5.18 10.24;5.19 10.25;5.17 10.23", null);

		assertEquals(GeoDataParser.GeoDataType.POLYGON, plots.get(0).getType());
		assertEquals(4, plots.get(0).getPoints().size());
	}

	@Test
	void singleGeopoint_isParsedAsAPoint() {
		List<GeoDataParser.ParsedPlot> plots = GeoDataParser.parse("5.1717367 10.2352433 1267.1 1.45", null);

		assertEquals(1, plots.size());
		assertEquals(GeoDataParser.GeoDataType.POINT, plots.get(0).getType());
		assertEquals(5.1717367, plots.get(0).getPoints().get(0)[0]);
		assertEquals(10.2352433, plots.get(0).getPoints().get(0)[1]);
	}

	@Test
	void geoshapeWithTooManyNumbersPerPoint_isRejected() {
		assertThrows(IllegalArgumentException.class,
				() -> GeoDataParser.parse("5.17 10.23 100 1.4 9;5.18 10.24 100 1.4 9;5.19 10.25 100 1.4 9", null));
	}

	@Test
	void geoshapeOutOfRange_isRejected() {
		assertThrows(IllegalArgumentException.class,
				() -> GeoDataParser.parse("95.17 10.23 100 1.4;96.18 10.24 100 1.4;97.19 10.25 100 1.4", null));
	}

	@Test
	void polygonWithFewerThanThreeDistinctVertices_isRejected() {
		assertThrows(IllegalArgumentException.class, () -> GeoDataParser.parse("1 2;3 4", null));
		assertThrows(IllegalArgumentException.class, () -> GeoDataParser.parse("POLYGON((1 2, 3 4))", null));
		// Duplicated point does not count towards the minimum of 3 distinct vertices
		assertThrows(IllegalArgumentException.class, () -> GeoDataParser.parse("1 2;3 4;1 2", null));
		assertThrows(IllegalArgumentException.class, () -> GeoDataParser.parse("POLYGON((1 2, 3 4, 1 2))", null));
	}

	@Test
	void unrecognizedPrefix_isRejected() {
		// MULTIPOLYGON needs one more level of brackets than this
		assertThrows(IllegalArgumentException.class,
				() -> GeoDataParser.parse("MULTIPOLYGON((1 2, 3 4, 5 6))", null));
		assertThrows(IllegalArgumentException.class, () -> GeoDataParser.parse("not geodata at all", null));
	}

	// ------------------------------------------------------------------ labelled multi-plot

	@Test
	void labelledMultiPlotCell_becomesOnePlotPerLabel() {
		String cell = "P1(" + REAL_GEOSHAPE + ")P3(5.17 10.23;5.18 10.24;5.19 10.25;5.17 10.23)";

		List<GeoDataParser.ParsedPlot> plots = GeoDataParser.parse(cell, "CM");

		assertEquals(2, plots.size());
		assertEquals("P1", plots.get(0).getLabel());
		assertEquals(5, plots.get(0).getPoints().size());
		assertEquals("P3", plots.get(1).getLabel());
		assertEquals(4, plots.get(1).getPoints().size());
	}

	@Test
	void labelledMultiPlotCell_acceptsAnyInnerFormat() {
		List<GeoDataParser.ParsedPlot> plots = GeoDataParser.parse(
				"P1(POLYGON((5.17 10.23, 5.18 10.24, 5.19 10.25)))"
						+ "P2(5.20 10.30;5.21 10.31;5.22 10.32;5.20 10.30)", "CM");

		assertEquals(2, plots.size());
		assertEquals("P1", plots.get(0).getLabel());
		assertEquals("P2", plots.get(1).getLabel());
	}

	@Test
	void labelledPlotWithGarbageInside_isRejected() {
		assertThrows(IllegalArgumentException.class, () -> GeoDataParser.parse("P1(nonsense)", null));
		assertThrows(IllegalArgumentException.class, () -> GeoDataParser.parse("P1()", null));
	}

	// ------------------------------------------------------------------ GeoJSON

	@Test
	void geoJsonPolygon_isParsedInLonLatOrder() {
		List<GeoDataParser.ParsedPlot> plots = GeoDataParser.parse(
				"{\"type\":\"Polygon\",\"coordinates\":"
						+ "[[[10.2352433,5.1717367],[10.235235,5.1718067],[10.2352027,5.1719302],[10.2352433,5.1717367]]]}",
				null);

		assertEquals(1, plots.size());
		assertEquals(GeoDataParser.GeoDataType.POLYGON, plots.get(0).getType());
		assertEquals(5.1717367, plots.get(0).getPoints().get(0)[0], 1e-9, "latitude");
		assertEquals(10.2352433, plots.get(0).getPoints().get(0)[1], 1e-9, "longitude");
	}

	@Test
	void geoJsonFeature_isParsed() {
		List<GeoDataParser.ParsedPlot> plots = GeoDataParser.parse(
				"{\"type\":\"Feature\",\"properties\":{},\"geometry\":{\"type\":\"Polygon\",\"coordinates\":"
						+ "[[[10.23,5.17],[10.24,5.18],[10.25,5.19],[10.23,5.17]]]}}", null);

		assertEquals(1, plots.size());
		assertEquals(5.17, plots.get(0).getPoints().get(0)[0], 1e-9);
	}

	@Test
	void geoJsonFeatureCollection_becomesOnePlotPerFeature() {
		List<GeoDataParser.ParsedPlot> plots = GeoDataParser.parse(
				"{\"type\":\"FeatureCollection\",\"features\":["
						+ "{\"type\":\"Feature\",\"properties\":{},\"geometry\":{\"type\":\"Polygon\",\"coordinates\":"
						+ "[[[10.23,5.17],[10.24,5.18],[10.25,5.19],[10.23,5.17]]]}},"
						+ "{\"type\":\"Feature\",\"properties\":{},\"geometry\":{\"type\":\"Point\",\"coordinates\":[11.23,6.17]}}]}",
				null);

		assertEquals(2, plots.size());
		assertEquals(GeoDataParser.GeoDataType.POLYGON, plots.get(0).getType());
		assertEquals(GeoDataParser.GeoDataType.POINT, plots.get(1).getType());
		assertEquals(6.17, plots.get(1).getPoints().get(0)[0], 1e-9);
	}

	@Test
	void bareGeoJsonCoordinatesArray_isParsed() {
		List<GeoDataParser.ParsedPlot> plots = GeoDataParser.parse(
				"[[[10.23,5.17],[10.24,5.18],[10.25,5.19],[10.23,5.17]]]", null);

		assertEquals(1, plots.size());
		assertEquals(5.17, plots.get(0).getPoints().get(0)[0], 1e-9);
	}

	@Test
	void malformedGeoJson_isRejected() {
		assertThrows(IllegalArgumentException.class, () -> GeoDataParser.parse("{\"type\":", null));
		assertThrows(IllegalArgumentException.class, () -> GeoDataParser.parse("{\"coordinates\":[]}", null));
	}
}
