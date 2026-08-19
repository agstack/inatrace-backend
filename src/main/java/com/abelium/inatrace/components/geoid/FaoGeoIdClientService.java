package com.abelium.inatrace.components.geoid;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Read-only client for the FAO GeoID registry (<a href="https://data.apps.fao.org/geoid/docs">docs</a>).
 *
 * <p>A GeoID is a globally unique, immutable identifier derived from a geometry, so it is the
 * interoperability handle for a plot: any system connected to the registry can resolve the same
 * identifier back to the same boundary. This client only <em>resolves</em>
 * ({@code GET /{geoid}} &rarr; GeoJSON). Minting new GeoIDs publishes farmer boundaries to a public
 * registry and is deliberately not done here.</p>
 */
@Service
public class FaoGeoIdClientService {

	private static final Logger logger = LoggerFactory.getLogger(FaoGeoIdClientService.class);

	/** GeoIDs are UUIDv8 values; anything else is rejected without a network round trip. */
	private static final Pattern GEOID_FORMAT = Pattern.compile(
			"^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$", Pattern.CASE_INSENSITIVE);

	@Value("${INATrace.geoid.baseURL:https://data.apps.fao.org/geoid}")
	private String baseURL;

	@Value("${INATrace.geoid.enabled:true}")
	private boolean enabled;

	@Value("${INATrace.geoid.timeoutSeconds:15}")
	private long timeoutSeconds;

	/** @return {@code true} if {@code value} looks like a GeoID at all (no network call involved) */
	public static boolean isGeoIdFormat(String value) {
		return value != null && GEOID_FORMAT.matcher(value.trim()).matches();
	}

	/**
	 * Resolves a GeoID to its GeoJSON Feature.
	 *
	 * @return the GeoJSON, or {@code null} when the registry is disabled, the identifier is
	 *         malformed or unknown, or the registry could not be reached. Callers fall back to any
	 *         coordinates the row carried rather than failing the import.
	 */
	public String resolveGeoJson(String geoId) {

		if (!enabled) {
			logger.debug("GeoID resolution is disabled; ignoring {}", geoId);
			return null;
		}
		if (!isGeoIdFormat(geoId)) {
			logger.warn("Not a well-formed GeoID, skipping resolution: {}", geoId);
			return null;
		}

		String normalized = geoId.trim().toLowerCase(Locale.ROOT);

		try {
			return WebClient.create(baseURL)
					.get()
					.uri(uriBuilder -> uriBuilder.pathSegment(normalized).build())
					.accept(MediaType.parseMediaType("application/geo+json"), MediaType.APPLICATION_JSON)
					.retrieve()
					.bodyToMono(String.class)
					.block(Duration.ofSeconds(timeoutSeconds));
		} catch (RuntimeException e) {
			logger.warn("Could not resolve GeoID {} against {}: {}", normalized, baseURL, e.getMessage());
			return null;
		}
	}
}
