package com.abelium.inatrace.configuration;

import com.abelium.inatrace.api.ApiStatus;
import com.abelium.inatrace.api.errors.ApiError;
import com.abelium.inatrace.components.exceptionhandling.SpringExceptionResponseBuilder;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class DefaultControllerExceptionHandlerTest {

    @Test
    void hidesImplementationDetailsFromUnexpectedErrors() {
        DefaultControllerExceptionHandler exceptionHandler = new DefaultControllerExceptionHandler();
        ReflectionTestUtils.setField(exceptionHandler, "exceptionResponseBuilder", new SpringExceptionResponseBuilder());

        ResponseEntity<?> response = exceptionHandler.handleException(
                new IllegalStateException("internal diagnostic must not reach the client"), new MockHttpServletRequest());

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        ApiError error = assertInstanceOf(ApiError.class, response.getBody());
        assertEquals(ApiStatus.ERROR, error.getStatus());
        assertEquals("Internal server error", error.getErrorMessage());
        assertFalse(error.getErrorMessage().contains(IllegalStateException.class.getName()));
        assertFalse(error.getErrorMessage().contains("internal diagnostic"));
    }
}
