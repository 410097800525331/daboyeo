package kr.daboyeo.backend.config;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "daboyeo.cors")
public record BackendCorsProperties(List<String> allowedOrigins) {

    public BackendCorsProperties {
        allowedOrigins = allowedOrigins == null
                ? List.of(
                    "http://localhost:4173",
                    "http://127.0.0.1:4173",
                    "http://localhost:5173",
                    "http://127.0.0.1:5173",
                    "http://localhost:5500",
                    "http://127.0.0.1:5500"
                )
                : List.copyOf(allowedOrigins);
    }
}
