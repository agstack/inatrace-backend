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
import com.abelium.inatrace.tools.SecurityTools;
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
        warnOrError(exc.getHttpStatus(), "ApiImageException: {} {} {}", exc.getHttpStatus(), exc.getHttpStatus().getReasonPhrase(), exc.getMessage());
        return ResponseEntity.status(exc.getHttpStatus()).build(); // return empty body
    }
    
    // ApiException - do not log (either a consequence of an exception that was logged before or a valid error return)
    @ExceptionHandler
    public ResponseEntity<?> handleApiException(ApiException exc, HttpServletRequest request) {
        if (exc instanceof ApiUpstreamServiceException) {
        	logAndUpdate(exc, request);
        } else {
            warnOrError(exc.getHttpStatus(), "ApiException: {} {}", exc.getApiStatus(), exc.getMessage());
        }
        return exceptionResponseBuilder.getAcceptableResponse(exc.responseHttpStatus(), exc.createResponseBody(), request);
    }
    
    @ExceptionHandler
    public ResponseEntity<String> handleHtmlPageException(HtmlPageException exc, HttpServletRequest request) {
        warnOrError(exc.getStatus(), "HtmlPageException: {} {}", exc.getStatus(), exc.getText());
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
        updateErrorCounter();
        logger.error("Bad http request: {}", exc.getMessage());
        return exceptionResponseBuilder.getAcceptableResponse(HttpStatus.BAD_REQUEST, ApiStatus.REQUEST_BODY_ERROR, exc.getMessage(), request);
    }

    private ResponseEntity<?> handleJsonMappingException(JsonMappingException exc, HttpServletRequest request) {
        updateErrorCounter();
        logger.error(exc.getMessage());
        ApiValidationError response = SpringExceptionConverter.buildValidationError(exc);
        return exceptionResponseBuilder.getAcceptableResponse(HttpStatus.BAD_REQUEST, response, request);
    }

    @ExceptionHandler
    public ResponseEntity<?> handleMethodArgumentTypeMismatchException(MethodArgumentTypeMismatchException exc, HttpServletRequest request) {
        updateErrorCounter();
        logger.error(exc.getMessage());
        String message = "Method parameter " + exc.getName() + " is of wrong type, required type is " + exc.getRequiredType();
        return exceptionResponseBuilder.getAcceptableResponse(HttpStatus.BAD_REQUEST, ApiStatus.REQUEST_BODY_ERROR, message, request);
    }
    
    @ExceptionHandler
    public ResponseEntity<?> handleConstaintValidationException(ConstraintViolationException exc, HttpServletRequest request) {
        ApiValidationError response = SpringExceptionConverter.buildValidationError(exc);
        updateErrorCounter();
        logger.error("{} {}", response.getErrorMessage(), response.getValidationErrorDetails().getFieldErrors());
        return exceptionResponseBuilder.getAcceptableResponse(HttpStatus.BAD_REQUEST, response, request);
    }

    @ExceptionHandler
    public ResponseEntity<?> handleMediaTypeException(HttpMediaTypeNotSupportedException exc, HttpServletRequest request) {
        updateErrorCounter();
        logger.error(exc.getMessage());
        return exceptionResponseBuilder.getAcceptableResponse(HttpStatus.UNSUPPORTED_MEDIA_TYPE, ApiStatus.REQUEST_BODY_ERROR, exc.getMessage(), request);
    }

    @ExceptionHandler
    public ResponseEntity<?> handleMediaTypeException(HttpMediaTypeNotAcceptableException exc, HttpServletRequest request) {
        updateErrorCounter();
        logger.error(exc.getMessage());
        return exceptionResponseBuilder.getAcceptableResponse(HttpStatus.NOT_ACCEPTABLE, ApiStatus.ERROR, exc.getMessage(), request);
    }

    @ExceptionHandler
    public ResponseEntity<?> handleMethodException(HttpRequestMethodNotSupportedException exc, HttpServletRequest request) {
        updateErrorCounter();
        logger.error(exc.getMessage());
        return exceptionResponseBuilder.getAcceptableResponse(HttpStatus.METHOD_NOT_ALLOWED, ApiStatus.ERROR, exc.getMessage(), request);
    }

    @ExceptionHandler
    public ResponseEntity<?> handleNotFoundException(NoHandlerFoundException exc, HttpServletRequest request) {
        updateErrorCounter();
        logger.error(exc.getMessage());
        return exceptionResponseBuilder.getAcceptableResponse(HttpStatus.NOT_FOUND, ApiStatus.ERROR, exc.getMessage(), request);
    }

    @ExceptionHandler
    public ResponseEntity<?> handleNoResourceFoundException(NoResourceFoundException exc, HttpServletRequest request) {
        logger.warn("No resource found: {}", exc.getMessage());
        return exceptionResponseBuilder.getAcceptableResponse(HttpStatus.NOT_FOUND, ApiStatus.ERROR, exc.getMessage(), request);
    }
    
    @ExceptionHandler
    public ResponseEntity<?> handleValidationException(MethodArgumentNotValidException exc, HttpServletRequest request) {
        ApiValidationError response = SpringExceptionConverter.buildValidationError(exc);
        updateErrorCounter();
        logger.error("{} {}", response.getErrorMessage(), response.getValidationErrorDetails().getFieldErrors());
        return exceptionResponseBuilder.getAcceptableResponse(HttpStatus.BAD_REQUEST, response, request);
    }
    
    @ExceptionHandler
    public ResponseEntity<?> handleAccessDeniedException(AccessDeniedException exc, HttpServletRequest request) {
        ApiStatus status = ApiStatus.UNAUTHORIZED;
        String message = "Unauthorized";
        String details = exc.getMessage();
        updateErrorCounter();
        logger.error(message);
        return exceptionResponseBuilder.getAcceptableResponse(HttpStatus.FORBIDDEN, new ApiError(status, message, null, details), request);
    }
    
    @ExceptionHandler
    public ResponseEntity<?> handleBindException(BindException exc, HttpServletRequest request) {
         ApiValidationError response = SpringExceptionConverter.buildValidationError(exc);
         updateErrorCounter();
         logger.error("{} {}", response.getErrorMessage(), response.getValidationErrorDetails().getFieldErrors());
         return exceptionResponseBuilder.getAcceptableResponse(HttpStatus.BAD_REQUEST, response, request);
    }

    @ExceptionHandler
    public ResponseEntity<?> handleMissingServletRequestPartException(MissingServletRequestPartException exc, HttpServletRequest request) {
        updateErrorCounter();
        logger.error(exc.getMessage());
        return exceptionResponseBuilder.getAcceptableResponse(HttpStatus.BAD_REQUEST, ApiStatus.REQUEST_BODY_ERROR, exc.getMessage(), request);
    }

    @ExceptionHandler
    public void handleClientAbortException(ClientAbortException exc, HttpServletRequest request) {
        String message = ExceptionUtils.getRootCauseMessage(exc);
        logger.warn("Socket closed during processing ({}) in {}", message, request.getRequestURI());
    }
    
    @ExceptionHandler
    public ResponseEntity<?> handleIOException(IOException exc, HttpServletRequest request) {
        if (isClientConnectionAbort(exc)) {
            String message = ExceptionUtils.getRootCauseMessage(exc);
            logger.warn("Socket closed during processing ({}) in {}", message, request.getRequestURI());
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
    	logAndUpdate(exc, request);
        
        // Keep technical details in the server log only; response bodies must not expose exception classes or messages.
        return exceptionResponseBuilder.getAcceptableResponse(HttpStatus.INTERNAL_SERVER_ERROR, ApiStatus.ERROR,
                INTERNAL_SERVER_ERROR_MESSAGE, request);
    }
    
    private void logAndUpdate(Throwable exc, HttpServletRequest request) {
    	// log unknown exceptions 
    	logError(exc, request);
        // update error counter
        updateErrorCounter();
    }
    
    private void logError(Throwable exc, HttpServletRequest request) {
    	logger.error("IP: {}, exception: {}, message: {}", 
    			SecurityTools.getClientIP(request), 
    			exc.getClass().getName(),
    			exc.getMessage(), 
    			exc);
    }
    
    private void warnOrError(HttpStatus status, String format, Object... args) {
        if (status == null || status.is5xxServerError()) {
            updateErrorCounter();
            logger.error(format, args);
        } else {
            logger.warn(format, args);
        }
    }

    public void updateErrorCounter() {
    }
}
