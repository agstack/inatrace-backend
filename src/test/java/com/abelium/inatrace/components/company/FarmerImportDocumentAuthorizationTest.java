package com.abelium.inatrace.components.company;

import com.abelium.inatrace.components.common.TokenService;
import com.abelium.inatrace.components.common.StorageService;
import com.abelium.inatrace.db.entities.codebook.ProductType;
import com.abelium.inatrace.db.entities.common.Document;
import com.abelium.inatrace.db.entities.common.Country;
import com.abelium.inatrace.db.entities.common.UserCustomer;
import com.abelium.inatrace.db.entities.common.User;
import com.abelium.inatrace.db.entities.company.Company;
import com.abelium.inatrace.db.entities.company.CompanyUser;
import com.abelium.inatrace.db.entities.value_chain.CompanyValueChain;
import com.abelium.inatrace.db.entities.value_chain.ValueChain;
import com.abelium.inatrace.db.entities.value_chain.enums.ValueChainStatus;
import com.abelium.inatrace.support.AbstractMySqlIntegrationTest;
import com.abelium.inatrace.types.CompanyStatus;
import com.abelium.inatrace.types.CompanyUserRole;
import com.abelium.inatrace.types.Language;
import com.abelium.inatrace.types.UserRole;
import com.abelium.inatrace.types.UserStatus;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import jakarta.servlet.http.Cookie;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/**
 * The farmer import endpoint receives only a document id.  These tests prove that the id is not
 * an authorization capability: it is usable only by the authenticated user who uploaded it.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class FarmerImportDocumentAuthorizationTest extends AbstractMySqlIntegrationTest {

    private static final String WORKBOOK_NAME = "farmer-import-owner-only.xlsx";

    @Autowired private MockMvc mockMvc;
    @Autowired private EntityManager em;
    @Autowired private TokenService tokenService;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private StorageService storageService;
    private final List<Path> uploadedFiles = new ArrayList<>();

    @Value("${INATrace.auth.accessTokenCookieName}")
    private String accessCookieName;

    private Company importingCompany;
    private Company otherCompany;
    private User uploader;
    private User sameCompanyColleague;
    private User otherCompanyAdmin;
    private User systemAdmin;
    private Cookie uploaderSession;
    private Cookie colleagueSession;
    private Cookie otherCompanySession;
    private Cookie systemAdminSession;

    @AfterEach
    void removeOwnedUploadedFiles() throws Exception {
        for (Path file : uploadedFiles) Files.deleteIfExists(file);
        uploadedFiles.clear();
    }

    @BeforeEach
    void seedImportPermissionFixture() {
        uploader = regionalAdmin("uploader");
        sameCompanyColleague = regionalAdmin("colleague");
        otherCompanyAdmin = regionalAdmin("other-company");
        systemAdmin = systemAdmin();

        importingCompany = company("Importing cooperative");
        otherCompany = company("Other cooperative");
        enroll(importingCompany, uploader);
        enroll(importingCompany, sameCompanyColleague);
        enroll(otherCompany, otherCompanyAdmin);
        configureProductType(importingCompany, uploader);

        em.flush();
        em.clear();

        uploaderSession = sessionFor(uploader);
        colleagueSession = sessionFor(sameCompanyColleague);
        otherCompanySession = sessionFor(otherCompanyAdmin);
        systemAdminSession = sessionFor(systemAdmin);
    }

    @Test
    @DisplayName("an uploader can import its own spreadsheet into an enrolled company")
    void uploaderCanImportOwnSpreadsheet() throws Exception {
        long documentId = uploadWorkbook(uploaderSession);

        MvcResult result = importFarmers(importingCompany.getId(), documentId, uploaderSession);

        assertEquals(200, result.getResponse().getStatus(), body(result));
        assertTrue(body(result).contains("\"successful\":0"), body(result));
        assertEquals(uploader.getId(), em.find(Document.class, documentId).getUploadedBy().getId(),
                "the common upload endpoint must persist the authenticated uploader");
    }

    @Test
    @DisplayName("a real workbook imports a farmer and persists its company and product association")
    void importsValidFarmerAndPersistsBusinessFields() throws Exception {
        ensureSyntheticCountry();
        String marker = "SHEET-" + UUID.randomUUID();
        long documentId = uploadWorkbook(uploaderSession, workbook(marker, false));
        MvcResult result = importFarmers(importingCompany.getId(), documentId, uploaderSession);
        assertEquals(200, result.getResponse().getStatus(), body(result));
        assertTrue(body(result).contains("\"successful\":1"), body(result));
        em.flush();
        em.clear();
        var farmers = em.createQuery("select u from UserCustomer u where u.company.id = :company and u.farmerCompanyInternalId = :marker", UserCustomer.class)
                .setParameter("company", importingCompany.getId()).setParameter("marker", marker).getResultList();
        assertEquals(1, farmers.size());
        assertEquals("Synthetic", farmers.get(0).getName());
        assertEquals("Farmer", farmers.get(0).getSurname());
        long associations = em.createQuery("select count(p) from UserCustomerProductType p where p.userCustomer.id = :id", Long.class)
                .setParameter("id", farmers.get(0).getId()).getSingleResult();
        assertEquals(1L, associations);
    }

    @Test
    @DisplayName("a row validation error prevents a valid row from being persisted")
    void invalidRowPreventsPersistenceOfValidRow() throws Exception {
        ensureSyntheticCountry();
        String marker = "SHEET-" + UUID.randomUUID();
        long documentId = uploadWorkbook(uploaderSession, workbook(marker, true));
        MvcResult result = importFarmers(importingCompany.getId(), documentId, uploaderSession);
        assertEquals(200, result.getResponse().getStatus(), body(result));
        assertTrue(body(result).contains("\"successful\":0"), body(result));
        assertTrue(body(result).contains("B7"), body(result));
        assertTrue(body(result).contains("INCORRECT_TYPE"), body(result));
        em.flush();
        em.clear();
        long count = em.createQuery("select count(u) from UserCustomer u where u.company.id = :company and u.farmerCompanyInternalId = :marker", Long.class)
                .setParameter("company", importingCompany.getId()).setParameter("marker", marker).getSingleResult();
        assertEquals(0L, count);
    }

    @Test
    @DisplayName("invalid cell types and domain values report their location and do not persist")
    void invalidCellsReportLocationWithoutWriting() throws Exception {
        ensureSyntheticCountry();
        String[] cases = {"blank-surname", "bad-country", "bad-gender", "text-area", "formula-id"};
        String[] addresses = {"B6", "P6", "Q6", "V6", "A6"};
        String[] errors = {"REQUIRED", "INVALID_VALUE", "INVALID_VALUE", "INCORRECT_TYPE", "INCORRECT_TYPE"};
        for (int i = 0; i < cases.length; i++) {
            String marker = "SHEET-" + UUID.randomUUID();
            byte[] bytes;
            try (XSSFWorkbook book = new XSSFWorkbook(new ByteArrayInputStream(workbook(marker, false)));
                 ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                var row = book.getSheetAt(0).getRow(5);
                switch (cases[i]) {
                    case "blank-surname" -> row.getCell(1).setBlank();
                    case "bad-country" -> row.getCell(15).setCellValue("??");
                    case "bad-gender" -> row.getCell(16).setCellValue("invalid");
                    case "text-area" -> row.createCell(21).setCellValue("not a number");
                    case "formula-id" -> row.getCell(0).setCellFormula("1+1");
                    default -> throw new IllegalStateException(cases[i]);
                }
                book.write(out);
                bytes = out.toByteArray();
            }
            MvcResult result = importFarmers(importingCompany.getId(), uploadWorkbook(uploaderSession, bytes), uploaderSession);
            assertEquals(200, result.getResponse().getStatus(), body(result));
            assertTrue(body(result).contains("\"successful\":0"), body(result));
            assertTrue(body(result).contains(addresses[i]), body(result));
            assertTrue(body(result).contains(errors[i]), body(result));
            em.flush();
            long count = em.createQuery("select count(u) from UserCustomer u where u.company.id = :company and u.farmerCompanyInternalId = :marker", Long.class)
                    .setParameter("company", importingCompany.getId()).setParameter("marker", marker).getSingleResult();
            assertEquals(0L, count, cases[i]);
        }
    }

    @Test
    @DisplayName("non-XLSX and legacy XLS uploads produce a safe client error without writing farmers")
    void invalidWorkbookProducesClientErrorWithoutWrites() throws Exception {
        byte[] legacyXls;
        try (HSSFWorkbook workbook = new HSSFWorkbook(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            workbook.createSheet("Farmers");
            workbook.write(output);
            legacyXls = output.toByteArray();
        }
        byte[] unrelatedZip;
        try (ByteArrayOutputStream output = new ByteArrayOutputStream(); ZipOutputStream zip = new ZipOutputStream(output)) {
            zip.putNextEntry(new ZipEntry("readme.txt"));
            zip.write("not a spreadsheet".getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
            zip.finish();
            unrelatedZip = output.toByteArray();
        }
        byte[] docx;
        try (XWPFDocument document = new XWPFDocument(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            document.createParagraph().createRun().setText("Not a spreadsheet");
            document.write(output);
            docx = output.toByteArray();
        }
        byte[] noSheets;
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            workbook.write(output);
            noSheets = output.toByteArray();
        }
        byte[] validWorkbook = emptyImportWorkbook();
        byte[] truncated = Arrays.copyOf(validWorkbook, validWorkbook.length / 2);
        byte[] corrupt = validWorkbook.clone();
        int nameLength = (corrupt[26] & 0xff) | ((corrupt[27] & 0xff) << 8);
        int extraLength = (corrupt[28] & 0xff) | ((corrupt[29] & 0xff) << 8);
        int compressedData = 30 + nameLength + extraLength;
        corrupt[compressedData] = (byte) ((corrupt[compressedData] & ~6) | 6); // reserved DEFLATE block type
        byte[] strict = rewriteZipEntry(validWorkbook, "_rels/.rels",
                xml -> xml.replace("http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument",
                        "http://purl.oclc.org/ooxml/officeDocument/relationships/officeDocument"));
        byte[] zipBomb = rewriteZipEntry(validWorkbook, "xl/workbook.xml",
                xml -> xml.replace("</workbook>", " ".repeat(500_000) + "</workbook>"));
        for (byte[] bytes : new byte[][] { new byte[0], "not a spreadsheet".getBytes(StandardCharsets.UTF_8),
                legacyXls, unrelatedZip, docx, truncated, corrupt, strict, zipBomb, noSheets }) {
            long before = em.createQuery("select count(u) from UserCustomer u where u.company.id = :company", Long.class)
                    .setParameter("company", importingCompany.getId()).getSingleResult();
            long documentId = uploadWorkbook(uploaderSession, bytes);
            Document document = em.find(Document.class, documentId);
            var file = storageService.getPath(document);
            try {
                MvcResult result = importFarmers(importingCompany.getId(), documentId, uploaderSession);
                assertEquals(400, result.getResponse().getStatus(), body(result));
                var response = objectMapper.readTree(body(result));
                assertEquals("INVALID_REQUEST", response.path("status").asText());
                assertEquals("The file is not a valid XLSX spreadsheet. Please check the file and try again.", response.path("errorMessage").asText());
                assertFalse(body(result).contains("Exception"), body(result));
                em.flush();
                em.clear();
                long after = em.createQuery("select count(u) from UserCustomer u where u.company.id = :company", Long.class)
                        .setParameter("company", importingCompany.getId()).getSingleResult();
                assertEquals(before, after);
            } finally {
                Files.deleteIfExists(file);
            }
        }
    }

    @Test
    @DisplayName("importing the same workbook twice reports a duplicate and leaves one farmer")
    void repeatImportReportsDuplicate() throws Exception {
        ensureSyntheticCountry();
        String marker = "SHEET-" + UUID.randomUUID();
        byte[] bytes = workbook(marker, false);
        MvcResult first = importFarmers(importingCompany.getId(), uploadWorkbook(uploaderSession, bytes), uploaderSession);
        assertEquals(200, first.getResponse().getStatus(), body(first));
        assertTrue(body(first).contains("\"successful\":1"), body(first));
        em.flush();
        em.clear();
        MvcResult second = importFarmers(importingCompany.getId(), uploadWorkbook(uploaderSession, bytes), uploaderSession);
        assertEquals(200, second.getResponse().getStatus(), body(second));
        assertTrue(body(second).contains("\"successful\":0"), body(second));
        assertTrue(body(second).contains(marker), body(second));
        em.flush();
        long count = em.createQuery("select count(u) from UserCustomer u where u.company.id = :company and u.farmerCompanyInternalId = :marker", Long.class)
                .setParameter("company", importingCompany.getId()).setParameter("marker", marker).getSingleResult();
        assertEquals(1L, count);
    }

    @Test
    @DisplayName("a two-product workbook associates both configured products")
    void importsTwoProductTypes() throws Exception {
        ensureSyntheticCountry();
        configureProductType(importingCompany, uploader);
        em.flush();
        em.clear();
        String marker = "SHEET-" + UUID.randomUUID();
        MvcResult result = importFarmers(importingCompany.getId(), uploadWorkbook(uploaderSession, workbook(marker, false, true)), uploaderSession);
        assertEquals(200, result.getResponse().getStatus(), body(result));
        assertTrue(body(result).contains("\"successful\":1"), body(result));
        em.flush();
        em.clear();
        UserCustomer farmer = em.createQuery("select u from UserCustomer u where u.company.id = :company and u.farmerCompanyInternalId = :marker", UserCustomer.class)
                .setParameter("company", importingCompany.getId()).setParameter("marker", marker).getSingleResult();
        long associations = em.createQuery("select count(p) from UserCustomerProductType p where p.userCustomer.id = :id", Long.class)
                .setParameter("id", farmer.getId()).getSingleResult();
        assertEquals(2L, associations);
    }

    @Test
    @DisplayName("farmer ZIP export contains the imported farmer's workbook fields")
    void exportContainsImportedFarmer() throws Exception {
        ensureSyntheticCountry();
        String marker = "SHEET-" + UUID.randomUUID();
        MvcResult imported = importFarmers(importingCompany.getId(), uploadWorkbook(uploaderSession, workbook(marker, false)), uploaderSession);
        assertEquals(200, imported.getResponse().getStatus(), body(imported));
        assertTrue(body(imported).contains("\"successful\":1"), body(imported));
        em.flush();
        em.clear();
        MvcResult exported = mockMvc.perform(get("/api/company/userCustomers/" + importingCompany.getId() + "/exportFarmerData")
                .header("language", "EN").cookie(uploaderSession)).andReturn();
        assertEquals(200, exported.getResponse().getStatus(), body(exported));
        boolean found = false;
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(exported.getResponse().getContentAsByteArray()))) {
            java.util.zip.ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if ("farmers.xlsx".equals(entry.getName())) {
                    try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(zip.readAllBytes()))) {
                        var row = workbook.getSheetAt(0).getRow(1);
                        assertNotNull(row);
                        assertEquals(marker, row.getCell(1).getStringCellValue());
                        assertEquals("Farmer", row.getCell(2).getStringCellValue());
                        assertEquals("Synthetic", row.getCell(3).getStringCellValue());
                        found = true;
                    }
                }
                zip.closeEntry();
            }
        }
        assertTrue(found, "ZIP must contain farmers.xlsx");
    }

    @Test
    void missingStoredFileRemainsServerError() throws Exception {
        long documentId = uploadWorkbook(uploaderSession);
        Document document = em.find(Document.class, documentId);
        Files.delete(storageService.getPath(document));
        MvcResult result = importFarmers(importingCompany.getId(), documentId, uploaderSession);
        assertEquals(500, result.getResponse().getStatus(), body(result));
        assertFalse(body(result).contains("not a valid XLSX"));
    }

    private byte[] rewriteZipEntry(byte[] original, String name, java.util.function.UnaryOperator<String> rewrite) throws Exception {
        boolean replaced = false;
        try (ZipInputStream input = new ZipInputStream(new ByteArrayInputStream(original));
             ByteArrayOutputStream output = new ByteArrayOutputStream();
             ZipOutputStream zip = new ZipOutputStream(output)) {
            ZipEntry entry;
            while ((entry = input.getNextEntry()) != null) {
                zip.putNextEntry(new ZipEntry(entry.getName()));
                byte[] data = input.readAllBytes();
                if (name.equals(entry.getName())) {
                    String before = new String(data, StandardCharsets.UTF_8);
                    String after = rewrite.apply(before);
                    assertFalse(before.equals(after), "fixture must modify " + name);
                    data = after.getBytes(StandardCharsets.UTF_8);
                    replaced = true;
                }
                zip.write(data);
                zip.closeEntry();
            }
            assertTrue(replaced, "fixture must contain " + name);
            zip.finish();
            return output.toByteArray();
        }
    }

    @Test
    @DisplayName("a regional admin cannot import a spreadsheet uploaded by a colleague in the same company")
    void sameCompanyColleagueCannotImportUpload() throws Exception {
        long documentId = uploadWorkbook(uploaderSession);

        MvcResult result = importFarmers(importingCompany.getId(), documentId, colleagueSession);

        assertRefused(result);
        assertFalse(body(result).contains(WORKBOOK_NAME),
                "a rejection must not disclose source-document metadata");
    }

    @Test
    @DisplayName("a regional admin cannot import another company's spreadsheet into its company")
    void crossCompanyDocumentCannotBeImported() throws Exception {
        long documentId = uploadWorkbook(uploaderSession);
        long before = farmerCount(otherCompany.getId());

        MvcResult result = importFarmers(otherCompany.getId(), documentId, otherCompanySession);

        assertRefused(result);
        assertEquals(before, farmerCount(otherCompany.getId()));
    }

    @Test
    @DisplayName("a system admin does not bypass spreadsheet ownership")
    void systemAdminCannotImportAnotherUsersSpreadsheet() throws Exception {
        long documentId = uploadWorkbook(uploaderSession);

        MvcResult result = importFarmers(importingCompany.getId(), documentId, systemAdminSession);

        assertRefused(result);
    }

    @Test
    @DisplayName("an unknown import document id is a controlled bad request")
    void unknownDocumentIsRejectedWithoutServerError() throws Exception {
        MvcResult result = importFarmers(importingCompany.getId(), Long.MAX_VALUE, uploaderSession);

        assertEquals(400, result.getResponse().getStatus(), body(result));
        assertFalse(body(result).contains("NullPointerException"), body(result));
    }

    @Test
    @DisplayName("a legacy document without an uploader is rejected")
    void legacyDocumentWithoutUploaderIsRejected() throws Exception {
        Document legacy = new Document();
        legacy.setName("legacy-import.xlsx");
        legacy.setType(com.abelium.inatrace.types.DocumentType.GENERAL);
        legacy.setStorageKey("legacy-document-" + UUID.randomUUID());
        legacy.setContentType(MediaType.APPLICATION_OCTET_STREAM_VALUE);
        legacy.setSize(1L);
        em.persist(legacy);
        em.flush();

        MvcResult result = importFarmers(importingCompany.getId(), legacy.getId(), uploaderSession);

        assertRefused(result);
    }

    @Test
    @DisplayName("the first empty data row stops import of later rows")
    void emptyDataRowStopsImport() throws Exception {
        ensureSyntheticCountry();
        String marker = "SHEET-" + UUID.randomUUID();
        byte[] bytes;
        try (XSSFWorkbook book = new XSSFWorkbook(new ByteArrayInputStream(workbook(marker, false)));
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            var row = book.getSheetAt(0).createRow(7);
            row.createCell(0).setCellValue(marker + "-later");
            row.createCell(1).setCellValue("Later");
            row.createCell(15).setCellValue("QZ");
            row.createCell(16).setCellValue("N/A");
            book.write(out);
            bytes = out.toByteArray();
        }
        MvcResult result = importFarmers(importingCompany.getId(), uploadWorkbook(uploaderSession, bytes), uploaderSession);
        assertEquals(200, result.getResponse().getStatus(), body(result));
        assertTrue(body(result).contains("\"successful\":1"), body(result));
        em.flush();
        long later = em.createQuery("select count(u) from UserCustomer u where u.company.id = :company and u.farmerCompanyInternalId = :marker", Long.class)
                .setParameter("company", importingCompany.getId()).setParameter("marker", marker + "-later").getSingleResult();
        assertEquals(0L, later);
    }

    @Test
    @DisplayName("an unreadable uploaded workbook does not create farmers")
    void unreadableWorkbookDoesNotCreateFarmers() throws Exception {
        long before = farmerCount(importingCompany.getId());
        long documentId = uploadWorkbook(uploaderSession, "not an XLSX file".getBytes(StandardCharsets.UTF_8));
        MvcResult result = importFarmers(importingCompany.getId(), documentId, uploaderSession);
        em.flush();
        em.clear();
        assertEquals(before, farmerCount(importingCompany.getId()));
        assertTrue(result.getResponse().getStatus() >= 400 && result.getResponse().getStatus() < 500,
                "Malformed user input should receive a controlled client error: " + body(result));
    }

    private long uploadWorkbook(Cookie session) throws Exception {
        return uploadWorkbook(session, emptyImportWorkbook());
    }

    private long uploadWorkbook(Cookie session, byte[] workbookBytes) throws Exception {
        MockMultipartFile upload = new MockMultipartFile(
                "file", WORKBOOK_NAME,
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", workbookBytes);

        MvcResult result = mockMvc.perform(multipart("/api/common/document")
                        .file(upload)
                        .param("type", "GENERAL")
                        .cookie(session))
                .andReturn();

        assertEquals(200, result.getResponse().getStatus(), body(result));
        long documentId = objectMapper.readTree(body(result)).path("data").path("id").asLong();
        assertTrue(documentId > 0, "upload response must contain the persisted document id: " + body(result));
        uploadedFiles.add(storageService.getPath(em.find(Document.class, documentId)));
        return documentId;
    }

    private void ensureSyntheticCountry() {
        if (em.createQuery("select count(c) from Country c where c.code = :code", Long.class)
                .setParameter("code", "QZ").getSingleResult() == 0) {
            Country country = new Country();
            country.setCode("QZ");
            country.setName("Synthetic country");
            em.persist(country);
            em.flush();
        }
    }

    private byte[] workbook(String marker, boolean invalidSecondRow) throws Exception {
        return workbook(marker, invalidSecondRow, false);
    }

    private byte[] workbook(String marker, boolean invalidSecondRow, boolean secondProduct) throws Exception {
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            XSSFSheet sheet = workbook.createSheet("Farmers");
            var header = sheet.createRow(4);
            if (secondProduct) header.createCell(24).setCellValue("Second product");
            var valid = sheet.createRow(5);
            valid.createCell(0).setCellValue(marker);
            valid.createCell(1).setCellValue("Farmer");
            valid.createCell(2).setCellValue("Synthetic");
            valid.createCell(11).setCellValue("Test city");
            valid.createCell(12).setCellValue("Test state");
            valid.createCell(15).setCellValue("QZ");
            valid.createCell(16).setCellValue("N/A");
            if (secondProduct) {
                valid.createCell(24).setCellValue(1.25);
                valid.createCell(25).setCellValue(5);
            }
            if (invalidSecondRow) {
                var invalid = sheet.createRow(6);
                invalid.createCell(0).setCellValue(marker + "-invalid");
                invalid.createCell(1).setCellValue(123);
                invalid.createCell(15).setCellValue("QZ");
                invalid.createCell(16).setCellValue("N/A");
            }
            workbook.write(output);
            return output.toByteArray();
        }
    }

    private MvcResult importFarmers(long companyId, long documentId, Cookie session) throws Exception {
        return mockMvc.perform(post("/api/company/userCustomers/import/farmers/" + companyId + "/" + documentId)
                        .header("language", "EN")
                        .cookie(session))
                .andReturn();
    }

    private byte[] emptyImportWorkbook() throws Exception {
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            XSSFSheet sheet = workbook.createSheet("Farmers");
            sheet.createRow(4); // The importer inspects the optional second-product header on row five.
            workbook.write(output);
            return output.toByteArray();
        }
    }

    private User regionalAdmin(String prefix) {
        return user(prefix, UserRole.REGIONAL_ADMIN);
    }

    private User systemAdmin() {
        return user("system", UserRole.SYSTEM_ADMIN);
    }

    private User user(String prefix, UserRole role) {
        User user = new User();
        user.setEmail(prefix + "-" + UUID.randomUUID() + "@test.invalid");
        user.setName(prefix);
        user.setSurname("Admin");
        user.setLanguage(Language.EN);
        user.setStatus(UserStatus.ACTIVE);
        user.setRole(role);
        user.setPassword("not-used-by-token-test");
        em.persist(user);
        return user;
    }

    private Company company(String prefix) {
        Company company = new Company();
        company.setName(prefix + " " + UUID.randomUUID());
        company.setStatus(CompanyStatus.ACTIVE);
        em.persist(company);
        return company;
    }

    private void enroll(Company company, User user) {
        CompanyUser membership = new CompanyUser();
        membership.setCompany(company);
        membership.setUser(user);
        membership.setRole(CompanyUserRole.COMPANY_ADMIN);
        em.persist(membership);
    }

    private void configureProductType(Company company, User createdBy) {
        ProductType productType = new ProductType();
        productType.setCode("IMPORT-" + UUID.randomUUID());
        productType.setName("Import test product");
        productType.setDescription("Import test product");
        em.persist(productType);

        ValueChain valueChain = new ValueChain();
        valueChain.setName("Import test chain");
        valueChain.setDescription("Import test chain");
        valueChain.setValueChainStatus(ValueChainStatus.ENABLED);
        valueChain.setCreatedBy(createdBy);
        valueChain.setProductType(productType);
        em.persist(valueChain);

        CompanyValueChain association = new CompanyValueChain();
        association.setCompany(company);
        association.setValueChain(valueChain);
        em.persist(association);
    }

    private Cookie sessionFor(User user) {
        return new Cookie(accessCookieName, tokenService.createAccessToken(user));
    }

    private void assertRefused(MvcResult result) throws Exception {
        assertEquals(403, result.getResponse().getStatus(), body(result));
        assertFalse(body(result).contains("Exception"), body(result));
    }

    private long farmerCount(long companyId) {
        return em.createQuery("select count(u) from UserCustomer u where u.company.id = :company", Long.class)
                .setParameter("company", companyId).getSingleResult();
    }

    private String body(MvcResult result) throws Exception {
        String body = result.getResponse().getContentAsString();
        assertNotNull(body);
        return body;
    }
}
