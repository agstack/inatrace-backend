package com.abelium.inatrace.configuration;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.http.HttpMessageConverters;
import org.springframework.boot.autoconfigure.web.client.RestTemplateAutoConfiguration;
import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.ClientHttpRequestFactorySettings;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.boot.web.client.RestTemplateCustomizer;
import org.springframework.boot.web.client.RestTemplateRequestCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.client.BufferingClientHttpRequestFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

@Configuration
public class RestTemplateConfig {

    @Autowired
    private RestTemplateAutoConfiguration restTemplateConfiguration;

    @Autowired
    private ObjectProvider<ClientHttpRequestFactoryBuilder<?>> clientHttpRequestFactoryBuilders;

    @Autowired
    private ObjectProvider<ClientHttpRequestFactorySettings> clientHttpRequestFactorySettings;

    @Autowired
    private ObjectProvider<HttpMessageConverters> messageConverters;
    
    @Autowired
    private ObjectProvider<RestTemplateCustomizer> restTemplateCustomizers;
    
    @Autowired
    private ObjectProvider<RestTemplateRequestCustomizer<?>> restTemplateRequestCustomizers;

    // replace Spring's RestTemplateBuilder because it allows only 5 concurrent requests
    @Bean
    public RestTemplateBuilder restTemplateBuilder() {

        return restTemplateConfiguration.restTemplateBuilderConfigurer(clientHttpRequestFactoryBuilders,
                clientHttpRequestFactorySettings, messageConverters, restTemplateCustomizers,
                restTemplateRequestCustomizers).configure(new RestTemplateBuilder()).requestFactory(
                        () -> new BufferingClientHttpRequestFactory(new SimpleClientHttpRequestFactory()));
    }

    @Lazy
    @Bean
    public RestTemplate restTemplate() {
        return restTemplateBuilder().build();
    }
}
