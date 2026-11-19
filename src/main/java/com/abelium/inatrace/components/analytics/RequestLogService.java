package com.abelium.inatrace.components.analytics;

import com.abelium.inatrace.api.ApiStatus;
import com.abelium.inatrace.api.errors.ApiException;
import com.abelium.inatrace.components.analytics.api.ApiLogRequest;
import com.abelium.inatrace.components.common.BaseService;
import com.abelium.inatrace.components.transactions.AbTransactionTemplate;
import com.abelium.inatrace.db.entities.analytics.RequestGeoLocation;
import com.abelium.inatrace.db.entities.analytics.RequestLog;
import com.abelium.inatrace.types.RequestLogType;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.cfg.DateTimeFeature;
import tools.jackson.databind.json.JsonMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;

import jakarta.servlet.http.HttpServletRequest;
import java.util.concurrent.CompletableFuture;

//import org.springframework.web.client.RestTemplate;

@Lazy
@Service
public class RequestLogService extends BaseService {
	
	@Autowired
	private AbTransactionTemplate transactionTemplate;
	
	@Autowired 
	private IpGeoLocationEngine ipGeoLocationEngine;
	
    @Value("${INATrace.requestLog.token}")
    private String requestLogToken;

	
	//	@Autowired
	//	private RestTemplate restTemplate;
	
	//	private final boolean LOG_TO_HOG = true;

	private ObjectMapper objectMapper = createObjectMapper();	
	
	public void log(HttpServletRequest servletRequest, RequestLogType type, String logKey , Object value1, Object value2) {
		CompletableFuture.runAsync(() -> logAsync(getClientIP(servletRequest), type, logKey, value1, value2));
	}
	
	public void log(HttpServletRequest servletRequest, ApiLogRequest request) throws ApiException {
		if (!requestLogToken.equals(request.token)) {
			throw new ApiException(ApiStatus.UNAUTHORIZED, "Forbidden");
		}
		CompletableFuture.runAsync(() -> logAsync(getClientIP(servletRequest), request.type, request.logKey, request.value1, request.value2));
	}
	
	private void logAsync(String ip, RequestLogType type, String logKey, Object value1, Object value2) {
		//		if (LOG_TO_HOG) {
		//			logToHog(ip, type, data, request, response);
		//		} else {
		RequestGeoLocation geoLocation = ipGeoLocationEngine.getGeoLocation(ip);
		
		transactionTemplate.executeVoidWithoutExceptions(() -> {
			RequestLog logEntry = new RequestLog(ip, type, logKey, geoLocation);
			logEntry.setValue1(convertToJson(value1));
			logEntry.setValue2(convertToJson(value2));
			em.persist(logEntry);
		});
		//	}
	}

	//    private void logToHog(String ip, RequestLogType type, String data, Object request, Object response) {
	//    	ApiHogRequest req = new ApiHogRequest("VzM-fZ8tIjuXNwgM7svguhrWkpsmqw6C2p9QWnirq0c", type.toString(), 
	//    			new ApiHogRequestProperties(data, "", ""));
	//		restTemplate.postForEntity("http://localhost:8000/capture", req, null);
	//	}

	public static String getClientIP(HttpServletRequest request) {
        String xfHeader = request.getHeader("X-Forwarded-For");
        
        if (xfHeader == null) return request.getRemoteAddr();
        return xfHeader.split(",")[0].trim();
    }
    
    private String convertToJson(Object o) {
    	if (o == null) {
    		return null;
    	} else if (o instanceof CharSequence) {
    		return o.toString();
    	} else {
			try {
				return objectMapper.writeValueAsString(o);
			} catch (JacksonException e) {
				return null;
			}
    	}
    }
    
    private static ObjectMapper createObjectMapper() {
        return JsonMapper.builder().disable(DateTimeFeature.WRITE_DATES_AS_TIMESTAMPS).build();
    }

}
