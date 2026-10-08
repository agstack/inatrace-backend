package com.abelium.inatrace.components.exceptionhandling;

import com.abelium.inatrace.api.ApiStatus;
import com.abelium.inatrace.api.errors.ApiError;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;

class SpringExceptionResponseBuilderTest {

    private final SpringExceptionResponseBuilder responseBuilder = new SpringExceptionResponseBuilder();

    @Test
    void defaultsApiErrorsToJsonWhenAcceptHeaderIsMissing() {
        ResponseEntity<?> response = responseBuilder.getAcceptableResponse(
                HttpStatus.BAD_REQUEST, ApiStatus.VALIDATION_ERROR, "Receipt document has to be provided", request(null));

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals(org.springframework.http.MediaType.APPLICATION_JSON, response.getHeaders().getContentType());
        ApiError error = assertInstanceOf(ApiError.class, response.getBody());
        assertEquals(ApiStatus.VALIDATION_ERROR, error.getStatus());
        assertEquals("Receipt document has to be provided", error.getErrorMessage());
    }

    @Test
    void returnsJsonWheneverJsonIsAccepted() {
        for (String accept : new String[] {
                "application/json",
                "*/*",
                "text/plain;q=0.9, application/json;q=0.1",
                "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8"
        }) {
            ResponseEntity<?> response = responseBuilder.getAcceptableResponse(
                    HttpStatus.FORBIDDEN, ApiStatus.UNAUTHORIZED, "Unauthorized", request(accept));

            assertInstanceOf(ApiError.class, response.getBody(), accept);
        }
    }

    @Test
    void returnsTextWhenJsonIsExplicitlyRejected() {
        ResponseEntity<?> jsonRejected = responseBuilder.getAcceptableResponse(
                HttpStatus.BAD_REQUEST, ApiStatus.INVALID_REQUEST, "Invalid request",
                request("application/json;q=0, text/plain"));

        assertEquals("Invalid request", jsonRejected.getBody());
    }

    @Test
    void returnsNoBodyWhenTheClientAcceptsNoSupportedErrorRepresentation() {
        ResponseEntity<?> response = responseBuilder.getAcceptableResponse(
                HttpStatus.BAD_REQUEST, ApiStatus.INVALID_REQUEST, "Invalid request", request("application/xml"));

        assertNull(response.getBody());
    }

    private MockHttpServletRequest request(String accept) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        if (accept != null) {
            request.addHeader(HttpHeaders.ACCEPT, accept);
        }
        return request;
    }
}
