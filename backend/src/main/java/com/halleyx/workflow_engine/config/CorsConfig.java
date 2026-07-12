package com.halleyx.workflow_engine.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * CorsConfig — production-ready CORS configuration.
 *
 * IMPROVEMENTS vs original:
 * - Allowed origins driven by ${app.cors.allowed-origins} environment variable.
 * - Defaults to localhost:* for development; production sets the env var.
 * - @CrossOrigin on each controller uses "*" as a defence-in-depth fallback only;
 *   this bean is the authoritative CORS policy.
 * - exposedHeaders includes "X-Total-Count" for pagination metadata consumers.
 */
@Configuration
public class CorsConfig {

    @Value("${app.cors.allowed-origins:http://localhost:*,http://127.0.0.1:*}")
    private String[] allowedOrigins;

    @Bean
    public WebMvcConfigurer corsConfigurer() {
        return new WebMvcConfigurer() {
            @Override
            public void addCorsMappings(CorsRegistry registry) {
                registry.addMapping("/**")
                        .allowedOriginPatterns(allowedOrigins)
                        .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH")
                        .allowedHeaders("*")
                        .exposedHeaders("X-Total-Count")
                        .allowCredentials(false)
                        .maxAge(3600);
            }
        };
    }
}
