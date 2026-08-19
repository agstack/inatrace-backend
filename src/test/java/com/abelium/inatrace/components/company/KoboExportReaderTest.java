package com.abelium.inatrace.components.company;

import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import java.io.FileInputStream;
import java.io.InputStream;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
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
}
