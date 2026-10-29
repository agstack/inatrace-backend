package com.abelium.inatrace.configuration;

import com.abelium.inatrace.api.ApiStatus;
import com.abelium.inatrace.api.errors.ApiError;
import com.abelium.inatrace.api.errors.ApiException;
import com.abelium.inatrace.api.errors.ApiImageException;
import com.abelium.inatrace.api.errors.HtmlPageException;
import com.abelium.inatrace.api.errors.validation.ApiValidationError;
import com.abelium.inatrace.api.exceptions.ApiUpstreamServiceException;
import com.abelium.inatrace.components.exceptionhandling.SpringExceptionConverter;
import com.abelium.inatrace.components.exceptionhandling.SpringExceptionResponseBuilder;
import com.fasterxml.jackson.databind.JsonMappingException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.apache.catalina.connector.ClientAbortException;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.BindException;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.io.IOException;
import java.util.concurrent.CompletionException;

@ControllerAdvice
public class DefaultControllerExceptionHandler {

    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(DefaultControllerExceptionHandler.class);
    private static final String INTERNAL_SERVER_ERROR_MESSAGE = "Internal server error";

    @Autowired
    private SpringExceptionResponseBuilder exceptionResponseBuilder;

    @ExceptionHandler
    public ResponseEntity<?> handleRestClientException(HttpClientErrorException exc, HttpServletRequest request) {
        return ResponseEntity.status(exc.getStatusCode()).contentType(
                exc.getResponseHeaders() != null && exc.getResponseHeaders().getContentType() != null ?
                        exc.getResponseHeaders().getContentType() : MediaType.TEXT_PLAIN
        ).body(exc.getResponseBodyAsString());
    }

    // ApiImageException - do not log (either a consequence of an exception that was logged before or a valid error return)
    @ExceptionHandler
    public ResponseEntity<?> handleApiImageException(ApiImageException exc, HttpServletRequest request) {
        warnOrError(exc.getHttpStatus(), "ApiImageException", exc.getApiStatus());
        return ResponseEntity.status(exc.getHttpStatus()).build(); // return empty body
    }
    
    // ApiException - do not log (either a consequence of an exception that was logged before or a valid error return)
    @ExceptionHandler
    public ResponseEntity<?> handleApiException(ApiException exc, HttpServletRequest request) {
        if (exc instanceof ApiUpstreamServiceException) {
            SafeExceptionLog.error(logger, "Upstream service error", exc);
        } else {
            warnOrError(exc.getHttpStatus(), "ApiException", exc.getApiStatus());
        }
        return exceptionResponseBuilder.getAcceptableResponse(exc.responseHttpStatus(), exc.createResponseBody(), request);
    }
    
    @ExceptionHandler
    public ResponseEntity<String> handleHtmlPageException(HtmlPageException exc, HttpServletRequest request) {
        warnOrError(exc.getStatus(), "HtmlPageException", null);
        return exceptionResponseBuilder.getAcceptableResponse(exc.getStatus(), exc.getText(), request);
    }
    
    // CompletionException - wraps original exception for async request handlers
    @ExceptionHandler
    public ResponseEntity<?> handleAsyncException(CompletionException exc, HttpServletRequest request) {
        Throwable cause = exc.getCause();
        if (cause != null && cause != exc) {
            if (cause instanceof ApiException) {
                return handleApiException((ApiException) cause, request);
            } else if (cause instanceof ApiImageException) {
                return handleApiImageException((ApiImageException) cause, request);
            } else {
                return handleUnknownException(cause, request);
            }
        }
        return handleUnknownException(exc, request);
    }
    
    // Spring request parsing exceptions
    @ExceptionHandler
    public ResponseEntity<?> handleMessageNotReadableException(HttpMessageNotReadableException exc, HttpServletRequest request) {
        // when field values are of wrong type (e.g. string for int field), we get HttpMessageNotReadableException with cause JsonMappingException
        if ( exc.getCause() instanceof JsonMappingException ) {
            return handleJsonMappingException((JsonMappingException) exc.getCause(), request);
        }
        // probably unparsable body
        return exceptionResponseBuilder.getAcceptableResponse(HttpStatus.BAD_REQUEST, ApiStatus.REQUEST_BODY_ERROR, exc.getMessage(), request);
    }

    private ResponseEntity<?> handleJsonMappingException(JsonMappingException exc, HttpServletRequest request) {
        ApiValidationError response = SpringExceptionConverter.buildValidationError(exc);
        return exceptionResponseBuilder.getAcceptableResponse(HttpStatus.BAD_REQUEST, response, request);
    }

    @ExceptionHandler
    public ResponseEntity<?> handleMethodArgumentTypeMismatchException(MethodArgumentTypeMismatchException exc, HttpServletRequest request) {
        String message = "Method parameter " + exc.getName() + " is of wrong type, required type is " + exc.getRequiredType();
        return exceptionResponseBuilder.getAcceptableResponse(HttpStatus.BAD_REQUEST, ApiStatus.REQUEST_BODY_ERROR, message, request);
    }
    
    @ExceptionHandler
    public ResponseEntity<?> handleConstaintValidationException(ConstraintViolationException exc, HttpServletRequest request) {
        ApiValidationError response = SpringExceptionConverter.buildValidationError(exc);
        return exceptionResponseBuilder.getAcceptableResponse(HttpStatus.BAD_REQUEST, response, request);
    }

    @ExceptionHandler
    public ResponseEntity<?> handleMediaTypeException(HttpMediaTypeNotSupportedException exc, HttpServletRequest request) {
        return exceptionResponseBuilder.getAcceptableResponse(HttpStatus.UNSUPPORTED_MEDIA_TYPE, ApiStatus.REQUEST_BODY_ERROR, exc.getMessage(), request);
    }

    @ExceptionHandler
    public ResponseEntity<?> handleMediaTypeException(HttpMediaTypeNotAcceptableException exc, HttpServletRequest request) {
        return exceptionResponseBuilder.getAcceptableResponse(HttpStatus.NOT_ACCEPTABLE, ApiStatus.ERROR, exc.getMessage(), request);
    }

    @ExceptionHandler
    public ResponseEntity<?> handleMethodException(HttpRequestMethodNotSupportedException exc, HttpServletRequest request) {
        return exceptionResponseBuilder.getAcceptableResponse(HttpStatus.METHOD_NOT_ALLOWED, ApiStatus.ERROR, exc.getMessage(), request);
    }

    @ExceptionHandler
    public ResponseEntity<?> handleNotFoundException(NoHandlerFoundException exc, HttpServletRequest request) {
        return exceptionResponseBuilder.getAcceptableResponse(HttpStatus.NOT_FOUND, ApiStatus.ERROR, exc.getMessage(), request);
    }

    @ExceptionHandler
    public ResponseEntity<?> handleNoResourceFoundException(NoResourceFoundException exc, HttpServletRequest request) {
        return exceptionResponseBuilder.getAcceptableResponse(HttpStatus.NOT_FOUND, ApiStatus.ERROR, exc.getMessage(), request);
    }
    
    @ExceptionHandler
    public ResponseEntity<?> handleValidationException(MethodArgumentNotValidException exc, HttpServletRequest request) {
        ApiValidationError response = SpringExceptionConverter.buildValidationError(exc);
        return exceptionResponseBuilder.getAcceptableResponse(HttpStatus.BAD_REQUEST, response, request);
    }
    
    @ExceptionHandler
    public ResponseEntity<?> handleAccessDeniedException(AccessDeniedException exc, HttpServletRequest request) {
        ApiStatus status = ApiStatus.UNAUTHORIZED;
        String message = "Unauthorized";
        String details = exc.getMessage();
        return exceptionResponseBuilder.getAcceptableResponse(HttpStatus.FORBIDDEN, new ApiError(status, message, null, details), request);
    }
    
    @ExceptionHandler
    public ResponseEntity<?> handleBindException(BindException exc, HttpServletRequest request) {
         ApiValidationError response = SpringExceptionConverter.buildValidationError(exc);
         return exceptionResponseBuilder.getAcceptableResponse(HttpStatus.BAD_REQUEST, response, request);
    }

    @ExceptionHandler
    public ResponseEntity<?> handleMissingServletRequestPartException(MissingServletRequestPartException exc, HttpServletRequest request) {
        return exceptionResponseBuilder.getAcceptableResponse(HttpStatus.BAD_REQUEST, ApiStatus.REQUEST_BODY_ERROR, exc.getMessage(), request);
    }

    @ExceptionHandler
    public void handleClientAbortException(ClientAbortException exc, HttpServletRequest request) {
        logger.warn("Client connection closed request_id={}", ApiRequestObservability.requestId());
    }
    
    @ExceptionHandler
    public ResponseEntity<?> handleIOException(IOException exc, HttpServletRequest request) {
        if (isClientConnectionAbort(exc)) {
            logger.warn("Client connection closed request_id={}", ApiRequestObservability.requestId());
            return null;    // socket is closed, cannot return any response
        }
        return handleUnknownException(exc, request);
    }
    
    private boolean isClientConnectionAbort(IOException exc) {
        String message = ExceptionUtils.getRootCauseMessage(exc);
        return StringUtils.containsIgnoreCase(message, "Broken pipe") ||
               StringUtils.containsIgnoreCase(message, "Connection reset by peer");
    }
    
    @ExceptionHandler
    public ResponseEntity<?> handleException(Exception exc, HttpServletRequest request) {
        return handleUnknownException(exc, request);
    }
    
    private ResponseEntity<?> handleUnknownException(Throwable exc, HttpServletRequest request) {
        SafeExceptionLog.error(logger, "Unexpected API error", exc);
        
        // Keep technical details in the server log only; response bodies must not expose exception classes or messages.
        return exceptionResponseBuilder.getAcceptableResponse(HttpStatus.INTERNAL_SERVER_ERROR, ApiStatus.ERROR,
                INTERNAL_SERVER_ERROR_MESSAGE, request);
    }
    
    private void warnOrError(HttpStatus status, String category, ApiStatus apiStatus) {
        if (status == null || status.is5xxServerError()) {
            logger.error("API error category={} status={} api_status={} request_id={}", category, status, apiStatus, ApiRequestObservability.requestId());
        } else {
            logger.warn("API error category={} status={} api_status={} request_id={}", category, status, apiStatus, ApiRequestObservability.requestId());
        }
    }
}
