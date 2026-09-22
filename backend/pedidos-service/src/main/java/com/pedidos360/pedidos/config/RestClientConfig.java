package com.pedidos360.pedidos.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
public class RestClientConfig {

    @Value("${productos.service.url}")
    private String productosServiceUrl;

    @Bean
    public RestClient productosRestClient() {
        return RestClient.builder()
                .baseUrl(productosServiceUrl)
                .build();
    }
}
