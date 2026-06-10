package com.microshop.recommendation.config;

import feign.Logger;
import feign.codec.ErrorDecoder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class FeignConfig {

    /** Log solo en nivel debug para no saturar consola en prod */
    @Bean
    public Logger.Level feignLoggerLevel() {
        return Logger.Level.BASIC;
    }

    /** Devuelve excepción limpia cuando el catalog-service retorna 4xx/5xx */
    @Bean
    public ErrorDecoder errorDecoder() {
        return (methodKey, response) -> {
            if (response.status() == 404) {
                return new com.microshop.recommendation.exception.ResourceNotFoundException(
                    "Producto no encontrado en catalog-service");
            }
            return new RuntimeException(
                "catalog-service respondió con status " + response.status());
        };
    }
}
