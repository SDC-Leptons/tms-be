// src/main/java/com/example/transformer_app/config/RestTemplateConfig.java
package com.example.transformer_app.config;

// Make sure you have these exact import statements
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

@Configuration
public class RestTemplateConfig {

    @Bean
    public RestTemplate restTemplate() {
        // This is a more robust way to create the RestTemplate for PATCH support.
        // It explicitly creates an Apache HttpClient and injects it into Spring's factory.
        CloseableHttpClient httpClient = HttpClients.createDefault();
        HttpComponentsClientHttpRequestFactory requestFactory = new HttpComponentsClientHttpRequestFactory(httpClient);

        return new RestTemplate(requestFactory);
    }
}