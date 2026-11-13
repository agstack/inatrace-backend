package com.abelium.inatrace.configuration;

import com.abelium.inatrace.support.AbstractMySqlIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.core.io.ClassPathResource;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ApiErrorResponseIntegrationTest extends AbstractMySqlIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void validationErrorsDefaultToJsonWhenAcceptHeaderIsMissing() throws Exception {
        mockMvc.perform(post("/api/user/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\":\"correct-horse-battery\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(header().string("Content-Type", MediaType.APPLICATION_JSON_VALUE))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(content().json(fixture("login-missing-username.json"), JsonCompareMode.STRICT))
                .andExpect(jsonPath("$.status").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.errorMessage").value("Invalid field(s) in request"))
                .andExpect(jsonPath("$.validationErrorDetails.fieldErrors.username").value("must not be null"));
    }

    @Test
    void apiExceptionsDefaultToJsonWhenAcceptHeaderIsMissing() throws Exception {
        mockMvc.perform(post("/api/user/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"unknown-user@test.invalid\",\"password\":\"correct-horse-battery\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("Content-Type", MediaType.APPLICATION_JSON_VALUE))
                .andExpect(content().json(fixture("login-invalid-credentials.json"), JsonCompareMode.STRICT))
                .andExpect(jsonPath("$.status").value("AUTH_ERROR"))
                .andExpect(jsonPath("$.errorMessage").value("Invalid credentials"));
    }

    @Test
    void anonymousRequestsAreRejectedByTheSecurityEntryPointBeforeTheControllerAdvice() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/user/profile"))
                .andExpect(status().isUnauthorized())
                .andReturn();

        assertEquals("", result.getResponse().getContentAsString());
        assertNull(result.getResponse().getContentType());
    }

    private String fixture(String name) throws Exception {
        return new ClassPathResource("contract/" + name).getContentAsString(StandardCharsets.UTF_8);
    }
}
