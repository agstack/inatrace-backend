package com.abelium.inatrace.components.company;

import com.abelium.inatrace.components.agstack.AgStackClientService;
import com.abelium.inatrace.components.common.TokenService;
import com.abelium.inatrace.db.entities.codebook.ProductType;
import com.abelium.inatrace.db.entities.common.User;
import com.abelium.inatrace.db.entities.common.UserCustomer;
import com.abelium.inatrace.db.entities.common.UserCustomerProductType;
import com.abelium.inatrace.db.entities.company.Company;
import com.abelium.inatrace.db.entities.company.CompanyUser;
import com.abelium.inatrace.support.AbstractMySqlIntegrationTest;
import com.abelium.inatrace.types.*;
import jakarta.persistence.EntityManager;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PlotGeometryTransactionTest extends AbstractMySqlIntegrationTest {
    @Autowired EntityManager em;
    @Autowired PlatformTransactionManager transactionManager;
    @Autowired TokenService tokenService;
    @Autowired MockMvc mockMvc;
    @MockitoBean AgStackClientService agStack;
    @Value("${INATrace.auth.accessTokenCookieName}") String cookieName;

    @Test
    void failedMixedUploadRollsBackEarlierPointInIndependentTransaction() throws Exception {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        String marker = UUID.randomUUID().toString();
        var fixture = tx.execute(status -> {
            Company company = new Company();
            company.setName("geometry-" + marker);
            company.setStatus(CompanyStatus.ACTIVE);
            em.persist(company);
            User user = new User();
            user.setEmail(marker + "@example.test");
            user.setName("Test"); user.setSurname("User"); user.setPassword("test");
            user.setRole(UserRole.USER); user.setStatus(UserStatus.ACTIVE);
            em.persist(user);
            CompanyUser membership = new CompanyUser();
            membership.setCompany(company); membership.setUser(user); membership.setRole(CompanyUserRole.COMPANY_ADMIN);
            em.persist(membership);
            ProductType product = new ProductType();
            product.setCode(marker); product.setName("Synthetic"); em.persist(product);
            UserCustomer farmer = new UserCustomer();
            farmer.setCompany(company); farmer.setType(UserCustomerType.FARMER);
            farmer.setName("Synthetic"); farmer.setSurname("Farmer"); em.persist(farmer);
            UserCustomerProductType relation = new UserCustomerProductType();
            relation.setUserCustomer(farmer); relation.setProductType(product); em.persist(relation);
            em.flush();
            return new Object[] {company.getId(), user.getId(), product.getId(), farmer.getId(),
                    new Cookie(cookieName, tokenService.createAccessToken(user))};
        });
        try {
            String input = "{\"type\":\"FeatureCollection\",\"features\":["
                    + "{\"type\":\"Feature\",\"properties\":{},\"geometry\":{\"type\":\"Point\",\"coordinates\":[30,-1]}},null]}";
            var result = mockMvc.perform(multipart("/api/company/userCustomers/" + fixture[3] + "/uploadGeoData")
                    .file(new MockMultipartFile("file", "mixed.geojson", "application/geo+json", input.getBytes(StandardCharsets.UTF_8)))
                    .cookie((Cookie) fixture[4])).andReturn();
            assertEquals(400, result.getResponse().getStatus());
            Long count = tx.execute(status -> em.createQuery("select count(p) from Plot p where p.farmer.id = :id", Long.class)
                    .setParameter("id", fixture[3]).getSingleResult());
            assertEquals(0L, count.longValue());
            verifyNoInteractions(agStack);

            for (String ring : new String[]{"[]", "[[[30,-1],[31,-1],[30,-1]]]", "[[[30,-1],[31,-1],[31,-2],[30,-2]]]", "[[[30,-1],[31,-1],[31,-2],[30,-1]],[[30.1,-1.1],[30.2,-1.1],[30.2,-1.2],[30.1,-1.1]]]"}) {
                String invalidPolygon = "{\"type\":\"FeatureCollection\",\"features\":["
                        + "{\"type\":\"Feature\",\"properties\":{},\"geometry\":{\"type\":\"Point\",\"coordinates\":[30,-1]}},"
                        + "{\"type\":\"Feature\",\"properties\":{},\"geometry\":{\"type\":\"Polygon\",\"coordinates\":" + ring + "}}]}";
                var invalid = mockMvc.perform(multipart("/api/company/userCustomers/" + fixture[3] + "/uploadGeoData")
                        .file(new MockMultipartFile("file", "invalid.geojson", "application/geo+json", invalidPolygon.getBytes(StandardCharsets.UTF_8)))
                        .cookie((Cookie) fixture[4])).andReturn();
                assertEquals(400, invalid.getResponse().getStatus(), ring);
                Long afterInvalid = tx.execute(status -> em.createQuery("select count(p) from Plot p where p.farmer.id = :id", Long.class)
                        .setParameter("id", fixture[3]).getSingleResult());
                assertEquals(0L, afterInvalid.longValue(), ring);
            }

            for (String payload : new String[]{"null", "{\"type\":\"FeatureCollection\",\"features\":[]}"}) {
                var empty = mockMvc.perform(multipart("/api/company/userCustomers/" + fixture[3] + "/uploadGeoData")
                        .file(new MockMultipartFile("file", "empty.geojson", "application/geo+json", payload.getBytes(StandardCharsets.UTF_8)))
                        .cookie((Cookie) fixture[4])).andReturn();
                assertEquals(payload.equals("null") ? 400 : 200, empty.getResponse().getStatus(), payload);
                Long afterEmpty = tx.execute(status -> em.createQuery("select count(p) from Plot p where p.farmer.id = :id", Long.class)
                        .setParameter("id", fixture[3]).getSingleResult());
                assertEquals(0L, afterEmpty.longValue(), payload);
            }

            String ignored = "{\"type\":\"FeatureCollection\",\"features\":["
                    + "{\"type\":\"Feature\",\"properties\":{},\"geometry\":null},"
                    + "{\"type\":\"Feature\",\"properties\":{},\"geometry\":{\"type\":\"LineString\",\"coordinates\":[[30,-1],[31,-1]]}},"
                    + "{\"type\":\"Feature\",\"properties\":{},\"geometry\":{\"type\":\"MultiPolygon\",\"coordinates\":[[[[30,-1],[31,-1],[31,-2],[30,-1]]]]}},"
                    + "{\"type\":\"Feature\",\"properties\":{},\"geometry\":{\"type\":\"Point\",\"coordinates\":[30,-1]}}]}";
            var mixed = mockMvc.perform(multipart("/api/company/userCustomers/" + fixture[3] + "/uploadGeoData")
                    .file(new MockMultipartFile("file", "mixed.geojson", "application/geo+json", ignored.getBytes(StandardCharsets.UTF_8)))
                    .cookie((Cookie) fixture[4])).andReturn();
            assertEquals(200, mixed.getResponse().getStatus());
            Long committed = tx.execute(status -> em.createQuery("select count(p) from Plot p where p.farmer.id = :id", Long.class)
                    .setParameter("id", fixture[3]).getSingleResult());
            assertEquals(1L, committed.longValue());
            verifyNoInteractions(agStack);

            org.mockito.Mockito.when(agStack.registerFieldBoundaryResponse(org.mockito.ArgumentMatchers.anyList()))
                    .thenReturn(new com.abelium.inatrace.components.agstack.api.ApiRegisterFieldBoundaryResponse());
            String bowtie = "{\"type\":\"FeatureCollection\",\"features\":[{\"type\":\"Feature\",\"properties\":{},"
                    + "\"geometry\":{\"type\":\"Polygon\",\"coordinates\":[[[30,-1],[31,-2],[30,-2],[31,-1],[30,-1]]]}}]}";
            var selfIntersecting = mockMvc.perform(multipart("/api/company/userCustomers/" + fixture[3] + "/uploadGeoData")
                    .file(new MockMultipartFile("file", "bowtie.geojson", "application/geo+json", bowtie.getBytes(StandardCharsets.UTF_8)))
                    .cookie((Cookie) fixture[4])).andReturn();
            assertEquals(200, selfIntersecting.getResponse().getStatus());
            Long afterBowtie = tx.execute(status -> em.createQuery("select count(p) from Plot p where p.farmer.id = :id", Long.class)
                    .setParameter("id", fixture[3]).getSingleResult());
            assertEquals(2L, afterBowtie.longValue());
            org.mockito.Mockito.verify(agStack).registerFieldBoundaryResponse(org.mockito.ArgumentMatchers.anyList());
        } finally {
            tx.executeWithoutResult(status -> {
                em.remove(em.find(UserCustomer.class, fixture[3]));
                em.flush();
                em.createQuery("delete from CompanyUser c where c.company.id = :id").setParameter("id", fixture[0]).executeUpdate();
                em.remove(em.find(ProductType.class, fixture[2]));
                em.remove(em.find(Company.class, fixture[0]));
                em.remove(em.find(User.class, fixture[1]));
            });
        }
    }
}
