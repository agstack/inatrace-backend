package com.abelium.inatrace.components.company;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.abelium.inatrace.components.company.types.UserCustomerImportCellErrorType;

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
 * against {@code /geo/kobo-header-synonyms.csv}. Geometry questions require a recognisable
 * header; unknown or ambiguous repeat sheets are reported instead of guessing from values.</p>
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
		private final String sheetName;
		private final int rowNum;
		private final int columnNum;

		KoboPlot(String geoData, Double size, Integer numberOfPlants, String sheetName, int rowNum, int columnNum) {
			this.geoData = geoData;
			this.size = size;
			this.numberOfPlants = numberOfPlants;
			this.sheetName = sheetName;
			this.rowNum = rowNum;
			this.columnNum = columnNum;
		}

		public String getSheetName() { return sheetName; }
		public int getRowNum() { return rowNum; }
		public int getColumnNum() { return columnNum; }

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
		private final Map<Concept, Integer> columns;
		private final List<KoboPlot> plots = new ArrayList<>();

		KoboFarmer(int rowNum, Map<Concept, String> fields, Map<Concept, Integer> columns) {
			this.rowNum = rowNum;
			this.fields = fields;
			this.columns = columns;
		}

		/** 0-based sheet row this submission came from, for validation error addresses. */
		public int getRowNum() {
			return rowNum;
		}

		public String get(Concept concept) {
			return fields.get(concept);
		}

		/** Zero-based column of a concept on the submission sheet, or {@code -1} when unmatched. */
		public int getColumn(Concept concept) {
			Integer column = columns.get(concept);
			return column == null ? -1 : column;
		}

		public List<KoboPlot> getPlots() {
			return plots;
		}
	}

	/**
	 * A problem found on a specific spreadsheet cell, so the import can answer with the same
	 * cell-addressed validation errors it uses for the INATrace template instead of aborting the
	 * whole file with a single message.
	 */
	public static final class KoboCellError {

		private final String sheetName;
		private final int rowNum;
		private final int columnNum;
		private final UserCustomerImportCellErrorType errorType;

		KoboCellError(String sheetName, int rowNum, int columnNum, UserCustomerImportCellErrorType errorType) {
			this.sheetName = sheetName;
			this.rowNum = rowNum;
			this.columnNum = columnNum;
			this.errorType = errorType;
		}

		public String getSheetName() { return sheetName; }
		public int getRowNum() { return rowNum; }
		public int getColumnNum() { return columnNum; }
		public UserCustomerImportCellErrorType getErrorType() { return errorType; }
	}

	/** What a read produced, including the concepts no column could be matched to. */
	public static final class KoboExport {

		private final List<KoboFarmer> farmers;
		private final Set<Concept> missingConcepts;
		private final List<String> headers;
		private final Map<String, List<String>> unresolvedSheets;
		private final List<KoboCellError> errors;

		KoboExport(List<KoboFarmer> farmers, Set<Concept> missingConcepts, List<String> headers) {
			this(farmers, missingConcepts, headers, Map.of(), List.of());
		}

		KoboExport(List<KoboFarmer> farmers, Set<Concept> missingConcepts, List<String> headers,
				Map<String, List<String>> unresolvedSheets) {
			this(farmers, missingConcepts, headers, unresolvedSheets, List.of());
		}

		KoboExport(List<KoboFarmer> farmers, Set<Concept> missingConcepts, List<String> headers,
				Map<String, List<String>> unresolvedSheets, List<KoboCellError> errors) {
			this.farmers = farmers;
			this.missingConcepts = missingConcepts;
			this.headers = headers;
			this.unresolvedSheets = unresolvedSheets;
			this.errors = errors;
		}

		public Map<String, List<String>> getUnresolvedSheets() {
			return unresolvedSheets;
		}

		/** Cell-level problems found while reading; the import reports them and persists nothing. */
		public List<KoboCellError> getErrors() {
			return errors;
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
		return read(workbook, Map.of());
	}

	/** A selection is a zero-based column index, or -1 to explicitly ignore a repeat sheet. */
	public static KoboExport read(XSSFWorkbook workbook, Map<String, Integer> selections) {

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
		int idColumn = headers.indexOf("_id");

		List<KoboFarmer> farmers = new ArrayList<>();
		List<KoboCellError> errors = new ArrayList<>();
		Map<String, KoboFarmer> byIndex = new LinkedHashMap<>();
		Map<String, KoboFarmer> byId = new LinkedHashMap<>();

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

			if (fields.isEmpty() && !hasNonSystemValues(row, headers, -1)) {
				continue; // empty submission row
			}

			KoboFarmer farmer = new KoboFarmer(r, fields, columns);
			farmers.add(farmer);

			if (indexColumn >= 0) {
				String index = stringValue(row.getCell(indexColumn));
				if (index != null && !index.isBlank() && byIndex.putIfAbsent(index.trim(), farmer) != null) {
					errors.add(new KoboCellError(submissions.getSheetName(), r, indexColumn,
							UserCustomerImportCellErrorType.INVALID_VALUE));
				}
			}
			if (idColumn >= 0) {
				String id = stringValue(row.getCell(idColumn));
				if (id != null && !id.isBlank() && byId.putIfAbsent(id.trim(), farmer) != null) {
					errors.add(new KoboCellError(submissions.getSheetName(), r, idColumn,
							UserCustomerImportCellErrorType.INVALID_VALUE));
				}
			}
		}

		Map<String, List<String>> unresolved = new LinkedHashMap<>();
		Set<String> usedSelections = new LinkedHashSet<>();
		for (int s = 1; s < workbook.getNumberOfSheets(); s++) {
			Sheet sheet = workbook.getSheetAt(s);
			List<String> sheetHeaders = rawHeaders(sheet);
			if (!sheetHeaders.contains(PARENT_INDEX) && !sheetHeaders.contains(SUBMISSION_ID)) continue;
			boolean populated = false;
			for (int r = 1; r <= sheet.getLastRowNum(); r++) {
				if (hasNonSystemValues(sheet.getRow(r), sheetHeaders, -1)) { populated = true; break; }
			}
			if (!populated) continue;
			int detected = findGeoColumn(sheet, sheetHeaders);
			Integer selected = selections.get(sheet.getSheetName());
			if (selected != null) {
				usedSelections.add(sheet.getSheetName());
				if (detected >= 0 || selected < -1 || selected >= sheetHeaders.size()
						|| (selected >= 0 && (sheetHeaders.get(selected).isBlank() || sheetHeaders.get(selected).startsWith("_")))) {
					throw new IllegalArgumentException("Invalid selection for repeat sheet \"" + sheet.getSheetName() + "\"");
				}
				if (selected == -1) continue;
				detected = selected;
			} else if (detected < 0) {
				unresolved.put(sheet.getSheetName(), sheetHeaders);
				continue;
			}
			readRepeatSheet(sheet, farmers, byIndex, byId, detected, errors);
		}
		if (usedSelections.size() != selections.size()) throw new IllegalArgumentException("Invalid repeat sheet selection");

		return new KoboExport(farmers, Set.of(), headers, unresolved, errors);
	}

	/** Attaches the plots of one repeat-group sheet to the submissions they belong to. */
	private static void readRepeatSheet(Sheet sheet, List<KoboFarmer> farmers, Map<String, KoboFarmer> byIndex,
			Map<String, KoboFarmer> byId, int geoColumn, List<KoboCellError> errors) {

		List<String> headers = rawHeaders(sheet);

		int parentColumn = headers.indexOf(PARENT_INDEX);
		int submissionColumn = headers.indexOf(SUBMISSION_ID);
		if (parentColumn < 0 && !headers.contains(SUBMISSION_ID)) {
			return; // not a repeat group of the submission sheet
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
				if (hasNonSystemValues(row, headers, geoColumn)) {
					errors.add(new KoboCellError(sheet.getSheetName(), r, geoColumn,
							UserCustomerImportCellErrorType.INVALID_GEODATA));
				}
				continue;
			}

			KoboFarmer farmer = null;
			if (parentColumn >= 0) {
				String parentIndex = stringValue(row.getCell(parentColumn));
				if (parentIndex != null) {
					farmer = byIndex.get(parentIndex.trim());
				}
			}
			if (submissionColumn >= 0) {
				String submissionId = stringValue(row.getCell(submissionColumn));
				if (submissionId != null && !submissionId.isBlank()) {
					KoboFarmer bySubmissionId = byId.get(submissionId.trim());
					if (farmer != null && bySubmissionId != farmer) {
						errors.add(new KoboCellError(sheet.getSheetName(), r, submissionColumn,
								UserCustomerImportCellErrorType.INVALID_VALUE));
						continue;
					}
					farmer = bySubmissionId;
				}
			}
			if (farmer == null) {
				errors.add(new KoboCellError(sheet.getSheetName(), r,
						parentColumn >= 0 ? parentColumn : submissionColumn,
						UserCustomerImportCellErrorType.INVALID_VALUE));
				continue;
			}

			farmer.getPlots().add(new KoboPlot(
					geoData.trim(),
					sizeColumn == null ? null : doubleValue(row.getCell(sizeColumn)),
					plantsColumn == null ? null : integerValue(row.getCell(plantsColumn)),
					sheet.getSheetName(), r, geoColumn));
		}

		if (farmers.isEmpty()) {
			logger.warn("Repeat sheet \"{}\" has plots but there are no submissions to attach them to",
					sheet.getSheetName());
		}
	}

	/**
	 * Identify geometry by its question name, not just by parseable numeric values: arbitrary
	 * survey answers can look like coordinates and must never silently become plot boundaries.
	 */
	private static int findGeoColumn(Sheet sheet, List<String> headers) {
		int identified = -1;
		for (int c = 0; c < headers.size(); c++) {
			List<String> terms = words(headers.get(c));
			boolean counting = terms.stream().anyMatch(Set.of("count", "number", "nombre", "cantidad", "numero")::contains);
			boolean geometry = terms.stream().anyMatch(Set.of("gps", "geodata", "geojson", "geoshape", "geopoint",
					"geotrace", "geometry", "geometrie", "geometria", "polygon", "polygone", "coordinates",
					"coordonnees", "coordenadas")::contains)
					|| (terms.contains("geo") && terms.contains("data"));
			if (geometry && !counting) {
				if (identified >= 0) return -2;
				identified = c;
			}
		}
		return identified;
	}

	private static boolean hasNonSystemValues(Row row, List<String> headers, int excludedColumn) {
		if (row == null) return false;
		for (int c = 0; c < headers.size(); c++) {
			if (c == excludedColumn || headers.get(c).startsWith("_")) continue;
			String value = stringValue(row.getCell(c));
			if (value != null && !value.isBlank()) return true;
		}
		return false;
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
