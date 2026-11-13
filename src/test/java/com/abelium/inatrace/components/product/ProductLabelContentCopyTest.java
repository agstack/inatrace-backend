package com.abelium.inatrace.components.product;

import com.abelium.inatrace.components.common.TokenService;
import com.abelium.inatrace.db.entities.common.User;
import com.abelium.inatrace.db.entities.company.Company;
import com.abelium.inatrace.db.entities.company.CompanyUser;
import com.abelium.inatrace.db.entities.codebook.ProductType;
import com.abelium.inatrace.db.entities.product.Product;
import com.abelium.inatrace.db.entities.product.ProductCompany;
import com.abelium.inatrace.db.entities.product.ProductLabel;
import com.abelium.inatrace.db.entities.product.ProductLabelContent;
import com.abelium.inatrace.db.entities.product.ProductLocation;
import com.abelium.inatrace.db.entities.value_chain.ValueChain;
import com.abelium.inatrace.db.entities.value_chain.enums.ValueChainStatus;
import com.abelium.inatrace.support.AbstractMySqlIntegrationTest;
import com.abelium.inatrace.types.CompanyStatus;
import com.abelium.inatrace.types.CompanyUserRole;
import com.abelium.inatrace.types.ProductCompanyType;
import com.abelium.inatrace.types.UserRole;
import com.abelium.inatrace.types.UserStatus;
import jakarta.persistence.EntityManager;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

/**
 * A new label starts from a copy of the product content, and its origin locations can be edited
 * afterwards. Covers the labor policies and origin locations, which were lost in that copy.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ProductLabelContentCopyTest extends AbstractMySqlIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private EntityManager em;
    @Autowired private TokenService tokenService;
    @Value("${INATrace.auth.accessTokenCookieName}") private String accessCookieName;

    private Long productId;
    private Cookie ownerSession;

    @BeforeEach
    void seed() {
        Company owner = new Company();
        owner.setName("Label copy owner");
        owner.setStatus(CompanyStatus.ACTIVE);
        em.persist(owner);
        User user = new User();
        user.setEmail("label-copy-owner@test");
        user.setName("Label");
        user.setSurname("Copy");
        user.setPassword("not-used");
        user.setRole(UserRole.USER);
        user.setStatus(UserStatus.ACTIVE);
        em.persist(user);
        CompanyUser enrollment = new CompanyUser();
        enrollment.setUser(user);
        enrollment.setCompany(owner);
        enrollment.setRole(CompanyUserRole.COMPANY_ADMIN);
        em.persist(enrollment);
        owner.getUsers().add(enrollment);

        ProductType type = new ProductType();
        type.setCode("LABEL_COPY");
        type.setName("Label copy type");
        em.persist(type);
        ValueChain chain = new ValueChain();
        chain.setName("Label copy chain");
        chain.setDescription("Label copy chain");
        chain.setValueChainStatus(ValueChainStatus.ENABLED);
        chain.setProductType(type);
        chain.setCreatedBy(user);
        em.persist(chain);

        Product product = new Product();
        product.setName("Label copy product");
        product.setCompany(owner);
        product.setValueChain(chain);
        product.getResponsibility().setLaborPolicies("Fair pay within 15 days");
        product.getProcess().setProduction("Washed");
        em.persist(product.getProcess());
        em.persist(product.getResponsibility());
        em.persist(product.getSustainability());
        em.persist(product.getJourney());
        em.persist(product.getSettings());
        em.persist(product.getBusinessToCustomerSettings());
        em.persist(product);
        product.getOriginLocations().add(location(product, "North farm", 15.5));
        product.getOriginLocations().add(location(product, "South farm", 14.6));
        ProductCompany association = new ProductCompany();
        association.setProduct(product);
        association.setCompany(owner);
        association.setType(ProductCompanyType.OWNER);
        em.persist(association);
        product.getAssociatedCompanies().add(association);

        productId = product.getId();
        ownerSession = new Cookie(accessCookieName, tokenService.createAccessToken(user));
        em.flush();
        em.clear();
    }

    @Test
    void newLabelCopiesLaborPoliciesAndOriginLocationsOfTheProduct() throws Exception {
        ProductLabelContent content = em.find(ProductLabel.class, createLabel()).getContent();

        assertEquals("Fair pay within 15 days", content.getResponsibility().getLaborPolicies());
        assertEquals("Washed", content.getProcess().getProduction());
        assertEquals(Set.of("North farm", "South farm"), pinNames(content.getOriginLocations()));
        assertEquals(0L, orphanLocations());
        // The product keeps its own locations.
        assertEquals(Set.of("North farm", "South farm"), pinNames(em.find(Product.class, productId).getOriginLocations()));
    }

    @Test
    void labelContentUpdateReplacesItsOriginLocations() throws Exception {
        Long labelId = createLabel();

        perform(put("/api/product/label/content").contentType(MediaType.APPLICATION_JSON).cookie(ownerSession)
                .content("{\"labelId\":" + labelId + ",\"name\":\"Label copy product\",\"origin\":{\"text\":\"Highlands\","
                        + "\"locations\":[{\"latitude\":13.7,\"longitude\":-89.2,\"pinName\":\"Label farm\"}]}}"));
        em.flush();
        em.clear();

        ProductLabelContent content = em.find(ProductLabel.class, labelId).getContent();
        assertEquals("Highlands", content.getOriginText());
        assertEquals(Set.of("Label farm"), pinNames(content.getOriginLocations()));
        assertEquals(0L, orphanLocations());
        assertEquals(Set.of("North farm", "South farm"), pinNames(em.find(Product.class, productId).getOriginLocations()));
    }

    private Long createLabel() throws Exception {
        perform(post("/api/product/label/create").contentType(MediaType.APPLICATION_JSON).cookie(ownerSession)
                .content("{\"productId\":" + productId + ",\"language\":\"EN\",\"title\":\"Label copy\"}"));
        em.flush();
        em.clear();
        return em.createQuery("SELECT pl.id FROM ProductLabel pl WHERE pl.product.id = :productId", Long.class)
                .setParameter("productId", productId)
                .getSingleResult();
    }

    private void perform(org.springframework.test.web.servlet.RequestBuilder request) throws Exception {
        MvcResult result = mockMvc.perform(request).andReturn();
        assertEquals(200, result.getResponse().getStatus(), result.getResponse().getContentAsString());
    }

    private ProductLocation location(Product product, String pinName, double latitude) {
        ProductLocation location = new ProductLocation();
        location.setProduct(product);
        location.setPinName(pinName);
        location.setLatitude(latitude);
        location.setLongitude(-91.0);
        location.setNumberOfFarmers(10);
        em.persist(location);
        return location;
    }

    private long orphanLocations() {
        return em.createQuery("SELECT count(l) FROM ProductLocation l WHERE l.product IS NULL AND l.content IS NULL", Long.class)
                .getSingleResult();
    }

    private static Set<String> pinNames(Set<ProductLocation> locations) {
        return locations.stream().map(ProductLocation::getPinName).collect(Collectors.toSet());
    }
}
