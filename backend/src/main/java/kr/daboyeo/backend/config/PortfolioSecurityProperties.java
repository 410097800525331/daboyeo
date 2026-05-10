package kr.daboyeo.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "daboyeo.security")
public record PortfolioSecurityProperties(
    String adminToken,
    boolean publicCollectionEnabled,
    boolean publicNearbyRefreshEnabled,
    boolean publicSeatLayoutEnabled
) {

    public PortfolioSecurityProperties {
        adminToken = adminToken == null ? "" : adminToken.trim();
    }
}
