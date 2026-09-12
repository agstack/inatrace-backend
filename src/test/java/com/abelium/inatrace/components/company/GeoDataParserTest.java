package com.abelium.inatrace.components.company;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
		// Duplicated point does not count towards the minimum of 3 distinct vertices
		assertThrows(IllegalArgumentException.class, () -> GeoDataParser.parse("1 2;3 4;1 2", null));
	}

	@Test
	void unrecognizedPrefix_isRejected() {
		assertThrows(IllegalArgumentException.class, () -> GeoDataParser.parse("not geodata at all", null));
	}
}
