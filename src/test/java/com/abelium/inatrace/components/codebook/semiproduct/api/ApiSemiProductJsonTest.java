package com.abelium.inatrace.components.codebook.semiproduct.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The published OpenAPI document names these flags {@code sku} and {@code skuendCustomer}. When the
 * getters followed the JavaBean convention the runtime only accepted {@code SKU}/{@code SKUEndCustomer},
 * so every client generated from the contract sent flags that were silently dropped.
 */
class ApiSemiProductJsonTest {

	private final ObjectMapper mapper = new ObjectMapper();

	@Test
	@DisplayName("reads the SKU flags using the published property names")
	void readsPublishedPropertyNames() throws Exception {

		ApiSemiProduct semiProduct = mapper.readValue(
				"{\"name\":\"Coffee Cherries\",\"sku\":true,\"skuendCustomer\":false}",
				ApiSemiProduct.class);

		assertTrue(semiProduct.getSKU());
		assertFalse(semiProduct.getSKUEndCustomer());
	}

	@Test
	@DisplayName("writes the SKU flags using the published property names")
	void writesPublishedPropertyNames() throws Exception {

		ApiSemiProduct semiProduct = new ApiSemiProduct();
		semiProduct.setName("Coffee Cherries");
		semiProduct.setSKU(true);
		semiProduct.setSKUEndCustomer(false);

		JsonNode json = mapper.readTree(mapper.writeValueAsString(semiProduct));

		assertNotNull(json.get("sku"));
		assertTrue(json.get("sku").asBoolean());
		assertNotNull(json.get("skuendCustomer"));
		assertFalse(json.has("SKU"));
		assertFalse(json.has("SKUEndCustomer"));
	}
}
