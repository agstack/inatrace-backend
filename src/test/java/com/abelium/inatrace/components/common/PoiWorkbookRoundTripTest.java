package com.abelium.inatrace.components.common;

import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Guards the POI / commons-io pairing. POI 5.3.0 needs commons-io 2.16.1, but tika-core 2.9.2
 * pins commons-io 2.15.1 through Maven's nearest-wins resolution, and then every attempt to open
 * a workbook - which is how farmer imports read the uploaded file - fails with
 * {@code NoSuchMethodError: BoundedInputStream.builder()}. The explicit commons-io dependency in
 * the pom is what keeps this test green.
 */
class PoiWorkbookRoundTripTest {

	@Test
	void workbookWrittenToBytes_canBeReadBackThroughAnInputStream() throws Exception {
		byte[] bytes;
		try (XSSFWorkbook workbook = new XSSFWorkbook();
			 ByteArrayOutputStream out = new ByteArrayOutputStream()) {
			XSSFSheet sheet = workbook.createSheet("farmers");
			sheet.createRow(0).createCell(0).setCellValue("Geo Data");
			sheet.createRow(1).createCell(0).setCellValue("POINT(5.17 10.23)");
			workbook.write(out);
			bytes = out.toByteArray();
		}

		// This constructor is the one that fails with NoSuchMethodError when commons-io is too old.
		try (XSSFWorkbook read = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
			XSSFSheet sheet = read.getSheet("farmers");
			assertEquals("Geo Data", sheet.getRow(0).getCell(0).getStringCellValue());
			assertEquals("POINT(5.17 10.23)", sheet.getRow(1).getCell(0).getStringCellValue());
		}
	}
}
