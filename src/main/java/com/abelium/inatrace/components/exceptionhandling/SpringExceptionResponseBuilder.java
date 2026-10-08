package com.abelium.inatrace.components.exceptionhandling;

import com.abelium.inatrace.api.ApiStatus;
import com.abelium.inatrace.api.errors.ApiError;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.http.server.ServletServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.MimeTypeUtils;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

@Lazy
@Component
public class SpringExceptionResponseBuilder {

    private static final MediaType TEXT_ANY = MediaType.valueOf("text/*");
    
    @Autowired
    private MappingJackson2HttpMessageConverter springJacksonConverter;
    
    public ResponseEntity<?> getAcceptableResponse(HttpStatus httpStatus, ApiStatus apiStatus, String message, HttpServletRequest request) {
        return getAcceptableResponse(httpStatus, new ApiError(apiStatus, message), message, request);
    }
    
    public ResponseEntity<?> getAcceptableResponse(HttpStatus httpStatus, ApiError response, HttpServletRequest request) {
        String message = response != null ? response.getErrorMessage() : "<error>";
        return getAcceptableResponse(httpStatus, response, message, request);
    }
    
    private ResponseEntity<?> getAcceptableResponse(HttpStatus httpStatus, ApiError response, String message, HttpServletRequest request) {
        List<MediaType> acceptedMediaTypes = parseMediaTypes(request.getHeader(HttpHeaders.ACCEPT));
        ErrorResponseType responseType = negotiateErrorResponseType(acceptedMediaTypes);
        if (responseType == ErrorResponseType.JSON) {
            return ResponseEntity.status(httpStatus).contentType(MediaType.APPLICATION_JSON).body(response);
        }
        if (responseType == ErrorResponseType.TEXT) {
            return ResponseEntity.status(httpStatus).contentType(MediaType.TEXT_PLAIN).body(message);
        }
        return ResponseEntity.status(httpStatus).build(); // return empty body
    }
    
    public ResponseEntity<String> getAcceptableResponse(HttpStatus httpStatus, String message, HttpServletRequest request) {
        if (message != null) {
            List<MediaType> acceptedMediaTypes = parseMediaTypes(request.getHeader(HttpHeaders.ACCEPT));
            // if any text response is accepted, we return status message 
            if (anyTypeIsIncluded(acceptedMediaTypes, TEXT_ANY) ) {
                return new ResponseEntity<>(message, httpStatus);
            }
        }
        return new ResponseEntity<>((String) null, httpStatus);
    }

    // for non-spring responses
    public void writeAcceptableResponse(Object responseObj, HttpServletRequest request, HttpServletResponse response) throws IOException {
        List<MediaType> accepted = parseMediaTypes(request.getHeader(HttpHeaders.ACCEPT));
        if (anyTypeIncludes(accepted, MediaType.APPLICATION_JSON)) {
            springJacksonConverter.write(responseObj, MediaType.APPLICATION_JSON, new ServletServerHttpResponse(response));
        } else {
            // TODO: correctly negotiate response content type
            MimeTypeUtils.sortBySpecificity(accepted);
            if (!accepted.isEmpty() && !accepted.get(0).isWildcardType()) {
                response.setHeader(HttpHeaders.CONTENT_TYPE, accepted.get(0).toString());
            }
            response.getOutputStream().flush();
        }
    }

    private List<MediaType> parseMediaTypes(String str) {
        return str != null ? MediaType.parseMediaTypes(str) : Collections.emptyList();
    }

    /**
     * Error responses are JSON by default. Browsers and API clients commonly omit {@code Accept},
     * and returning only an HTTP status in that case discards the {@link ApiError} assembled by
     * the exception handler.
     */
    private ErrorResponseType negotiateErrorResponseType(List<MediaType> acceptedMediaTypes) {
        if (acceptedMediaTypes.isEmpty()) {
            return ErrorResponseType.JSON;
        }

        // This is an API error response, so prefer JSON whenever the client permits it.
        if (anyTypeIncludes(acceptedMediaTypes, MediaType.APPLICATION_JSON)
                && !isExplicitlyRejected(acceptedMediaTypes, MediaType.APPLICATION_JSON)) {
            return ErrorResponseType.JSON;
        }
        if (anyTypeIsIncluded(acceptedMediaTypes, TEXT_ANY)
                && !isExplicitlyRejected(acceptedMediaTypes, MediaType.TEXT_PLAIN)) {
            return ErrorResponseType.TEXT;
        }
        return ErrorResponseType.EMPTY;
    }

    private boolean isExplicitlyRejected(List<MediaType> acceptedMediaTypes, MediaType supportedMediaType) {
        return acceptedMediaTypes.stream()
                .anyMatch(accepted -> accepted.getQualityValue() == 0 && accepted.includes(supportedMediaType));
    }

    private enum ErrorResponseType {
        JSON,
        TEXT,
        EMPTY
    }
    
	//    private List<MediaType> parseMediaTypes(String[] strs) {
	//        return strs != null ? MediaType.parseMediaTypes(Arrays.asList(strs)) : Collections.emptyList();
	//    }
    
    private boolean anyTypeIncludes(List<MediaType> accepted, MediaType... types) {
        for (MediaType acc : accepted) {
            for (MediaType mt : types) {
                if (acc.includes(mt)) return true;
            }
        }
        return false;
    }
    
    private boolean anyTypeIsIncluded(List<MediaType> accepted, MediaType... types) {
        for (MediaType acc : accepted) {
            for (MediaType mt : types) {
                if (mt.includes(acc)) return true;
            }
        }
        return false;
    }
    
	//    private boolean producesAnyOf(HttpServletRequest request, MediaType... types) {
	//        RequestMapping mapping = getHandlerMethodAnnotation(request, RequestMapping.class);
	//        if (mapping != null) {
	//            List<MediaType> producedTypes = parseMediaTypes(mapping.produces());
	//            if (producedTypes.size() == 0 || anyTypeIsIncluded(producedTypes, types)) {
	//                return true;
	//            }
	//        }
	//        return false;
	//    }
    
	//    private <A extends Annotation> A getHandlerMethodAnnotation(HttpServletRequest request, Class<A> cls) {
	//        try {
	//            HandlerExecutionChain chain = handlerMapping.getHandler(request);
	//            if (chain.getHandler() instanceof HandlerMethod) {
	//                HandlerMethod method = (HandlerMethod) chain.getHandler();
	//                return method.getMethodAnnotation(cls);
	//            }
	//        } catch (Exception e) {
	//            // it's ok - will happen when wrong path or wrong method is requested 
	//        }
	//        return null;
	//    }
}
