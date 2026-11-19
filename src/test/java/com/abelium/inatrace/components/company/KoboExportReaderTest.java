package com.abelium.inatrace.components.company;

import com.abelium.inatrace.components.company.types.UserCustomerImportCellErrorType;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import java.io.FileInputStream;
import java.io.InputStream;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Drives the UCCAO KoboToolbox export (anonymised) - the file that motivated reading exports directly.
 *
 * <p>The regression this guards is concrete: when the same collection was copied into the INATrace
 * template by hand, the plots of three farmers ended up on the wrong rows and Essomba's second
 * parcel was lost. Read from the export, all 7 farmers and all 10 plots survive.</p>
 */
class KoboExportReaderTest {

	private static final String RESOURCES = "src/test/resources/farmer-import/";

	private static final String KOBO_EXPORT = RESOURCES + "UCCAO_kobo_export.xlsx";

	private static final String INATRACE_TEMPLATE = RESOURCES + "Template_list_of_farmers_other_countries_en.xlsx";

	@Test
	void koboExport_isRecognised_andTheInatraceTemplateIsNot() throws Exception {
		try (InputStream in = new FileInputStream(KOBO_EXPORT); XSSFWorkbook workbook = new XSSFWorkbook(in)) {
			assertTrue(KoboExportReader.isKoboExport(workbook));
		}
		try (InputStream in = new FileInputStream(INATRACE_TEMPLATE); XSSFWorkbook workbook = new XSSFWorkbook(in)) {
			assertFalse(KoboExportReader.isKoboExport(workbook),
					"the INATrace template must keep going down the template path");
		}
	}

	@Test
	void everyFarmerAndEveryPlotOfTheRealExportIsRead() throws Exception {
		KoboExportReader.KoboExport export = read();

		assertTrue(export.getMissingConcepts().isEmpty(),
				"unmatched fields: " + export.getMissingConcepts() + " in " + export.getHeaders());
		assertEquals(7, export.getFarmers().size(), "farmers");

		int plots = export.getFarmers().stream().mapToInt(f -> f.getPlots().size()).sum();
		assertEquals(10, plots, "plots across all farmers");
	}

	@Test
	void multiPlotFarmersKeepEveryPlot() throws Exception {
		Map<String, Integer> plotsByFarmer = read().getFarmers().stream().collect(
				java.util.stream.Collectors.toMap(
						f -> f.get(KoboExportReader.Concept.LAST_NAME).trim(),
						f -> f.getPlots().size()));

		assertEquals(1, plotsByFarmer.get("Clarisse"));
		assertEquals(2, plotsByFarmer.get("Kevine"));
		assertEquals(1, plotsByFarmer.get("Wamba"));
		// The parcel the hand-copied template lost is the third one here.
		assertEquals(3, plotsByFarmer.get("Essomba"));
	}

	@Test
	void farmerFieldsAreMappedFromFrenchHeaders() throws Exception {
		KoboExportReader.KoboFarmer first = read().getFarmers().get(0);

		assertEquals("Clarisse", first.get(KoboExportReader.Concept.LAST_NAME));
		assertEquals("Dongmo", first.get(KoboExportReader.Concept.FIRST_NAME));
		assertEquals("Bangou", first.get(KoboExportReader.Concept.CITY));
		assertEquals("Balessing", first.get(KoboExportReader.Concept.STATE));
		assertEquals("Cameroon", first.get(KoboExportReader.Concept.COUNTRY));
		assertEquals("Féminin", first.get(KoboExportReader.Concept.GENDER));
		assertEquals("600000000", first.get(KoboExportReader.Concept.PHONE));
		assertEquals("Oui", first.get(KoboExportReader.Concept.SMARTPHONE));
	}

	@Test
	void plotGeometryAndSurveyedSizeAreRead() throws Exception {
		KoboExportReader.KoboFarmer first = read().getFarmers().get(0);
		KoboExportReader.KoboPlot plot = first.getPlots().get(0);

		assertNotNull(plot.getGeoData());
		List<GeoDataParser.ParsedPlot> parsed = GeoDataParser.parse(plot.getGeoData(), "CM");
		assertEquals(1, parsed.size());
		assertEquals(GeoDataParser.GeoDataType.POLYGON, parsed.get(0).getType());
		assertEquals(28, parsed.get(0).getPoints().size());

		assertEquals(2.0, plot.getSize(), "surveyed total plot size in ha");
		assertEquals(200, plot.getNumberOfPlants());
	}

	@Test
	void countingColumnsAreNotMistakenForNames() {
		// "Nombre de parcelle" is a plot count, not the Spanish "Nombre" (first name).
		Map<KoboExportReader.Concept, Integer> columns = KoboExportReader.matchColumns(
				List.of("Nom*", "Prénom", "Nombre de parcelle (au plus 03)"));

		assertEquals(0, columns.get(KoboExportReader.Concept.LAST_NAME));
		assertEquals(1, columns.get(KoboExportReader.Concept.FIRST_NAME));
	}

	@Test
	void smartphoneColumnIsNotMistakenForAPhoneNumber() {
		Map<KoboExportReader.Concept, Integer> columns = KoboExportReader.matchColumns(
				List.of("Numéro de téléphone", "As-t-il un smartphone ?* (Oui/Non)"));

		assertEquals(0, columns.get(KoboExportReader.Concept.PHONE));
		assertEquals(1, columns.get(KoboExportReader.Concept.SMARTPHONE));
	}

	@Test
	void englishAndSpanishHeadersAreMatchedToo() {
		Map<KoboExportReader.Concept, Integer> english = KoboExportReader.matchColumns(
				List.of("Last name*", "First name", "City / Town / Village*", "State / Province*", "Country*", "Gender*"));
		assertEquals(0, english.get(KoboExportReader.Concept.LAST_NAME));
		assertEquals(1, english.get(KoboExportReader.Concept.FIRST_NAME));
		assertEquals(2, english.get(KoboExportReader.Concept.CITY));
		assertEquals(3, english.get(KoboExportReader.Concept.STATE));
		assertEquals(4, english.get(KoboExportReader.Concept.COUNTRY));
		assertEquals(5, english.get(KoboExportReader.Concept.GENDER));

		Map<KoboExportReader.Concept, Integer> spanish = KoboExportReader.matchColumns(
				List.of("Apellidos", "Nombre", "Ciudad", "Departamento", "País", "Sexo"));
		assertEquals(0, spanish.get(KoboExportReader.Concept.LAST_NAME));
		assertEquals(1, spanish.get(KoboExportReader.Concept.FIRST_NAME));
		assertEquals(2, spanish.get(KoboExportReader.Concept.CITY));
		assertEquals(3, spanish.get(KoboExportReader.Concept.STATE));
		assertEquals(4, spanish.get(KoboExportReader.Concept.COUNTRY));
		assertEquals(5, spanish.get(KoboExportReader.Concept.GENDER));
	}

	private KoboExportReader.KoboExport read() throws Exception {
		try (InputStream in = new FileInputStream(KOBO_EXPORT); XSSFWorkbook workbook = new XSSFWorkbook(in)) {
			return KoboExportReader.read(workbook);
		}
	}

	@Test
	void repeatSheetCanIdentifySubmissionById() throws Exception {
		try (XSSFWorkbook workbook = syntheticExport("42")) {
			KoboExportReader.KoboExport export = KoboExportReader.read(workbook);
			assertEquals(1, export.getFarmers().size());
			assertEquals(1, export.getFarmers().get(0).getPlots().size());
		}
	}

	@Test
	void repeatSheetReportsUnknownSubmissionOnTheReferenceCell() throws Exception {
		try (XSSFWorkbook workbook = syntheticExport("99")) {
			KoboExportReader.KoboExport export = KoboExportReader.read(workbook);
			assertEquals(1, export.getErrors().size());
			assertEquals(UserCustomerImportCellErrorType.INVALID_VALUE, export.getErrors().get(0).getErrorType());
			assertEquals("plots", export.getErrors().get(0).getSheetName());
			assertEquals(0, export.getErrors().get(0).getColumnNum());
		}
	}

	@Test
	void repeatSheetReportsConflictingReferencesOnTheSubmissionCell() throws Exception {
		try (XSSFWorkbook workbook = syntheticExport("42")) {
			var submissions = workbook.getSheetAt(0);
			var second = submissions.createRow(2);
			String[] values = {"Other", "Abuja", "FCT", "Nigeria", "Female", "43", "2"};
			for (int i = 0; i < values.length; i++) second.createCell(i).setCellValue(values[i]);
			var repeat = workbook.getSheetAt(1);
			repeat.getRow(0).createCell(2).setCellValue("_parent_index");
			repeat.getRow(1).createCell(2).setCellValue("2");
			KoboExportReader.KoboExport export = KoboExportReader.read(workbook);
			assertEquals(1, export.getErrors().size());
			assertEquals(UserCustomerImportCellErrorType.INVALID_VALUE, export.getErrors().get(0).getErrorType());
			assertEquals(0, export.getErrors().get(0).getColumnNum());
		}
	}

	@Test
	void filledRepeatRowWithoutGeometryIsReportedOnTheGeometryCell() throws Exception {
		try (XSSFWorkbook workbook = syntheticExport("42")) {
			var repeat = workbook.getSheetAt(1);
			repeat.getRow(0).createCell(2).setCellValue("plot size");
			repeat.getRow(1).getCell(1).setCellValue("");
			repeat.getRow(1).createCell(2).setCellValue("2");
			KoboExportReader.KoboExport export = KoboExportReader.read(workbook);
			assertEquals(1, export.getErrors().size());
			assertEquals(UserCustomerImportCellErrorType.INVALID_GEODATA, export.getErrors().get(0).getErrorType());
			assertEquals("plots", export.getErrors().get(0).getSheetName());
			assertEquals(1, export.getErrors().get(0).getColumnNum());
		}
	}

	@Test
	void malformedGeoValueDoesNotHideAColumnWithValidPlots() throws Exception {
		try (XSSFWorkbook workbook = syntheticExport("42")) {
			var repeat = workbook.getSheetAt(1);
			repeat.getRow(1).getCell(1).setCellValue("invalid geometry");
			var second = repeat.createRow(2);
			second.createCell(0).setCellValue("42");
			second.createCell(1).setCellValue("5.1 10.1");
			var plots = KoboExportReader.read(workbook).getFarmers().get(0).getPlots();
			assertEquals(2, plots.size());
			assertEquals("invalid geometry", plots.get(0).getGeoData());
		}
	}

	@Test
	void explicitGeoColumnWithOnlyInvalidValuesStillReachesValidation() throws Exception {
		try (XSSFWorkbook workbook = syntheticExport("42")) {
			workbook.getSheetAt(1).getRow(1).getCell(1).setCellValue("invalid geometry");
			var plots = KoboExportReader.read(workbook).getFarmers().get(0).getPlots();
			assertEquals(1, plots.size());
			assertEquals("invalid geometry", plots.get(0).getGeoData());
		}
	}

	@Test
	void populatedUnrecognisedRepeatSheetRequiresExplicitChoice() throws Exception {
		try (XSSFWorkbook workbook = syntheticExport("42")) {
			var sheet = workbook.getSheetAt(1);
			sheet.getRow(0).getCell(1).setCellValue("Survey answer");
			sheet.getRow(1).getCell(1).setCellValue("invalid geometry");
			assertTrue(KoboExportReader.read(workbook).getUnresolvedSheets().containsKey("plots"));
			assertTrue(KoboExportReader.read(workbook, java.util.Map.of("plots", -1)).getFarmers().get(0).getPlots().isEmpty());
			assertEquals("invalid geometry", KoboExportReader.read(workbook, java.util.Map.of("plots", 1)).getFarmers().get(0).getPlots().get(0).getGeoData());
			assertThrows(IllegalArgumentException.class, () -> KoboExportReader.read(workbook, java.util.Map.of("plots", 0)));
		}
	}

	@Test
	void numericValuesInUnknownColumnsAreNotGuessedAsGeometry() throws Exception {
		try (XSSFWorkbook workbook = syntheticExport("42")) {
			var sheet = workbook.getSheetAt(1);
			sheet.getRow(0).getCell(1).setCellValue("Unusual plot question");
			sheet.getRow(1).getCell(1).setCellValue("invalid one");
			for (int i = 2; i <= 4; i++) {
				var row = sheet.createRow(i);
				row.createCell(0).setCellValue("42");
				row.createCell(1).setCellValue(i == 4 ? "5.1 10.1" : "invalid " + i);
			}
			assertTrue(KoboExportReader.read(workbook).getUnresolvedSheets().containsKey("plots"));
		}
	}

	@Test
	void ambiguousGeometryColumnsRequireExplicitChoice() throws Exception {
		try (XSSFWorkbook workbook = syntheticExport("42")) {
			var sheet = workbook.getSheetAt(1);
			sheet.getRow(0).createCell(2).setCellValue("geometry");
			sheet.getRow(1).createCell(2).setCellValue("5.2 10.2");
			assertTrue(KoboExportReader.read(workbook).getUnresolvedSheets().containsKey("plots"));
			assertEquals("5.2 10.2", KoboExportReader.read(workbook, java.util.Map.of("plots", 2)).getFarmers().get(0).getPlots().get(0).getGeoData());
		}
	}

	private XSSFWorkbook syntheticExport(String submissionId) {
		XSSFWorkbook workbook = new XSSFWorkbook();
		var sheet = workbook.createSheet("submissions");
		var header = sheet.createRow(0);
		String[] headers = {"Last name", "City", "State", "Country", "Gender", "_id", "_index"};
		String[] values = {"Test", "Abuja", "FCT", "Nigeria", "Female", "42", "1"};
		var row = sheet.createRow(1);
		for (int i = 0; i < headers.length; i++) {
			header.createCell(i).setCellValue(headers[i]);
			row.createCell(i).setCellValue(values[i]);
		}
		var repeat = workbook.createSheet("plots");
		var repeatHeader = repeat.createRow(0);
		repeatHeader.createCell(0).setCellValue("_submission__id");
		repeatHeader.createCell(1).setCellValue("GPS");
		var repeatRow = repeat.createRow(1);
		repeatRow.createCell(0).setCellValue(submissionId);
		repeatRow.createCell(1).setCellValue("5.1 10.1");
		return workbook;
	}
}
