package com.abelium.inatrace.components.company;

import com.abelium.inatrace.components.common.TokenService;
import com.abelium.inatrace.db.entities.codebook.ProductType;
import com.abelium.inatrace.db.entities.common.Document;
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
import org.junit.jupiter.api.BeforeEach;
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
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

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

        MvcResult result = importFarmers(otherCompany.getId(), documentId, otherCompanySession);

        assertRefused(result);
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

    private long uploadWorkbook(Cookie session) throws Exception {
        MockMultipartFile upload = new MockMultipartFile(
                "file", WORKBOOK_NAME,
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", emptyImportWorkbook());

        MvcResult result = mockMvc.perform(multipart("/api/common/document")
                        .file(upload)
                        .param("type", "GENERAL")
                        .cookie(session))
                .andReturn();

        assertEquals(200, result.getResponse().getStatus(), body(result));
        long documentId = objectMapper.readTree(body(result)).path("data").path("id").asLong();
        assertTrue(documentId > 0, "upload response must contain the persisted document id: " + body(result));
        return documentId;
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

    private String body(MvcResult result) throws Exception {
        String body = result.getResponse().getContentAsString();
        assertNotNull(body);
        return body;
    }
}
