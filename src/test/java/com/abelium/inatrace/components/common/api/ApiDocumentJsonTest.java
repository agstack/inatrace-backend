package com.abelium.inatrace.components.common.api;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Guards how a document reference arrives from the client. Jackson binds this class through its
 * two-argument constructor, because the constructor parameter names are available at runtime, so a
 * constructor that drops one of its parameters silently loses that field. A missing
 * {@code storageKey} then reaches {@code CommonService.fetchDocument} as a null key and the
 * company profile update fails with a 500 instead of saving the uploaded logo.
 */
class ApiDocumentJsonTest {

	private final JsonMapper mapper = JsonMapper.builder().build();

	@Test
	void deserializesEveryDocumentField() {
		ApiDocument document = mapper.readValue("""
			{"id": 7, "storageKey": "temp-storage-key", "name": "logo.png",
			 "contentType": "image/png", "size": 42}
			""", ApiDocument.class);

		assertEquals(7L, document.id);
		assertEquals("temp-storage-key", document.storageKey);
		assertEquals("logo.png", document.name);
		assertEquals("image/png", document.contentType);
		assertEquals(42L, document.size);
	}
}
