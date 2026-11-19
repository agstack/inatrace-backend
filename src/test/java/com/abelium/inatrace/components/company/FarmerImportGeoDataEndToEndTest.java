package com.abelium.inatrace.components.company;

import org.springframework.test.context.ActiveProfiles;
import com.abelium.inatrace.support.AbstractMySqlIntegrationTest;
import com.abelium.inatrace.components.common.TokenService;
import com.abelium.inatrace.db.entities.codebook.ProductType;
import com.abelium.inatrace.db.entities.common.Country;
import com.abelium.inatrace.db.entities.common.PlotCoordinate;
import com.abelium.inatrace.db.entities.common.User;
import com.abelium.inatrace.db.entities.common.UserCustomer;
import com.abelium.inatrace.db.entities.company.Company;
import com.abelium.inatrace.db.entities.company.CompanyUser;
import com.abelium.inatrace.db.entities.value_chain.CompanyValueChain;
import com.abelium.inatrace.db.entities.value_chain.ValueChain;
import com.abelium.inatrace.db.entities.value_chain.enums.ValueChainStatus;
import com.abelium.inatrace.types.CompanyStatus;
import com.abelium.inatrace.types.CompanyUserRole;
import com.abelium.inatrace.types.DocumentType;
import com.abelium.inatrace.types.Language;
import com.abelium.inatrace.types.UserRole;
import com.abelium.inatrace.types.UserStatus;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.apache.poi.xssf.usermodel.XSSFRow;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * End-to-end verification that a genuine farmer-import upload - through the real HTTP endpoints,
 * against the MySQL that {@link AbstractMySqlIntegrationTest} starts - accepts valid plot geodata and
 * rejects invalid geodata, using the real shipped "other countries" template as the base file (the
 * same one verified column-for-column in {@link FarmerImportTemplateAssetTest}).
 *
 * <p>All fixture data (country/user/company/product type/value chain and anything the import
 * creates) is tagged with a unique run id and removed in {@link #cleanup()}, so the tests that
 * share the container do not see each other's farmers.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@ActiveProfiles("test")
class FarmerImportGeoDataEndToEndTest extends AbstractMySqlIntegrationTest {

    private static final String TEMPLATE_PATH =
            "src/test/resources/farmer-import/Template_list_of_farmers_other_countries_en.xlsx";

    // ---------------------------------------------------------------- stub GeoID registry

    /** The one GeoID the stub registry knows. */
    private static final String KNOWN_GEOID = "018f3b2c-1a4e-8000-9c3d-2b6f5a1e7d40";

    private static final String UNKNOWN_GEOID = "018f3b2c-1a4e-8000-9c3d-000000000000";

    /** What the registry answers for {@link #KNOWN_GEOID}: a triangle near (5.40, 10.40). */
    private static final String REGISTRY_GEOJSON =
            "{\"type\":\"Feature\",\"properties\":{},\"geometry\":{\"type\":\"Polygon\",\"coordinates\":"
                    + "[[[10.400000123,5.400000123],[10.41,5.41],[10.42,5.40],[10.400000123,5.400000123]]]}}";

    private static final AtomicInteger REGISTRY_REQUESTS = new AtomicInteger();
    private static volatile java.util.concurrent.CountDownLatch registryBlocked;
    private static volatile java.util.concurrent.CountDownLatch registryRelease;

    /** Started before the Spring context so that {@link #geoIdRegistry} can hand out its port. */
    private static final HttpServer REGISTRY = startStubRegistry();

    private static HttpServer startStubRegistry() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/", exchange -> {
                REGISTRY_REQUESTS.incrementAndGet();
                var blocked = registryBlocked;
                var release = registryRelease;
                if (blocked != null && release != null) {
                    blocked.countDown();
                    try {
                        release.await(10, java.util.concurrent.TimeUnit.SECONDS);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }
                byte[] body;
                int status;
                if (exchange.getRequestURI().getPath().endsWith(KNOWN_GEOID)) {
                    body = REGISTRY_GEOJSON.getBytes(StandardCharsets.UTF_8);
                    status = 200;
                } else {
                    body = "{\"detail\":\"not found\"}".getBytes(StandardCharsets.UTF_8);
                    status = 404;
                }
                exchange.getResponseHeaders().add("Content-Type", "application/geo+json");
                exchange.sendResponseHeaders(status, body.length);
                try (OutputStream out = exchange.getResponseBody()) {
                    out.write(body);
                }
            });
            server.start();
            return server;
        } catch (Exception e) {
            throw new IllegalStateException("could not start the stub GeoID registry", e);
        }
    }

    @DynamicPropertySource
    static void geoIdRegistry(DynamicPropertyRegistry registry) {
        registry.add("INATrace.geoid.baseURL", () -> "http://127.0.0.1:" + REGISTRY.getAddress().getPort());
    }

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private TokenService tokenService;

    @Autowired
    private PlatformTransactionManager txManager;

    @PersistenceContext
    private EntityManager em;

    @Value("${INATrace.auth.accessTokenCookieName}")
    private String accessTokenCookieName;

    @Value("${INATrace.fileStorage.root}")
    private String fileStorageRoot;

    private final String runId = "geo-e2e-" + System.nanoTime();
    private final ObjectMapper objectMapper = new ObjectMapper();

    private Long countryId;
    private Long cameroonCountryId;
    private Long userId;
    private Long companyId;
    private Long productTypeId;
    private Long valueChainId;
    private Long companyValueChainId;
    private Long companyUserId;
    private String accessToken;
    private final List<Long> createdDocumentIds = new ArrayList<>();

    @BeforeEach
    void seedFixtures() {
        new TransactionTemplate(txManager).executeWithoutResult(status -> {
            Country country = new Country();
            country.setCode("ZZ");
            country.setName("Geodata E2E Test Country " + runId);
            em.persist(country);

            // The real collection files declare Cameroon, by ISO code in the template and by name
            // in the Kobo export. Seeded here because this database's Country table is empty.
            List<Country> existingCameroon = em.createQuery(
                            "SELECT c FROM Country c WHERE c.code = 'CM'", Country.class)
                    .getResultList();
            if (existingCameroon.isEmpty()) {
                Country cameroon = new Country();
                cameroon.setCode("CM");
                cameroon.setName("Cameroon");
                em.persist(cameroon);
                em.flush();
                cameroonCountryId = cameroon.getId();
            }

            User user = new User();
            user.setEmail(runId + "@example.test");
            user.setName("Geo");
            user.setSurname("Tester");
            user.setStatus(UserStatus.ACTIVE);
            user.setLanguage(Language.EN);
            user.setRole(UserRole.SYSTEM_ADMIN);
            em.persist(user);

            Company company = new Company();
            company.setName("Geodata E2E Test Company " + runId);
            company.setStatus(CompanyStatus.ACTIVE);
            em.persist(company);

            CompanyUser companyUser = new CompanyUser();
            companyUser.setUser(user);
            companyUser.setCompany(company);
            companyUser.setRole(CompanyUserRole.COMPANY_ADMIN);
            em.persist(companyUser);

            ProductType productType = new ProductType();
            productType.setCode("GEOE2E");
            productType.setName("Geodata E2E Test Crop " + runId);
            em.persist(productType);

            ValueChain valueChain = new ValueChain();
            valueChain.setName("Geodata E2E Test Value Chain " + runId);
            valueChain.setDescription("Created by FarmerImportGeoDataEndToEndTest");
            valueChain.setValueChainStatus(ValueChainStatus.ENABLED);
            valueChain.setCreatedBy(user);
            valueChain.setProductType(productType);
            em.persist(valueChain);

            CompanyValueChain companyValueChain = new CompanyValueChain();
            companyValueChain.setCompany(company);
            companyValueChain.setValueChain(valueChain);
            em.persist(companyValueChain);

            em.flush();

            countryId = country.getId();
            userId = user.getId();
            companyId = company.getId();
            productTypeId = productType.getId();
            valueChainId = valueChain.getId();
            companyValueChainId = companyValueChain.getId();
            companyUserId = companyUser.getId();

            accessToken = tokenService.createAccessToken(user);
        });
    }

    @AfterEach
    void cleanup() {
        new TransactionTemplate(txManager).executeWithoutResult(status -> {
            // Remove any farmers the import created (cascades Plot/PlotCoordinate/etc.), plus
            // their UserCustomerLocation, which isn't cascaded from UserCustomer. Scoped by the
            // throwaway company rather than by internal id, because real collection files leave the
            // company-internal id column empty.
            List<UserCustomer> farmers = em.createQuery(
                            "SELECT uc FROM UserCustomer uc WHERE uc.company.id = :companyId",
                            UserCustomer.class)
                    .setParameter("companyId", companyId)
                    .getResultList();
            for (UserCustomer farmer : farmers) {
                var location = farmer.getUserCustomerLocation();
                em.remove(farmer);
                if (location != null) {
                    em.remove(em.merge(location));
                }
            }
            em.flush();

            em.createQuery("DELETE FROM FarmerImportDecision d WHERE d.company.id = :companyId")
                    .setParameter("companyId", companyId).executeUpdate();

            for (Long documentId : createdDocumentIds) {
                deleteIfPresent(com.abelium.inatrace.db.entities.common.Document.class, documentId);
            }

            deleteIfPresent(com.abelium.inatrace.db.entities.value_chain.CompanyValueChain.class, companyValueChainId);
            deleteIfPresent(ValueChain.class, valueChainId);
            deleteIfPresent(CompanyUser.class, companyUserId);
            deleteIfPresent(Company.class, companyId);
            deleteIfPresent(ProductType.class, productTypeId);
            deleteIfPresent(Country.class, countryId);
            deleteIfPresent(Country.class, cameroonCountryId);
            deleteIfPresent(User.class, userId);
        });

        // Best-effort: remove the physical files the uploads wrote under Upload/GENERAL/*.
        File generalDir = Paths.get(fileStorageRoot, DocumentType.GENERAL.toString()).toFile();
        File[] files = generalDir.listFiles();
        if (files != null) {
            for (File f : files) {
                if (f.lastModified() >= testStartMillis) {
                    f.delete();
                }
            }
        }
    }

    private final long testStartMillis = System.currentTimeMillis();

    private <T> void deleteIfPresent(Class<T> type, Object id) {
        if (id == null) {
            return;
        }
        T entity = em.find(type, id);
        if (entity != null) {
            em.remove(entity);
        }
    }

    @Test
    void validPolygon_isAcceptedAndPersistedWithCorrectCoordinates() throws Exception {
        String internalId = runId + "-valid";
        byte[] xlsx = buildWorkbook(internalId,
                "POLYGON((5.2217367 10.2852433, 5.2218067 10.285235, 5.2219302 10.2852027))");

        Long documentId = uploadDocument(xlsx);
        JsonNode response = callImportEndpoint(documentId);

        assertEquals(1, response.get("successful").asInt(), "expected exactly 1 farmer imported: " + response);
        assertTrue(response.get("validationErrors").isEmpty(), "expected no validation errors: " + response);

        new TransactionTemplate(txManager).executeWithoutResult(status -> {
            UserCustomer farmer = em.createQuery(
                            "SELECT uc FROM UserCustomer uc WHERE uc.farmerCompanyInternalId = :id", UserCustomer.class)
                    .setParameter("id", internalId)
                    .getSingleResult();

            assertEquals(1, farmer.getPlots().size());
            var plot = farmer.getPlots().iterator().next();
            List<PlotCoordinate> coordinates = plot.getCoordinates().stream()
                    .sorted((a, b) -> a.getId().compareTo(b.getId()))
                    .toList();

            // The vertices retain the cell's order and precision.
            assertEquals(3, coordinates.size());
            assertEquals(5.2217367, coordinates.get(0).getLatitude());
            assertEquals(10.2852433, coordinates.get(0).getLongitude());
        });
    }

    @Test
    void invalidGeodata_rejectsTheWholeRowAndCreatesNoFarmer() throws Exception {
        String internalId = runId + "-invalid";
        byte[] xlsx = buildWorkbook(internalId,
                "POLYGON((95.17 10.23, 96.18 10.24, 97.19 10.25))"); // out-of-range latitude

        Long documentId = uploadDocument(xlsx);
        JsonNode response = callImportEndpoint(documentId);

        assertEquals(0, response.get("successful").asInt(), "expected no farmers imported: " + response);
        assertEquals(1, response.get("validationErrors").size(), "expected exactly 1 row validation error: " + response);

        JsonNode rowError = response.get("validationErrors").get(0);
        JsonNode columnErrors = rowError.get("columnValidationErrors");
        assertEquals(1, columnErrors.size());
        assertEquals("INVALID_GEODATA", columnErrors.get(0).get("errorType").asText());
        assertEquals("AH6", columnErrors.get(0).get("cellAddress").asText());

        long farmerCount = em.createQuery(
                        "SELECT COUNT(uc) FROM UserCustomer uc WHERE uc.farmerCompanyInternalId = :id", Long.class)
                .setParameter("id", internalId)
                .getSingleResult();
        assertEquals(0L, farmerCount, "no farmer should have been persisted for a rejected row");
    }

    @Test
    void koboGeoshape_isAcceptedAndPersisted() throws Exception {
        String internalId = runId + "-geoshape";
        // A UCCAO collection's format: "lat lon altitude accuracy", points separated by ";"
        byte[] xlsx = buildWorkbook(internalId,
                "5.2217367 10.2852433 1267.1 1.45;5.2218067 10.285235 1267.8 1.3;"
                        + "5.2219302 10.2852027 1267.0 1.3;5.2217367 10.2852433 1267.1 1.45");

        JsonNode response = callImportEndpoint(uploadDocument(xlsx));

        assertEquals(1, response.get("successful").asInt(), "expected exactly 1 farmer imported: " + response);
        assertTrue(response.get("validationErrors").isEmpty(), "expected no validation errors: " + response);

        new TransactionTemplate(txManager).executeWithoutResult(status -> {
            UserCustomer farmer = em.createQuery(
                            "SELECT uc FROM UserCustomer uc WHERE uc.farmerCompanyInternalId = :id", UserCustomer.class)
                    .setParameter("id", internalId)
                    .getSingleResult();

            assertEquals(1, farmer.getPlots().size());
            var plot = farmer.getPlots().iterator().next();
            // 4 vertices, the last repeating the first, stored as recorded
            assertEquals(4, plot.getCoordinates().size());

            List<PlotCoordinate> coordinates = plot.getCoordinates().stream()
                    .sorted((a, b) -> a.getId().compareTo(b.getId()))
                    .toList();
            assertEquals(5.2217367, coordinates.get(0).getLatitude(), "altitude and accuracy are discarded");
            assertEquals(10.2852433, coordinates.get(0).getLongitude());
        });
    }

    /**
     * The hand-filled UCCAO template, uploaded byte-for-byte as the user has it (with personal
     * data anonymised and the plots translated): no internal
     * ids anywhere, ODK geoshape in every geo cell, and a trailing row that carries nothing but
     * three more plots written as {@code P1(...)P2 (...)P3(...)}.
     *
     * <p>Every plot of the collection survives the hand copy, but three of them ended up against
     * the wrong farmer - which the importer cannot detect and faithfully reproduces. That
     * misattribution is what {@link #realKoboExport_attributesEveryPlotToTheRightFarmer()} avoids.</p>
     */
    @Test
    void realFilledTemplate_importsEveryFarmerAndEveryPlot() throws Exception {
        byte[] xlsx = java.nio.file.Files.readAllBytes(
                Paths.get("src/test/resources/farmer-import/UCCAO_filled_template.xlsx"));

        JsonNode response = callImportEndpoint(uploadDocument(xlsx));

        assertTrue(response.get("validationErrors").isEmpty(), "expected no validation errors: " + response);
        // 7 named rows, each its own farmer despite every internal-id cell being empty.
        assertEquals(7, response.get("successful").asInt(), "farmers imported: " + response);

        new TransactionTemplate(txManager).executeWithoutResult(status -> {
            List<UserCustomer> farmers = farmersOfTestCompany();

            assertEquals(7, farmers.size(), "one farmer per named row, not one farmer for the whole file");
            assertEquals(10, totalPlots(farmers),
                    "7 rows with one plot each, plus the 3 plots of the trailing geo-only row: "
                            + plotDistribution(farmers));

            UserCustomer kamga = farmer(farmers, "Kamga");
            // 1 of its own + the 3 of the geo-only row that follows it.
            assertEquals(4, kamga.getPlots().size(), "the P1(...)P2 (...)P3(...) row attaches to the farmer above");
            assertEquals(4, kamga.getPlots().stream().map(p -> p.getPlotName()).distinct().count(),
                    "plots of one farmer get distinct names");

            Double size = farmer(farmers, "Bekono").getPlots().iterator().next().getSize();
            // ~6685 m². In hectares that is 0.66 - the pre-fix code stored 6.68.
            assertTrue(size > 0.6 && size < 0.7, "plot size should be in hectares, was " + size);
        });
    }

    /**
     * The same collection read straight from its KoboToolbox export, with no re-keying at all.
     *
     * <p>The export links each plot to its submission, so the two plots that the hand copy filed
     * against the wrong farmer land where they belong.</p>
     */
    @Test
    void realKoboExport_attributesEveryPlotToTheRightFarmer() throws Exception {
        byte[] xlsx = java.nio.file.Files.readAllBytes(
                Paths.get("src/test/resources/farmer-import/UCCAO_kobo_export.xlsx"));

        JsonNode response = callImportEndpoint(uploadDocument(xlsx));

        assertTrue(response.get("validationErrors").isEmpty(), "expected no validation errors: " + response);
        assertEquals(7, response.get("successful").asInt(), "farmers imported: " + response);

        new TransactionTemplate(txManager).executeWithoutResult(status -> {
            List<UserCustomer> farmers = farmersOfTestCompany();

            assertEquals(7, farmers.size());
            assertEquals(10, totalPlots(farmers), "every plot of every repeat group: " + plotDistribution(farmers));

            // The hand-copied template gives these 1 / 1 / 4; the export gives the true 2 / 1 / 3.
            assertEquals(2, farmer(farmers, "Kevine").getPlots().size());
            assertEquals(1, farmer(farmers, "Wamba").getPlots().size());
            assertEquals(3, farmer(farmers, "Essomba").getPlots().size());

            assertEquals("Cameroon",
                    farmer(farmers, "Clarisse").getUserCustomerLocation().getAddress().getCountry().getName(),
                    "the export names the country instead of using its ISO code");
        });
    }

    private List<UserCustomer> farmersOfTestCompany() {
        return em.createQuery(
                        "SELECT uc FROM UserCustomer uc WHERE uc.company.id = :companyId", UserCustomer.class)
                .setParameter("companyId", companyId)
                .getResultList();
    }

    private static int totalPlots(List<UserCustomer> farmers) {
        return farmers.stream().mapToInt(f -> f.getPlots().size()).sum();
    }

    private static String plotDistribution(List<UserCustomer> farmers) {
        return farmers.stream()
                .map(f -> f.getSurname() + "=" + f.getPlots().size())
                .collect(java.util.stream.Collectors.joining(", "));
    }

    private static UserCustomer farmer(List<UserCustomer> farmers, String surname) {
        return farmers.stream()
                .filter(f -> surname.equals(f.getSurname() == null ? null : f.getSurname().trim()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no farmer " + surname + " in " + plotDistribution(farmers)));
    }

    // ---------------------------------------------------------------- re-imported farmers

    private static final String FIRST_PLOT =
            "POLYGON((5.2217367 10.2852433, 5.2218067 10.285235, 5.2219302 10.2852027))";

    private static final String SECOND_PLOT =
            "POLYGON((5.2000000 10.3000000, 5.2100000 10.3100000, 5.2200000 10.3000000))";

    @Test
    void reimportedFarmerWithInternalId_getsItsNewPlotAttachedInsteadOfATwin() throws Exception {
        String internalId = runId + "-reimport";

        JsonNode first = callImportEndpoint(uploadDocument(buildWorkbook(internalId, FIRST_PLOT)));
        assertEquals(1, first.get("successful").asInt(), "first import: " + first);

        Long secondDocument = uploadDocument(buildWorkbook(internalId, SECOND_PLOT));
        JsonNode second = callImportEndpoint(secondDocument);

        assertEquals(0, second.get("successful").asInt(), "the farmer is not created a second time: " + second);
        assertTrue(second.get("validationErrors").isEmpty(), "expected no validation errors: " + second);
        assertEquals(1, second.get("duplicates").size(), "the row is reported as a duplicate: " + second);

        new TransactionTemplate(txManager).executeWithoutResult(status ->
                assertEquals(1, farmerByInternalId(internalId).getPlots().size(), "no plot is added before confirmation"));

        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.COOKIE, accessTokenCookieName + "=" + accessToken);
        headers.setContentType(MediaType.APPLICATION_JSON);
        String url = "/api/company/userCustomers/import/farmers/" + companyId + "/" + secondDocument + "/confirm-plots";
        var selection = (com.fasterxml.jackson.databind.node.ObjectNode) second.get("duplicates").get(0).deepCopy();
        ((com.fasterxml.jackson.databind.node.ObjectNode) selection.get("plots").get(0)).put("plotName", "forged name");
        HttpEntity<String> confirmation = new HttpEntity<>(selection.toString(), headers);
        assertEquals(HttpStatus.OK, restTemplate.exchange(url, HttpMethod.POST, confirmation, String.class).getStatusCode());
        assertEquals(HttpStatus.OK, restTemplate.exchange(url, HttpMethod.POST, confirmation, String.class).getStatusCode());

        new TransactionTemplate(txManager).executeWithoutResult(status -> {
            assertEquals(1L, countFarmersByInternalId(internalId), "still exactly one farmer with this id");

            UserCustomer farmer = farmerByInternalId(internalId);
            assertEquals(2, farmer.getPlots().size(), "the second file's plot was attached to the existing farmer");

            List<com.abelium.inatrace.db.entities.common.Plot> plots = farmer.getPlots().stream()
                    .sorted((a, b) -> a.getId().compareTo(b.getId()))
                    .toList();
            List<PlotCoordinate> attached = orderedCoordinates(plots.get(1).getCoordinates());

            assertNotEquals("forged name", plots.get(1).getPlotName(), "plot data comes from the uploaded file");

            assertEquals(3, attached.size());
            assertEquals(5.2, attached.get(0).getLatitude());
            assertEquals(10.3, attached.get(0).getLongitude());
            assertEquals(5.22, attached.get(2).getLatitude());
        });
    }

    // ---------------------------------------------------------------- GeoID column

    @Test
    void concurrentConfirmations_addThePlotOnlyOnce() throws Exception {
        String internalId = runId + "-concurrent";
        callImportEndpoint(uploadDocument(buildWorkbook(internalId, FIRST_PLOT)));
        Long document = uploadDocument(buildWorkbook(internalId, SECOND_PLOT));
        JsonNode preview = callImportEndpoint(document);
        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.COOKIE, accessTokenCookieName + "=" + accessToken);
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<String> request = new HttpEntity<>(preview.get("duplicates").get(0).toString(), headers);
        String url = "/api/company/userCustomers/import/farmers/" + companyId + "/" + document + "/confirm-plots";
        var ready = new java.util.concurrent.CountDownLatch(2);
        var start = new java.util.concurrent.CountDownLatch(1);
        var executor = java.util.concurrent.Executors.newFixedThreadPool(2);
        try {
            java.util.concurrent.Callable<org.springframework.http.HttpStatusCode> call = () -> {
                ready.countDown();
                if (!start.await(10, java.util.concurrent.TimeUnit.SECONDS)) throw new IllegalStateException("start timed out");
                return restTemplate.exchange(url, HttpMethod.POST, request, String.class).getStatusCode();
            };
            var first = executor.submit(call);
            var second = executor.submit(call);
            assertTrue(ready.await(10, java.util.concurrent.TimeUnit.SECONDS));
            start.countDown();
            assertEquals(HttpStatus.OK, first.get(30, java.util.concurrent.TimeUnit.SECONDS));
            assertEquals(HttpStatus.OK, second.get(30, java.util.concurrent.TimeUnit.SECONDS));
        } finally {
            executor.shutdownNow();
        }
        new TransactionTemplate(txManager).executeWithoutResult(status ->
                assertEquals(2, farmerByInternalId(internalId).getPlots().size()));
    }

    @Test
    void confirmingAPlotAfterTheFarmerChangedIsRejectedWithoutWriting() throws Exception {
        String internalId = runId + "-concurrent-change";
        callImportEndpoint(uploadDocument(buildWorkbook(internalId, FIRST_PLOT)));
        Long document = uploadDocument(buildWorkbook(internalId, SECOND_PLOT));
        JsonNode preview = callImportEndpoint(document);
        assertEquals(1, preview.get("duplicates").size(), "preview: " + preview);

        // Simulate another operator editing the identifying fields between preview and confirmation.
        new TransactionTemplate(txManager).executeWithoutResult(status -> {
            UserCustomer farmer = farmerByInternalId(internalId);
            farmer.setSurname("Renamed meanwhile");
            farmer.setFarmerCompanyInternalId(internalId + "-edited");
        });

        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.COOKIE, accessTokenCookieName + "=" + accessToken);
        headers.setContentType(MediaType.APPLICATION_JSON);
        String url = "/api/company/userCustomers/import/farmers/" + companyId + "/" + document + "/confirm-plots";
        assertEquals(HttpStatus.BAD_REQUEST, restTemplate.exchange(url, HttpMethod.POST,
                new HttpEntity<>(preview.get("duplicates").get(0).toString(), headers), String.class).getStatusCode());

        new TransactionTemplate(txManager).executeWithoutResult(status ->
                assertEquals(1, farmerByInternalId(internalId + "-edited").getPlots().size(),
                        "no plot is attached after the farmer no longer matches the selection"));
    }

    @Test
    void distinctPlotsThatDifferBeyondSixDecimalsAreNotDiscarded() throws Exception {
        String internalId = runId + "-distinct-precision";
        callImportEndpoint(uploadDocument(buildWorkbook(internalId, FIRST_PLOT)));
        String nearbyPlot = FIRST_PLOT.replace("5.2217367", "5.2217368");
        Long document = uploadDocument(buildWorkbook(internalId, nearbyPlot));
        JsonNode preview = callImportEndpoint(document);
        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.COOKIE, accessTokenCookieName + "=" + accessToken);
        headers.setContentType(MediaType.APPLICATION_JSON);
        String url = "/api/company/userCustomers/import/farmers/" + companyId + "/" + document + "/confirm-plots";
        assertEquals(HttpStatus.OK, restTemplate.exchange(url, HttpMethod.POST,
                new HttpEntity<>(preview.get("duplicates").get(0).toString(), headers), String.class).getStatusCode());
        new TransactionTemplate(txManager).executeWithoutResult(status ->
                assertEquals(2, farmerByInternalId(internalId).getPlots().size()));
    }

    @Test
    void reimportedPlotWithTheSameRingRotatedOrReversed_isNotDuplicated() throws Exception {
        String internalId = runId + "-same-ring";
        callImportEndpoint(uploadDocument(buildWorkbook(internalId, FIRST_PLOT)));

        // Same three vertices, starting at the last one and written in the opposite direction.
        String rotated = FIRST_PLOT.replace(
                "POLYGON((5.2217367 10.2852433, 5.2218067 10.285235, 5.2219302 10.2852027))",
                "POLYGON((5.2219302 10.2852027, 5.2218067 10.285235, 5.2217367 10.2852433))");
        Long document = uploadDocument(buildWorkbook(internalId, rotated));
        JsonNode preview = callImportEndpoint(document);
        assertEquals(1, preview.get("duplicates").size(), "the farmer is still a duplicate: " + preview);

        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.COOKIE, accessTokenCookieName + "=" + accessToken);
        headers.setContentType(MediaType.APPLICATION_JSON);
        String url = "/api/company/userCustomers/import/farmers/" + companyId + "/" + document + "/confirm-plots";
        assertEquals(HttpStatus.OK, restTemplate.exchange(url, HttpMethod.POST,
                new HttpEntity<>(preview.get("duplicates").get(0).toString(), headers), String.class).getStatusCode());

        new TransactionTemplate(txManager).executeWithoutResult(status ->
                assertEquals(1, farmerByInternalId(internalId).getPlots().size(),
                        "the same ring must not be attached twice"));
    }

    @Test
    void slowGeoIdLookupDoesNotHoldTheCompanyLock() throws Exception {
        String internalId = runId + "-slow-geoid";
        callImportEndpoint(uploadDocument(buildWorkbook(internalId, FIRST_PLOT)));
        Long document = uploadDocument(buildWorkbook(List.of(new FarmerRow(internalId, "Tester", null, KNOWN_GEOID))));
        JsonNode preview = callImportEndpoint(document);
        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.COOKIE, accessTokenCookieName + "=" + accessToken);
        headers.setContentType(MediaType.APPLICATION_JSON);
        var request = new HttpEntity<>(preview.get("duplicates").get(0).toString(), headers);
        String url = "/api/company/userCustomers/import/farmers/" + companyId + "/" + document + "/confirm-plots";
        registryBlocked = new java.util.concurrent.CountDownLatch(1);
        registryRelease = new java.util.concurrent.CountDownLatch(1);
        var executor = java.util.concurrent.Executors.newFixedThreadPool(2);
        try {
            var confirmation = executor.submit(() -> restTemplate.exchange(url, HttpMethod.POST, request, String.class));
            assertTrue(registryBlocked.await(10, java.util.concurrent.TimeUnit.SECONDS));
            var lock = executor.submit(() -> new TransactionTemplate(txManager).execute(status -> {
                em.find(Company.class, companyId, jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);
                return true;
            }));
            assertTrue(lock.get(3, java.util.concurrent.TimeUnit.SECONDS), "company must not be locked during GeoID lookup");
            registryRelease.countDown();
            assertEquals(HttpStatus.OK, confirmation.get(20, java.util.concurrent.TimeUnit.SECONDS).getStatusCode());
        } finally {
            registryRelease.countDown();
            registryBlocked = null;
            registryRelease = null;
            executor.shutdownNow();
        }
    }

    @Test
    void knownGeoId_withoutGeoData_getsItsBoundaryFromTheRegistry() throws Exception {
        String internalId = runId + "-geoid";
        REGISTRY_REQUESTS.set(0);

        JsonNode response = callImportEndpoint(uploadDocument(
                buildWorkbook(List.of(new FarmerRow(internalId, "Tester", null, KNOWN_GEOID)))));

        assertEquals(1, response.get("successful").asInt(), "expected exactly 1 farmer imported: " + response);
        assertTrue(response.get("validationErrors").isEmpty(), "expected no validation errors: " + response);
        assertEquals(1, REGISTRY_REQUESTS.get(), "the registry was asked for the GeoID");

        new TransactionTemplate(txManager).executeWithoutResult(status -> {
            UserCustomer farmer = farmerByInternalId(internalId);

            assertEquals(1, farmer.getPlots().size());
            var plot = farmer.getPlots().iterator().next();
            List<PlotCoordinate> coordinates = orderedCoordinates(plot.getCoordinates());

            // The registry's GeoJSON is [lon, lat]; the plot stores it as lat/lon in the given order.
            assertEquals(4, coordinates.size());
            assertEquals(5.400000123, coordinates.get(0).getLatitude());
            assertEquals(10.400000123, coordinates.get(0).getLongitude());
            assertEquals(coordinates.get(0).getLatitude(), coordinates.get(3).getLatitude());
            assertEquals(coordinates.get(0).getLongitude(), coordinates.get(3).getLongitude());
            assertEquals(5.41, coordinates.get(1).getLatitude());
            assertEquals(10.41, coordinates.get(1).getLongitude());
            assertEquals("ha", plot.getUnit());
            assertEquals(KNOWN_GEOID, plot.getGeoId());
        });

        Long farmerId = new TransactionTemplate(txManager).execute(status -> farmerByInternalId(internalId).getId());
        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.COOKIE, accessTokenCookieName + "=" + accessToken);
        ResponseEntity<String> exported = restTemplate.exchange(
                "/api/company/userCustomers/" + farmerId + "/exportGeoData",
                HttpMethod.GET, new HttpEntity<>(headers), String.class);
        assertEquals(HttpStatus.OK, exported.getStatusCode());
        JsonNode feature = objectMapper.readTree(exported.getBody()).get("features").get(0);
        assertEquals(KNOWN_GEOID, feature.get("properties").get("geoID").asText());
        JsonNode ring = feature.get("geometry").get("coordinates").get(0);
        assertEquals(4, ring.size(), "already closed geometry must not gain another closing point");
        assertEquals(10.400000123, ring.get(0).get(0).asDouble());
        assertEquals(5.400000123, ring.get(0).get(1).asDouble());
        assertEquals(ring.get(0), ring.get(3));
    }

    @Test
    void createSeparateFarmer_preservesVerifiedGeoIdFromOriginalDocument() throws Exception {
        String internalId = runId + "-separate-geoid";
        callImportEndpoint(uploadDocument(buildWorkbook(internalId, FIRST_PLOT)));
        Long document = uploadDocument(buildWorkbook(List.of(new FarmerRow(internalId, "Tester", null, KNOWN_GEOID))));
        JsonNode preview = callImportEndpoint(document);
        var selection = (com.fasterxml.jackson.databind.node.ObjectNode) preview.get("duplicates").get(0).deepCopy();
        ((com.fasterxml.jackson.databind.node.ObjectNode) selection.get("plots").get(0)).put("geoId", UNKNOWN_GEOID);
        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.COOKIE, accessTokenCookieName + "=" + accessToken);
        headers.setContentType(MediaType.APPLICATION_JSON);
        String url = "/api/company/userCustomers/import/farmers/" + companyId + "/" + document + "/create-separate";
        var request = new HttpEntity<>(selection.toString(), headers);
        var ready = new java.util.concurrent.CountDownLatch(2);
        var start = new java.util.concurrent.CountDownLatch(1);
        var executor = java.util.concurrent.Executors.newFixedThreadPool(2);
        try {
            java.util.concurrent.Callable<org.springframework.http.HttpStatusCode> call = () -> {
                ready.countDown();
                if (!start.await(10, java.util.concurrent.TimeUnit.SECONDS)) throw new IllegalStateException("start timed out");
                return restTemplate.exchange(url, HttpMethod.POST, request, String.class).getStatusCode();
            };
            var first = executor.submit(call);
            var second = executor.submit(call);
            assertTrue(ready.await(10, java.util.concurrent.TimeUnit.SECONDS));
            start.countDown();
            assertEquals(HttpStatus.OK, first.get(30, java.util.concurrent.TimeUnit.SECONDS));
            assertEquals(HttpStatus.OK, second.get(30, java.util.concurrent.TimeUnit.SECONDS));
            assertEquals(HttpStatus.OK, restTemplate.exchange(url, HttpMethod.POST, request, String.class).getStatusCode());
        } finally {
            executor.shutdownNow();
        }
        new TransactionTemplate(txManager).executeWithoutResult(status -> {
            var farmers = em.createQuery("SELECT u FROM UserCustomer u WHERE u.farmerCompanyInternalId = :id ORDER BY u.id", UserCustomer.class)
                    .setParameter("id", internalId).getResultList();
            assertEquals(2, farmers.size());
            assertEquals(KNOWN_GEOID, farmers.get(1).getPlots().iterator().next().getGeoId());
        });
        new TransactionTemplate(txManager).executeWithoutResult(status -> {
            assertEquals(1L, em.createQuery("SELECT COUNT(d) FROM FarmerImportDecision d WHERE d.document.id = :id", Long.class)
                    .setParameter("id", document).getSingleResult());
            em.remove(em.find(com.abelium.inatrace.db.entities.common.Document.class, document));
            em.flush();
            em.clear();
            assertEquals(0L, em.createQuery("SELECT COUNT(d) FROM FarmerImportDecision d WHERE d.document.id = :id", Long.class)
                    .setParameter("id", document).getSingleResult());
        });
        Long newDocument = uploadDocument(buildWorkbook(List.of(new FarmerRow(internalId, "Tester", null, KNOWN_GEOID))));
        JsonNode newPreview = callImportEndpoint(newDocument);
        String newUrl = "/api/company/userCustomers/import/farmers/" + companyId + "/" + newDocument + "/create-separate";
        assertEquals(HttpStatus.OK, restTemplate.exchange(newUrl, HttpMethod.POST,
                new HttpEntity<>(newPreview.get("duplicates").get(0).toString(), headers), String.class).getStatusCode());
        assertEquals(3L, countFarmersByInternalId(internalId), "a new document represents a new explicit decision");
    }

    @Test
    void unknownGeoId_withGeoData_fallsBackToTheCell() throws Exception {
        String internalId = runId + "-geoid-fallback";
        REGISTRY_REQUESTS.set(0);

        JsonNode response = callImportEndpoint(uploadDocument(
                buildWorkbook(List.of(new FarmerRow(internalId, "Tester", FIRST_PLOT, UNKNOWN_GEOID)))));

        assertEquals(1, response.get("successful").asInt(), "expected exactly 1 farmer imported: " + response);
        assertTrue(response.get("validationErrors").isEmpty(), "expected no validation errors: " + response);
        assertEquals(1, REGISTRY_REQUESTS.get(), "the registry was asked, and answered 404");

        new TransactionTemplate(txManager).executeWithoutResult(status -> {
            UserCustomer farmer = farmerByInternalId(internalId);

            assertEquals(1, farmer.getPlots().size());
            List<PlotCoordinate> coordinates = orderedCoordinates(farmer.getPlots().iterator().next().getCoordinates());

            // The Geo Data cell's own triangle, not the registry's.
            assertEquals(3, coordinates.size());
            assertEquals(5.2217367, coordinates.get(0).getLatitude());
            assertEquals(10.2852433, coordinates.get(0).getLongitude());
        });
    }

    @Test
    void malformedGeoId_isRejectedAsInvalidGeodata() throws Exception {
        String internalId = runId + "-geoid-malformed";
        REGISTRY_REQUESTS.set(0);

        JsonNode response = callImportEndpoint(uploadDocument(
                buildWorkbook(List.of(new FarmerRow(internalId, "Tester", null, "not-a-geoid")))));

        assertEquals(0, response.get("successful").asInt(), "expected no farmers imported: " + response);
        assertEquals(1, response.get("validationErrors").size(), "expected exactly 1 row validation error: " + response);

        JsonNode columnErrors = response.get("validationErrors").get(0).get("columnValidationErrors");
        assertEquals(1, columnErrors.size());
        assertEquals("INVALID_GEODATA", columnErrors.get(0).get("errorType").asText());
        assertEquals("AI6", columnErrors.get(0).get("cellAddress").asText());
        assertEquals(0, REGISTRY_REQUESTS.get(), "a malformed id never reaches the registry");

        assertEquals(0L, countFarmersByInternalId(internalId), "no farmer should have been persisted for a rejected row");
    }

    @Test
    void unknownGeoIdWithoutFallback_doesNotCreateFarmer() throws Exception {
        String internalId = runId + "-geoid-unknown";
        JsonNode response = callImportEndpoint(uploadDocument(buildWorkbook(
                List.of(new FarmerRow(internalId, "Tester", null, UNKNOWN_GEOID)))));
        assertEquals(0, response.get("successful").asInt());
        JsonNode error = response.get("validationErrors").get(0).get("columnValidationErrors").get(0);
        assertEquals("INVALID_GEODATA", error.get("errorType").asText());
        assertEquals("AI6", error.get("cellAddress").asText());
        assertEquals(0L, countFarmersByInternalId(internalId));
    }

    @Test
    void repeatedInternalIdWithDifferentFarmer_rejectsTheWholeFile() throws Exception {
        String internalId = runId + "-conflicting-farmer";
        JsonNode response = callImportEndpoint(uploadDocument(buildWorkbook(List.of(
                new FarmerRow(internalId, "First", FIRST_PLOT, null),
                new FarmerRow(internalId, "Different", SECOND_PLOT, null)))));

        assertEquals(0, response.get("successful").asInt());
        assertEquals("INVALID_VALUE", response.get("validationErrors").get(0)
                .get("columnValidationErrors").get(0).get("errorType").asText());
        assertEquals(0L, countFarmersByInternalId(internalId));
    }

    @Test
    void koboWithInvalidGpsColumn_rejectsTheFileWithoutCreatingFarmer() throws Exception {
        String internalId = runId + "-invalid-kobo";
        byte[] xlsx;
        try (XSSFWorkbook workbook = new XSSFWorkbook(); var output = new java.io.ByteArrayOutputStream()) {
            var submissions = workbook.createSheet("submissions");
            var header = submissions.createRow(0);
            var row = submissions.createRow(1);
            String[] headers = {"Last name", "City", "State", "Country", "Gender", "Farmer ID", "_id", "_index"};
            String[] values = {"Tester", "Testville", "Test Region", "Cameroon", "Female", internalId, "42", "1"};
            for (int i = 0; i < headers.length; i++) {
                header.createCell(i).setCellValue(headers[i]);
                row.createCell(i).setCellValue(values[i]);
            }
            var plots = workbook.createSheet("plots");
            plots.createRow(0).createCell(0).setCellValue("_submission__id");
            plots.getRow(0).createCell(1).setCellValue("GPS");
            plots.createRow(1).createCell(0).setCellValue("42");
            plots.getRow(1).createCell(1).setCellValue("5.1 10.1");
            plots.createRow(2).createCell(0).setCellValue("42");
            plots.getRow(2).createCell(1).setCellValue("invalid geometry");
            workbook.write(output);
            xlsx = output.toByteArray();
        }

        JsonNode response = callImportEndpoint(uploadDocument(xlsx));
        assertEquals(0, response.get("successful").asInt());
        assertEquals("INVALID_GEODATA", response.get("validationErrors").get(0)
                .get("columnValidationErrors").get(0).get("errorType").asText());
        assertEquals(2, response.get("validationErrors").get(0).get("rowIndex").asInt());
        assertEquals("'plots'!B3", response.get("validationErrors").get(0)
                .get("columnValidationErrors").get(0).get("cellAddress").asText());
        assertEquals(0L, countFarmersByInternalId(internalId));
    }

    @Test
    void koboMissingSurnameRejectsEntireFileInsteadOfSkippingSubmission() throws Exception {
        String internalId = runId + "-missing-surname";
        byte[] xlsx;
        try (XSSFWorkbook workbook = new XSSFWorkbook(); var output = new java.io.ByteArrayOutputStream()) {
            var sheet = workbook.createSheet("submissions");
            var header = sheet.createRow(0);
            String[] headers = {"Last name", "City", "State", "Country", "Gender", "Farmer ID", "_id", "_index"};
            String[] values = {"Tester", "Testville", "Test Region", "Cameroon", "Female", internalId, "42", "1"};
            var valid = sheet.createRow(1);
            var missing = sheet.createRow(2);
            for (int i = 0; i < headers.length; i++) {
                header.createCell(i).setCellValue(headers[i]);
                valid.createCell(i).setCellValue(values[i]);
                if (i != 0) missing.createCell(i).setCellValue(i == 5 ? internalId + "-other" : values[i]);
            }
            missing.getCell(6).setCellValue("43");
            missing.getCell(7).setCellValue("2");
            workbook.write(output);
            xlsx = output.toByteArray();
        }
        JsonNode response = callImportEndpoint(uploadDocument(xlsx));
        assertEquals(0, response.get("successful").asInt());
        assertEquals(2, response.get("validationErrors").get(0).get("rowIndex").asInt());
        assertEquals("REQUIRED", response.get("validationErrors").get(0)
                .get("columnValidationErrors").get(0).get("errorType").asText());
        assertEquals("A3", response.get("validationErrors").get(0)
                .get("columnValidationErrors").get(0).get("cellAddress").asText(),
                "the error is addressed to the missing surname cell");
        assertEquals(0L, countFarmersByInternalId(internalId));
    }

    @Test
    void koboFilledRepeatRowWithoutGeometryFailsTheCellAndPersistsNothing() throws Exception {
        String internalId = runId + "-missing-geometry";
        byte[] xlsx;
        try (XSSFWorkbook workbook = new XSSFWorkbook(); var output = new java.io.ByteArrayOutputStream()) {
            var sheet = workbook.createSheet("submissions");
            var header = sheet.createRow(0);
            String[] headers = {"Last name", "City", "State", "Country", "Gender", "Farmer ID", "_id", "_index"};
            String[] values = {"Tester", "Testville", "Test Region", "Cameroon", "Female", internalId, "42", "1"};
            var row = sheet.createRow(1);
            for (int i = 0; i < headers.length; i++) {
                header.createCell(i).setCellValue(headers[i]);
                row.createCell(i).setCellValue(values[i]);
            }
            var plots = workbook.createSheet("plots");
            plots.createRow(0).createCell(0).setCellValue("_submission__id");
            plots.getRow(0).createCell(1).setCellValue("GPS");
            plots.getRow(0).createCell(2).setCellValue("plot size");
            plots.createRow(1).createCell(0).setCellValue("42");
            // The geometry cell is left blank while the row carries a surveyed size.
            plots.getRow(1).createCell(2).setCellValue("1.5");
            workbook.write(output);
            xlsx = output.toByteArray();
        }
        JsonNode response = callImportEndpoint(uploadDocument(xlsx));
        assertEquals(0, response.get("successful").asInt(), "nothing is persisted: " + response);
        JsonNode cellError = response.get("validationErrors").get(0).get("columnValidationErrors").get(0);
        assertEquals("INVALID_GEODATA", cellError.get("errorType").asText());
        assertTrue(cellError.get("cellAddress").asText().startsWith("'plots'!"), cellError.toString());
        assertEquals(0L, countFarmersByInternalId(internalId));
    }

    @Test
    void koboRepeatSheetsRequireExplicitChoicesBeforeAnyPersistence() throws Exception {
        String internalId = runId + "-kobo-choice";
        byte[] xlsx;
        try (XSSFWorkbook workbook = new XSSFWorkbook(); var output = new java.io.ByteArrayOutputStream()) {
            var submissions = workbook.createSheet("submissions");
            var header = submissions.createRow(0);
            var row = submissions.createRow(1);
            String[] headers = {"Last name", "City", "State", "Country", "Gender", "Farmer ID", "_id", "_index"};
            String[] values = {"Tester", "Testville", "Test Region", "Cameroon", "Female", internalId, "42", "1"};
            for (int i = 0; i < headers.length; i++) {
                header.createCell(i).setCellValue(headers[i]);
                row.createCell(i).setCellValue(values[i]);
            }
            var plots = workbook.createSheet("plots");
            plots.createRow(0).createCell(0).setCellValue("_submission__id");
            plots.getRow(0).createCell(1).setCellValue("field location");
            plots.createRow(1).createCell(0).setCellValue("42");
            plots.getRow(1).createCell(1).setCellValue("5.1 10.1");
            var other = workbook.createSheet("survey");
            other.createRow(0).createCell(0).setCellValue("_submission__id");
            other.getRow(0).createCell(1).setCellValue("answer");
            other.createRow(1).createCell(0).setCellValue("42");
            other.getRow(1).createCell(1).setCellValue("yes");
            workbook.write(output);
            xlsx = output.toByteArray();
        }
        Long document = uploadDocument(xlsx);
        JsonNode pending = callImportEndpoint(document);
        assertEquals(2, pending.get("unresolvedKoboSheets").size());
        assertEquals(0, countFarmersByInternalId(internalId));
        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.COOKIE, accessTokenCookieName + "=" + accessToken);
        ResponseEntity<String> response = restTemplate.exchange(
                "/api/company/userCustomers/import/farmers/" + companyId + "/" + document
                        + "?koboSelections=1:1&koboSelections=2:-1",
                HttpMethod.POST, new HttpEntity<>(headers), String.class);
        assertEquals(HttpStatus.OK, response.getStatusCode(), response.getBody());
        assertEquals(1, objectMapper.readTree(response.getBody()).get("successful").asInt());
        assertEquals(1, countFarmersByInternalId(internalId));
        ResponseEntity<String> repeated = restTemplate.exchange(
                "/api/company/userCustomers/import/farmers/" + companyId + "/" + document
                        + "?koboSelections=1:1&koboSelections=2:-1",
                HttpMethod.POST, new HttpEntity<>(headers), String.class);
        JsonNode duplicate = objectMapper.readTree(repeated.getBody()).get("duplicates").get(0);
        headers.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<String> confirmation = restTemplate.exchange(
                "/api/company/userCustomers/import/farmers/" + companyId + "/" + document
                        + "/confirm-plots?koboSelections=1:1&koboSelections=2:-1",
                HttpMethod.POST, new HttpEntity<>(duplicate.toString(), headers), String.class);
        assertEquals(HttpStatus.OK, confirmation.getStatusCode(), confirmation.getBody());
        assertEquals(1, countFarmersByInternalId(internalId));
    }

    @Test
    void confirmPlotsRequiresAuthentication() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        for (String action : List.of("confirm-plots", "create-separate")) {
            ResponseEntity<String> response = restTemplate.exchange(
                    "/api/company/userCustomers/import/farmers/" + companyId + "/1/" + action,
                    HttpMethod.POST, new HttpEntity<>("{}", headers), String.class);
            assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        }
    }

    @Test
    void confirmPlotsRejectsRegionalAdminOutsideCompany() throws Exception {
        Long documentId = uploadDocument(buildWorkbook(runId + "-tenant", FIRST_PLOT));
        Long otherUserId = new TransactionTemplate(txManager).execute(status -> {
            User user = new User();
            user.setEmail(runId + "-other@example.test");
            user.setName("Other");
            user.setSurname("Admin");
            user.setStatus(UserStatus.ACTIVE);
            user.setLanguage(Language.EN);
            user.setRole(UserRole.REGIONAL_ADMIN);
            em.persist(user);
            em.flush();
            return user.getId();
        });
        try {
            String token = new TransactionTemplate(txManager).execute(status -> tokenService.createAccessToken(em.find(User.class, otherUserId)));
            HttpHeaders headers = new HttpHeaders();
            headers.add(HttpHeaders.COOKIE, accessTokenCookieName + "=" + token);
            headers.setContentType(MediaType.APPLICATION_JSON);
            for (String action : List.of("confirm-plots", "create-separate")) {
                ResponseEntity<String> response = restTemplate.exchange(
                        "/api/company/userCustomers/import/farmers/" + companyId + "/" + documentId + "/" + action,
                        HttpMethod.POST, new HttpEntity<>("{\"location\":{\"address\":{}}}", headers), String.class);
                assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
            }
        } finally {
            new TransactionTemplate(txManager).executeWithoutResult(status -> em.remove(em.find(User.class, otherUserId)));
        }
    }

    @Test
    void rowsSharingAGeoId_hitTheRegistryOnce() throws Exception {
        String firstId = runId + "-shared-1";
        String secondId = runId + "-shared-2";
        REGISTRY_REQUESTS.set(0);

        JsonNode response = callImportEndpoint(uploadDocument(buildWorkbook(List.of(
                new FarmerRow(firstId, "Tester", null, KNOWN_GEOID),
                new FarmerRow(secondId, "Other", null, KNOWN_GEOID)))));

        assertEquals(2, response.get("successful").asInt(), "expected 2 farmers imported: " + response);
        assertTrue(response.get("validationErrors").isEmpty(), "expected no validation errors: " + response);
        assertEquals(1, REGISTRY_REQUESTS.get(), "one lookup per distinct GeoID per file");

        new TransactionTemplate(txManager).executeWithoutResult(status -> {
            for (String internalId : List.of(firstId, secondId)) {
                UserCustomer farmer = farmerByInternalId(internalId);
                assertEquals(1, farmer.getPlots().size(), internalId);
                List<PlotCoordinate> coordinates = orderedCoordinates(farmer.getPlots().iterator().next().getCoordinates());
                assertEquals(4, coordinates.size(), internalId);
                assertEquals(5.400000123, coordinates.get(0).getLatitude(), internalId);
                assertEquals(10.400000123, coordinates.get(0).getLongitude(), internalId);
            }
        });
    }

    @Test
    void confirmingOneFarmerDoesNotResolveOtherFarmersGeoIds() throws Exception {
        String firstId = runId + "-selection-1";
        String secondId = runId + "-selection-2";
        callImportEndpoint(uploadDocument(buildWorkbook(List.of(
                new FarmerRow(firstId, "First", FIRST_PLOT, null),
                new FarmerRow(secondId, "Second", FIRST_PLOT, null)))));
        Long document = uploadDocument(buildWorkbook(List.of(
                new FarmerRow(firstId, "First", null, KNOWN_GEOID),
                new FarmerRow(secondId, "Second", FIRST_PLOT, UNKNOWN_GEOID))));
        JsonNode preview = callImportEndpoint(document);
        assertEquals(2, preview.get("duplicates").size());
        REGISTRY_REQUESTS.set(0);
        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.COOKIE, accessTokenCookieName + "=" + accessToken);
        headers.setContentType(MediaType.APPLICATION_JSON);
        String url = "/api/company/userCustomers/import/farmers/" + companyId + "/" + document + "/confirm-plots";
        assertEquals(HttpStatus.OK, restTemplate.exchange(url, HttpMethod.POST,
                new HttpEntity<>(preview.get("duplicates").get(0).toString(), headers), String.class).getStatusCode());
        assertEquals(1, REGISTRY_REQUESTS.get(), "the unrelated farmer's GeoID must not be resolved again");
    }

    /** Plot coordinates in the order they were written, which is the order their ids were assigned. */
    private static List<PlotCoordinate> orderedCoordinates(List<PlotCoordinate> coordinates) {
        return coordinates.stream()
                .sorted((a, b) -> a.getId().compareTo(b.getId()))
                .toList();
    }

    private UserCustomer farmerByInternalId(String internalId) {
        return em.createQuery(
                        "SELECT uc FROM UserCustomer uc WHERE uc.company.id = :companyId AND uc.farmerCompanyInternalId = :id",
                        UserCustomer.class)
                .setParameter("companyId", companyId)
                .setParameter("id", internalId)
                .getSingleResult();
    }

    private long countFarmersByInternalId(String internalId) {
        return em.createQuery(
                        "SELECT COUNT(uc) FROM UserCustomer uc WHERE uc.company.id = :companyId AND uc.farmerCompanyInternalId = :id",
                        Long.class)
                .setParameter("companyId", companyId)
                .setParameter("id", internalId)
                .getSingleResult();
    }

    /** One data row of a built workbook; {@code null} leaves the corresponding cell blank. */
    private record FarmerRow(String internalId, String surname, String geoData, String geoId) {
    }

    /** Builds a single-data-row copy of the real shipped template, matching a realistic full farmer entry. */
    private byte[] buildWorkbook(String internalId, String geoData) throws Exception {
        return buildWorkbook(List.of(new FarmerRow(internalId, "Tester", geoData, null)));
    }

    /** Builds a copy of the real shipped template with one data row per {@link FarmerRow}. */
    private byte[] buildWorkbook(List<FarmerRow> rows) throws Exception {
        try (var in = new FileInputStream(TEMPLATE_PATH);
             XSSFWorkbook workbook = new XSSFWorkbook(in)) {

            XSSFSheet sheet = workbook.getSheetAt(0);
            int rowIndex = 5; // first data row, per UserCustomerImportService.rowIndex = 5

            for (FarmerRow farmerRow : rows) {
                XSSFRow row = sheet.createRow(rowIndex++);

                if (farmerRow.internalId() != null) {
                    row.createCell(0).setCellValue(farmerRow.internalId());
                }
                row.createCell(1).setCellValue(farmerRow.surname());
                row.createCell(2).setCellValue("Geo");
                row.createCell(3).setCellValue("Test Village");
                row.createCell(4).setCellValue("Test Cell");
                row.createCell(5).setCellValue("Test Sector");
                // 6 Caserio, 7 Aldea, 8 Municipio, 9 Departamento - Honduras-only, left blank
                row.createCell(10).setCellValue("123 Test Street");
                row.createCell(11).setCellValue("Testville");
                row.createCell(12).setCellValue("Test Region");
                row.createCell(13).setCellValue("00000");
                // 14 additional address - left blank
                row.createCell(15).setCellValue("ZZ");
                row.createCell(16).setCellValue("F");
                row.createCell(17).setCellValue("555123456");
                row.createCell(18).setCellValue(runId + "@farmer.test");
                row.createCell(19).setCellValue("Y");
                row.createCell(20).setCellValue("ha");
                row.createCell(21).setCellValue(2.5);
                row.createCell(22).setCellValue(2.5);
                row.createCell(23).setCellValue(1000);
                // 24/25 second product type - left blank (template has no second product type column)
                row.createCell(26).setCellValue("N");
                // 27 area organic certified, 28 start of transition - left blank
                row.createCell(29).setCellValue("00123456789");
                row.createCell(30).setCellValue("Geo Tester");
                row.createCell(31).setCellValue("Test Bank");
                row.createCell(32).setCellValue("none");
                if (farmerRow.geoData() != null) {
                    row.createCell(33).setCellValue(farmerRow.geoData());
                }
                if (farmerRow.geoId() != null) {
                    row.createCell(34).setCellValue(farmerRow.geoId());
                }
            }

            try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                workbook.write(out);
                return out.toByteArray();
            }
        }
    }

    private Long uploadDocument(byte[] xlsx) {
        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.COOKIE, accessTokenCookieName + "=" + accessToken);
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", new ByteArrayResource(xlsx) {
            @Override
            public String getFilename() {
                return "farmers.xlsx";
            }
        });

        ResponseEntity<String> response = restTemplate.exchange(
                "/api/common/document?type=GENERAL", HttpMethod.POST, new HttpEntity<>(body, headers), String.class);

        assertEquals(HttpStatus.OK, response.getStatusCode(), "document upload failed: " + response.getBody());
        try {
            JsonNode json = objectMapper.readTree(response.getBody());
            Long documentId = json.get("data").get("id").asLong();
            createdDocumentIds.add(documentId);
            return documentId;
        } catch (Exception e) {
            throw new RuntimeException("Could not parse upload response: " + response.getBody(), e);
        }
    }

    private JsonNode callImportEndpoint(Long documentId) throws Exception {
        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.COOKIE, accessTokenCookieName + "=" + accessToken);

        ResponseEntity<String> response = restTemplate.exchange(
                "/api/company/userCustomers/import/farmers/" + companyId + "/" + documentId,
                HttpMethod.POST, new HttpEntity<>(headers), String.class);

        assertEquals(HttpStatus.OK, response.getStatusCode(), "import call failed: " + response.getBody());
        return objectMapper.readTree(response.getBody());
    }
}
