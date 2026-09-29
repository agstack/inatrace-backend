package com.abelium.inatrace.components.facility;

import com.abelium.inatrace.components.common.TokenService;
import com.abelium.inatrace.db.entities.codebook.FacilityType;
import com.abelium.inatrace.db.entities.codebook.ProductType;
import com.abelium.inatrace.db.entities.common.Address;
import com.abelium.inatrace.db.entities.common.Country;
import com.abelium.inatrace.db.entities.common.User;
import com.abelium.inatrace.db.entities.company.Company;
import com.abelium.inatrace.db.entities.company.CompanyUser;
import com.abelium.inatrace.db.entities.facility.Facility;
import com.abelium.inatrace.db.entities.facility.FacilityLocation;
import com.abelium.inatrace.db.entities.facility.FacilityTranslation;
import com.abelium.inatrace.db.entities.stockorder.StockOrder;
import com.abelium.inatrace.db.entities.product.Product;
import com.abelium.inatrace.db.entities.product.ProductCompany;
import com.abelium.inatrace.db.entities.product.FinalProduct;
import com.abelium.inatrace.db.entities.value_chain.ValueChain;
import com.abelium.inatrace.db.entities.value_chain.enums.ValueChainStatus;
import com.abelium.inatrace.support.AbstractMySqlIntegrationTest;
import com.abelium.inatrace.types.CompanyStatus;
import com.abelium.inatrace.types.CompanyUserRole;
import com.abelium.inatrace.types.Language;
import com.abelium.inatrace.types.ProductCompanyType;
import com.abelium.inatrace.types.UserRole;
import com.abelium.inatrace.types.UserStatus;
import jakarta.persistence.EntityManager;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class FacilityApiAuthTest extends AbstractMySqlIntegrationTest {

    private static final String MARKER = "ACME-FACILITY-ONLY";

    @Autowired private MockMvc mockMvc;
    @Autowired private EntityManager em;
    @Autowired private TokenService tokenService;

    @Value("${INATrace.auth.accessTokenCookieName}")
    private String accessCookieName;

    private Long companyId;
    private Long facilityId;
    private Long locationId;
    private Long countryId;
    private Long facilityTypeId;
    private Long ownerId;
    private Long rivalCompanyId;
    private Cookie ownerSession;
    private Cookie strangerSession;

    @BeforeEach
    void seed() {
        Company acme = company("Acme facilities");
        Company rival = company("Rival facilities");
        User owner = user("facility-owner@acme.test");
        User stranger = user("facility-stranger@rival.test");
        enroll(owner, acme);
        enroll(stranger, rival);

        Country country = new Country();
        country.setCode("XF");
        country.setName("Facility Test Country");
        em.persist(country);
        countryId = country.getId();

        FacilityType type = new FacilityType("FACILITY_AUTH_TEST", "Facility auth test");
        em.persist(type);
        facilityTypeId = type.getId();

        Address address = new Address();
        address.setCountry(country);
        address.setAddress("Acme facility address");
        FacilityLocation location = new FacilityLocation();
        location.setAddress(address);
        em.persist(location);
        locationId = location.getId();

        Facility facility = new Facility();
        facility.setName(MARKER);
        facility.setCompany(acme);
        facility.setFacilityLocation(location);
        facility.setFacilityType(type);
        facility.setIsCollectionFacility(true);
        facility.setIsPublic(false);
        facility.setIsDeactivated(false);
        em.persist(facility);
        facilityId = facility.getId();

        FacilityTranslation translation = new FacilityTranslation();
        translation.setFacility(facility);
        translation.setLanguage(Language.EN);
        translation.setName(MARKER);
        em.persist(translation);
        facility.getFacilityTranslations().add(translation);

        companyId = acme.getId();
        rivalCompanyId = rival.getId();
        ownerId = owner.getId();
        ownerSession = cookie(owner);
        strangerSession = cookie(stranger);
        em.flush();
        em.clear();
    }

    @Test
    void facilityReadsAreScopedToOwningCompany() throws Exception {
        for (String path : List.of(
                "/api/chain/facility/" + facilityId,
                "/api/chain/facility/" + facilityId + "/detail",
                "/api/chain/facility/list/company/" + companyId,
                "/api/chain/facility/list/company/" + companyId + "/all",
                "/api/chain/facility/list/collecting/company/" + companyId)) {
            assertAllowedAndRefused(path, true);
        }
    }

    @Test
    void availableSellingListChecksEnrollmentEvenWhenNoProductsAreConnected() throws Exception {
        assertAllowedAndRefused("/api/chain/facility/list/company/" + companyId + "/available-selling", false);
    }

    @Test
    void connectedPublicSellerIsVisibleButUnrelatedTenantIsNot() throws Exception {
        Long publicFacilityId = seedConnectedPublicSeller();
        Company unrelated = company("Unrelated public facility test");
        User unrelatedUser = user("unrelated-facility-35@test.local");
        enroll(unrelatedUser, unrelated);
        Cookie unrelatedSession = cookie(unrelatedUser);
        em.flush();
        em.clear();

        String publicPath = "/api/chain/facility/" + publicFacilityId;
        MvcResult connected = mockMvc.perform(get(publicPath).cookie(ownerSession)).andReturn();
        assertEquals(200, connected.getResponse().getStatus(), connected.getResponse().getContentAsString());
        assertTrue(connected.getResponse().getContentAsString().contains("CONNECTED-SELLER-35"));
        MvcResult disconnected = mockMvc.perform(get(publicPath).cookie(unrelatedSession)).andReturn();
        assertEquals(403, disconnected.getResponse().getStatus(), disconnected.getResponse().getContentAsString());
        assertFalse(disconnected.getResponse().getContentAsString().contains("CONNECTED-SELLER-35"));
        MvcResult anonymous = mockMvc.perform(get(publicPath)).andReturn();
        assertEquals(401, anonymous.getResponse().getStatus());

        String listPath = "/api/chain/facility/list/company/" + companyId + "/available-selling";
        MvcResult sellerList = mockMvc.perform(get(listPath).cookie(ownerSession)).andReturn();
        assertEquals(200, sellerList.getResponse().getStatus(), sellerList.getResponse().getContentAsString());
        assertTrue(sellerList.getResponse().getContentAsString().contains("CONNECTED-SELLER-35"));
        MvcResult refusedList = mockMvc.perform(get(listPath).cookie(strangerSession)).andReturn();
        assertEquals(403, refusedList.getResponse().getStatus(), refusedList.getResponse().getContentAsString());
        assertFalse(refusedList.getResponse().getContentAsString().contains("CONNECTED-SELLER-35"));
        assertEquals(401, mockMvc.perform(get(listPath)).andReturn().getResponse().getStatus());
    }

    @Test
    void facilityCanAttachFinalProductFromConnectedValueChain() throws Exception {
        seedConnectedPublicSeller();
        Product connectedProduct = em.createQuery(
                "SELECT p FROM Product p WHERE p.name = :name", Product.class)
                .setParameter("name", "Connected facility product").getSingleResult();
        FinalProduct finalProduct = new FinalProduct();
        finalProduct.setName("CONNECTED-FINAL-35");
        finalProduct.setDescription("Shared through product association");
        finalProduct.setProduct(connectedProduct);
        em.persist(finalProduct);
        em.flush();
        em.clear();

        String body = facilityBody(facilityId, "ACME-CONNECTED-FINAL-35")
                .replace("\"facilityFinalProducts\":[]",
                        "\"facilityFinalProducts\":[{\"id\":" + finalProduct.getId() + "}]");
        MvcResult allowed = mockMvc.perform(put("/api/chain/facility").cookie(ownerSession)
                .contentType(MediaType.APPLICATION_JSON).content(body)).andReturn();
        assertEquals(200, allowed.getResponse().getStatus(), allowed.getResponse().getContentAsString());
        em.flush();
        em.clear();
        assertEquals(finalProduct.getId(), em.find(Facility.class, facilityId)
                .getFacilityFinalProducts().iterator().next().getFinalProduct().getId());
    }

    private Long seedConnectedPublicSeller() {
        Company acme = em.find(Company.class, companyId);
        Company rival = em.find(Company.class, rivalCompanyId);
        ProductType productType = new ProductType();
        productType.setCode("FACILITY_PUBLIC_35");
        productType.setName("Public facility product type");
        em.persist(productType);
        ValueChain valueChain = new ValueChain();
        valueChain.setName("Public facility chain");
        valueChain.setDescription("Connected public seller test");
        valueChain.setValueChainStatus(ValueChainStatus.ENABLED);
        valueChain.setCreatedBy(em.find(User.class, ownerId));
        valueChain.setProductType(productType);
        em.persist(valueChain);
        Product product = new Product();
        product.setName("Connected facility product");
        product.setCompany(acme);
        product.setValueChain(valueChain);
        product.setProcess(null);
        product.setResponsibility(null);
        product.setSustainability(null);
        product.setJourney(null);
        product.setSettings(null);
        product.setBusinessToCustomerSettings(null);
        em.persist(product);
        ProductCompany buyer = new ProductCompany();
        buyer.setProduct(product);
        buyer.setCompany(acme);
        buyer.setType(ProductCompanyType.BUYER);
        em.persist(buyer);
        product.getAssociatedCompanies().add(buyer);
        ProductCompany exporter = new ProductCompany();
        exporter.setProduct(product);
        exporter.setCompany(rival);
        exporter.setType(ProductCompanyType.EXPORTER);
        em.persist(exporter);
        product.getAssociatedCompanies().add(exporter);

        Facility seller = new Facility();
        seller.setName("CONNECTED-SELLER-35");
        seller.setCompany(rival);
        seller.setFacilityLocation(em.find(FacilityLocation.class, locationId));
        seller.setFacilityType(em.find(FacilityType.class, facilityTypeId));
        seller.setIsPublic(true);
        seller.setIsDeactivated(false);
        em.persist(seller);
        FacilityTranslation translation = new FacilityTranslation();
        translation.setFacility(seller);
        translation.setLanguage(Language.EN);
        translation.setName("CONNECTED-SELLER-35");
        em.persist(translation);
        seller.getFacilityTranslations().add(translation);
        return seller.getId();
    }

    @Test
    void foreignUserCannotActivateOrDeactivateFacility() throws Exception {
        for (String action : List.of("activate", "deactivate")) {
            String path = "/api/chain/facility/" + facilityId + "/" + action;
            MvcResult refused = mockMvc.perform(put(path).cookie(strangerSession)).andReturn();
            assertEquals(403, refused.getResponse().getStatus(), refused.getResponse().getContentAsString());
            assertFalse(refused.getResponse().getContentAsString().contains(MARKER));
            em.clear();
            assertFalse(em.find(Facility.class, facilityId).getIsDeactivated());

            MvcResult anonymous = mockMvc.perform(put(path)).andReturn();
            assertEquals(401, anonymous.getResponse().getStatus());
        }

        MvcResult deactivated = mockMvc.perform(put("/api/chain/facility/" + facilityId + "/deactivate")
                .cookie(ownerSession)).andReturn();
        assertEquals(200, deactivated.getResponse().getStatus(), deactivated.getResponse().getContentAsString());
        em.flush();
        em.clear();
        assertTrue(em.find(Facility.class, facilityId).getIsDeactivated());

        MvcResult activated = mockMvc.perform(put("/api/chain/facility/" + facilityId + "/activate")
                .cookie(ownerSession)).andReturn();
        assertEquals(200, activated.getResponse().getStatus(), activated.getResponse().getContentAsString());
        em.flush();
        em.clear();
        assertFalse(em.find(Facility.class, facilityId).getIsDeactivated());
    }

    @Test
    void facilityUpdateAndCreationRejectForeignCompanyWithoutMutation() throws Exception {
        String update = facilityBody(facilityId, "ACME-UPDATED-FACILITY-ONLY");
        MvcResult refusedUpdate = mockMvc.perform(put("/api/chain/facility").cookie(strangerSession)
                .contentType(MediaType.APPLICATION_JSON).content(update)).andReturn();
        assertEquals(403, refusedUpdate.getResponse().getStatus(), refusedUpdate.getResponse().getContentAsString());
        assertFalse(refusedUpdate.getResponse().getContentAsString().contains("ACME-UPDATED-FACILITY-ONLY"));
        em.clear();
        assertEquals(MARKER, em.find(Facility.class, facilityId).getName());

        long before = facilityCount();
        String create = facilityBody(null, "ACME-NEW-FACILITY-ONLY");
        MvcResult refusedCreate = mockMvc.perform(put("/api/chain/facility").cookie(strangerSession)
                .contentType(MediaType.APPLICATION_JSON).content(create)).andReturn();
        assertEquals(403, refusedCreate.getResponse().getStatus(), refusedCreate.getResponse().getContentAsString());
        assertEquals(before, facilityCount());

        MvcResult anonymous = mockMvc.perform(put("/api/chain/facility")
                .contentType(MediaType.APPLICATION_JSON).content(update)).andReturn();
        assertEquals(401, anonymous.getResponse().getStatus());
        assertEquals(before, facilityCount());

        MvcResult allowedUpdate = mockMvc.perform(put("/api/chain/facility").cookie(ownerSession)
                .contentType(MediaType.APPLICATION_JSON).content(update)).andReturn();
        assertEquals(200, allowedUpdate.getResponse().getStatus(), allowedUpdate.getResponse().getContentAsString());
        em.flush();
        em.clear();
        assertEquals("ACME-UPDATED-FACILITY-ONLY", em.find(Facility.class, facilityId).getFacilityTranslations()
                .iterator().next().getName());

        MvcResult allowedCreate = mockMvc.perform(put("/api/chain/facility").cookie(ownerSession)
                .contentType(MediaType.APPLICATION_JSON).content(create)).andReturn();
        assertEquals(200, allowedCreate.getResponse().getStatus(), allowedCreate.getResponse().getContentAsString());
        assertEquals(before + 1, facilityCount());
    }

    @Test
    void facilityCannotAttachUnrelatedTenantsFinalProduct() throws Exception {
        ProductType type = new ProductType();
        type.setCode("FOREIGN_FINAL_35");
        type.setName("Foreign final product type");
        em.persist(type);
        ValueChain chain = new ValueChain();
        chain.setName("Foreign final product chain");
        chain.setDescription("Unrelated to Acme");
        chain.setValueChainStatus(ValueChainStatus.ENABLED);
        chain.setProductType(type);
        chain.setCreatedBy(em.find(User.class, ownerId));
        em.persist(chain);
        Product product = new Product();
        product.setName("Rival-only product 35");
        product.setCompany(em.find(Company.class, rivalCompanyId));
        product.setValueChain(chain);
        product.setProcess(null);
        product.setResponsibility(null);
        product.setSustainability(null);
        product.setJourney(null);
        product.setSettings(null);
        product.setBusinessToCustomerSettings(null);
        em.persist(product);
        ProductCompany association = new ProductCompany();
        association.setProduct(product);
        association.setCompany(em.find(Company.class, rivalCompanyId));
        association.setType(ProductCompanyType.OWNER);
        em.persist(association);
        product.getAssociatedCompanies().add(association);
        FinalProduct finalProduct = new FinalProduct();
        finalProduct.setName("RIVAL-FINAL-ONLY-35");
        finalProduct.setDescription("Must not be attached by Acme");
        finalProduct.setProduct(product);
        em.persist(finalProduct);
        em.flush();
        em.clear();

        String body = facilityBody(facilityId, "ACME-FOREIGN-FINAL-ATTEMPT-35")
                .replace("\"facilityFinalProducts\":[]",
                        "\"facilityFinalProducts\":[{\"id\":" + finalProduct.getId() + "}]");
        MvcResult refused = mockMvc.perform(put("/api/chain/facility").cookie(ownerSession)
                .contentType(MediaType.APPLICATION_JSON).content(body)).andReturn();
        assertEquals(403, refused.getResponse().getStatus(), refused.getResponse().getContentAsString());
        assertFalse(refused.getResponse().getContentAsString().contains("RIVAL-FINAL-ONLY-35"));
        em.clear();
        Facility original = em.find(Facility.class, facilityId);
        assertEquals(MARKER, original.getName());
        assertTrue(original.getFacilityFinalProducts().isEmpty());
    }

    @Test
    void rejectedFacilityDeletePreservesFacilityAndLocation() throws Exception {
        String path = "/api/chain/facility/" + facilityId;
        MvcResult refused = mockMvc.perform(delete(path).cookie(strangerSession)).andReturn();
        assertEquals(403, refused.getResponse().getStatus(), refused.getResponse().getContentAsString());
        assertFalse(refused.getResponse().getContentAsString().contains(MARKER));
        em.clear();
        assertEquals(MARKER, em.find(Facility.class, facilityId).getName());
        assertEquals(locationId, em.find(Facility.class, facilityId).getFacilityLocation().getId());

        MvcResult anonymous = mockMvc.perform(delete(path)).andReturn();
        assertEquals(401, anonymous.getResponse().getStatus());

        MvcResult allowed = mockMvc.perform(delete(path).cookie(ownerSession)).andReturn();
        assertEquals(200, allowed.getResponse().getStatus(), allowed.getResponse().getContentAsString());
        em.flush();
        em.clear();
        assertNull(em.find(Facility.class, facilityId));
        assertNull(em.find(FacilityLocation.class, locationId));
    }

    @Test
    void ownerCannotDeleteFacilityWithStockHistory() throws Exception {
        StockOrder order = new StockOrder();
        order.setCompany(em.find(Company.class, companyId));
        order.setFacility(em.find(Facility.class, facilityId));
        order.setCreatedBy(em.find(User.class, ownerId));
        order.setIdentifier("ACME-FACILITY-STOCK-HISTORY-35");
        em.persist(order);
        em.flush();
        em.clear();

        MvcResult refused = mockMvc.perform(delete("/api/chain/facility/" + facilityId)
                .cookie(ownerSession)).andReturn();
        assertEquals(400, refused.getResponse().getStatus(), refused.getResponse().getContentAsString());
        assertFalse(refused.getResponse().getContentAsString().contains("ACME-FACILITY-STOCK-HISTORY-35"));
        em.clear();
        assertEquals(MARKER, em.find(Facility.class, facilityId).getName());
        assertEquals(locationId, em.find(Facility.class, facilityId).getFacilityLocation().getId());
        assertEquals(order.getId(), em.createQuery(
                "SELECT so.id FROM StockOrder so WHERE so.identifier = :identifier", Long.class)
                .setParameter("identifier", "ACME-FACILITY-STOCK-HISTORY-35").getSingleResult());
    }

    private void assertAllowedAndRefused(String path, boolean expectMarker) throws Exception {
        MvcResult allowed = mockMvc.perform(get(path).cookie(ownerSession)).andReturn();
        String ownerBody = allowed.getResponse().getContentAsString();
        assertEquals(200, allowed.getResponse().getStatus(), path + ": " + ownerBody);
        if (expectMarker) {
            assertTrue(ownerBody.contains(MARKER), path + ": " + ownerBody);
        }

        MvcResult refused = mockMvc.perform(get(path).cookie(strangerSession)).andReturn();
        String strangerBody = refused.getResponse().getContentAsString();
        assertEquals(403, refused.getResponse().getStatus(), path + ": " + strangerBody);
        assertFalse(strangerBody.contains(MARKER), path + ": " + strangerBody);

        MvcResult anonymous = mockMvc.perform(get(path)).andReturn();
        assertEquals(401, anonymous.getResponse().getStatus(), path);
        assertFalse(anonymous.getResponse().getContentAsString().contains(MARKER));
    }

    private String facilityBody(Long id, String name) {
        String idField = id == null ? "" : "\"id\":" + id + ",";
        return """
                {%s"company":{"id":%d},"isCollectionFacility":true,"isPublic":false,
                 "facilityLocation":{"address":{"address":"Acme facility address","country":{"id":%d}}},
                 "facilityType":{"id":%d},"facilitySemiProductList":[],"facilityFinalProducts":[],
                 "facilityValueChains":[],"translations":[{"language":"EN","name":"%s"}]}
                """.formatted(idField, companyId, countryId, facilityTypeId, name);
    }

    private long facilityCount() {
        em.flush();
        return em.createQuery("SELECT COUNT(f) FROM Facility f", Long.class).getSingleResult();
    }

    private Company company(String name) {
        Company company = new Company();
        company.setName(name);
        company.setStatus(CompanyStatus.ACTIVE);
        em.persist(company);
        return company;
    }

    private User user(String email) {
        User user = new User();
        user.setEmail(email);
        user.setName(email);
        user.setSurname("Test");
        user.setPassword("not-used-in-this-test");
        user.setRole(UserRole.USER);
        user.setStatus(UserStatus.ACTIVE);
        em.persist(user);
        return user;
    }

    private void enroll(User user, Company company) {
        CompanyUser enrollment = new CompanyUser();
        enrollment.setUser(user);
        enrollment.setCompany(company);
        enrollment.setRole(CompanyUserRole.COMPANY_ADMIN);
        em.persist(enrollment);
        company.getUsers().add(enrollment);
    }

    private Cookie cookie(User user) {
        return new Cookie(accessCookieName, tokenService.createAccessToken(user));
    }
}
