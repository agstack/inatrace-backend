package com.abelium.inatrace.configuration;

import org.springframework.boot.restclient.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.client.BufferingClientHttpRequestFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

@Configuration
public class RestTemplateConfig {

    /**
     * Build the application RestTemplate on top of the autoconfigured builder, but use a
     * buffering request factory so retries/logging can read the body more than once and the
     * connection behaviour stays under application control.
     */
    @Lazy
    @Bean
    public RestTemplate restTemplate(RestTemplateBuilder builder) {
        return builder.requestFactory(
                () -> new BufferingClientHttpRequestFactory(new SimpleClientHttpRequestFactory())).build();
    }
}
