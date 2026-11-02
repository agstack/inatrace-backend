package com.abelium.inatrace.components.company;

import net.jqwik.api.ForAll;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Provide;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.LongRange;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class SpreadsheetPropertiesTest {
    @Property(tries = 20)
    void missingAndBlankIdentifiersAreReadAsNull(@ForAll boolean blankCell) throws Exception {
        byte[] bytes;
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            var row = workbook.createSheet("Farmers").createRow(5);
            if (blankCell) row.createCell(0).setBlank();
            workbook.write(output);
            bytes = output.toByteArray();
        }
        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            Method reader = UserCustomerImportService.class.getDeclaredMethod("getStringOrNumeric", Cell.class);
            reader.setAccessible(true);
            assertNull(reader.invoke(new UserCustomerImportService(), workbook.getSheetAt(0).getRow(5).getCell(0)));
        }
    }

    @Provide
    Arbitrary<String> textIdentifiers() {
        return Arbitraries.oneOf(
                Arbitraries.of("00123", "A, B", "A\"B", "Café", "line\nbreak", "農家-123"),
                Arbitraries.strings().withChars('a', 'Z', '0', ',', '"', '\n', 'é', '農')
                        .ofMinLength(1).ofMaxLength(30));
    }

    @Property(tries = 50)
    void textIdentifiersPreserveTheirExactCharacters(@ForAll("textIdentifiers") String value) throws Exception {
        byte[] bytes;
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            workbook.createSheet("Farmers").createRow(5).createCell(0).setCellValue(value);
            workbook.write(output);
            bytes = output.toByteArray();
        }
        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            Method reader = UserCustomerImportService.class.getDeclaredMethod("getStringOrNumeric", Cell.class);
            reader.setAccessible(true);
            assertEquals(value, reader.invoke(new UserCustomerImportService(), workbook.getSheetAt(0).getRow(5).getCell(0)));
        }
    }

    @Property(tries = 50)
    void numericIdentifiersAreReadAsWholeNumberStrings(@ForAll @LongRange(min = 0, max = 999999999) long value) throws Exception {
        byte[] bytes;
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            workbook.createSheet("Farmers").createRow(5).createCell(0).setCellValue((double) value);
            workbook.write(output);
            bytes = output.toByteArray();
        }
        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            Cell cell = workbook.getSheetAt(0).getRow(5).getCell(0);
            Method reader = UserCustomerImportService.class.getDeclaredMethod("getStringOrNumeric", Cell.class);
            reader.setAccessible(true);
            assertEquals(Long.toString(value), reader.invoke(new UserCustomerImportService(), cell));
        }
    }
}
