package kr.daboyeo.backend.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(CollectorSyncProperties.class)
public class SyncConfig {
}
