package com.abelium.inatrace.components.company;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.FileInputStream;
import java.io.InputStream;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies that every farmer import template actually shipped to the frontend (see
 * company-farmers-import.component.ts, which picks one of these 4 by company country/locale)
 * lines up, column-for-column, with what {@link UserCustomerImportService} expects to read at
 * each cell index - in particular that the "Geo Data" column is at index 33 (0-based) in all of
 * them and documents the exact format the parser requires.
 */
class FarmerImportTemplateAssetTest {

    private static final String RESOURCES = "src/test/resources/farmer-import/";

    private static final String TEMPLATE_WITH_KNOWN_ENGLISH_HEADER =
            RESOURCES + "Template_list_of_farmers_other_countries_en.xlsx";

    @ParameterizedTest
    @ValueSource(strings = {
            "Template_list_of_farmers_other_countries_en.xlsx",
            "Template_list_of_farmers_Rwanda_en.xlsx",
            "Plantilla_listado_de_agricultores_otros_paises_es.xlsx",
            "Plantilla_listado_de_agricultores_Honduras_es.xlsx"
    })
    void geoDataColumn_isAtIndex33_andDocumentsTheSupportedFormat(String fileName) throws Exception {
        try (InputStream in = new FileInputStream(RESOURCES + fileName);
             XSSFWorkbook workbook = new XSSFWorkbook(in)) {

            XSSFSheet sheet = workbook.getSheetAt(0);
            Row headerRow = sheet.getRow(4); // 0-based row 4 = the column-header row

            Cell geoDataHeader = headerRow.getCell(33);
            assertEquals(CellType.STRING, geoDataHeader.getCellType(), fileName + ": column 33 header is missing");

            String header = geoDataHeader.getStringCellValue();
            assertTrue(header.toLowerCase().contains("geo") || header.toLowerCase().contains("geográfic"),
                    fileName + ": column 33 header should describe the geodata column, was: " + header);
            assertTrue(header.contains("POLYGON"),
                    fileName + ": column 33 header should document the POLYGON format, was: " + header);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "Template_list_of_farmers_other_countries_en.xlsx",
            "Template_list_of_farmers_Rwanda_en.xlsx",
            "Plantilla_listado_de_agricultores_otros_paises_es.xlsx",
            "Plantilla_listado_de_agricultores_Honduras_es.xlsx"
    })
    void geoIdColumn_isAtIndex34_afterTheGeoDataColumn(String fileName) throws Exception {
        try (InputStream in = new FileInputStream(RESOURCES + fileName);
             XSSFWorkbook workbook = new XSSFWorkbook(in)) {

            Row headerRow = workbook.getSheetAt(0).getRow(4);

            Cell geoIdHeader = headerRow.getCell(34);
            assertEquals(CellType.STRING, geoIdHeader.getCellType(), fileName + ": column 34 header is missing");
            assertTrue(geoIdHeader.getStringCellValue().contains("GeoID"),
                    fileName + ": column 34 should be the GeoID column, was: " + geoIdHeader.getStringCellValue());
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "Template_list_of_farmers_other_countries_en.xlsx",
            "Template_list_of_farmers_Rwanda_en.xlsx",
            "Plantilla_listado_de_agricultores_otros_paises_es.xlsx",
            "Plantilla_listado_de_agricultores_Honduras_es.xlsx"
    })
    void geoDataHeader_documentsEveryAcceptedFormat(String fileName) throws Exception {
        try (InputStream in = new FileInputStream(RESOURCES + fileName);
             XSSFWorkbook workbook = new XSSFWorkbook(in)) {

            String header = workbook.getSheetAt(0).getRow(4).getCell(33).getStringCellValue();

            assertTrue(header.contains("POINT"), fileName + ": POINT not documented, was: " + header);
            assertTrue(header.contains("GeoJSON"), fileName + ": GeoJSON not documented, was: " + header);
            assertTrue(header.toLowerCase().contains("geoshape"),
                    fileName + ": ODK/Kobo geoshape not documented, was: " + header);
            assertTrue(header.contains("P1(...)"),
                    fileName + ": multi-plot form not documented, was: " + header);
        }
    }

    @Test
    void realTemplateRow_withValidPolygon_parsesThroughRealPoiCell() throws Exception {
        try (InputStream in = new FileInputStream(TEMPLATE_WITH_KNOWN_ENGLISH_HEADER);
             XSSFWorkbook workbook = new XSSFWorkbook(in)) {

            XSSFSheet sheet = workbook.getSheetAt(0);
            Row dataRow = sheet.createRow(5); // first data row per UserCustomerImportService.rowIndex = 5
            dataRow.createCell(33).setCellValue("POLYGON((5.2217367 10.2852433, 5.2218067 10.285235, 5.2219302 10.2852027))");

            List<GeoDataParser.ParsedPlot> plots =
                    GeoDataParser.parse(dataRow.getCell(33).getStringCellValue().trim(), "CM");

            assertEquals(1, plots.size());
            assertEquals(GeoDataParser.GeoDataType.POLYGON, plots.get(0).getType());
            assertEquals(3, plots.get(0).getPoints().size());
            assertEquals(5.2217367, plots.get(0).getPoints().get(0)[0]);
            assertEquals(10.2852433, plots.get(0).getPoints().get(0)[1]);
        }
    }

    @Test
    void realTemplateRow_withKoboGeoshape_parsesThroughRealPoiCell() throws Exception {
        try (InputStream in = new FileInputStream(TEMPLATE_WITH_KNOWN_ENGLISH_HEADER);
             XSSFWorkbook workbook = new XSSFWorkbook(in)) {

            XSSFSheet sheet = workbook.getSheetAt(0);
            Row dataRow = sheet.createRow(5);
            // A UCCAO collection's format - what users actually paste into this column
            dataRow.createCell(33).setCellValue(
                    "5.2217367 10.2852433 1267.1000000000001 1.45;5.2218067 10.285235 1267.8 1.3;"
                            + "5.2219302 10.2852027 1267.0 1.3;5.2217367 10.2852433 1267.1000000000001 1.45");

            List<GeoDataParser.ParsedPlot> plots =
                    GeoDataParser.parse(dataRow.getCell(33).getStringCellValue().trim(), "CM");

            assertEquals(1, plots.size());
            assertEquals(GeoDataParser.GeoDataType.POLYGON, plots.get(0).getType());
            assertEquals(4, plots.get(0).getPoints().size());
        }
    }

    @Test
    void realTemplateRow_withInvalidGeodata_isRejectedThroughRealPoiCell() throws Exception {
        try (InputStream in = new FileInputStream(TEMPLATE_WITH_KNOWN_ENGLISH_HEADER);
             XSSFWorkbook workbook = new XSSFWorkbook(in)) {

            XSSFSheet sheet = workbook.getSheetAt(0);
            Row dataRow = sheet.createRow(5);
            // Out-of-range latitude, as a real fat-fingered upload might contain
            dataRow.createCell(33).setCellValue("POLYGON((95.17 10.23, 96.18 10.24, 97.19 10.25))");

            String cellValue = dataRow.getCell(33).getStringCellValue().trim();
            assertThrows(IllegalArgumentException.class, () -> GeoDataParser.parse(cellValue, "CM"));
        }
    }
}
