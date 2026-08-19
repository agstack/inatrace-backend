package com.abelium.inatrace.components.geoid;

import com.abelium.inatrace.components.company.GeoDataParser;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exercises the FAO GeoID resolver against a local stub - CI never reaches the real registry.
 *
 * <p>The behaviour that matters is that no failure mode throws: an import falls back to whatever
 * coordinates the row carried, so a registry outage must never cost a user their upload.</p>
 */
class FaoGeoIdClientServiceTest {

	private static final String VALID_GEOID = "018f3b2c-1a4e-8000-9c3d-2b6f5a1e7d40";

	private static final String GEOJSON =
			"{\"type\":\"Feature\",\"properties\":{},\"geometry\":{\"type\":\"Polygon\",\"coordinates\":"
					+ "[[[10.2352433,5.1717367],[10.235235,5.1718067],[10.2352027,5.1719302],[10.2352433,5.1717367]]]}}";

	private HttpServer server;
	private FaoGeoIdClientService service;
	private final AtomicInteger requests = new AtomicInteger();

	@BeforeEach
	void startStubRegistry() throws Exception {
		server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		server.createContext("/", exchange -> {
			requests.incrementAndGet();
			byte[] body;
			int status;
			if (exchange.getRequestURI().getPath().endsWith(VALID_GEOID)) {
				body = GEOJSON.getBytes(StandardCharsets.UTF_8);
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

		service = new FaoGeoIdClientService();
		ReflectionTestUtils.setField(service, "baseURL", "http://127.0.0.1:" + server.getAddress().getPort());
		ReflectionTestUtils.setField(service, "enabled", true);
		ReflectionTestUtils.setField(service, "timeoutSeconds", 5L);
	}

	@AfterEach
	void stopStubRegistry() {
		server.stop(0);
	}

	@Test
	void knownGeoId_resolvesToGeoJson() {
		String geoJson = service.resolveGeoJson(VALID_GEOID);

		assertNotNull(geoJson);
		assertTrue(geoJson.contains("Polygon"));
		// The result feeds straight back into the same parser every other format uses.
		assertEquals(1, GeoDataParser.parse(geoJson, null).size());
	}

	@Test
	void unknownGeoId_returnsNullInsteadOfThrowing() {
		assertNull(service.resolveGeoJson("018f3b2c-1a4e-8000-9c3d-000000000000"));
	}

	@Test
	void unreachableRegistry_returnsNullInsteadOfThrowing() {
		ReflectionTestUtils.setField(service, "baseURL", "http://127.0.0.1:1");

		assertNull(service.resolveGeoJson(VALID_GEOID));
	}

	@Test
	void disabledRegistry_isNotCalledAtAll() {
		ReflectionTestUtils.setField(service, "enabled", false);
		requests.set(0);

		assertNull(service.resolveGeoJson(VALID_GEOID));
		assertEquals(0, requests.get(), "no request should reach the registry");
	}

	@Test
	void malformedGeoId_isRejectedWithoutARequest() {
		requests.set(0);

		assertNull(service.resolveGeoJson("not-a-geoid"));
		assertNull(service.resolveGeoJson(""));
		assertNull(service.resolveGeoJson(null));
		assertEquals(0, requests.get(), "no request should reach the registry");
	}

	@Test
	void geoIdFormatIsRecognised() {
		assertTrue(FaoGeoIdClientService.isGeoIdFormat(VALID_GEOID));
		assertTrue(FaoGeoIdClientService.isGeoIdFormat(VALID_GEOID.toUpperCase()));
		assertFalse(FaoGeoIdClientService.isGeoIdFormat("POLYGON((1 2, 3 4, 5 6))"));
		assertFalse(FaoGeoIdClientService.isGeoIdFormat("018f3b2c1a4e80009c3d2b6f5a1e7d40"));
	}
}
