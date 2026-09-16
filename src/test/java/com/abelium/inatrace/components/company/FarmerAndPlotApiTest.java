package com.abelium.inatrace.components.company;

import com.abelium.inatrace.components.common.TokenService;
import com.abelium.inatrace.db.entities.codebook.ProductType;
import com.abelium.inatrace.db.entities.common.Address;
import com.abelium.inatrace.db.entities.common.BankInformation;
import com.abelium.inatrace.db.entities.common.Country;
import com.abelium.inatrace.db.entities.common.FarmInformation;
import com.abelium.inatrace.db.entities.common.Plot;
import com.abelium.inatrace.db.entities.common.PlotCoordinate;
import com.abelium.inatrace.db.entities.common.User;
import com.abelium.inatrace.db.entities.common.UserCustomer;
import com.abelium.inatrace.db.entities.common.UserCustomerLocation;
import com.abelium.inatrace.db.entities.common.UserCustomerProductType;
import com.abelium.inatrace.db.entities.company.Company;
import com.abelium.inatrace.db.entities.company.CompanyUser;
import com.abelium.inatrace.types.CompanyStatus;
import com.abelium.inatrace.types.CompanyUserRole;
import com.abelium.inatrace.types.Gender;
import com.abelium.inatrace.types.UserCustomerType;
import com.abelium.inatrace.types.UserRole;
import com.abelium.inatrace.types.UserStatus;
import jakarta.persistence.EntityManager;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

/**
 * The farmer and plot endpoints of {@link CompanyController}, exercised over the real filter chain.
 *
 * <p>A farmer record is the most sensitive thing this system stores: a named smallholder, their
 * phone number, their bank account, and the satellite coordinates of the land they work. Every one
 * of these endpoints takes an id straight from the URL or the request body, so the only thing
 * standing between one cooperative and another's farmer register is the enrolment check in
 * {@code CompanyService}. These tests hold that line from both sides -- each endpoint is asked once
 * by someone entitled to the data and once by a stranger, and the stranger's response is checked
 * not merely for a 403 but for the absence of the data itself.
 *
 * <p>Two unrelated tenants are seeded: Alice belongs to "Acme Coffee Cooperative", which has one
 * farmer with a bank account and a mapped plot; Bob belongs to "Rival Trading Ltd" and has a farmer
 * of his own. Both authenticate normally -- a real JWT from {@link TokenService} in the access
 * cookie, through the real {@code TokenAuthenticationFilter} -- so a refusal here is the
 * application's authorization decision and not a failure to log in.
 *
 * <p>The markers seeded into Acme's farmer ({@link #BANK_ACCOUNT}, {@link #PLOT_GEO_ID},
 * {@link #PLOT_LATITUDE} and the rest) are deliberately distinctive strings. Asserting a status
 * code alone would not catch an endpoint that answered 403 in the header and still wrote the rows
 * into the body, or an export that streamed a file before the check ran, so every refusal is also
 * searched for those markers.
 *
 * @see com.abelium.inatrace.security.MultiTenantIsolationTest for the same treatment of the
 *      dashboard endpoints
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers
@Transactional
class FarmerAndPlotApiTest {

	@Container
	@ServiceConnection
	static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4");

	/** Markers that must never appear in a response to someone outside Acme. */
	private static final String FARMER_NAME = "Celestine";
	private static final String FARMER_SURNAME = "Uwaseacmeonly";
	private static final String BANK_ACCOUNT = "ACME-IBAN-00000001";
	private static final String INTERNAL_ID = "ACME-F-0001";
	private static final String PLOT_NAME = "Acme North Ridge";
	private static final String PLOT_GEO_ID = "acme-geo-id-9f3a7c";
	private static final String PLOT_LATITUDE = "-1.9438715";
	private static final String PLOT_LONGITUDE = "30.0617283";

	/** Rival's own farmer, used to prove that a listing is scoped and not merely gated. */
	private static final String RIVAL_FARMER_SURNAME = "Mwangirivalonly";

	@Autowired private MockMvc mockMvc;
	@Autowired private EntityManager em;
	@Autowired private TokenService tokenService;

	@Value("${INATrace.auth.accessTokenCookieName}")
	private String accessCookieName;

	private Long acmeId;
	private Long rivalId;
	private Long acmeFarmerId;
	private Long acmeSecondFarmerId;
	private Long rivalFarmerId;
	private Long acmePlotId;
	private Long acmeSecondFarmerPlotId;
	private Long coffeeProductTypeId;

	private Cookie aliceSession;
	private Cookie bobSession;

	@BeforeEach
	void seedTwoTenants() {

		Country rwanda = new Country();
		rwanda.setCode("RW");
		rwanda.setName("Rwanda");
		em.persist(rwanda);

		ProductType coffee = new ProductType();
		coffee.setCode("COFFEE");
		coffee.setName("Coffee");
		em.persist(coffee);

		Company acme = company("Acme Coffee Cooperative");
		Company rival = company("Rival Trading Ltd");

		User alice = user("alice@acme.test", "Alice", "Acme");
		User bob = user("bob@rival.test", "Bob", "Rival");

		enroll(alice, acme);
		enroll(bob, rival);

		// Acme's farmer: the record Bob must never reach, by any route.
		UserCustomer acmeFarmer = farmer(acme, FARMER_NAME, FARMER_SURNAME, rwanda, coffee);
		acmeFarmer.setFarmerCompanyInternalId(INTERNAL_ID);
		acmeFarmer.getBank().setAccountNumber(BANK_ACCOUNT);

		// A mapped plot: four corners and a GeoID, as a farm boundary arrives from the field app.
		Plot acmePlot = plot(acmeFarmer, coffee, PLOT_NAME, PLOT_GEO_ID);

		// A second Acme farmer with a plot of their own. Alice is entitled to both, which is what
		// makes this the right fixture for proving that a plot id is resolved against the farmer in
		// the path and not looked up globally.
		UserCustomer acmeSecondFarmer = farmer(acme, "Jean", "Habimana", rwanda, coffee);
		Plot acmeSecondPlot = plot(acmeSecondFarmer, coffee, "Acme South Slope", "acme-geo-id-2");

		UserCustomer rivalFarmer = farmer(rival, "Grace", RIVAL_FARMER_SURNAME, rwanda, coffee);

		em.flush();

		acmeId = acme.getId();
		rivalId = rival.getId();
		acmeFarmerId = acmeFarmer.getId();
		acmeSecondFarmerId = acmeSecondFarmer.getId();
		rivalFarmerId = rivalFarmer.getId();
		acmePlotId = acmePlot.getId();
		acmeSecondFarmerPlotId = acmeSecondPlot.getId();
		coffeeProductTypeId = coffee.getId();

		aliceSession = sessionCookie(alice);
		bobSession = sessionCookie(bob);

		// Hand the requests a cold persistence context, so every endpoint loads its entities from
		// the database the way it does in production. It matters: UserCustomer.getAssociations()
		// and its neighbours -- unlike getPlots() and getProductTypes() -- do not initialise
		// themselves when the field is null, so an object still sitting in the session from the
		// seed above would fail in the mapper for a reason no real request ever meets.
		em.flush();
		em.clear();
	}

	// ------------------------------------------------------------------ the farmer register

	@Test
	@DisplayName("Alice can list her own company's farmers")
	void ownCompanyFarmerListIsReadable() throws Exception {

		MvcResult result = mockMvc.perform(get("/api/company/userCustomers/" + acmeId + "/FARMER")
						.queryParam("limit", "50")
						.queryParam("offset", "0")
						.cookie(aliceSession))
				.andReturn();

		assertEquals(200, result.getResponse().getStatus(), body(result));
		assertTrue(body(result).contains(FARMER_SURNAME),
				"Acme's own farmer should be listed for Acme: " + body(result));
	}

	@Test
	@DisplayName("Bob cannot list the farmers of a company he does not belong to")
	void foreignCompanyFarmerListIsRejected() throws Exception {

		MvcResult result = mockMvc.perform(get("/api/company/userCustomers/" + acmeId + "/FARMER")
						.queryParam("limit", "50")
						.queryParam("offset", "0")
						.cookie(bobSession))
				.andReturn();

		assertRefused(result, "the farmer register of another tenant");
		assertDoesNotLeak(result, FARMER_SURNAME, INTERNAL_ID, BANK_ACCOUNT);
	}

	@Test
	@DisplayName("A farmer listing is scoped to the company asked for, not to everything the caller may see")
	void farmerListDoesNotSpillOtherTenantsIn() throws Exception {

		MvcResult result = mockMvc.perform(get("/api/company/userCustomers/" + acmeId + "/FARMER")
						.queryParam("limit", "50")
						.queryParam("offset", "0")
						.cookie(aliceSession))
				.andReturn();

		assertEquals(200, result.getResponse().getStatus(), body(result));
		assertFalse(body(result).contains(RIVAL_FARMER_SURNAME),
				"Rival's farmer appeared in Acme's register: " + body(result));
	}

	@Test
	@DisplayName("Alice can read one of her own farmers, bank details included")
	void ownFarmerIsReadable() throws Exception {

		MvcResult result = mockMvc.perform(get("/api/company/userCustomers/" + acmeFarmerId)
						.cookie(aliceSession))
				.andReturn();

		assertEquals(200, result.getResponse().getStatus(), body(result));
		assertTrue(body(result).contains(BANK_ACCOUNT),
				"the owning company should see its farmer's bank details: " + body(result));
	}

	@Test
	@DisplayName("Bob cannot read a farmer of another tenant by guessing the id")
	void foreignFarmerByIdIsRejected() throws Exception {

		MvcResult result = mockMvc.perform(get("/api/company/userCustomers/" + acmeFarmerId)
						.cookie(bobSession))
				.andReturn();

		assertRefused(result, "a farmer record belonging to another tenant");
		assertDoesNotLeak(result, FARMER_SURNAME, BANK_ACCOUNT, INTERNAL_ID, PLOT_GEO_ID);
	}

	// ------------------------------------------------------------------ plots

	@Test
	@DisplayName("Alice can read the plots of her own company")
	void ownCompanyPlotsAreReadable() throws Exception {

		MvcResult result = mockMvc.perform(get("/api/company/userCustomers/" + acmeId + "/plots")
						.cookie(aliceSession))
				.andReturn();

		assertEquals(200, result.getResponse().getStatus(), body(result));
		assertTrue(body(result).contains(PLOT_NAME), body(result));
		assertTrue(body(result).contains(PLOT_GEO_ID), body(result));
	}

	@Test
	@DisplayName("Bob cannot read the plots of a company he does not belong to")
	void foreignCompanyPlotsAreRejected() throws Exception {

		MvcResult result = mockMvc.perform(get("/api/company/userCustomers/" + acmeId + "/plots")
						.cookie(bobSession))
				.andReturn();

		assertRefused(result, "the mapped plots of another tenant");
		assertDoesNotLeak(result, PLOT_NAME, PLOT_GEO_ID, PLOT_LATITUDE, PLOT_LONGITUDE);
	}

	@Test
	@DisplayName("Alice can add a plot to her own farmer")
	void plotCanBeAddedToOwnFarmer() throws Exception {

		long before = plotCount(acmeFarmerId);

		MvcResult result = mockMvc.perform(post("/api/company/userCustomers/" + acmeFarmerId + "/plots/add")
						.contentType(MediaType.APPLICATION_JSON)
						.content(newPlotJson("Acme East Terrace"))
						.cookie(aliceSession))
				.andReturn();

		assertEquals(200, result.getResponse().getStatus(), body(result));
		assertEquals(before + 1, plotCount(acmeFarmerId),
				"the plot should have been recorded against the farmer");
	}

	@Test
	@DisplayName("Bob cannot add a plot to a farmer of another tenant")
	void plotCannotBeAddedToForeignFarmer() throws Exception {

		long before = plotCount(acmeFarmerId);

		MvcResult result = mockMvc.perform(post("/api/company/userCustomers/" + acmeFarmerId + "/plots/add")
						.contentType(MediaType.APPLICATION_JSON)
						.content(newPlotJson("Bob's Intrusion"))
						.cookie(bobSession))
				.andReturn();

		assertRefused(result, "writing a plot onto another tenant's farmer");
		assertEquals(before, plotCount(acmeFarmerId),
				"a refused request must not have written a plot anyway");
	}

	@Test
	@DisplayName("Bob cannot refresh the GeoID of a plot belonging to another tenant")
	void geoIdRefreshOnForeignPlotIsRejected() throws Exception {

		MvcResult result = mockMvc.perform(
						post("/api/company/userCustomers/" + acmeFarmerId + "/plots/" + acmePlotId + "/updateGeoID")
								.cookie(bobSession))
				.andReturn();

		assertRefused(result, "refreshing the GeoID of another tenant's plot");
		assertDoesNotLeak(result, PLOT_NAME, PLOT_GEO_ID);
	}

	@Test
	@DisplayName("A plot id is resolved against the farmer in the path, not looked up globally")
	void geoIdRefreshRejectsAPlotOfADifferentFarmer() throws Exception {

		// Alice is entitled to both farmers, so nothing here is an enrolment failure: the question
		// is only whether the service takes the plot id at face value. It must not -- a plot is
		// addressed through its farmer, and a mismatched pair is an invalid request.
		MvcResult result = mockMvc.perform(
						post("/api/company/userCustomers/" + acmeFarmerId
								+ "/plots/" + acmeSecondFarmerPlotId + "/updateGeoID")
								.cookie(aliceSession))
				.andReturn();

		assertEquals(400, result.getResponse().getStatus(),
				"a plot belonging to a different farmer should be an invalid request, not an update. Got "
						+ result.getResponse().getStatus() + ": " + body(result));
	}

	// ------------------------------------------------------------------ geo data in and out

	@Test
	@DisplayName("Alice can export the geo data of her own farmer")
	void ownFarmerGeoDataIsExportable() throws Exception {

		MvcResult result = mockMvc.perform(get("/api/company/userCustomers/" + acmeFarmerId + "/exportGeoData")
						.cookie(aliceSession))
				.andReturn();

		assertEquals(200, result.getResponse().getStatus(), body(result));

		String geoJson = new String(result.getResponse().getContentAsByteArray(), StandardCharsets.UTF_8);
		assertTrue(geoJson.contains(PLOT_GEO_ID), "the export should carry the plot's GeoID: " + geoJson);
		assertTrue(geoJson.contains("Polygon"), "four corners should export as a polygon: " + geoJson);
	}

	@Test
	@DisplayName("Bob cannot export the geo data of another tenant's farmer")
	void foreignFarmerGeoDataExportIsRejected() throws Exception {

		MvcResult result = mockMvc.perform(get("/api/company/userCustomers/" + acmeFarmerId + "/exportGeoData")
						.cookie(bobSession))
				.andReturn();

		assertRefused(result, "the geo data of another tenant's farmer");

		// This endpoint streams bytes rather than JSON, so the file itself is what gets searched.
		String file = new String(result.getResponse().getContentAsByteArray(), StandardCharsets.UTF_8);
		for (String marker : List.of(PLOT_GEO_ID, PLOT_LATITUDE, PLOT_LONGITUDE)) {
			assertFalse(file.contains(marker),
					"the plot boundary of another tenant was downloaded (found " + marker + "): " + file);
		}
	}

	@Test
	@DisplayName("Bob cannot upload geo data onto another tenant's farmer")
	void foreignFarmerGeoDataUploadIsRejected() throws Exception {

		long before = plotCount(acmeFarmerId);

		MockMultipartFile upload = new MockMultipartFile(
				"file", "boundary.geojson", "application/geo+json",
				("{\"type\":\"FeatureCollection\",\"features\":[{\"type\":\"Feature\",\"properties\":{},"
						+ "\"geometry\":{\"type\":\"Point\",\"coordinates\":[30.1,-1.9]}}]}")
						.getBytes(StandardCharsets.UTF_8));

		MvcResult result = mockMvc.perform(multipart("/api/company/userCustomers/" + acmeFarmerId + "/uploadGeoData")
						.file(upload)
						.cookie(bobSession))
				.andReturn();

		assertRefused(result, "uploading a boundary onto another tenant's farmer");
		assertEquals(before, plotCount(acmeFarmerId),
				"a refused upload must not have created a plot");
	}

	// ------------------------------------------------------------------ the bulk export

	@Test
	@DisplayName("Alice can export her own company's farmer data")
	void ownCompanyFarmerExportIsDownloadable() throws Exception {

		MvcResult result = mockMvc.perform(get("/api/company/userCustomers/" + acmeId + "/exportFarmerData")
						.cookie(aliceSession))
				.andReturn();

		assertEquals(200, result.getResponse().getStatus(), body(result));

		byte[] zip = result.getResponse().getContentAsByteArray();
		assertTrue(zip.length > 0, "the export should not be empty");
		assertEquals('P', (char) zip[0], "the export should be a zip archive");
		assertEquals('K', (char) zip[1], "the export should be a zip archive");
	}

	@Test
	@DisplayName("Bob cannot export the farmer data of a company he does not belong to")
	void foreignCompanyFarmerExportIsRejected() throws Exception {

		MvcResult result = mockMvc.perform(get("/api/company/userCustomers/" + acmeId + "/exportFarmerData")
						.cookie(bobSession))
				.andReturn();

		assertRefused(result, "the farmer export of another tenant");

		byte[] payload = result.getResponse().getContentAsByteArray();
		assertFalse(payload.length > 1 && payload[0] == 'P' && payload[1] == 'K',
				"a zip of another tenant's farmer register was downloaded");
	}

	// ------------------------------------------------------------------ writes

	@Test
	@DisplayName("Bob cannot add a farmer to a company he does not belong to")
	void farmerCannotBeAddedToForeignCompany() throws Exception {

		MvcResult result = mockMvc.perform(post("/api/company/userCustomers/add/" + acmeId)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"type\":\"FARMER\",\"name\":\"Planted\",\"surname\":\"ByBob\"}")
						.cookie(bobSession))
				.andReturn();

		assertRefused(result, "planting a farmer in another tenant's register");
	}

	@Test
	@DisplayName("Bob cannot edit a farmer of another tenant by putting their id in the body")
	void foreignFarmerCannotBeEdited() throws Exception {

		MvcResult result = mockMvc.perform(put("/api/company/userCustomers/edit")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"id\":" + acmeFarmerId + ",\"type\":\"FARMER\","
								+ "\"name\":\"Overwritten\",\"surname\":\"ByBob\"}")
						.cookie(bobSession))
				.andReturn();

		assertRefused(result, "editing another tenant's farmer");

		em.flush();
		assertEquals(FARMER_SURNAME, em.find(UserCustomer.class, acmeFarmerId).getSurname(),
				"the farmer's own company owns this record; a stranger's edit must not stick");
	}

	@Test
	@DisplayName("Bob cannot delete a farmer of another tenant")
	void foreignFarmerCannotBeDeleted() throws Exception {

		MvcResult result = mockMvc.perform(delete("/api/company/userCustomers/" + acmeFarmerId)
						.cookie(bobSession))
				.andReturn();

		assertRefused(result, "deleting another tenant's farmer");

		em.flush();
		assertNotNull(em.find(UserCustomer.class, acmeFarmerId),
				"the farmer must survive a refused delete");
	}

	// ------------------------------------------------------------------ no session at all

	@Test
	@DisplayName("None of the farmer or plot endpoints answer without a session")
	void anonymousCallersAreRefused() throws Exception {

		List<String> guarded = List.of(
				"/api/company/userCustomers/" + acmeFarmerId,
				"/api/company/userCustomers/" + acmeId + "/FARMER",
				"/api/company/userCustomers/" + acmeId + "/plots",
				"/api/company/userCustomers/" + acmeId + "/exportFarmerData",
				"/api/company/userCustomers/" + acmeFarmerId + "/exportGeoData");

		for (String path : guarded) {
			MvcResult result = mockMvc.perform(get(path)).andReturn();

			assertEquals(401, result.getResponse().getStatus(),
					path + " answered an anonymous caller with " + result.getResponse().getStatus());
			assertDoesNotLeak(result, FARMER_SURNAME, BANK_ACCOUNT, PLOT_GEO_ID);
		}
	}

	// ------------------------------------------------------------------ assertions

	/** A refusal is 403 -- {@code ApiStatus.UNAUTHORIZED} -- and not a 200, a 404 or a crash. */
	private void assertRefused(MvcResult result, String what) throws Exception {
		assertEquals(403, result.getResponse().getStatus(),
				"a stranger asked for " + what + " and the API answered "
						+ result.getResponse().getStatus() + ": " + body(result));
	}

	private void assertDoesNotLeak(MvcResult result, String... markers) throws Exception {
		String body = body(result);
		for (String marker : markers) {
			assertFalse(body.contains(marker),
					"the response disclosed " + marker + " to a caller who should not see it: " + body);
		}
	}

	private String body(MvcResult result) throws Exception {
		return result.getResponse().getContentAsString();
	}

	/**
	 * Counts a farmer's plots with a query rather than through {@code UserCustomer.getPlots()}.
	 * {@code CompanyService.createUserCustomerPlot} persists the plot with its farmer set but never
	 * adds it to the farmer's collection, so a collection already loaded in this transaction would
	 * not show the new row. A query is flushed against the database and always would.
	 */
	private long plotCount(Long farmerId) {
		return em.createQuery("select count(p) from Plot p where p.farmer.id = :farmerId", Long.class)
				.setParameter("farmerId", farmerId)
				.getSingleResult();
	}

	// ------------------------------------------------------------------ fixture helpers

	private Company company(String name) {
		Company c = new Company();
		c.setName(name);
		c.setStatus(CompanyStatus.ACTIVE);
		em.persist(c);
		return c;
	}

	private User user(String email, String name, String surname) {
		User u = new User();
		u.setEmail(email);
		u.setName(name);
		u.setSurname(surname);
		u.setPassword("not-used-in-this-test");
		u.setRole(UserRole.USER);
		u.setStatus(UserStatus.ACTIVE);
		em.persist(u);
		return u;
	}

	private void enroll(User user, Company company) {
		CompanyUser cu = new CompanyUser();
		cu.setUser(user);
		cu.setCompany(company);
		cu.setRole(CompanyUserRole.COMPANY_ADMIN);
		em.persist(cu);
		company.getUsers().add(cu);
	}

	/**
	 * A farmer complete enough for the Excel export, which reads the address, the country and the
	 * gender without checking any of them for null.
	 */
	private UserCustomer farmer(Company company, String name, String surname, Country country, ProductType crop) {

		Address address = new Address();
		address.setAddress("Kigali-Nyabihu road");
		address.setCity("Nyabihu");
		address.setState("Western Province");
		address.setVillage("Rurembo");
		address.setCell("Kabatezi");
		address.setSector("Jenda");
		address.setZip("0000");
		address.setOtherAddress("near the washing station");
		address.setCountry(country);

		UserCustomerLocation location = new UserCustomerLocation();
		location.setAddress(address);
		location.setLatitude(Double.parseDouble(PLOT_LATITUDE));
		location.setLongitude(Double.parseDouble(PLOT_LONGITUDE));
		location.setPubliclyVisible(Boolean.FALSE);
		em.persist(location);

		UserCustomer farmer = new UserCustomer();
		farmer.setCompany(company);
		farmer.setType(UserCustomerType.FARMER);
		farmer.setName(name);
		farmer.setSurname(surname);
		farmer.setEmail(surname.toLowerCase() + "@farmers.test");
		farmer.setPhone("+250780000000");
		farmer.setGender(Gender.FEMALE);
		farmer.setHasSmartphone(Boolean.FALSE);
		farmer.setUserCustomerLocation(location);

		BankInformation bank = new BankInformation();
		bank.setAccountHolderName(name + " " + surname);
		bank.setBankName("Banque Populaire");
		bank.setAccountNumber("UNSET");
		farmer.setBank(bank);

		FarmInformation farm = new FarmInformation();
		farm.setAreaUnit("ha");
		farm.setOrganic(Boolean.TRUE);
		farm.setTotalCultivatedArea(new BigDecimal("2.75"));
		farm.setAreaOrganicCertified(new BigDecimal("1.50"));
		farmer.setFarm(farm);

		em.persist(farmer);

		UserCustomerProductType farmerCrop = new UserCustomerProductType();
		farmerCrop.setUserCustomer(farmer);
		farmerCrop.setProductType(crop);
		em.persist(farmerCrop);
		farmer.getProductTypes().add(farmerCrop);

		return farmer;
	}

	/** A four-corner boundary, the shape the mobile app records when a farm is walked. */
	private Plot plot(UserCustomer farmer, ProductType crop, String plotName, String geoId) {

		Plot plot = new Plot();
		plot.setFarmer(farmer);
		plot.setCrop(crop);
		plot.setPlotName(plotName);
		plot.setGeoId(geoId);
		plot.setUnit("ha");
		plot.setSize(2.75);
		plot.setNumberOfPlants(900);

		double lat = Double.parseDouble(PLOT_LATITUDE);
		double lng = Double.parseDouble(PLOT_LONGITUDE);

		List<PlotCoordinate> corners = new ArrayList<>();
		corners.add(corner(plot, lat, lng));
		corners.add(corner(plot, lat + 0.0010, lng));
		corners.add(corner(plot, lat + 0.0010, lng + 0.0010));
		corners.add(corner(plot, lat, lng + 0.0010));
		plot.setCoordinates(corners);

		em.persist(plot);
		farmer.getPlots().add(plot);

		return plot;
	}

	private PlotCoordinate corner(Plot plot, double latitude, double longitude) {
		PlotCoordinate coordinate = new PlotCoordinate();
		coordinate.setPlot(plot);
		coordinate.setLatitude(latitude);
		coordinate.setLongitude(longitude);
		return coordinate;
	}

	/**
	 * Two corners, not three. {@code CompanyService.generatePlotGeoID} only calls out to the AgStack
	 * registry for a ring of three or more, and the test profile has no AgStack credentials, so a
	 * two-point plot keeps the write path off the network and the test deterministic.
	 */
	private String newPlotJson(String plotName) {
		return "{\"plotName\":\"" + plotName + "\","
				+ "\"crop\":{\"id\":" + coffeeProductTypeId + "},"
				+ "\"numberOfPlants\":120,"
				+ "\"unit\":\"ha\","
				+ "\"size\":1.25,"
				+ "\"coordinates\":["
				+ "{\"latitude\":-1.9500000,\"longitude\":30.0700000},"
				+ "{\"latitude\":-1.9510000,\"longitude\":30.0710000}"
				+ "]}";
	}

	/** A real access cookie, issued the way the login endpoint issues it. */
	private Cookie sessionCookie(User user) {
		return new Cookie(accessCookieName, tokenService.createAccessToken(user));
	}
}
