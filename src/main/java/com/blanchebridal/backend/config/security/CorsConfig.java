package com.blanchebridal.backend.config.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
public class CorsConfig implements WebMvcConfigurer {

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOrigins(
                        "http://localhost:3000",
                        "http://localhost:3001",
                        "https://c16a-112-134-170-216.ngrok-free.app/"
                )
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(true)
                .maxAge(3600);

        registry.addMapping("/actuator/**")          // ← ADDED
                .allowedOrigins(
                        "http://localhost:3000",
                        "http://localhost:3001"
                )
                .allowedMethods("GET", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(true)
                .maxAge(3600);
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration apiConfig = new CorsConfiguration();
        apiConfig.setAllowedOrigins(List.of(
                "http://localhost:3000",
                "https://5c35-112-134-170-216.ngrok-free.app"
        ));
        apiConfig.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        apiConfig.setAllowedHeaders(List.of("*"));
        apiConfig.setAllowCredentials(true);
        apiConfig.setMaxAge(3600L);

        CorsConfiguration actuatorConfig = new CorsConfiguration();  // ← ADDED
        actuatorConfig.setAllowedOrigins(List.of("http://localhost:3000"));
        actuatorConfig.setAllowedMethods(List.of("GET", "OPTIONS"));
        actuatorConfig.setAllowedHeaders(List.of("*"));
        actuatorConfig.setAllowCredentials(true);
        actuatorConfig.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", apiConfig);
        source.registerCorsConfiguration("/actuator/**", actuatorConfig);  // ← ADDED

        return source;
    }
}