package com.abelium.inatrace.components.payment;

import com.abelium.inatrace.components.common.TokenService;
import com.abelium.inatrace.db.entities.common.User;
import com.abelium.inatrace.db.entities.company.Company;
import com.abelium.inatrace.db.entities.company.CompanyUser;
import com.abelium.inatrace.db.entities.payment.BulkPayment;
import com.abelium.inatrace.db.entities.payment.Payment;
import com.abelium.inatrace.db.entities.payment.PaymentPurposeType;
import com.abelium.inatrace.db.entities.payment.PaymentStatus;
import com.abelium.inatrace.db.entities.payment.RecipientType;
import com.abelium.inatrace.db.entities.stockorder.StockOrder;
import com.abelium.inatrace.db.entities.stockorder.enums.OrderType;
import com.abelium.inatrace.db.entities.stockorder.enums.PreferredWayOfPayment;
import com.abelium.inatrace.support.AbstractMySqlIntegrationTest;
import com.abelium.inatrace.types.CompanyStatus;
import com.abelium.inatrace.types.CompanyUserRole;
import com.abelium.inatrace.types.UserRole;
import com.abelium.inatrace.types.UserStatus;
import jakarta.persistence.EntityManager;
import jakarta.servlet.http.Cookie;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
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

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class PaymentApiAuthTest extends AbstractMySqlIntegrationTest {

    private static final String RECEIPT = "ACME-PAYMENT-RECEIPT-ONLY";
    private static final String BULK_RECEIPT = "ACME-BULK-RECEIPT-ONLY";

    @Autowired private MockMvc mockMvc;
    @Autowired private EntityManager em;
    @Autowired private TokenService tokenService;

    @Value("${INATrace.auth.accessTokenCookieName}")
    private String accessCookieName;

    private Long companyId;
    private Long purchaseId;
    private Long rivalPurchaseId;
    private Long paymentId;
    private Long bulkPaymentId;
    private Cookie ownerSession;
    private Cookie strangerSession;

    @BeforeEach
    void seed() {
        Company acme = company("Acme payments");
        Company rival = company("Rival payments");
        User owner = user("payment-owner@acme.test");
        User stranger = user("payment-stranger@rival.test");
        enroll(owner, acme);
        enroll(stranger, rival);

        StockOrder purchase = new StockOrder();
        purchase.setCompany(acme);
        purchase.setOrderType(OrderType.PURCHASE_ORDER);
        purchase.setProductionDate(LocalDate.of(2025, 8, 18));
        purchase.setTotalQuantity(new BigDecimal("321.50"));
        purchase.setCost(new BigDecimal("2000.00"));
        purchase.setPreferredWayOfPayment(PreferredWayOfPayment.BANK_TRANSFER);
        purchase.setCreatedBy(owner);
        purchase.setUpdatedBy(owner);
        em.persist(purchase);

        StockOrder rivalPurchase = new StockOrder();
        rivalPurchase.setCompany(rival);
        rivalPurchase.setOrderType(OrderType.PURCHASE_ORDER);
        rivalPurchase.setProductionDate(LocalDate.of(2025, 8, 18));
        rivalPurchase.setTotalQuantity(new BigDecimal("10.00"));
        rivalPurchase.setCost(new BigDecimal("100.00"));
        rivalPurchase.setCreatedBy(stranger);
        em.persist(rivalPurchase);
        rivalPurchaseId = rivalPurchase.getId();

        BulkPayment bulk = new BulkPayment();
        bulk.setCreatedBy(owner);
        bulk.setPayingCompany(acme);
        bulk.setReceiptNumber(BULK_RECEIPT);
        bulk.setPaymentPurposeType(PaymentPurposeType.INVOICE_PAYMENT);
        bulk.setTotalAmount(new BigDecimal("731.25"));
        bulk.setCurrency("USD");
        em.persist(bulk);

        Payment payment = new Payment();
        payment.setCreatedBy(owner);
        payment.setUpdatedBy(owner);
        payment.setStockOrder(purchase);
        payment.setPayingCompany(acme);
        payment.setRecipientCompany(acme);
        payment.setRecipientType(RecipientType.COMPANY);
        payment.setBulkPayment(bulk);
        payment.setReceiptNumber(RECEIPT);
        payment.setPaymentPurposeType(PaymentPurposeType.INVOICE_PAYMENT);
        payment.setPaymentStatus(PaymentStatus.UNCONFIRMED);
        payment.setAmount(new BigDecimal("731.25"));
        payment.setPurchased(new BigDecimal("321.50"));
        payment.setCurrency("USD");
        payment.setProductionDate(LocalDate.of(2025, 8, 18));
        payment.setFormalCreationTime(LocalDate.of(2025, 8, 19));
        payment.setPreferredWayOfPayment(PreferredWayOfPayment.BANK_TRANSFER);
        em.persist(payment);

        companyId = acme.getId();
        purchaseId = purchase.getId();
        paymentId = payment.getId();
        bulkPaymentId = bulk.getId();
        ownerSession = cookie(owner);
        strangerSession = cookie(stranger);
        em.flush();
        em.clear();
    }

    @Test
    void paymentAndBulkPaymentByIdAreTenantScoped() throws Exception {
        for (String path : List.of("/api/chain/payment/" + paymentId,
                "/api/chain/payment/bulk-payment/" + bulkPaymentId)) {
            String marker = path.contains("bulk-payment") ? BULK_RECEIPT : RECEIPT;
            assertAllowedJson(path, marker);
            assertRefusedJson(path, marker);
        }
    }

    @Test
    void paymentListsCheckBothCompanyAndPurchaseEnrollment() throws Exception {
        for (String path : List.of("/api/chain/payment/list/company/" + companyId,
                "/api/chain/payment/list/purchase/" + purchaseId)) {
            assertAllowedJson(path, RECEIPT);
            assertRefusedJson(path, RECEIPT);
        }
    }

    @Test
    void bulkPaymentListChecksCompanyEnrollment() throws Exception {
        String path = "/api/chain/payment/list/bulk-payment/company/" + companyId;
        assertAllowedJson(path, BULK_RECEIPT);
        assertRefusedJson(path, BULK_RECEIPT);
    }

    @Test
    void paymentExportsCheckEnrollmentBeforeWritingWorkbook() throws Exception {
        assertWorkbookExport("/api/chain/payment/export/company/" + companyId, RECEIPT);
        assertWorkbookExport("/api/chain/payment/export/bulk-payment/company/" + companyId, BULK_RECEIPT);
    }

    @Test
    void rejectedDeleteLeavesPaymentUntouched() throws Exception {
        String path = "/api/chain/payment/" + paymentId;
        MvcResult refused = mockMvc.perform(delete(path).cookie(strangerSession)).andReturn();
        assertEquals(403, refused.getResponse().getStatus(), refused.getResponse().getContentAsString());
        assertFalse(refused.getResponse().getContentAsString().contains(RECEIPT));
        em.clear();
        assertEquals(RECEIPT, em.find(Payment.class, paymentId).getReceiptNumber());

        MvcResult anonymous = mockMvc.perform(delete(path)).andReturn();
        assertEquals(401, anonymous.getResponse().getStatus());
        em.clear();
        assertNotNull(em.find(Payment.class, paymentId));

        long before = paymentCount();
        MvcResult allowed = mockMvc.perform(delete(path).cookie(ownerSession)).andReturn();
        assertEquals(200, allowed.getResponse().getStatus(), allowed.getResponse().getContentAsString());
        assertEquals(before - 1, paymentCount());
        em.clear();
        assertNull(em.find(Payment.class, paymentId));
    }

    @Test
    void paymentUpdateChecksOwnerBeforeChangingStatus() throws Exception {
        String path = "/api/chain/payment";
        String body = "{\"id\":" + paymentId + ",\"paymentStatus\":\"CONFIRMED\"}";

        MvcResult refused = mockMvc.perform(put(path).cookie(strangerSession)
                .contentType(MediaType.APPLICATION_JSON).content(body)).andReturn();
        assertEquals(403, refused.getResponse().getStatus(), refused.getResponse().getContentAsString());
        assertFalse(refused.getResponse().getContentAsString().contains(RECEIPT));
        em.clear();
        assertEquals(PaymentStatus.UNCONFIRMED, em.find(Payment.class, paymentId).getPaymentStatus());

        MvcResult anonymous = mockMvc.perform(put(path)
                .contentType(MediaType.APPLICATION_JSON).content(body)).andReturn();
        assertEquals(401, anonymous.getResponse().getStatus());

        MvcResult allowed = mockMvc.perform(put(path).cookie(ownerSession)
                .contentType(MediaType.APPLICATION_JSON).content(body)).andReturn();
        assertEquals(200, allowed.getResponse().getStatus(), allowed.getResponse().getContentAsString());
        em.flush();
        em.clear();
        assertEquals(PaymentStatus.CONFIRMED, em.find(Payment.class, paymentId).getPaymentStatus());
    }

    @Test
    void paymentCreationChecksPurchaseCompanyBeforeInserting() throws Exception {
        String body = newPaymentBody("ACME-NEW-PAYMENT-ONLY");
        long before = paymentCount();

        MvcResult refused = mockMvc.perform(put("/api/chain/payment").cookie(strangerSession)
                .contentType(MediaType.APPLICATION_JSON).content(body)).andReturn();
        assertEquals(403, refused.getResponse().getStatus(), refused.getResponse().getContentAsString());
        assertFalse(refused.getResponse().getContentAsString().contains("ACME-NEW-PAYMENT-ONLY"));
        assertEquals(before, paymentCount());

        MvcResult anonymous = mockMvc.perform(put("/api/chain/payment")
                .contentType(MediaType.APPLICATION_JSON).content(body)).andReturn();
        assertEquals(401, anonymous.getResponse().getStatus());
        assertEquals(before, paymentCount());

        MvcResult allowed = mockMvc.perform(put("/api/chain/payment").cookie(ownerSession)
                .contentType(MediaType.APPLICATION_JSON).content(body)).andReturn();
        assertEquals(200, allowed.getResponse().getStatus(), allowed.getResponse().getContentAsString());
        assertEquals(before + 1, paymentCount());
    }

    @Test
    void bulkPaymentCreationChecksPayingCompanyBeforeInserting() throws Exception {
        String body = """
                {"payingCompany":{"id":%d},"payments":[%s],"paymentDescription":"Acme bulk payment",
                 "receiptNumber":"ACME-NEW-BULK-ONLY","paymentPurposeType":"INVOICE_PAYMENT",
                 "totalAmount":25.00,"currency":"USD","additionalProofs":[]}
                """.formatted(companyId, newPaymentBody("ACME-BULK-CHILD-ONLY"));
        long paymentsBefore = paymentCount();
        long bulkBefore = bulkPaymentCount();

        MvcResult refused = mockMvc.perform(post("/api/chain/payment/bulk-payment").cookie(strangerSession)
                .contentType(MediaType.APPLICATION_JSON).content(body)).andReturn();
        assertEquals(403, refused.getResponse().getStatus(), refused.getResponse().getContentAsString());
        assertFalse(refused.getResponse().getContentAsString().contains("ACME-NEW-BULK-ONLY"));
        assertEquals(paymentsBefore, paymentCount());
        assertEquals(bulkBefore, bulkPaymentCount());

        MvcResult anonymous = mockMvc.perform(post("/api/chain/payment/bulk-payment")
                .contentType(MediaType.APPLICATION_JSON).content(body)).andReturn();
        assertEquals(401, anonymous.getResponse().getStatus());
        assertEquals(paymentsBefore, paymentCount());
        assertEquals(bulkBefore, bulkPaymentCount());

        MvcResult allowed = mockMvc.perform(post("/api/chain/payment/bulk-payment").cookie(ownerSession)
                .contentType(MediaType.APPLICATION_JSON).content(body)).andReturn();
        assertEquals(200, allowed.getResponse().getStatus(), allowed.getResponse().getContentAsString());
        assertEquals(paymentsBefore + 1, paymentCount());
        assertEquals(bulkBefore + 1, bulkPaymentCount());
    }

    @Test
    void bulkPaymentWithForeignChildWritesNoPayments() throws Exception {
        String rivalPayment = newPaymentBody("RIVAL-BULK-CHILD-ONLY")
                .replace("\"stockOrder\":{\"id\":" + purchaseId + "}",
                        "\"stockOrder\":{\"id\":" + rivalPurchaseId + "}");
        String body = """
                {"payingCompany":{"id":%d},"payments":[%s,%s],
                 "paymentDescription":"Mixed-tenant bulk payment",
                 "receiptNumber":"MIXED-BULK-RECEIPT-ONLY",
                 "paymentPurposeType":"INVOICE_PAYMENT","totalAmount":50,
                 "currency":"USD","additionalProofs":[]}
                """.formatted(companyId, newPaymentBody("ACME-FIRST-CHILD-ONLY"), rivalPayment);
        long paymentsBefore = paymentCount();
        long bulkBefore = bulkPaymentCount();
        MvcResult refused = mockMvc.perform(post("/api/chain/payment/bulk-payment")
                .cookie(ownerSession).contentType(MediaType.APPLICATION_JSON).content(body)).andReturn();
        assertEquals(403, refused.getResponse().getStatus(), refused.getResponse().getContentAsString());
        assertFalse(refused.getResponse().getContentAsString().contains("ACME-FIRST-CHILD-ONLY"));
        assertFalse(refused.getResponse().getContentAsString().contains("RIVAL-BULK-CHILD-ONLY"));
        assertEquals(paymentsBefore, paymentCount());
        assertEquals(bulkBefore, bulkPaymentCount());
    }

    private String newPaymentBody(String receipt) {
        return """
                {"stockOrder":{"id":%d},"recipientType":"COMPANY","recipientCompany":{"id":%d},
                 "paymentPurposeType":"INVOICE_PAYMENT","paymentStatus":"UNCONFIRMED",
                 "amount":25.00,"currency":"USD","receiptNumber":"%s",
                 "formalCreationTime":"2025-08-19"}
                """.formatted(purchaseId, companyId, receipt);
    }

    private long paymentCount() {
        em.flush();
        return em.createQuery("SELECT COUNT(p) FROM Payment p", Long.class).getSingleResult();
    }

    private long bulkPaymentCount() {
        em.flush();
        return em.createQuery("SELECT COUNT(p) FROM BulkPayment p", Long.class).getSingleResult();
    }

    private void assertAllowedJson(String path, String marker) throws Exception {
        MvcResult allowed = mockMvc.perform(get(path).cookie(ownerSession)).andReturn();
        String body = allowed.getResponse().getContentAsString();
        assertEquals(200, allowed.getResponse().getStatus(), path + ": " + body);
        assertTrue(body.contains(marker), path + ": " + body);
    }

    private void assertRefusedJson(String path, String marker) throws Exception {
        MvcResult refused = mockMvc.perform(get(path).cookie(strangerSession)).andReturn();
        String body = refused.getResponse().getContentAsString();
        assertEquals(403, refused.getResponse().getStatus(), path + ": " + body);
        assertFalse(body.contains(marker), path + ": " + body);

        MvcResult anonymous = mockMvc.perform(get(path)).andReturn();
        String anonymousBody = anonymous.getResponse().getContentAsString();
        assertEquals(401, anonymous.getResponse().getStatus(), path + ": " + anonymousBody);
        assertFalse(anonymousBody.contains(marker), path + ": " + anonymousBody);
    }

    private void assertWorkbookExport(String path, String marker) throws Exception {
        MvcResult allowed = mockMvc.perform(get(path).cookie(ownerSession)).andReturn();
        assertEquals(200, allowed.getResponse().getStatus(), path);
        assertEquals(MediaType.APPLICATION_OCTET_STREAM_VALUE, allowed.getResponse().getContentType());
        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(allowed.getResponse().getContentAsByteArray()))) {
            assertEquals(marker, workbook.getSheetAt(0).getRow(1).getCell(path.contains("bulk-payment") ? 1 : 8).getStringCellValue());
        }

        MvcResult refused = mockMvc.perform(get(path).cookie(strangerSession)).andReturn();
        assertEquals(403, refused.getResponse().getStatus(), path);
        assertFalse(new String(refused.getResponse().getContentAsByteArray(), StandardCharsets.UTF_8).contains(marker));
        assertFalse(MediaType.APPLICATION_OCTET_STREAM_VALUE.equals(refused.getResponse().getContentType()));

        MvcResult anonymous = mockMvc.perform(get(path)).andReturn();
        assertEquals(401, anonymous.getResponse().getStatus(), path);
        assertFalse(new String(anonymous.getResponse().getContentAsByteArray(), StandardCharsets.UTF_8).contains(marker));
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
