package com.abelium.inatrace.tools;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

/**
 * One-off maintenance tool that rewrites the shipped farmer import templates in place:
 * it restates the Geo Data column header so it names every format the importer now accepts,
 * appends the "GeoID (FAO)" column at index 34, and extends the instructions sheet with a worked
 * example per format.
 *
 * <p>The templates carry styling, data validation and a country-code sheet that hand-editing would
 * lose, so they are edited through POI rather than rebuilt. Run from the backend module:</p>
 *
 * <pre>
 * mvn -q test-compile exec:java -Dexec.mainClass=com.abelium.inatrace.tools.FarmerImportTemplateUpdater \
 *     -Dexec.classpathScope=test -Dexec.args="../inatrace-frontend/src/assets/farmer-import"
 * </pre>
 *
 * <p>It is idempotent: running it again on already-updated templates changes nothing.</p>
 */
public final class FarmerImportTemplateUpdater {

	/** 0-based index of the column-header row in every template. */
	private static final int HEADER_ROW = 4;

	private static final int GEO_DATA_COLUMN = 33;
	private static final int GEO_ID_COLUMN = 34;

	private static final String GEO_DATA_HEADER_EN =
			"Geo Data (POLYGON((lat1 lon1, lat2 lon2,...)), POINT(lat lon), GeoJSON, "
					+ "ODK/Kobo geoshape \"lat lon alt acc;...\", or P1(...)P2(...) for several plots)";

	private static final String GEO_DATA_HEADER_ES =
			"Datos geográficos (POLYGON((lat1 lon1, lat2 lon2,...)), POINT(lat lon), GeoJSON, "
					+ "geoshape de ODK/Kobo \"lat lon alt prec;...\", o P1(...)P2(...) para varias parcelas)";

	private static final String GEO_ID_HEADER_EN =
			"GeoID (FAO) - identifier of the plot boundary in the FAO GeoID registry";

	private static final String GEO_ID_HEADER_ES =
			"GeoID (FAO) - identificador del límite de la parcela en el registro GeoID de la FAO";

	private static final List<String> READ_ME_EN = List.of(
			"8. The Geo Data column accepts any one of these formats - use whichever your GPS, GIS or"
					+ " data collection tool produced, there is no need to convert by hand:",
			"    a) WKT polygon or point, as documented before: POLYGON((lat_1 lon_1, lat_2 lon_2, ...))"
					+ " or POINT(lat lon). Standard lon/lat WKT from QGIS or PostGIS is also understood.",
			"    b) ODK / KoboToolbox geoshape, geotrace or geopoint, copied straight out of the export:"
					+ " 5.1717367 10.2352433 1267.1 1.45;5.1718067 10.235235 1267.8 1.3;...",
			"    c) GeoJSON: {\"type\":\"Polygon\",\"coordinates\":[[[10.235,5.171],[10.236,5.172],[10.237,5.173],[10.235,5.171]]]}",
			"    d) Several plots for one farmer: P1(<geo data>)P2(<geo data>), using any of the formats above"
					+ " inside each pair of brackets.",
			"9. A farmer with more than one plot can also be spread over several rows: fill the farmer"
					+ " columns once, then put each extra plot on its own row leaving every other column empty.",
			"10. The GeoID column takes the identifier of a plot boundary already registered with FAO"
					+ " (https://data.apps.fao.org/geoid). The boundary is fetched from the registry, so no"
					+ " coordinates are needed. If both columns are filled, the GeoID is used and the"
					+ " coordinates serve as a fallback if the registry cannot be reached.");

	private static final List<String> READ_ME_ES = List.of(
			"8. La columna de datos geográficos acepta cualquiera de estos formatos: utilice el que haya"
					+ " generado su GPS, SIG o herramienta de recogida de datos, no hace falta convertirlo a mano:",
			"    a) Polígono o punto WKT, como se documentaba antes: POLYGON((lat_1 lon_1, lat_2 lon_2, ...))"
					+ " o POINT(lat lon). También se admite el WKT estándar lon/lat de QGIS o PostGIS.",
			"    b) Geoshape, geotrace o geopoint de ODK / KoboToolbox, copiado directamente de la exportación:"
					+ " 5.1717367 10.2352433 1267.1 1.45;5.1718067 10.235235 1267.8 1.3;...",
			"    c) GeoJSON: {\"type\":\"Polygon\",\"coordinates\":[[[10.235,5.171],[10.236,5.172],[10.237,5.173],[10.235,5.171]]]}",
			"    d) Varias parcelas para un mismo agricultor: P1(<datos geográficos>)P2(<datos geográficos>),"
					+ " usando cualquiera de los formatos anteriores dentro de cada paréntesis.",
			"9. Un agricultor con varias parcelas también puede ocupar varias filas: rellene las columnas del"
					+ " agricultor una sola vez y ponga cada parcela adicional en su propia fila, dejando vacías"
					+ " todas las demás columnas.",
			"10. La columna GeoID admite el identificador de un límite de parcela ya registrado en la FAO"
					+ " (https://data.apps.fao.org/geoid). El límite se obtiene del registro, por lo que no hacen"
					+ " falta coordenadas. Si se rellenan ambas columnas, se usa el GeoID y las coordenadas"
					+ " sirven de reserva si no se puede contactar con el registro.");

	private FarmerImportTemplateUpdater() {
	}

	public static void main(String[] args) throws Exception {

		if (args.length == 0) {
			System.err.println("Usage: FarmerImportTemplateUpdater <directory containing the templates>...");
			System.exit(1);
		}

		for (String directory : args) {
			try (var files = Files.list(Paths.get(directory))) {
				for (Path file : files.filter(p -> p.toString().endsWith(".xlsx")).sorted().toList()) {
					update(file);
				}
			}
		}
	}

	private static void update(Path file) throws Exception {

		boolean spanish = file.getFileName().toString().startsWith("Plantilla");

		XSSFWorkbook workbook;
		try (InputStream in = new FileInputStream(file.toFile())) {
			workbook = new XSSFWorkbook(in);
		}

		try (workbook) {
			Sheet sheet = workbook.getSheetAt(0);
			Row headerRow = sheet.getRow(HEADER_ROW);

			Cell geoDataHeader = headerRow.getCell(GEO_DATA_COLUMN);
			if (geoDataHeader == null) {
				throw new IllegalStateException(file + ": no Geo Data header at column " + GEO_DATA_COLUMN);
			}
			geoDataHeader.setCellValue(spanish ? GEO_DATA_HEADER_ES : GEO_DATA_HEADER_EN);

			// Reuse the Geo Data header's styling so the new column does not look bolted on.
			CellStyle headerStyle = geoDataHeader.getCellStyle();
			Cell geoIdHeader = headerRow.getCell(GEO_ID_COLUMN);
			if (geoIdHeader == null) {
				geoIdHeader = headerRow.createCell(GEO_ID_COLUMN);
			}
			geoIdHeader.setCellStyle(headerStyle);
			geoIdHeader.setCellValue(spanish ? GEO_ID_HEADER_ES : GEO_ID_HEADER_EN);
			sheet.setColumnWidth(GEO_ID_COLUMN, sheet.getColumnWidth(GEO_DATA_COLUMN));

			updateReadMe(workbook, spanish);

			try (OutputStream out = new FileOutputStream(file.toFile())) {
				workbook.write(out);
			}
		}

		System.out.println("Updated " + file);
	}

	/**
	 * Replaces the single "Geo Data column is in form: POLYGON(...)" instruction with one line per
	 * supported format, plus the GeoID column and multi-row farmer notes.
	 */
	private static void updateReadMe(XSSFWorkbook workbook, boolean spanish) {

		Sheet readMe = null;
		for (int i = 1; i < workbook.getNumberOfSheets(); i++) {
			String name = workbook.getSheetName(i).toLowerCase();
			if (name.contains("read me") || name.contains("readme") || name.contains("éame") || name.contains("eame")) {
				readMe = workbook.getSheetAt(i);
				break;
			}
		}
		if (readMe == null) {
			return;
		}

		List<String> lines = spanish ? READ_ME_ES : READ_ME_EN;

		// The geodata instruction is numbered 8 in every version of every template, so anchoring on
		// the number rather than on its wording keeps re-runs idempotent: the same rows are
		// overwritten instead of the block being inserted again.
		int firstLineRow = -1;
		for (int r = readMe.getFirstRowNum(); r <= readMe.getLastRowNum(); r++) {
			Row row = readMe.getRow(r);
			Cell cell = row == null ? null : row.getCell(0);
			if (cell != null && cell.getCellType() == org.apache.poi.ss.usermodel.CellType.STRING
					&& cell.getStringCellValue().trim().startsWith("8.")) {
				firstLineRow = r;
				break;
			}
		}
		if (firstLineRow < 0) {
			firstLineRow = readMe.getLastRowNum() + 1;
		}

		CellStyle style = null;
		Row template = readMe.getRow(firstLineRow);
		if (template != null && template.getCell(0) != null) {
			style = template.getCell(0).getCellStyle();
		}

		for (int i = 0; i < lines.size(); i++) {
			int rowIndex = firstLineRow + i;
			Row row = readMe.getRow(rowIndex);
			if (row == null) {
				row = readMe.createRow(rowIndex);
			}
			Cell cell = row.getCell(0);
			if (cell == null) {
				cell = row.createCell(0);
			}
			if (style != null) {
				cell.setCellStyle(style);
			}
			cell.setCellValue(lines.get(i));
		}
	}
}
