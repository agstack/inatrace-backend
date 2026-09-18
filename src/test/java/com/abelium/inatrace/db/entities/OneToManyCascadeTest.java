package com.abelium.inatrace.db.entities;

import com.abelium.inatrace.db.entities.codebook.ProductType;
import com.abelium.inatrace.db.entities.common.User;
import com.abelium.inatrace.db.entities.company.Company;
import com.abelium.inatrace.db.entities.company.CompanyUser;
import com.abelium.inatrace.db.entities.processingorder.ProcessingOrder;
import com.abelium.inatrace.db.entities.product.Product;
import com.abelium.inatrace.db.entities.product.ProductContent;
import com.abelium.inatrace.db.entities.product.ProductLabel;
import com.abelium.inatrace.db.entities.stockorder.Certification;
import com.abelium.inatrace.db.entities.stockorder.StockOrder;
import com.abelium.inatrace.db.entities.stockorder.Transaction;
import com.abelium.inatrace.db.entities.stockorder.enums.OrderType;
import com.abelium.inatrace.db.entities.stockorder.enums.TransactionStatus;
import com.abelium.inatrace.db.entities.value_chain.ValueChain;
import com.abelium.inatrace.db.entities.value_chain.enums.ValueChainStatus;
import com.abelium.inatrace.types.CompanyStatus;
import com.abelium.inatrace.types.CompanyUserRole;
import com.abelium.inatrace.types.Language;
import com.abelium.inatrace.types.ProductLabelStatus;
import com.abelium.inatrace.types.ProductStatus;
import com.abelium.inatrace.types.UserRole;
import com.abelium.inatrace.types.UserStatus;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
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
import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * What {@code CascadeType.ALL} did to the four {@code @OneToMany} collections it was added to.
 *
 * <p>"JPA: relax one-to-one document and location links to many-to-one" is a mapping refactor, but
 * four of its hunks are not about {@code @OneToOne} at all: they widen collections that previously
 * cascaded nothing.
 *
 * <table>
 *   <caption>The four collections</caption>
 *   <tr><th>Collection</th><th>Child table</th></tr>
 *   <tr><td>{@code User.userCompanies}</td><td>{@code CompanyUser}</td></tr>
 *   <tr><td>{@code ProcessingOrder.inputTransactions}</td><td>{@code Transaction}</td></tr>
 *   <tr><td>{@code ProcessingOrder.targetStockOrders}</td><td>{@code StockOrder}</td></tr>
 *   <tr><td>{@code Product.labels}</td><td>{@code ProductLabel}</td></tr>
 * </table>
 *
 * <p>{@code ALL} is not one change but six, and two of them are observable from a test: REMOVE
 * makes deleting a parent delete its children, and PERSIST makes saving a parent save the children
 * sitting in its collection. Each collection gets one test for each, so the diff's effect is
 * written down rather than argued about.
 *
 * <p><b>Every test here fails on {@code main}</b>, which is the point of the file: the remove tests
 * fail on a foreign key violation, because the child rows still point at the parent being deleted;
 * the persist tests fail on a null id, because the child is quietly never inserted. Run the class
 * on both sides of the commit to see the behaviour change rather than infer it.
 *
 * <p>Two of these deletes reach further than the child table.
 * {@link #deletingAProcessingOrderDeletesItsTargetStockOrdersAndTheirCertifications()} shows the
 * cascade travelling two levels, because {@code StockOrder} already cascades to its own
 * certifications, activity proofs and evidence values; a single {@code em.remove(processingOrder)}
 * now empties all of them. The controls in each test pin the other end: the company, the user and
 * the neighbouring stock order must still be there afterwards.
 *
 * @see com.abelium.inatrace.components.processingorder.ProcessingOrderService#deleteProcessingOrder
 *      which already deletes both of its collections by hand, so the REMOVE half of the cascade is
 *      redundant on that path rather than newly destructive
 * @see com.abelium.inatrace.components.product.ProductService#deleteProduct which likewise deletes
 *      the labels itself
 */
@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
@Transactional
class OneToManyCascadeTest {

	@Container
	@ServiceConnection
	static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4");

	@Autowired
	private EntityManager em;

	// ------------------------------------------------------------------ User.userCompanies

	@Test
	@DisplayName("User.userCompanies: deleting a user deletes its company enrolments")
	void deletingAUserDeletesItsCompanyEnrolments() {

		Company acme = company("Acme Coffee Cooperative");
		User alice = persistedUser("alice@acme.test");
		enrol(alice, acme);

		Long userId = alice.getId();
		Long companyId = acme.getId();
		coldContext();

		em.remove(em.find(User.class, userId));
		em.flush();

		assertEquals(0, count("SELECT COUNT(cu) FROM CompanyUser cu WHERE cu.user.id = :id", userId),
				"Deleting a user now deletes the CompanyUser rows that record which companies the "
						+ "user belongs to. Without the cascade the delete fails on the foreign key instead.");
		assertEquals(1, count("SELECT COUNT(c) FROM Company c WHERE c.id = :id", companyId),
				"Control: the company itself must survive the deletion of one of its members.");
	}

	@Test
	@DisplayName("User.userCompanies: saving a user saves the enrolments held in the collection")
	void savingAUserSavesTheEnrolmentsInItsCollection() {

		Company acme = company("Acme Coffee Cooperative");

		User alice = user("alice@acme.test");
		CompanyUser enrolment = new CompanyUser();
		enrolment.setUser(alice);
		enrolment.setCompany(acme);
		enrolment.setRole(CompanyUserRole.COMPANY_ADMIN);
		alice.setUserCompanies(new HashSet<>(Set.of(enrolment)));

		em.persist(alice);
		em.flush();

		assertNotNull(enrolment.getId(),
				"The enrolment was in the collection of the user being saved, so the cascade inserts "
						+ "it. Without the cascade nothing writes it and it is dropped without an error.");
	}

	// ------------------------------------------------------- ProcessingOrder.inputTransactions

	@Test
	@DisplayName("ProcessingOrder.inputTransactions: deleting a processing order deletes its input transactions")
	void deletingAProcessingOrderDeletesItsInputTransactions() {

		Company acme = company("Acme Coffee Cooperative");
		User alice = persistedUser("alice@acme.test");
		StockOrder source = stockOrder(acme, alice);
		ProcessingOrder order = processingOrder();

		Transaction input = new Transaction();
		input.setTargetProcessingOrder(order);
		input.setSourceStockOrder(source);
		input.setCompany(acme);
		input.setStatus(TransactionStatus.EXECUTED);
		input.setInputQuantity(new BigDecimal("120.00"));
		em.persist(input);

		Long orderId = order.getId();
		Long sourceId = source.getId();
		coldContext();

		em.remove(em.find(ProcessingOrder.class, orderId));
		em.flush();

		assertEquals(0, count("SELECT COUNT(t) FROM Transaction t WHERE t.targetProcessingOrder.id = :id", orderId),
				"Deleting a processing order now deletes the transactions that fed it -- the rows the "
						+ "service comments call \"should not be deleted -> May result in inappropriate quantities\".");
		assertEquals(1, count("SELECT COUNT(s) FROM StockOrder s WHERE s.id = :id", sourceId),
				"Control: the stock order the transaction drew from is on the other side of the link "
						+ "and must survive.");
	}

	@Test
	@DisplayName("ProcessingOrder.inputTransactions: saving a processing order saves the transactions in the collection")
	void savingAProcessingOrderSavesTheTransactionsInItsCollection() {

		Company acme = company("Acme Coffee Cooperative");
		User alice = persistedUser("alice@acme.test");
		StockOrder source = stockOrder(acme, alice);

		ProcessingOrder order = new ProcessingOrder();
		order.setProcessingDate(LocalDate.of(2025, 6, 17));

		Transaction input = new Transaction();
		input.setTargetProcessingOrder(order);
		input.setSourceStockOrder(source);
		input.setCompany(acme);
		input.setStatus(TransactionStatus.EXECUTED);
		input.setInputQuantity(new BigDecimal("120.00"));
		order.setInputTransactions(new HashSet<>(Set.of(input)));

		em.persist(order);
		em.flush();

		assertNotNull(input.getId(),
				"The transaction was in the collection of the processing order being saved, so the "
						+ "cascade inserts it. Without the cascade it is dropped without an error.");
	}

	// ------------------------------------------------------- ProcessingOrder.targetStockOrders

	@Test
	@DisplayName("ProcessingOrder.targetStockOrders: deleting a processing order deletes its target stock orders, and their certifications with them")
	void deletingAProcessingOrderDeletesItsTargetStockOrdersAndTheirCertifications() {

		Company acme = company("Acme Coffee Cooperative");
		User alice = persistedUser("alice@acme.test");
		ProcessingOrder order = processingOrder();

		StockOrder target = stockOrder(acme, alice);
		target.setProcessingOrder(order);

		// StockOrder cascades to its own certifications already, so this row is two hops from the
		// processing order and is reached only because of the hunk under test.
		Certification certification = new Certification();
		certification.setStockOrder(target);
		certification.setType("ORGANIC");
		certification.setDescription("Organic certificate for the processed lot");
		em.persist(certification);

		Long orderId = order.getId();
		Long targetId = target.getId();
		Long companyId = acme.getId();
		coldContext();

		em.remove(em.find(ProcessingOrder.class, orderId));
		em.flush();

		assertEquals(0, count("SELECT COUNT(s) FROM StockOrder s WHERE s.id = :id", targetId),
				"Deleting a processing order now deletes the stock orders it produced.");
		assertEquals(0, count("SELECT COUNT(c) FROM Certification c WHERE c.stockOrder.id = :id", targetId),
				"The cascade does not stop at the stock order: StockOrder itself cascades to its "
						+ "certifications, so one em.remove(processingOrder) reaches two levels down.");
		assertEquals(1, count("SELECT COUNT(c) FROM Company c WHERE c.id = :id", companyId),
				"Control: the owning company must survive.");
	}

	@Test
	@DisplayName("ProcessingOrder.targetStockOrders: saving a processing order saves the stock orders in the collection")
	void savingAProcessingOrderSavesTheStockOrdersInItsCollection() {

		Company acme = company("Acme Coffee Cooperative");
		User alice = persistedUser("alice@acme.test");

		ProcessingOrder order = new ProcessingOrder();
		order.setProcessingDate(LocalDate.of(2025, 6, 17));

		StockOrder target = newStockOrder(acme, alice);
		target.setProcessingOrder(order);
		order.setTargetStockOrders(new HashSet<>(Set.of(target)));

		em.persist(order);
		em.flush();

		assertNotNull(target.getId(),
				"The stock order was in the collection of the processing order being saved, so the "
						+ "cascade inserts it. Without the cascade it is dropped without an error.");
	}

	// ------------------------------------------------------------------ Product.labels

	@Test
	@DisplayName("Product.labels: deleting a product deletes its consumer labels")
	void deletingAProductDeletesItsLabels() {

		Company acme = company("Acme Coffee Cooperative");
		User alice = persistedUser("alice@acme.test");
		Product product = product(acme, alice);

		ProductLabel label = new ProductLabel();
		label.setProduct(product);
		label.setTitle("Kivu Washed Bourbon");
		label.setLanguage(Language.EN);
		label.setStatus(ProductLabelStatus.PUBLISHED);
		em.persist(label);

		Long productId = product.getId();
		Long companyId = acme.getId();
		coldContext();

		em.remove(em.find(Product.class, productId));
		em.flush();

		assertEquals(0, count("SELECT COUNT(l) FROM ProductLabel l WHERE l.product.id = :id", productId),
				"Deleting a product now deletes its labels. ProductService.deleteProduct already "
						+ "removes them by hand, so on that path the cascade only makes the hand-written "
						+ "loop redundant -- but the mapping now deletes them on any other path too.");
		assertEquals(1, count("SELECT COUNT(c) FROM Company c WHERE c.id = :id", companyId),
				"Control: the owning company must survive.");
	}

	@Test
	@DisplayName("Product.labels: saving a product saves the labels held in the collection")
	void savingAProductSavesTheLabelsInItsCollection() {

		Company acme = company("Acme Coffee Cooperative");
		User alice = persistedUser("alice@acme.test");

		Product product = newProduct(acme, alice);

		ProductLabel label = new ProductLabel();
		label.setProduct(product);
		label.setTitle("Kivu Washed Bourbon");
		label.setLanguage(Language.EN);
		label.setStatus(ProductLabelStatus.PUBLISHED);
		product.setLabels(new HashSet<>(Set.of(label)));

		em.persist(product);
		em.flush();

		assertNotNull(label.getId(),
				"The label was in the collection of the product being saved, so the cascade inserts "
						+ "it. Without the cascade it is dropped without an error.");
	}

	// ------------------------------------------------------------------ fixture helpers

	/**
	 * Flush the seed data and forget it, so the deletes below load their entities -- and their
	 * collections -- from the database rather than from the session that built them. A cascade that
	 * only fired on an already-populated in-memory collection would prove nothing.
	 */
	private void coldContext() {
		em.flush();
		em.clear();
	}

	private long count(String jpql, Long id) {
		return em.createQuery(jpql, Long.class).setParameter("id", id).getSingleResult();
	}

	private Company company(String name) {
		Company c = new Company();
		c.setName(name);
		c.setStatus(CompanyStatus.ACTIVE);
		em.persist(c);
		return c;
	}

	private User user(String email) {
		User u = new User();
		u.setEmail(email);
		u.setName("Alice");
		u.setSurname("Acme");
		u.setPassword("not-used-in-this-test");
		u.setRole(UserRole.USER);
		u.setStatus(UserStatus.ACTIVE);
		return u;
	}

	private User persistedUser(String email) {
		User u = user(email);
		em.persist(u);
		return u;
	}

	private void enrol(User user, Company company) {
		CompanyUser cu = new CompanyUser();
		cu.setUser(user);
		cu.setCompany(company);
		cu.setRole(CompanyUserRole.COMPANY_ADMIN);
		em.persist(cu);
		company.getUsers().add(cu);
	}

	private ProcessingOrder processingOrder() {
		ProcessingOrder order = new ProcessingOrder();
		order.setProcessingDate(LocalDate.of(2025, 6, 17));
		em.persist(order);
		return order;
	}

	/** {@code StockOrder.createdBy} is {@code optional = false}, so a stock order needs a user. */
	private StockOrder newStockOrder(Company company, User createdBy) {
		StockOrder so = new StockOrder();
		so.setCompany(company);
		so.setOrderType(OrderType.PROCESSING_ORDER);
		so.setProductionDate(LocalDate.of(2025, 6, 17));
		so.setTotalQuantity(new BigDecimal("120.00"));
		so.setCreatedBy(createdBy);
		so.setUpdatedBy(createdBy);
		return so;
	}

	private StockOrder stockOrder(Company company, User createdBy) {
		StockOrder so = newStockOrder(company, createdBy);
		em.persist(so);
		return so;
	}

	private Product newProduct(Company company, User createdBy) {
		Product p = new Product();
		p.setName("Kivu Washed Bourbon");
		p.setDescription("Washed bourbon from the Kivu hills");
		p.setStatus(ProductStatus.ACTIVE);
		p.setCompany(company);
		p.setValueChain(valueChain("Coffee", createdBy));
		persistProductContentChildren(p);
		return p;
	}

	private Product product(Company company, User createdBy) {
		Product p = newProduct(company, createdBy);
		em.persist(p);
		return p;
	}

	/** {@code Product.valueChain} is {@code @NotNull}, and a value chain needs a product type. */
	private ValueChain valueChain(String name, User createdBy) {
		ProductType productType = new ProductType();
		productType.setCode("COFFEE");
		productType.setName(name);
		productType.setDescription(name);
		em.persist(productType);

		ValueChain vc = new ValueChain();
		vc.setName(name);
		vc.setDescription(name + " value chain");
		vc.setValueChainStatus(ValueChainStatus.ENABLED);
		vc.setProductType(productType);
		vc.setCreatedBy(createdBy);
		em.persist(vc);
		return vc;
	}

	/**
	 * The to-one children of a {@code ProductContent} are not cascaded, which is why
	 * {@code ProductService.createProduct} persists each of them by hand before the product itself.
	 * A fixture that skips this fails on flush with a transient-instance error.
	 */
	private void persistProductContentChildren(ProductContent content) {
		em.persist(content.getProcess());
		em.persist(content.getResponsibility());
		em.persist(content.getSustainability());
		em.persist(content.getSettings());
		em.persist(content.getJourney());
		em.persist(content.getBusinessToCustomerSettings());
	}
}
