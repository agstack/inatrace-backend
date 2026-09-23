package com.abelium.inatrace.db.entities;

import com.abelium.inatrace.components.common.api.ApiActivityProof;
import com.abelium.inatrace.components.common.api.ApiDocument;
import com.abelium.inatrace.components.common.api.ApiCountry;
import com.abelium.inatrace.components.company.api.ApiAddress;
import com.abelium.inatrace.components.company.api.ApiCompany;
import com.abelium.inatrace.components.company.api.ApiUserCustomer;
import com.abelium.inatrace.components.facility.api.ApiFacility;
import com.abelium.inatrace.components.facility.api.ApiFacilityLocation;
import com.abelium.inatrace.components.facility.FacilityService;
import com.abelium.inatrace.components.codebook.facility_type.api.ApiFacilityType;
import com.abelium.inatrace.components.payment.PaymentService;
import com.abelium.inatrace.components.payment.api.ApiBulkPayment;
import com.abelium.inatrace.components.payment.api.ApiPayment;
import com.abelium.inatrace.components.stockorder.StockOrderService;
import com.abelium.inatrace.components.stockorder.api.ApiStockOrder;
import com.abelium.inatrace.components.stockorder.api.ApiStockOrderLocation;
import com.abelium.inatrace.components.codebook.semiproduct.api.ApiSemiProduct;
import com.abelium.inatrace.api.errors.ApiException;
import com.abelium.inatrace.db.entities.codebook.SemiProduct;
import com.abelium.inatrace.db.entities.codebook.FacilityType;
import com.abelium.inatrace.db.entities.common.ActivityProof;
import com.abelium.inatrace.db.entities.common.Address;
import com.abelium.inatrace.db.entities.common.Country;
import com.abelium.inatrace.db.entities.common.Document;
import com.abelium.inatrace.db.entities.common.User;
import com.abelium.inatrace.db.entities.common.UserCustomer;
import com.abelium.inatrace.db.entities.company.Company;
import com.abelium.inatrace.db.entities.company.CompanyUser;
import com.abelium.inatrace.db.entities.facility.Facility;
import com.abelium.inatrace.db.entities.facility.FacilityLocation;
import com.abelium.inatrace.db.entities.payment.BulkPayment;
import com.abelium.inatrace.db.entities.payment.RecipientType;
import com.abelium.inatrace.db.entities.stockorder.StockOrder;
import com.abelium.inatrace.db.entities.stockorder.StockOrderActivityProof;
import com.abelium.inatrace.db.entities.stockorder.StockOrderLocation;
import com.abelium.inatrace.db.entities.stockorder.enums.OrderType;
import com.abelium.inatrace.security.service.CustomUserDetails;
import com.abelium.inatrace.types.CompanyStatus;
import com.abelium.inatrace.types.CompanyUserRole;
import com.abelium.inatrace.types.UserRole;
import com.abelium.inatrace.types.UserStatus;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
@Transactional
class SharedReferenceServiceTest {

    @Container
    @ServiceConnection
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4");

    @Autowired EntityManager em;
    @Autowired StockOrderService stockOrderService;
    @Autowired PaymentService paymentService;
    @Autowired FacilityService facilityService;

    private Company company;
    private Facility facility;
    private SemiProduct semiProduct;
    private UserCustomer farmer;
    private CustomUserDetails principal;

    @BeforeEach
    void seed() {
        User user = new User();
        user.setEmail(UUID.randomUUID() + "@example.test");
        user.setName("Alice");
        user.setSurname("Farmer");
        user.setPassword("unused");
        user.setRole(UserRole.USER);
        user.setStatus(UserStatus.ACTIVE);
        em.persist(user);

        company = new Company();
        company.setName("Coffee " + UUID.randomUUID());
        company.setStatus(CompanyStatus.ACTIVE);
        em.persist(company);

        CompanyUser membership = new CompanyUser();
        membership.setUser(user);
        membership.setCompany(company);
        membership.setRole(CompanyUserRole.COMPANY_ADMIN);
        em.persist(membership);
        company.getUsers().add(membership);

        facility = new Facility();
        facility.setName("Collection station");
        facility.setCompany(company);
        em.persist(facility);

        semiProduct = new SemiProduct();
        semiProduct.setName("Coffee cherry");
        em.persist(semiProduct);

        farmer = new UserCustomer();
        farmer.setName("Producer");
        farmer.setCompany(company);
        em.persist(farmer);

        principal = new CustomUserDetails(user.getId(), user.getEmail(), user.getName(),
                user.getSurname(), user.getRole());
        em.flush();
    }

    @Test
    void purchaseOrderSavesLocationAndProofAndCleansReplacedProof() throws Exception {
        Document firstDocument = document();
        ApiStockOrder request = purchaseOrder();
        request.setProductionLocation(location(null, 1.0));
        request.setActivityProofs(List.of(proof(firstDocument)));

        Long orderId = stockOrderService.createOrUpdateStockOrder(request, principal, null).getId();
        em.flush();
        em.clear();

        StockOrder saved = em.find(StockOrder.class, orderId);
        assertNotNull(saved.getProductionLocation().getId());
        assertEquals(1, saved.getActivityProofs().size());
        Long oldProofId = saved.getActivityProofs().iterator().next().getActivityProof().getId();
        assertNotNull(em.find(ActivityProof.class, oldProofId));

        Document secondDocument = document();
        ApiStockOrder update = purchaseOrder();
        update.setId(orderId);
        update.setProductionLocation(location(saved.getProductionLocation().getId(), 1.0));
        update.setActivityProofs(List.of(proof(secondDocument)));
        stockOrderService.createOrUpdateStockOrder(update, principal, null);
        em.flush();
        em.clear();

        assertNull(em.find(ActivityProof.class, oldProofId));
        assertEquals(1, em.find(StockOrder.class, orderId).getActivityProofs().size());
    }

    @Test
    void editingSharedProductionLocationDoesNotChangeOtherOrder() throws Exception {
        ApiStockOrder first = purchaseOrder();
        first.setProductionLocation(location(null, 1.0));
        Long firstId = stockOrderService.createOrUpdateStockOrder(first, principal, null).getId();
        em.flush();
        Long originalLocationId = em.find(StockOrder.class, firstId).getProductionLocation().getId();

        ApiStockOrder second = purchaseOrder();
        second.setProductionLocation(location(originalLocationId, 1.0));
        Long secondId = stockOrderService.createOrUpdateStockOrder(second, principal, null).getId();
        em.flush();

        ApiStockOrder update = purchaseOrder();
        update.setId(firstId);
        update.setProductionLocation(location(originalLocationId, 2.0));
        stockOrderService.createOrUpdateStockOrder(update, principal, null);
        em.flush();
        em.clear();

        StockOrder firstSaved = em.find(StockOrder.class, firstId);
        StockOrder secondSaved = em.find(StockOrder.class, secondId);
        assertNotEquals(originalLocationId, firstSaved.getProductionLocation().getId());
        assertEquals(originalLocationId, secondSaved.getProductionLocation().getId());
        assertEquals(1.0, secondSaved.getProductionLocation().getLatitude());
        assertEquals(2.0, firstSaved.getProductionLocation().getLatitude());

        Long replacementId = firstSaved.getProductionLocation().getId();
        stockOrderService.deleteStockOrder(firstId, principal);
        em.flush();
        assertNull(em.find(StockOrderLocation.class, replacementId));
        assertNotNull(em.find(StockOrderLocation.class, originalLocationId));
    }

    @Test
    void replacingOneOrderProofKeepsAProofReferencedByAnotherOrder() throws Exception {
        Document document = document();
        ApiStockOrder first = purchaseOrder();
        first.setActivityProofs(List.of(proof(document)));
        Long firstId = stockOrderService.createOrUpdateStockOrder(first, principal, null).getId();
        Long secondId = stockOrderService.createOrUpdateStockOrder(purchaseOrder(), principal, null).getId();
        em.flush();

        ActivityProof sharedProof = em.find(StockOrder.class, firstId).getActivityProofs()
                .iterator().next().getActivityProof();
        StockOrderActivityProof secondLink = new StockOrderActivityProof();
        secondLink.setStockOrder(em.find(StockOrder.class, secondId));
        secondLink.setActivityProof(sharedProof);
        em.persist(secondLink);

        ApiStockOrder update = purchaseOrder();
        update.setId(firstId);
        stockOrderService.createOrUpdateStockOrder(update, principal, null);
        em.flush();
        em.clear();

        assertNotNull(em.find(ActivityProof.class, sharedProof.getId()));
        assertEquals(1, em.find(StockOrder.class, secondId).getActivityProofs().size());
    }

    @Test
    void rejectsProductionLocationFromAnotherCompany() {
        Company otherCompany = new Company();
        otherCompany.setName("Other company");
        otherCompany.setStatus(CompanyStatus.ACTIVE);
        em.persist(otherCompany);
        StockOrderLocation foreignLocation = new StockOrderLocation();
        foreignLocation.setLatitude(9.0);
        em.persist(foreignLocation);
        StockOrder foreignOrder = new StockOrder();
        foreignOrder.setCompany(otherCompany);
        foreignOrder.setCreatedBy(em.find(User.class, principal.getUserId()));
        foreignOrder.setOrderType(OrderType.PURCHASE_ORDER);
        foreignOrder.setTotalQuantity(BigDecimal.ONE);
        foreignOrder.setProductionLocation(foreignLocation);
        em.persist(foreignOrder);
        em.flush();

        ApiStockOrder request = purchaseOrder();
        request.setProductionLocation(location(foreignLocation.getId(), 9.0));
        assertThrows(ApiException.class,
                () -> stockOrderService.createOrUpdateStockOrder(request, principal, null));
    }

    @Test
    void bulkPaymentSavesAdditionalProof() throws Exception {
        Long orderId = stockOrderService.createOrUpdateStockOrder(purchaseOrder(), principal, null).getId();
        Document document = document();

        ApiPayment payment = new ApiPayment();
        ApiStockOrder orderRef = new ApiStockOrder();
        orderRef.setId(orderId);
        payment.setStockOrder(orderRef);
        payment.setRecipientType(RecipientType.USER_CUSTOMER);
        ApiUserCustomer farmerRef = new ApiUserCustomer();
        farmerRef.setId(farmer.getId());
        payment.setRecipientUserCustomer(farmerRef);
        payment.setAmount(BigDecimal.ONE);

        ApiBulkPayment request = new ApiBulkPayment();
        ApiCompany companyRef = new ApiCompany();
        companyRef.setId(company.getId());
        request.setPayingCompany(companyRef);
        request.setPaymentDescription("Producer payments");
        request.setReceiptNumber("R-1");
        request.setPayments(List.of(payment));
        request.setAdditionalProofs(List.of(proof(document)));

        Long bulkId = paymentService.createBulkPayment(request, principal).getId();
        em.flush();
        em.clear();

        BulkPayment saved = em.find(BulkPayment.class, bulkId);
        assertEquals(1, saved.getAdditionalProofs().size());
        assertNotNull(saved.getAdditionalProofs().iterator().next().getActivityProof().getId());
    }

    @Test
    void editingOneFacilityDoesNotChangeItsSharedLocation() throws Exception {
        Country country = new Country();
        country.setCode("TC");
        country.setName("Test country");
        em.persist(country);

        FacilityType type = new FacilityType("COLLECTION", "Collection");
        em.persist(type);

        FacilityLocation originalLocation = new FacilityLocation();
        Address originalAddress = new Address();
        originalAddress.setCity("Original city");
        originalAddress.setCountry(country);
        originalLocation.setAddress(originalAddress);
        originalLocation.setLatitude(1.0);
        em.persist(originalLocation);
        facility.setFacilityLocation(originalLocation);
        facility.setFacilityType(type);

        Facility other = new Facility();
        other.setName("Second station");
        other.setCompany(company);
        other.setFacilityType(type);
        other.setFacilityLocation(originalLocation);
        em.persist(other);
        em.flush();

        ApiFacility request = new ApiFacility();
        request.setId(facility.getId());
        ApiFacilityType typeRef = new ApiFacilityType();
        typeRef.setId(type.getId());
        request.setFacilityType(typeRef);
        ApiFacilityLocation editedLocation = new ApiFacilityLocation();
        editedLocation.setLatitude(2.0);
        ApiAddress editedAddress = new ApiAddress();
        editedAddress.setCity("Edited city");
        ApiCountry countryRef = new ApiCountry();
        countryRef.setId(country.getId());
        editedAddress.setCountry(countryRef);
        editedLocation.setAddress(editedAddress);
        request.setFacilityLocation(editedLocation);
        request.setFacilitySemiProductList(List.of());
        request.setFacilityFinalProducts(List.of());
        request.setFacilityValueChains(List.of());
        request.setTranslations(List.of());

        facilityService.createOrUpdateFacility(request, principal);
        em.flush();
        em.clear();

        Facility firstSaved = em.find(Facility.class, facility.getId());
        Facility secondSaved = em.find(Facility.class, other.getId());
        assertNotEquals(originalLocation.getId(), firstSaved.getFacilityLocation().getId());
        assertEquals(originalLocation.getId(), secondSaved.getFacilityLocation().getId());
        assertEquals("Original city", secondSaved.getFacilityLocation().getAddress().getCity());
        assertEquals("Edited city", firstSaved.getFacilityLocation().getAddress().getCity());

        facilityService.deleteFacility(firstSaved.getId(), principal);
        em.flush();
        assertNotNull(em.find(FacilityLocation.class, originalLocation.getId()));
    }

    private ApiStockOrder purchaseOrder() {
        ApiStockOrder request = new ApiStockOrder();
        request.setOrderType(OrderType.PURCHASE_ORDER);
        ApiFacility facilityRef = new ApiFacility();
        facilityRef.setId(facility.getId());
        request.setFacility(facilityRef);
        ApiSemiProduct semiProductRef = new ApiSemiProduct();
        semiProductRef.setId(semiProduct.getId());
        request.setSemiProduct(semiProductRef);
        ApiUserCustomer farmerRef = new ApiUserCustomer();
        farmerRef.setId(farmer.getId());
        request.setProducerUserCustomer(farmerRef);
        request.setPriceDeterminedLater(true);
        request.setTotalQuantity(BigDecimal.TEN);
        request.setTotalGrossQuantity(BigDecimal.TEN);
        request.setFulfilledQuantity(BigDecimal.TEN);
        return request;
    }

    private ApiStockOrderLocation location(Long id, Double latitude) {
        ApiStockOrderLocation location = new ApiStockOrderLocation();
        location.setId(id);
        location.setLatitude(latitude);
        location.setLongitude(3.0);
        location.setPinName("Farm");
        return location;
    }

    private ApiActivityProof proof(Document document) {
        ApiActivityProof proof = new ApiActivityProof();
        ApiDocument documentRef = new ApiDocument();
        documentRef.setId(document.getId());
        proof.setDocument(documentRef);
        proof.setType("RECEIPT");
        return proof;
    }

    private Document document() {
        Document document = new Document();
        document.setStorageKey(UUID.randomUUID().toString());
        em.persist(document);
        return document;
    }
}
