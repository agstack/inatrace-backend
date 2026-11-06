package com.abelium.inatrace.components.analytics;

import com.abelium.inatrace.configuration.SafeExceptionLog;

import com.abelium.inatrace.db.entities.analytics.RequestGeoLocation;
import com.maxmind.geoip2.DatabaseReader;
import com.maxmind.geoip2.exception.GeoIp2Exception;
import com.maxmind.geoip2.model.CityResponse;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.io.File;
import java.io.IOException;
import java.net.InetAddress;

@Lazy
@Component
public class IpGeoLocationEngine {
	
	protected final Logger logger = LoggerFactory.getLogger(IpGeoLocationEngine.class);	
	
    @Value("${INATrace.maxmindDB.path}")
    private String dbPath;
    
    
    private DatabaseReader reader = null;
    
	@PostConstruct
	private void postConstruct() {
		if (StringUtils.isNotBlank(dbPath)) {
	        try {
		        File database = new File(dbPath);
				reader = new DatabaseReader.Builder(database).build();
			} catch (IOException e) {
				SafeExceptionLog.error(logger, "GeoIP database cannot be created", e);
			}
		}
	}
	
	public RequestGeoLocation getGeoLocation(String ipAddress) {
		if (reader == null) return null;
		
        try {
			CityResponse dbResponse = reader.city(InetAddress.getByName(ipAddress));
			RequestGeoLocation result = new RequestGeoLocation();
			
			result.setCity(dbResponse.getCity() == null ? null : dbResponse.getCity().getName());
			result.setCountry(dbResponse.getCountry() == null ? null : dbResponse.getCountry().getName());
			if (dbResponse.getLocation() != null) {
				result.setLatitude(dbResponse.getLocation().getLatitude());
				result.setLongitude(dbResponse.getLocation().getLongitude());
			}
			return result;
		} catch (IOException | GeoIp2Exception e) {
			// TODO: needed? too much logs
			logger.debug("Cannot resolve client location");
			return null;
		}
	}

}
