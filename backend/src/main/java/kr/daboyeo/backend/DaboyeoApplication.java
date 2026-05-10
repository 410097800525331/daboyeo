package kr.daboyeo.backend;

import kr.daboyeo.backend.config.BackendCorsProperties;
import kr.daboyeo.backend.config.CollectorSyncProperties;
import kr.daboyeo.backend.config.PortfolioSecurityProperties;
import kr.daboyeo.backend.config.RecommendationProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication
@EnableConfigurationProperties({
    RecommendationProperties.class,
    BackendCorsProperties.class,
    CollectorSyncProperties.class,
    PortfolioSecurityProperties.class
})
public class DaboyeoApplication {

    public static void main(String[] args) {
        SpringApplication.run(DaboyeoApplication.class, args);
    }
}
