package com.abelium.inatrace.security;

import com.abelium.inatrace.support.AbstractMySqlIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import org.springframework.web.method.HandlerMethod;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/** Keeps the route inventory aligned with the API actually registered by Spring MVC. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ApiRouteInventoryTest extends AbstractMySqlIntegrationTest {

    private static final Path INVENTORY = Path.of("docs", "api-auth-coverage", "routes.tsv");

    @Autowired
    @Qualifier("requestMappingHandlerMapping")
    private RequestMappingHandlerMapping handlerMapping;

    @Autowired
    private MockMvc mockMvc;

    @Test
    void registeredControllerOperationsMatchInventory() throws IOException {
        SortedSet<String> registered = new TreeSet<>();

        for (var entry : handlerMapping.getHandlerMethods().entrySet()) {
            RequestMappingInfo mapping = entry.getKey();
            HandlerMethod handler = entry.getValue();
            Class<?> controller = handler.getBeanType();

            if (!controller.getPackageName().startsWith("com.abelium.inatrace.")
                    || !controller.getSimpleName().endsWith("Controller")
                    || mapping.getPathPatternsCondition() == null) {
                continue;
            }

            Set<RequestMethod> methods = mapping.getMethodsCondition().getMethods();
            if (methods.isEmpty()) {
                throw new IllegalStateException("API mapping without an HTTP method: " + mapping);
            }

            for (RequestMethod method : methods) {
                for (String path : mapping.getPathPatternsCondition().getPatternValues()) {
                    registered.add(method.name() + "\t" + path + "\t"
                            + controller.getSimpleName() + "#" + handler.getMethod().getName());
                }
            }
        }

        if (Boolean.getBoolean("api.inventory.dump")) {
            registered.forEach(route -> System.out.println("API_AUTH_ROUTE\t" + route));
            return;
        }

        SortedSet<String> inventoried = new TreeSet<>();
        int documentedCount = 0;
        for (String line : Files.readAllLines(INVENTORY)) {
            if (!line.isBlank() && !line.startsWith("#")) {
                inventoried.add(line);
                documentedCount++;
            }
        }

        SortedSet<String> missing = new TreeSet<>(registered);
        missing.removeAll(inventoried);
        SortedSet<String> stale = new TreeSet<>(inventoried);
        stale.removeAll(registered);

        assertEquals(Set.of(), missing, "Registered routes absent from " + INVENTORY);
        assertEquals(Set.of(), stale, "Inventory routes absent from Spring MVC");
        assertEquals(registered.size(), documentedCount, "Inventory contains duplicate routes");
    }

    @Test
    void openApiOperationsMatchRegisteredRoutes() throws Exception {
        JsonNode paths = new ObjectMapper().readTree(mockMvc.perform(get("/v3/api-docs"))
                .andReturn().getResponse().getContentAsString()).path("paths");
        assertTrue(paths.isObject(), "OpenAPI must contain a paths object");

        SortedSet<String> documented = new TreeSet<>();
        paths.fields().forEachRemaining(path -> path.getValue().fieldNames().forEachRemaining(method -> {
            if (Set.of("get", "post", "put", "patch", "delete", "head", "options", "trace").contains(method)) {
                documented.add(method.toUpperCase(Locale.ROOT) + "\t" + path.getKey());
            }
        }));

        SortedSet<String> registered = new TreeSet<>();
        for (String line : Files.readAllLines(INVENTORY)) {
            if (!line.isBlank() && !line.startsWith("#")) {
                String[] parts = line.split("\t", 3);
                registered.add(parts[0] + "\t" + parts[1]);
            }
        }

        SortedSet<String> mvcOnly = new TreeSet<>(registered);
        mvcOnly.removeAll(documented);
        SortedSet<String> openApiOnly = new TreeSet<>(documented);
        openApiOnly.removeAll(registered);

        if (Boolean.getBoolean("api.inventory.dump")) {
            System.out.println("API_AUTH_OPENAPI_COUNT\t" + documented.size());
            mvcOnly.forEach(route -> System.out.println("API_AUTH_MVC_ONLY\t" + route));
            openApiOnly.forEach(route -> System.out.println("API_AUTH_OPENAPI_ONLY\t" + route));
            return;
        }

        assertEquals(Set.of(), openApiOnly, "OpenAPI operations absent from Spring MVC");
        assertEquals(Set.of(), mvcOnly, "Spring MVC operations absent from OpenAPI");
    }
}
