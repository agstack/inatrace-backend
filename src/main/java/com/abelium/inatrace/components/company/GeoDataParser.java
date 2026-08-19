package com.abelium.inatrace.components.company;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses the "Geo Data" cell of the farmer import spreadsheet into plot geometries.
 *
 * <p>Field data reaches INATrace in whatever shape the collecting tool produced, so several
 * formats are recognised and auto-detected (first match wins):</p>
 *
 * <ol>
 *     <li><b>WKT</b> - {@code POLYGON((...))}, {@code POINT(...)} or {@code MULTIPOLYGON(((...)))}.
 *         The import template documents {@code lat lon}, but real OGC WKT from QGIS/PostGIS is
 *         {@code lon lat}; see {@link #orientPoints}.</li>
 *     <li><b>ODK / KoboToolbox geoshape, geotrace and geopoint</b> - {@code lat lon alt accuracy}
 *         per point, points separated by {@code ;}. Altitude and accuracy are validated then
 *         discarded.</li>
 * </ol>
 *
 * <p>All results are returned as {@code [latitude, longitude]} pairs, the order the rest of the
 * import path and {@code ApiPlotCoordinate} use.</p>
 */
public final class GeoDataParser {

	private static final Logger logger = LoggerFactory.getLogger(GeoDataParser.class);

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

	private static final Pattern WKT_PREFIX = Pattern.compile(
			"^(POLYGON|POINT|MULTIPOLYGON)\\s*\\(", Pattern.CASE_INSENSITIVE);

	/**
	 * Parses a Geo Data cell value into one plot per geometry it contains.
	 *
	 * @param raw the raw cell value; blank means "no geo data", which is not an error
	 * @param countryCode the row's ISO 3166-1 alpha-2 country code, used only to disambiguate WKT
	 *                    axis order; may be {@code null}
	 * @return the plots found, in source order; empty when {@code raw} is blank
	 * @throws IllegalArgumentException if {@code raw} is non-blank but not recognisable geo data
	 */
	public static List<ParsedPlot> parse(String raw, String countryCode) {

		if (raw == null || raw.isBlank()) {
			return List.of();
		}

		String trimmed = raw.trim();

		if (WKT_PREFIX.matcher(trimmed).find()) {
			return parseWkt(trimmed, countryCode);
		}

		return parseOdk(trimmed);
	}

	// ---------------------------------------------------------------- WKT (rule 1)

	private static List<ParsedPlot> parseWkt(String raw, String countryCode) {

		Matcher matcher = WKT_PREFIX.matcher(raw);
		if (!matcher.find()) {
			throw new IllegalArgumentException("Not a WKT geometry: " + raw);
		}

		String keyword = matcher.group(1).toUpperCase(Locale.ROOT);
		String body = balancedGroup(raw, matcher.end() - 1);

		switch (keyword) {
			case "POINT": {
				List<double[]> points = orientPoints(coordinatePairs(body), countryCode);
				if (points.size() != 1) {
					throw new IllegalArgumentException("POINT must have exactly one coordinate pair");
				}
				return List.of(new ParsedPlot(null, GeoDataType.POINT, points));
			}
			case "POLYGON": {
				List<String> rings = topLevelGroups(body);
				if (rings.isEmpty()) {
					throw new IllegalArgumentException("POLYGON must contain a ring");
				}
				// Interior rings (holes) are dropped - a plot is stored as a single boundary.
				return List.of(polygonPlot(null, orientPoints(coordinatePairs(rings.get(0)), countryCode)));
			}
			case "MULTIPOLYGON": {
				List<String> polygons = topLevelGroups(body);
				if (polygons.isEmpty()) {
					throw new IllegalArgumentException("MULTIPOLYGON must contain a polygon");
				}
				List<ParsedPlot> plots = new ArrayList<>();
				for (int i = 0; i < polygons.size(); i++) {
					List<String> rings = topLevelGroups(polygons.get(i));
					if (rings.isEmpty()) {
						throw new IllegalArgumentException("MULTIPOLYGON member must contain a ring");
					}
					plots.add(polygonPlot(labelIndexed(null, i, polygons.size()),
							orientPoints(coordinatePairs(rings.get(0)), countryCode)));
				}
				return plots;
			}
			default:
				throw new IllegalArgumentException("Unsupported WKT geometry: " + keyword);
		}
	}

	/**
	 * Splits a comma-separated WKT coordinate list into raw {@code {first, second}} pairs, without
	 * yet deciding which of the two is the latitude - see {@link #orientPoints}.
	 */
	private static List<double[]> coordinatePairs(String coordinateList) {

		if (coordinateList.indexOf('(') >= 0) {
			throw new IllegalArgumentException("Unexpected nested parentheses in coordinate list");
		}

		List<double[]> pairs = new ArrayList<>();
		for (String rawPoint : coordinateList.split(",")) {
			String[] tokens = rawPoint.trim().split("\\s+");
			if (tokens.length != 2) {
				throw new IllegalArgumentException("Expected exactly 2 numbers per point: " + rawPoint);
			}
			pairs.add(new double[] { parseNumber(tokens[0]), parseNumber(tokens[1]) });
		}
		if (pairs.isEmpty()) {
			throw new IllegalArgumentException("Empty coordinate list");
		}
		return pairs;
	}

	/**
	 * Decides whether a WKT coordinate list is {@code lat lon} - the order this import template has
	 * always documented - or {@code lon lat}, the order real OGC WKT uses.
	 *
	 * <p>Around the equator both readings are usually inside the valid global ranges, so ranges
	 * alone cannot decide. The order is only flipped on positive evidence: the row's declared
	 * country is known, {@code lat lon} puts the plot outside that country, and {@code lon lat}
	 * puts it inside. Otherwise the documented {@code lat lon} reading is kept and validated
	 * strictly, so no file that imports today changes meaning or starts being rejected.</p>
	 */
	private static List<double[]> orientPoints(List<double[]> pairs, String countryCode) {

		double[] bbox = countryBoundingBox(countryCode);
		if (bbox != null) {
			boolean latLonFits = allInside(pairs, bbox, false);
			boolean lonLatFits = allInside(pairs, bbox, true);

			if (lonLatFits && !latLonFits) {
				logger.debug("Reading WKT coordinates as lon/lat: lat/lat order falls outside country {}",
						countryCode);
				return validated(swap(pairs));
			}
			if (latLonFits && !lonLatFits) {
				return validated(copy(pairs));
			}
		}

		return validated(copy(pairs));
	}

	private static List<double[]> swap(List<double[]> pairs) {
		List<double[]> swapped = new ArrayList<>(pairs.size());
		for (double[] pair : pairs) {
			swapped.add(new double[] { pair[1], pair[0] });
		}
		return swapped;
	}

	private static List<double[]> copy(List<double[]> pairs) {
		List<double[]> result = new ArrayList<>(pairs.size());
		for (double[] pair : pairs) {
			result.add(new double[] { pair[0], pair[1] });
		}
		return result;
	}

	/** Extracts the contents of the balanced parenthesis group that opens at {@code openIndex}. */
	private static String balancedGroup(String value, int openIndex) {

		if (openIndex < 0 || openIndex >= value.length() || value.charAt(openIndex) != '(') {
			throw new IllegalArgumentException("Expected '(' in: " + value);
		}

		int depth = 0;
		for (int i = openIndex; i < value.length(); i++) {
			char c = value.charAt(i);
			if (c == '(') {
				depth++;
			} else if (c == ')') {
				depth--;
				if (depth == 0) {
					if (!value.substring(i + 1).isBlank()) {
						throw new IllegalArgumentException("Trailing characters after geometry: " + value);
					}
					return value.substring(openIndex + 1, i);
				}
			}
		}
		throw new IllegalArgumentException("Unbalanced parentheses in: " + value);
	}

	/** Splits {@code "(a),(b)"} into the contents of each top-level group: {@code ["a", "b"]}. */
	private static List<String> topLevelGroups(String value) {

		List<String> groups = new ArrayList<>();
		int depth = 0;
		int start = -1;

		for (int i = 0; i < value.length(); i++) {
			char c = value.charAt(i);
			if (c == '(') {
				if (depth == 0) {
					start = i + 1;
				}
				depth++;
			} else if (c == ')') {
				depth--;
				if (depth == 0) {
					groups.add(value.substring(start, i));
				} else if (depth < 0) {
					throw new IllegalArgumentException("Unbalanced parentheses in: " + value);
				}
			} else if (depth == 0 && c != ',' && !Character.isWhitespace(c)) {
				throw new IllegalArgumentException("Unexpected character '" + c + "' in: " + value);
			}
		}

		if (depth != 0) {
			throw new IllegalArgumentException("Unbalanced parentheses in: " + value);
		}
		return groups;
	}

	// ---------------------------------------------------------------- ODK / Kobo (rule 2)

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

	private static String labelIndexed(String label, int index, int total) {
		if (total <= 1) {
			return label;
		}
		return label == null ? String.valueOf(index + 1) : label + "-" + (index + 1);
	}

	// ---------------------------------------------------------------- country bounding boxes

	private static final String COUNTRY_BBOX_RESOURCE = "/geo/country-bboxes.csv";

	/** Margin in degrees allowed around a country's bounding box before a point counts as outside. */
	private static final double BBOX_MARGIN_DEGREES = 0.5;

	private static final Map<String, double[]> COUNTRY_BBOXES = loadCountryBoundingBoxes();

	private static Map<String, double[]> loadCountryBoundingBoxes() {

		Map<String, double[]> boxes = new HashMap<>();

		try (InputStream in = GeoDataParser.class.getResourceAsStream(COUNTRY_BBOX_RESOURCE)) {
			if (in == null) {
				logger.warn("{} not found; WKT axis order will not be disambiguated by country",
						COUNTRY_BBOX_RESOURCE);
				return Collections.emptyMap();
			}
			BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
			String line;
			while ((line = reader.readLine()) != null) {
				if (line.isBlank() || line.startsWith("#") || line.startsWith("iso2")) {
					continue;
				}
				String[] fields = line.split(",");
				if (fields.length != 5) {
					continue;
				}
				boxes.put(fields[0].trim().toUpperCase(Locale.ROOT), new double[] {
						Double.parseDouble(fields[1].trim()),
						Double.parseDouble(fields[2].trim()),
						Double.parseDouble(fields[3].trim()),
						Double.parseDouble(fields[4].trim())
				});
			}
		} catch (IOException | RuntimeException e) {
			logger.warn("Could not read {}; WKT axis order will not be disambiguated by country",
					COUNTRY_BBOX_RESOURCE, e);
			return Collections.emptyMap();
		}

		return boxes;
	}

	/** @return {@code {minLat, minLon, maxLat, maxLon}}, or {@code null} when the country is unknown */
	private static double[] countryBoundingBox(String countryCode) {
		if (countryCode == null || countryCode.isBlank()) {
			return null;
		}
		return COUNTRY_BBOXES.get(countryCode.trim().toUpperCase(Locale.ROOT));
	}

	private static boolean allInside(List<double[]> pairs, double[] bbox, boolean swapped) {
		for (double[] pair : pairs) {
			double lat = swapped ? pair[1] : pair[0];
			double lon = swapped ? pair[0] : pair[1];
			if (lat < bbox[0] - BBOX_MARGIN_DEGREES || lat > bbox[2] + BBOX_MARGIN_DEGREES
					|| lon < bbox[1] - BBOX_MARGIN_DEGREES || lon > bbox[3] + BBOX_MARGIN_DEGREES) {
				return false;
			}
		}
		return true;
	}
}
