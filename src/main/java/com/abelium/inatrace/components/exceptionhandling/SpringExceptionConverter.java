package com.abelium.inatrace.components.exceptionhandling;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;

import org.springframework.core.MethodParameter;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.validation.ObjectError;
import org.springframework.web.bind.MethodArgumentNotValidException;

import com.abelium.inatrace.api.errors.validation.ApiValidationError;
import com.abelium.inatrace.configuration.SafeExceptionLog;
import com.fasterxml.jackson.databind.JsonMappingException;

public class SpringExceptionConverter {
	
    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(SpringExceptionConverter.class);

    public static ApiValidationError buildValidationError(JsonMappingException exc) {
        String path = SpringExceptionConverter.buildPathReference(exc.getPath());
        ApiValidationError error = new ApiValidationError();
        error.getValidationErrorDetails().setClassName(SpringExceptionConverter.buildObjectType(exc.getPath(), "?")); 
        error.getValidationErrorDetails().getFieldErrors().put(path, exc.getOriginalMessage());
        return error;
    }
    
    private static String buildObjectType(List<JsonMappingException.Reference> path, String defaultValue) {
        if ( path == null || path.isEmpty() )
            return defaultValue;
        Object obj = path.get(0).getFrom();
        if ( obj != null )
            return obj.getClass().getSimpleName();
        return defaultValue;
    }
    
    private static String buildPathReference(List<JsonMappingException.Reference> path) {
        if ( path == null )
            return "";
        StringBuilder sb = new StringBuilder();
        for ( JsonMappingException.Reference part : path ) {
            if ( part.getFieldName() != null ) {
                if ( sb.length() > 0 )
                    sb.append('.');
                sb.append(part.getFieldName());
            } else {
                sb.append('[').append(part.getIndex()).append(']');
            }
        }
        return sb.toString();
    }

    public static ApiValidationError buildValidationError(MethodArgumentNotValidException exc) {
        try {
            Map<String, String> fieldErrors = createFieldErrors(exc.getBindingResult().getAllErrors(), exc.getParameter());
            return new ApiValidationError(ApiValidationError.DEFAULT_MESSAGE, null, fieldErrors);
        } catch ( Exception e ) {
            SafeExceptionLog.error(logger, "Problem formatting validation error message", e);
            return new ApiValidationError();
        }
    }

    public static ApiValidationError buildValidationError(ConstraintViolationException exc) {
        Set<ConstraintViolation<?>> violations = exc.getConstraintViolations();
        ApiValidationError response = new ApiValidationError();
        for (ConstraintViolation<?> violation : violations ) {
            String[] path = violation.getPropertyPath().toString().split("\\.", 3);
            String parameter = path.length > 0 ? path[path.length - 1] : "";     // last part is parameter name or field name
            response.setErrorMessage(path.length >= 3 ? "Invalid field(s) in request" : "Invalid parameters(s) in request"); 
            response.getValidationErrorDetails().getFieldErrors().put(parameter, violation.getMessage());
        }
        return response;
    }

    public static ApiValidationError buildValidationError(BindException exc) {
        try {
            Map<String, String> fieldErrors = createFieldErrors(exc.getBindingResult().getAllErrors(), null);
            return new ApiValidationError(ApiValidationError.DEFAULT_MESSAGE, null, fieldErrors);
        } catch ( Exception e ) {
            SafeExceptionLog.error(logger, "Problem formatting validation error message", e);
            return new ApiValidationError();
        }
    }
    
    private static Map<String, String> createFieldErrors(List<ObjectError> errors, MethodParameter param) {
        Map<String, String> fieldErrors = new LinkedHashMap<String, String>();
        String parameterName = getParameterName(param);
        for (ObjectError error : errors) {
            if (error instanceof FieldError) {
                FieldError flderr = (FieldError) error;
                fieldErrors.put(flderr.getField(), error.getDefaultMessage());
            } else if (parameterName != null) {
                fieldErrors.put(parameterName, error.getDefaultMessage());
            }
        }
        return fieldErrors;
    }

    private static String getParameterName(MethodParameter param) {
        if (param == null) {
            return null;
        } else if (param.getParameterName() == null) {
            return "arg_" + param.getParameterIndex();
        } else {
            return param.getParameterName();
        }
    }
} 
