package com.abelium.inatrace.api;

import java.lang.reflect.Type;

import org.springframework.http.HttpStatus;

/**
 * <p>Api statuses to be returned in the status field of api response.
 * Every status has associated http status, that will be set for {@code mobile}, {@code api} and {@code service} routes.
 * For {@code internal} routes, http status will always be {@code 200 OK}.</p>
 * <p>Http statuses to be used are {@code 400 BAD_REQUEST} for errors in request data and 
 * {@code 500 INTERNAL_SERVER_ERROR} for errors in server processing or invalid server state.
 * (For authentication services only, 401 UNAUTHORIZED and 403 FORBIDDEN are also used.)</p>
 */
public enum ApiStatus {
    /**
     * The only response status for successful responses.
     */
    OK(HttpStatus.OK),
    
    /**
     * Generic (unknown) error. You should create special error status code instead.
     */
    ERROR(HttpStatus.INTERNAL_SERVER_ERROR),

    /**
     * Request is in invalid format (e.g. for image) or parsable (for JSON and XML).
     */
    REQUEST_BODY_ERROR(HttpStatus.BAD_REQUEST),
    
    /**
     * Request has invalid fields or invalid field values. 
     */
    VALIDATION_ERROR(HttpStatus.BAD_REQUEST),

    /**
     * The company has no product association required for this operation.
     */
    PRODUCT_ASSOCIATION_REQUIRED(HttpStatus.BAD_REQUEST),

    /**
     * The request lost a concurrent write race and was safely rolled back.
     */
    CONFLICT(HttpStatus.CONFLICT),

    /**
     * rate limiting (e.g. for login) exceeded
     */
    TOO_MANY_REQUESTS(HttpStatus.TOO_MANY_REQUESTS),
    
    /**
     * unauthorized- if token ok, but user does not have required role/permission or deactivated user tries to login
     */
    UNAUTHORIZED(HttpStatus.FORBIDDEN),
    
    /**
     * authentication error - invalid or expired temporary token
     */
    AUTH_ERROR(HttpStatus.UNAUTHORIZED),
    
    /**
     * when calling another service, if the service is unavailable or cannot respond for some reason (server down or network error),
     * this status is set in ApiUpstreamServiceException; when receiving this error, it makes sense for the client to retry later 
     */
    UPSTREAM_HTTP_ERROR(HttpStatus.SERVICE_UNAVAILABLE),
    
    /**
     * Request is invalid 
     */
    INVALID_REQUEST(HttpStatus.BAD_REQUEST),
    
    /**
     * Storage key (for documents) is invalid or expired 
     */
    INVALID_OR_EXPIRED_STORAGE_KEY(HttpStatus.BAD_REQUEST),

    /**
     * not implemented error
     */
    NOT_IMPLEMENTED(HttpStatus.NOT_IMPLEMENTED),

    /**
     * Not found,
     */
    NOT_FOUND(HttpStatus.NOT_FOUND),
    ;
    
    private final HttpStatus httpStatus;
    private final Type errorDetailsType;

    ApiStatus(HttpStatus httpStatus) {
        this(httpStatus, Void.TYPE);
    }
    
    ApiStatus(HttpStatus httpStatus, Type errorDetailsType) {
        this.httpStatus = httpStatus;
        this.errorDetailsType = errorDetailsType;
    }

    public Type getErrorDetailsType() {
        return errorDetailsType;
    }

    public HttpStatus getHttpStatus() {
        return httpStatus;
    }
}
