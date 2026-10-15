package com.abelium.inatrace.components.common;

import com.abelium.inatrace.components.common.api.ApiDocument;
import com.abelium.inatrace.components.common.api.ApiGlobalSettingsValue;
import com.abelium.inatrace.db.entities.common.Country;
import com.abelium.inatrace.db.entities.common.Document;
import com.abelium.inatrace.db.entities.common.GlobalSettings;
import com.abelium.inatrace.db.entities.common.User;
import com.abelium.inatrace.db.entities.company.Company;
import com.abelium.inatrace.db.entities.company.CompanyUser;
import com.abelium.inatrace.support.AbstractMySqlIntegrationTest;
import com.abelium.inatrace.types.CompanyStatus;
import com.abelium.inatrace.types.CompanyUserRole;
import com.abelium.inatrace.types.DocumentType;
import com.abelium.inatrace.types.UserRole;
import com.abelium.inatrace.types.UserStatus;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
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

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * HTTP authorization coverage for CommonController's private temporary storage keys and global
 * settings. Documents deliberately have no tenant of their own: the temporary key is the
 * capability and must be bound to the authenticated user that received it.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class CommonFileApiAuthTest extends AbstractMySqlIntegrationTest {

    private static final String DOCUMENT_MARKER = "ACME-PRIVATE-DOCUMENT-39";
    private static final String IMAGE_MARKER = "ACME-PRIVATE-IMAGE-39";
    private static final String SETTING_NAME = "common-file-auth-39";
    private static final String INITIAL_SETTING = "INITIAL-SETTING-39";
    private static final String UPDATED_SETTING = "UPDATED-SETTING-39";

    @Autowired private MockMvc mockMvc;
    @Autowired private EntityManager em;
    @Autowired private TokenService tokenService;
    @Autowired private ObjectMapper objectMapper;

    @Value("${INATrace.auth.accessTokenCookieName}")
    private String accessCookieName;

    @Value("${INATrace.fileStorage.root}")
    private String storageRoot;

    private final List<Document> uploadedDocuments = new ArrayList<>();

    private Cookie acmeSession;
    private Cookie rivalSession;
    private Cookie regionalSession;
    private Cookie systemAdminSession;

    @BeforeEach
    void seed() {
        Company acme = company("Acme files 39");
        Company rival = company("Rival files 39");

        User acmeUser = user("acme-files-39@test", UserRole.USER);
        User rivalUser = user("rival-files-39@test", UserRole.USER);
        User regionalAdmin = user("regional-files-39@test", UserRole.REGIONAL_ADMIN);
        User systemAdmin = user("system-files-39@test", UserRole.SYSTEM_ADMIN);

        enroll(acmeUser, acme);
        enroll(rivalUser, rival);
        enroll(regionalAdmin, rival);

        Country country = new Country();
        country.setCode("F39");
        country.setName("Files country 39");
        em.persist(country);

        GlobalSettings setting = new GlobalSettings();
        setting.setName(SETTING_NAME);
        setting.setValue(INITIAL_SETTING);
        setting.setIsPublic(false);
        em.persist(setting);
        em.flush();

        acmeSession = session(acmeUser);
        rivalSession = session(rivalUser);
        regionalSession = session(regionalAdmin);
        systemAdminSession = session(systemAdmin);
    }

    @AfterEach
    void deleteUploadedFiles() throws Exception {
        for (Document document : uploadedDocuments) {
            Files.deleteIfExists(Paths.get(storageRoot, document.getType().toString(), document.getStorageKey()));
            if (document.getType() == DocumentType.IMAGE) {
                for (String size : List.of("SMALL", "MEDIUM", "LARGE", "XLARGE", "XXLARGE")) {
                    Files.deleteIfExists(Paths.get(storageRoot, DocumentType.IMAGE.toString(), size, document.getStorageKey()));
                }
            }
        }
    }

    @Test
    void countriesAndGlobalSettingsAreAuthenticatedGlobalReads() throws Exception {
        assertContains(get("/api/common/countries").cookie(acmeSession), 200, "Files country 39");
        assertContains(get("/api/common/countries").cookie(rivalSession), 200, "Files country 39");
        assertStatus(get("/api/common/countries"), 401);

        assertContains(get("/api/common/globalSettings/" + SETTING_NAME).cookie(acmeSession), 200, INITIAL_SETTING);
        assertContains(get("/api/common/globalSettings/" + SETTING_NAME).cookie(rivalSession), 200, INITIAL_SETTING);
        assertStatus(get("/api/common/globalSettings/" + SETTING_NAME), 401);
    }

    @Test
    void onlySystemAdminCanMutateGlobalSettingsWithoutChangingThemOnRefusal() throws Exception {
        String update = objectMapper.writeValueAsString(new ApiGlobalSettingsValue(UPDATED_SETTING, true));

        assertStatus(post("/api/common/globalSettings/" + SETTING_NAME)
                .contentType(MediaType.APPLICATION_JSON).content(update).cookie(systemAdminSession), 200);
        assertSetting(UPDATED_SETTING, true);

        assertStatus(post("/api/common/globalSettings/" + SETTING_NAME)
                .contentType(MediaType.APPLICATION_JSON).content("{\"value\":\"RIVAL-MUTATION-39\",\"isPublic\":false}")
                .cookie(rivalSession), 403);
        assertSetting(UPDATED_SETTING, true);

        assertStatus(post("/api/common/globalSettings/" + SETTING_NAME)
                .contentType(MediaType.APPLICATION_JSON).content("{\"value\":\"REGIONAL-MUTATION-39\",\"isPublic\":false}")
                .cookie(regionalSession), 403);
        assertSetting(UPDATED_SETTING, true);

        assertStatus(post("/api/common/globalSettings/" + SETTING_NAME)
                .contentType(MediaType.APPLICATION_JSON).content(update), 401);
        assertSetting(UPDATED_SETTING, true);
    }

    @Test
    void privateDocumentKeyIsUsableOnlyByTheUserWhoReceivedIt() throws Exception {
        byte[] documentBytes = DOCUMENT_MARKER.getBytes(StandardCharsets.UTF_8);
        Upload uploaded = uploadDocument(acmeSession, DOCUMENT_MARKER + ".txt", MediaType.TEXT_PLAIN_VALUE, documentBytes);

        MvcResult ownerDownload = mockMvc.perform(get("/api/common/document/" + uploaded.temporaryKey).cookie(acmeSession)).andReturn();
        assertEquals(200, ownerDownload.getResponse().getStatus(), ownerDownload.getResponse().getContentAsString());
        assertEquals(MediaType.TEXT_PLAIN_VALUE, ownerDownload.getResponse().getContentType());
        assertArrayEquals(documentBytes, ownerDownload.getResponse().getContentAsByteArray());

        assertPrivateDocumentIsNotDisclosed(get("/api/common/document/" + uploaded.temporaryKey).cookie(rivalSession), DOCUMENT_MARKER);
        assertPrivateDocumentIsNotDisclosed(get("/api/common/document/" + uploaded.internalKey).cookie(rivalSession), DOCUMENT_MARKER);
        assertStatus(get("/api/common/document/" + uploaded.temporaryKey), 401);
    }

    @Test
    void privateImageAndItsResizedVariantAreUsableOnlyByTheUserWhoReceivedTheirKey() throws Exception {
        byte[] imageBytes = png();
        Upload uploaded = uploadImage(acmeSession, IMAGE_MARKER + ".png", imageBytes);

        MvcResult ownerDownload = mockMvc.perform(get("/api/common/image/" + uploaded.temporaryKey).cookie(acmeSession)).andReturn();
        assertEquals(200, ownerDownload.getResponse().getStatus(), ownerDownload.getResponse().getContentAsString());
        assertEquals(MediaType.IMAGE_PNG_VALUE, ownerDownload.getResponse().getContentType());
        assertArrayEquals(imageBytes, ownerDownload.getResponse().getContentAsByteArray());

        MvcResult ownerResized = mockMvc.perform(get("/api/common/image/" + uploaded.temporaryKey + "/SMALL").cookie(acmeSession)).andReturn();
        assertEquals(200, ownerResized.getResponse().getStatus(), ownerResized.getResponse().getContentAsString());
        assertTrue(ownerResized.getResponse().getContentAsByteArray().length > 0);

        assertPrivateDocumentIsNotDisclosed(get("/api/common/image/" + uploaded.temporaryKey).cookie(rivalSession), IMAGE_MARKER);
        assertPrivateDocumentIsNotDisclosed(get("/api/common/image/" + uploaded.internalKey).cookie(rivalSession), IMAGE_MARKER);
        assertPrivateDocumentIsNotDisclosed(get("/api/common/image/" + uploaded.temporaryKey + "/SMALL").cookie(rivalSession), IMAGE_MARKER);
        assertStatus(get("/api/common/image/" + uploaded.temporaryKey), 401);
        assertStatus(get("/api/common/image/" + uploaded.temporaryKey + "/SMALL"), 401);
    }

    @Test
    void rejectedUploadsDoNotCreateDocumentRowsOrFiles() throws Exception {
        long before = countDocuments();

        MockMultipartFile document = new MockMultipartFile("file", "anonymous-39.txt", MediaType.TEXT_PLAIN_VALUE,
                DOCUMENT_MARKER.getBytes(StandardCharsets.UTF_8));
        assertStatus(multipart("/api/common/document").file(document), 401);
        assertEquals(before, countDocuments());

        MockMultipartFile invalidImage = new MockMultipartFile("file", "invalid-39.png", MediaType.IMAGE_PNG_VALUE,
                "not-an-image".getBytes(StandardCharsets.UTF_8));
        assertStatus(multipart("/api/common/image").file(invalidImage).cookie(acmeSession), 400);
        assertEquals(before, countDocuments());
    }

    private Upload uploadDocument(Cookie session, String filename, String contentType, byte[] bytes) throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", filename, contentType, bytes);
        MvcResult result = mockMvc.perform(multipart("/api/common/document").file(file).cookie(session)).andReturn();
        assertEquals(200, result.getResponse().getStatus(), result.getResponse().getContentAsString());
        return trackUpload(filename, result);
    }

    private Upload uploadImage(Cookie session, String filename, byte[] bytes) throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", filename, MediaType.IMAGE_PNG_VALUE, bytes);
        MvcResult result = mockMvc.perform(multipart("/api/common/image").file(file).cookie(session)).andReturn();
        assertEquals(200, result.getResponse().getStatus(), result.getResponse().getContentAsString());
        return trackUpload(filename, result);
    }

    private Upload trackUpload(String filename, MvcResult result) throws Exception {
        JsonNode json = objectMapper.readTree(result.getResponse().getContentAsByteArray());
        String temporaryKey = json.path("data").path("storageKey").asText();
        assertFalse(temporaryKey.isBlank());

        Document document = em.createQuery("select d from Document d where d.name = :name", Document.class)
                .setParameter("name", filename).getSingleResult();
        uploadedDocuments.add(document);
        assertNotEquals(document.getStorageKey(), temporaryKey, "The internal storage key must not be returned by the API");
        return new Upload(temporaryKey, document.getStorageKey());
    }

    private void assertPrivateDocumentIsNotDisclosed(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request,
                                                      String marker) throws Exception {
        MvcResult result = mockMvc.perform(request).andReturn();
        assertTrue(result.getResponse().getStatus() >= 400,
                "A foreign user must not receive a private document: HTTP " + result.getResponse().getStatus());
        String body = result.getResponse().getContentAsString();
        assertFalse(body.contains(marker), "A refused response leaked the private marker: " + body);
        assertEquals(0, result.getResponse().getContentAsByteArray().length,
                "A refused private download must not return file bytes");
    }

    private void assertSetting(String value, boolean isPublic) {
        GlobalSettings setting = em.createQuery("select g from GlobalSettings g where g.name = :name", GlobalSettings.class)
                .setParameter("name", SETTING_NAME).getSingleResult();
        assertEquals(value, setting.getValue());
        assertEquals(isPublic, setting.getIsPublic());
    }

    private long countDocuments() {
        return em.createQuery("select count(d) from Document d", Long.class).getSingleResult();
    }

    private void assertStatus(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request, int status) throws Exception {
        MvcResult result = mockMvc.perform(request).andReturn();
        assertEquals(status, result.getResponse().getStatus(), result.getResponse().getContentAsString());
    }

    private void assertContains(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request, int status, String marker) throws Exception {
        MvcResult result = mockMvc.perform(request).andReturn();
        assertEquals(status, result.getResponse().getStatus(), result.getResponse().getContentAsString());
        assertTrue(result.getResponse().getContentAsString().contains(marker), result.getResponse().getContentAsString());
    }

    private Cookie session(User user) {
        return new Cookie(accessCookieName, tokenService.createAccessToken(user));
    }

    private Company company(String name) {
        Company company = new Company();
        company.setName(name);
        company.setStatus(CompanyStatus.ACTIVE);
        em.persist(company);
        return company;
    }

    private User user(String email, UserRole role) {
        User user = new User();
        user.setEmail(email);
        user.setName("Files");
        user.setSurname("Auth");
        user.setPassword("not-used");
        user.setRole(role);
        user.setStatus(UserStatus.ACTIVE);
        em.persist(user);
        return user;
    }

    private void enroll(User user, Company company) {
        CompanyUser membership = new CompanyUser();
        membership.setUser(user);
        membership.setCompany(company);
        membership.setRole(CompanyUserRole.COMPANY_ADMIN);
        em.persist(membership);
        company.getUsers().add(membership);
    }

    private byte[] png() throws Exception {
        BufferedImage image = new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB);
        image.setRGB(0, 0, 0x007a39);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        ImageIO.write(image, "png", bytes);
        return bytes.toByteArray();
    }

    private record Upload(String temporaryKey, String internalKey) { }
}
