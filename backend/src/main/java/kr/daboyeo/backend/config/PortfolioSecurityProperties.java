package kr.daboyeo.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "daboyeo.security")
public record PortfolioSecurityProperties(
    String adminToken,
    boolean publicCollectionEnabled,
    boolean publicNearbyRefreshEnabled,
    boolean publicSeatLayoutEnabled,
    Integer recommendationRateLimitPerMinute,
    Integer sessionRateLimitPerMinute,
    Integer feedbackRateLimitPerMinute
) {

    public PortfolioSecurityProperties {
        adminToken = adminToken == null ? "" : adminToken.trim();
        recommendationRateLimitPerMinute = normalizeLimit(recommendationRateLimitPerMinute, 12);
        sessionRateLimitPerMinute = normalizeLimit(sessionRateLimitPerMinute, 30);
        feedbackRateLimitPerMinute = normalizeLimit(feedbackRateLimitPerMinute, 60);
    }

    private static int normalizeLimit(Integer value, int fallback) {
        int resolved = value == null ? fallback : value;
        return Math.max(1, Math.min(10_000, resolved));
    }
}
