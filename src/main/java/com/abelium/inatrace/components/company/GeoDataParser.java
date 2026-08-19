package com.abelium.inatrace.components.company;

import java.util.ArrayList;
import java.util.List;

/**
 * Parses the "Geo Data" cell of the farmer import spreadsheet into plot geometries.
 *
 * <p>Field data reaches INATrace in whatever shape the collecting tool produced, so several
 * formats are recognised and auto-detected (first match wins):</p>
 *
 * <ol>
 *     <li><b>ODK / KoboToolbox geoshape, geotrace and geopoint</b> - {@code lat lon alt accuracy}
 *         per point, points separated by {@code ;}. Altitude and accuracy are validated then
 *         discarded.</li>
 * </ol>
 *
 * <p>All results are returned as {@code [latitude, longitude]} pairs, the order the rest of the
 * import path and {@code ApiPlotCoordinate} use.</p>
 */
public final class GeoDataParser {

	public enum GeoDataType {
		POLYGON,
		POINT
	}

	/** One plot parsed out of a Geo Data cell: its optional label and its lat/lon vertices. */
	public static final class ParsedPlot {

		private final String label;
		private final GeoDataType type;
		private final List<double[]> points;

		ParsedPlot(String label, GeoDataType type, List<double[]> points) {
			this.label = label;
			this.type = type;
			this.points = points;
		}

		/** Plot label when the source carried one (e.g. {@code P1}), otherwise {@code null}. */
		public String getLabel() {
			return label;
		}

		public GeoDataType getType() {
			return type;
		}

		/** The vertices as {@code {latitude, longitude}} pairs, in the order they were recorded. */
		public List<double[]> getPoints() {
			return points;
		}
	}

	private GeoDataParser() {
	}

	// ---------------------------------------------------------------- entry point

	/**
	 * Parses a Geo Data cell value into one plot per geometry it contains.
	 *
	 * @param raw the raw cell value; blank means "no geo data", which is not an error
	 * @param countryCode the row's ISO 3166-1 alpha-2 country code; reserved for formats whose
	 *                    axis order is ambiguous, may be {@code null}
	 * @return the plots found, in source order; empty when {@code raw} is blank
	 * @throws IllegalArgumentException if {@code raw} is non-blank but not recognisable geo data
	 */
	public static List<ParsedPlot> parse(String raw, String countryCode) {

		if (raw == null || raw.isBlank()) {
			return List.of();
		}

		String trimmed = raw.trim();

		return parseOdk(trimmed);
	}

	// ---------------------------------------------------------------- ODK / Kobo (rule 1)

	/**
	 * Parses the ODK / KoboToolbox geoshape, geotrace and geopoint syntax:
	 * {@code lat lon altitude accuracy}, points separated by {@code ;}. Altitude and accuracy are
	 * optional and are discarded once parsed.
	 */
	private static List<ParsedPlot> parseOdk(String raw) {

		List<double[]> points = new ArrayList<>();

		for (String rawPoint : raw.split(";")) {
			if (rawPoint.isBlank()) {
				continue;
			}
			String[] tokens = rawPoint.trim().split("\\s+");
			if (tokens.length < 2 || tokens.length > 4) {
				throw new IllegalArgumentException(
						"Expected \"latitude longitude [altitude [accuracy]]\" per point: " + rawPoint.trim());
			}
			// Altitude and accuracy must still be numbers for the value to be geo data at all.
			for (String token : tokens) {
				parseNumber(token);
			}
			points.add(new double[] { parseNumber(tokens[0]), parseNumber(tokens[1]) });
		}

		if (points.isEmpty()) {
			throw new IllegalArgumentException("No coordinates found");
		}
		validated(points);

		if (points.size() == 1) {
			return List.of(new ParsedPlot(null, GeoDataType.POINT, points));
		}
		return List.of(polygonPlot(null, points));
	}

	// ---------------------------------------------------------------- shared validation

	private static double parseNumber(String token) {
		try {
			return Double.parseDouble(token);
		} catch (NumberFormatException e) {
			throw new IllegalArgumentException("Not a number: " + token, e);
		}
	}

	/** Validates global latitude/longitude ranges, returning the same list for chaining. */
	private static List<double[]> validated(List<double[]> points) {
		for (double[] point : points) {
			if (point[0] < -90 || point[0] > 90) {
				throw new IllegalArgumentException("Latitude out of range: " + point[0]);
			}
			if (point[1] < -180 || point[1] > 180) {
				throw new IllegalArgumentException("Longitude out of range: " + point[1]);
			}
		}
		return points;
	}

	/**
	 * Builds a polygon plot, rejecting rings that cannot enclose an area. The closing vertex that
	 * geoshape and WKT rings repeat does not count towards the minimum, but is kept in the result so
	 * the boundary is stored exactly as it was recorded.
	 */
	private static ParsedPlot polygonPlot(String label, List<double[]> points) {

		int distinct = 0;
		List<double[]> seen = new ArrayList<>();
		for (double[] point : points) {
			boolean duplicate = false;
			for (double[] existing : seen) {
				if (existing[0] == point[0] && existing[1] == point[1]) {
					duplicate = true;
					break;
				}
			}
			if (!duplicate) {
				seen.add(point);
				distinct++;
			}
		}

		if (distinct < 3) {
			throw new IllegalArgumentException("A plot boundary needs at least 3 distinct vertices, found " + distinct);
		}
		return new ParsedPlot(label, GeoDataType.POLYGON, points);
	}

	private static List<ParsedPlot> requireNonEmpty(List<ParsedPlot> plots) {
		if (plots.isEmpty()) {
			throw new IllegalArgumentException("No geometry found");
		}
		return plots;
	}
}
