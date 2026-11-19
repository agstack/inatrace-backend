package com.abelium.inatrace;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import com.abelium.inatrace.components.flyway.JpaMigration;

import java.util.TimeZone;

@SpringBootApplication
@ComponentScan(basePackageClasses = { INATraceBackendApplication.class }, 
	excludeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = JpaMigration.class))
public class INATraceBackendApplication {

	public static void main(String[] args) {
		// Establish UTC before Spring creates the datasource and JDBC components.
		TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
		SpringApplication.run(INATraceBackendApplication.class, args);
	}
}
