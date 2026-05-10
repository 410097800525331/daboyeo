package kr.daboyeo.backend.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@EnableConfigurationProperties(BackendCorsProperties.class)
public class WebConfig implements WebMvcConfigurer {

    private final BackendCorsProperties corsProperties;

    public WebConfig(BackendCorsProperties corsProperties) {
        this.corsProperties = corsProperties;
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
            .allowedOrigins(corsProperties.allowedOrigins().toArray(String[]::new))
            .allowedMethods("GET", "POST", "DELETE", "OPTIONS")
            .allowedHeaders("Accept", "Content-Type", "X-DABOYEO-ADMIN-TOKEN", "X-DABOYEO-BRIDGE-TOKEN")
            .maxAge(3600);
    }
}
