package com.abelium.inatrace.components.company;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Reads a KoboToolbox / ODK export workbook directly, so that a collection can be imported without
 * first being re-keyed into the INATrace template.
 *
 * <p>Re-keying is what this avoids: in the collection that motivated this reader, the hand-copied
 * template had the plots of three farmers shifted onto the wrong rows and lost one plot entirely.
 * The export is the source of truth and already links every plot to its farmer.</p>
 *
 * <p>An export has one sheet per form, plus one extra sheet per repeat group. Kobo adds its own
 * {@code _}-prefixed system columns, which are stable across forms and languages and are what this
 * reader keys on; the question columns are named by whoever built the form, so they are matched
 * against {@code /geo/kobo-header-synonyms.csv}. The geo column is found by the shape of its
 * values rather than by its header, which makes it immune to the form's wording entirely.</p>
 */
public final class KoboExportReader {

	private static final Logger logger = LoggerFactory.getLogger(KoboExportReader.class);

	/** Farmer fields this reader can recognise in an export. */
	public enum Concept {
		SMARTPHONE,
		EXCLUDE_COUNTING,
		FIRST_NAME,
		LAST_NAME,
		CITY,
		STATE,
		COUNTRY,
		GENDER,
		PHONE,
		EMAIL,
		INTERNAL_ID,
		PLOT_SIZE,
		PLOT_PLANTS
	}

	/** One plot of a repeat group. */
	public static final class KoboPlot {

		private final String geoData;
		private final Double size;
		private final Integer numberOfPlants;

		KoboPlot(String geoData, Double size, Integer numberOfPlants) {
			this.geoData = geoData;
			this.size = size;
			this.numberOfPlants = numberOfPlants;
		}

		public String getGeoData() {
			return geoData;
		}

		public Double getSize() {
			return size;
		}

		public Integer getNumberOfPlants() {
			return numberOfPlants;
		}
	}

	/** One submission: the farmer's recognised fields plus every plot of its repeat groups. */
	public static final class KoboFarmer {

		private final int rowNum;
		private final Map<Concept, String> fields;
		private final List<KoboPlot> plots = new ArrayList<>();

		KoboFarmer(int rowNum, Map<Concept, String> fields) {
			this.rowNum = rowNum;
			this.fields = fields;
		}

		/** 0-based sheet row this submission came from, for validation error addresses. */
		public int getRowNum() {
			return rowNum;
		}

		public String get(Concept concept) {
			return fields.get(concept);
		}

		public List<KoboPlot> getPlots() {
			return plots;
		}
	}

	/** What a read produced, including the concepts no column could be matched to. */
	public static final class KoboExport {

		private final List<KoboFarmer> farmers;
		private final Set<Concept> missingConcepts;
		private final List<String> headers;

		KoboExport(List<KoboFarmer> farmers, Set<Concept> missingConcepts, List<String> headers) {
			this.farmers = farmers;
			this.missingConcepts = missingConcepts;
			this.headers = headers;
		}

		public List<KoboFarmer> getFarmers() {
			return farmers;
		}

		/** Required concepts that no column matched - the import cannot proceed without these. */
		public Set<Concept> getMissingConcepts() {
			return missingConcepts;
		}

		/** The headers that were seen, so an error can tell the user what to rename. */
		public List<String> getHeaders() {
			return headers;
		}
	}

	private KoboExportReader() {
	}

	// ---------------------------------------------------------------- detection

	/** Kobo system columns on a submission sheet. Present in every export, in every language. */
	private static final List<String> SUBMISSION_MARKERS = List.of("_uuid", "_submission_time", "_index", "_id");

	/** Kobo system columns that link a repeat-group row back to its submission. */
	private static final String PARENT_INDEX = "_parent_index";
	private static final String SUBMISSION_ID = "_submission__id";

	/** Concepts a farmer cannot be created without. */
	private static final Set<Concept> REQUIRED_CONCEPTS =
			Set.of(Concept.LAST_NAME, Concept.CITY, Concept.STATE, Concept.COUNTRY, Concept.GENDER);

	/**
	 * @return {@code true} if this workbook is a KoboToolbox export rather than an INATrace farmer
	 *         template
	 */
	public static boolean isKoboExport(XSSFWorkbook workbook) {

		if (workbook.getNumberOfSheets() == 0) {
			return false;
		}
		List<String> headers = rawHeaders(workbook.getSheetAt(0));

		long markers = SUBMISSION_MARKERS.stream().filter(headers::contains).count();
		return markers >= 2;
	}

	// ---------------------------------------------------------------- reading

	public static KoboExport read(XSSFWorkbook workbook) {

		Sheet submissions = workbook.getSheetAt(0);
		List<String> headers = rawHeaders(submissions);

		Map<Concept, Integer> columns = matchColumns(headers);

		Set<Concept> missing = new LinkedHashSet<>();
		for (Concept concept : REQUIRED_CONCEPTS) {
			if (!columns.containsKey(concept)) {
				missing.add(concept);
			}
		}
		if (!missing.isEmpty()) {
			return new KoboExport(List.of(), missing, headers);
		}

		int indexColumn = headers.indexOf("_index");

		List<KoboFarmer> farmers = new ArrayList<>();
		Map<String, KoboFarmer> byIndex = new LinkedHashMap<>();

		for (int r = 1; r <= submissions.getLastRowNum(); r++) {
			Row row = submissions.getRow(r);
			if (row == null) {
				continue;
			}

			Map<Concept, String> fields = new EnumMap<>(Concept.class);
			for (Map.Entry<Concept, Integer> entry : columns.entrySet()) {
				String value = stringValue(row.getCell(entry.getValue()));
				if (value != null && !value.isBlank()) {
					fields.put(entry.getKey(), value.trim());
				}
			}

			if (fields.get(Concept.LAST_NAME) == null) {
				continue; // not a submission row
			}

			KoboFarmer farmer = new KoboFarmer(r, fields);
			farmers.add(farmer);

			if (indexColumn >= 0) {
				String index = stringValue(row.getCell(indexColumn));
				if (index != null && !index.isBlank()) {
					byIndex.put(index.trim(), farmer);
				}
			}
		}

		for (int s = 1; s < workbook.getNumberOfSheets(); s++) {
			readRepeatSheet(workbook.getSheetAt(s), farmers, byIndex);
		}

		return new KoboExport(farmers, Set.of(), headers);
	}

	/** Attaches the plots of one repeat-group sheet to the submissions they belong to. */
	private static void readRepeatSheet(Sheet sheet, List<KoboFarmer> farmers, Map<String, KoboFarmer> byIndex) {

		List<String> headers = rawHeaders(sheet);

		int parentColumn = headers.indexOf(PARENT_INDEX);
		if (parentColumn < 0 && !headers.contains(SUBMISSION_ID)) {
			return; // not a repeat group of the submission sheet
		}

		int geoColumn = findGeoColumn(sheet, headers.size());
		if (geoColumn < 0) {
			logger.debug("Repeat sheet \"{}\" carries no geo data column; skipped", sheet.getSheetName());
			return;
		}

		Map<Concept, Integer> columns = matchColumns(headers);
		Integer sizeColumn = columns.get(Concept.PLOT_SIZE);
		Integer plantsColumn = columns.get(Concept.PLOT_PLANTS);

		for (int r = 1; r <= sheet.getLastRowNum(); r++) {
			Row row = sheet.getRow(r);
			if (row == null) {
				continue;
			}

			String geoData = stringValue(row.getCell(geoColumn));
			if (geoData == null || geoData.isBlank()) {
				continue;
			}

			KoboFarmer farmer = null;
			if (parentColumn >= 0) {
				String parentIndex = stringValue(row.getCell(parentColumn));
				if (parentIndex != null) {
					farmer = byIndex.get(parentIndex.trim());
				}
			}
			if (farmer == null) {
				logger.warn("Plot on row {} of sheet \"{}\" references an unknown submission; skipped",
						r + 1, sheet.getSheetName());
				continue;
			}

			farmer.getPlots().add(new KoboPlot(
					geoData.trim(),
					sizeColumn == null ? null : doubleValue(row.getCell(sizeColumn)),
					plantsColumn == null ? null : integerValue(row.getCell(plantsColumn))));
		}

		if (farmers.isEmpty()) {
			logger.warn("Repeat sheet \"{}\" has plots but there are no submissions to attach them to",
					sheet.getSheetName());
		}
	}

	/**
	 * Finds the geo data column by the shape of its values rather than its header: the first column
	 * whose non-blank values all parse as geo data. Form authors name this question anything from
	 * "Prise du polygone de la parcelle" to "GPS", so the header is no help.
	 */
	private static int findGeoColumn(Sheet sheet, int columnCount) {

		for (int c = 0; c < columnCount; c++) {

			int parsed = 0;
			boolean allParsed = true;

			for (int r = 1; r <= sheet.getLastRowNum() && parsed < 3; r++) {
				Row row = sheet.getRow(r);
				String value = row == null ? null : stringValue(row.getCell(c));
				if (value == null || value.isBlank()) {
					continue;
				}
				try {
					GeoDataParser.parse(value.trim(), null);
					parsed++;
				} catch (IllegalArgumentException e) {
					allParsed = false;
					break;
				}
			}

			if (allParsed && parsed > 0) {
				return c;
			}
		}

		return -1;
	}

	// ---------------------------------------------------------------- header matching

	private static List<String> rawHeaders(Sheet sheet) {

		Row header = sheet.getRow(0);
		if (header == null) {
			return List.of();
		}

		List<String> headers = new ArrayList<>();
		for (int c = 0; c < header.getLastCellNum(); c++) {
			String value = stringValue(header.getCell(c));
			headers.add(value == null ? "" : value.trim());
		}
		return headers;
	}

	/** Maps each recognised concept to the left-most column whose header matches it. */
	static Map<Concept, Integer> matchColumns(List<String> headers) {

		Map<Concept, Integer> columns = new EnumMap<>(Concept.class);

		for (int c = 0; c < headers.size(); c++) {

			String header = headers.get(c);
			if (header.isBlank() || header.startsWith("_")) {
				continue; // Kobo system column
			}

			List<String> words = words(header);
			if (words.isEmpty()) {
				continue;
			}
			// "Nombre de parcelle" counts plots; it is not the Spanish "Nombre" (name). The guard
			// is deliberately narrow - the same words legitimately appear in plot size and plant
			// count headers.
			boolean counting = matches(words, Concept.EXCLUDE_COUNTING);

			for (Concept concept : Concept.values()) {
				if (concept == Concept.EXCLUDE_COUNTING || columns.containsKey(concept)) {
					continue;
				}
				if (counting && concept == Concept.FIRST_NAME) {
					continue;
				}
				if (matches(words, concept)) {
					columns.put(concept, c);
					break;
				}
			}
		}

		return columns;
	}

	private static boolean matches(List<String> words, Concept concept) {
		for (List<String> synonym : SYNONYMS.getOrDefault(concept, List.of())) {
			if (containsSequence(words, synonym)) {
				return true;
			}
		}
		return false;
	}

	private static boolean containsSequence(List<String> words, List<String> sequence) {
		if (sequence.isEmpty() || sequence.size() > words.size()) {
			return false;
		}
		outer:
		for (int i = 0; i <= words.size() - sequence.size(); i++) {
			for (int j = 0; j < sequence.size(); j++) {
				if (!words.get(i + j).equals(sequence.get(j))) {
					continue outer;
				}
			}
			return true;
		}
		return false;
	}

	/**
	 * Reduces a header to comparable words: drops any "(...)" qualifier, strips accents, and splits
	 * on everything that is not a letter or digit. "Numéro de téléphone" becomes
	 * {@code [numero, de, telephone]}.
	 */
	static List<String> words(String header) {

		String withoutQualifiers = header.replaceAll("\\([^)]*\\)", " ");
		String stripped = Normalizer.normalize(withoutQualifiers, Normalizer.Form.NFD)
				.replaceAll("\\p{M}", "")
				.toLowerCase(Locale.ROOT);

		List<String> words = new ArrayList<>();
		for (String word : stripped.split("[^a-z0-9]+")) {
			if (!word.isBlank()) {
				words.add(word);
			}
		}
		return words;
	}

	// ---------------------------------------------------------------- cell helpers

	private static String stringValue(Cell cell) {
		if (cell == null) {
			return null;
		}
		return switch (cell.getCellType()) {
			case STRING -> cell.getStringCellValue();
			case NUMERIC -> {
				double value = cell.getNumericCellValue();
				yield value == Math.floor(value) && !Double.isInfinite(value)
						? String.valueOf((long) value)
						: String.valueOf(value);
			}
			case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
			default -> null;
		};
	}

	private static Double doubleValue(Cell cell) {
		if (cell == null) {
			return null;
		}
		if (cell.getCellType() == CellType.NUMERIC) {
			return cell.getNumericCellValue();
		}
		String value = stringValue(cell);
		try {
			return value == null || value.isBlank() ? null : Double.valueOf(value.trim());
		} catch (NumberFormatException e) {
			return null;
		}
	}

	private static Integer integerValue(Cell cell) {
		Double value = doubleValue(cell);
		return value == null ? null : value.intValue();
	}

	// ---------------------------------------------------------------- synonym table

	private static final String SYNONYMS_RESOURCE = "/geo/kobo-header-synonyms.csv";

	private static final Map<Concept, List<List<String>>> SYNONYMS = loadSynonyms();

	private static Map<Concept, List<List<String>>> loadSynonyms() {

		Map<Concept, List<List<String>>> synonyms = new EnumMap<>(Concept.class);

		try (InputStream in = KoboExportReader.class.getResourceAsStream(SYNONYMS_RESOURCE)) {
			if (in == null) {
				logger.warn("{} not found; KoboToolbox exports cannot be mapped", SYNONYMS_RESOURCE);
				return Collections.emptyMap();
			}
			BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
			String line;
			while ((line = reader.readLine()) != null) {
				if (line.isBlank() || line.startsWith("#")) {
					continue;
				}
				int comma = line.indexOf(',');
				if (comma <= 0) {
					continue;
				}
				try {
					Concept concept = Concept.valueOf(line.substring(0, comma).trim());
					List<String> words = words(line.substring(comma + 1));
					if (!words.isEmpty()) {
						synonyms.computeIfAbsent(concept, c -> new ArrayList<>()).add(words);
					}
				} catch (IllegalArgumentException e) {
					logger.warn("Unknown concept in {}: {}", SYNONYMS_RESOURCE, line);
				}
			}
		} catch (IOException e) {
			logger.warn("Could not read {}; KoboToolbox exports cannot be mapped", SYNONYMS_RESOURCE, e);
			return Collections.emptyMap();
		}

		return synonyms;
	}
}
