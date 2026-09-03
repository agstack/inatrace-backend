package com.abelium.inatrace;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.ApplicationContext;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import javax.sql.DataSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Smoke test: the whole Spring context wires up.
 *
 * <p>It catches the failures that stop the application before it serves a single request --
 * a bean that cannot be created, a broken entity mapping, a missing configuration property.
 * It needs no local {@code application.properties}: the test profile carries every property
 * the beans resolve at startup.
 *
 * <p>It runs on a real port and answers one request, because a context that loads is not
 * yet an application that serves: a broken security filter chain or a mis-mapped controller
 * only shows up once something calls in.
 *
 * <p>The database is a real MySQL, started from Docker by Testcontainers, so the entity model
 * is built by the same dialect and the same server version production uses. Hibernate creates
 * the schema from the entity model; the Flyway migrations are pointed at an empty location and
 * are covered separately, because they need a database that is still empty at startup.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Testcontainers
class ContextBootSmokeTest {

	/**
	 * Started once for the class and thrown away afterwards. {@code @ServiceConnection} feeds
	 * its url, username and password to Spring, which is why the test profile configures no
	 * datasource of its own.
	 */
	@Container
	@ServiceConnection
	static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4");

	@Autowired
	private ApplicationContext context;

	@Autowired
	private DataSource dataSource;

	@Autowired
	private TestRestTemplate rest;

	@Test
	@DisplayName("the application context loads")
	void contextLoads() {
		assertNotNull(context, "application context should have been created");
	}

	@Test
	@DisplayName("the entity model maps cleanly and core beans are present")
	void coreBeansArePresent() {
		// Hibernate builds the schema from the entity model on startup, so reaching this point
		// already proves the mappings are consistent. Check the pieces the app cannot run without.
		assertNotNull(dataSource, "a DataSource should be configured");
		assertTrue(context.containsBean("entityManagerFactory"), "JPA should be wired up");
		assertTrue(context.getBeanNamesForType(org.flywaydb.core.Flyway.class).length > 0,
				"MigrationsConfiguration injects the Flyway bean, so it must exist");
	}

	@Test
	@DisplayName("Swagger UI is reachable at the address springdoc documents")
	void swaggerUiIsServed() {
		// /swagger-ui.html is the address springdoc documents and the one people type. It is a
		// redirect to /swagger-ui/index.html, so it has to be permitted in its own right: when it
		// is not, it answers 401, the redirect never happens, and the UI looks like it was never
		// shipped. Both ends of that redirect are checked, because permitting only one of them is
		// exactly the state this guards against.
		assertEquals(HttpStatus.OK, rest.getForEntity("/swagger-ui/index.html", String.class).getStatusCode(),
				"the Swagger UI page should be served");
		assertNotEquals(HttpStatus.UNAUTHORIZED,
				rest.getForEntity("/swagger-ui.html", String.class).getStatusCode(),
				"/swagger-ui.html must not be refused: it is the documented entry point");
	}

	@Test
	@DisplayName("the application answers GET /v3/api-docs")
	void apiDocsAreServed() {
		// The OpenAPI document is generated from the controllers, so serving it exercises the
		// whole request path -- port, filter chain, handler mapping -- and every @RestController
		// springdoc can see. It is public, as the frontend and the API consumers read it.
		ResponseEntity<String> response = rest.getForEntity("/v3/api-docs", String.class);

		assertEquals(HttpStatus.OK, response.getStatusCode(), "GET /v3/api-docs should answer 200");
		assertNotNull(response.getBody(), "the OpenAPI document should have a body");
		assertTrue(response.getBody().contains("\"openapi\""),
				"the body should be an OpenAPI document");
	}
}
