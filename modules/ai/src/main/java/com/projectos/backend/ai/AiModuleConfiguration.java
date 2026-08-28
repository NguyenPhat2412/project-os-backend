package com.projectos.backend.ai;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
@EnableConfigurationProperties(AiProperties.class)
public class AiModuleConfiguration {
    @Bean
    RestClient aiRestClient(AiProperties properties) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(properties.connectTimeout());
        requestFactory.setReadTimeout(properties.readTimeout());
        return RestClient.builder().requestFactory(requestFactory).baseUrl(properties.url()).build();
    }

    @Bean
    NineRouterClient nineRouterClient(RestClient aiRestClient, AiProperties properties) {
        return new NineRouterClient(aiRestClient, properties);
    }
}
