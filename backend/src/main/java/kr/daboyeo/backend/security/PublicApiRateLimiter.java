package kr.daboyeo.backend.security;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;
import kr.daboyeo.backend.config.PortfolioSecurityProperties;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
public class PublicApiRateLimiter {

    private static final int MAX_KEY_PART_LENGTH = 96;
    private static final int MAX_BUCKETS = 8_192;
    private static final Set<String> LOOPBACK_ADDRESSES = Set.of("127.0.0.1", "0:0:0:0:0:0:0:1", "::1");

    private final PortfolioSecurityProperties properties;
    private final ConcurrentMap<String, AtomicInteger> buckets = new ConcurrentHashMap<>();
    private volatile long lastCleanupWindow = -1;

    public PublicApiRateLimiter(PortfolioSecurityProperties properties) {
        this.properties = properties;
    }

    public void requireRecommendationAllowed(HttpServletRequest request, String anonymousId) {
        requireAllowed("recommendation", request, anonymousId, properties.recommendationRateLimitPerMinute());
    }

    public void requireSessionAllowed(HttpServletRequest request, String anonymousId) {
        requireAllowed("session", request, anonymousId, properties.sessionRateLimitPerMinute());
    }

    public void requireFeedbackAllowed(HttpServletRequest request, String anonymousId) {
        requireAllowed("feedback", request, anonymousId, properties.feedbackRateLimitPerMinute());
    }

    public void requireNearbyAllowed(HttpServletRequest request) {
        requireAllowed("nearby", request, "", properties.nearbyRefreshRateLimitPerMinute());
    }

    private void requireAllowed(
        String bucketName,
        HttpServletRequest request,
        String anonymousId,
        int limit
    ) {
        long window = System.currentTimeMillis() / 60_000L;
        cleanupOldBuckets(window);
        String clientKey = clientKey(request);
        incrementOrReject(bucketName + ":" + window + ":" + clientKey, limit);

        String sessionKey = cleanPart(anonymousId);
        if (!sessionKey.isBlank()) {
            incrementOrReject(bucketName + ":session:" + window + ":" + clientKey + ":" + sessionKey, limit);
        }
    }

    private void incrementOrReject(String key, int limit) {
        int count = buckets.computeIfAbsent(key, ignored -> new AtomicInteger()).incrementAndGet();
        if (count > limit) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "요청이 너무 많아. 잠시 후 다시 시도해줘.");
        }
    }

    private void cleanupOldBuckets(long currentWindow) {
        if (lastCleanupWindow == currentWindow && buckets.size() <= MAX_BUCKETS) {
            return;
        }
        lastCleanupWindow = currentWindow;
        String currentMarker = ":" + currentWindow + ":";
        String previousMarker = ":" + (currentWindow - 1) + ":";
        buckets.keySet().removeIf(key -> !key.contains(currentMarker) && !key.contains(previousMarker));
    }

    private String clientKey(HttpServletRequest request) {
        if (request == null) {
            return "unknown";
        }
        String remoteAddress = cleanPart(request.getRemoteAddr());
        String forwarded = firstForwardedAddress(request.getHeader("X-Forwarded-For"));
        if (!forwarded.isBlank() && LOOPBACK_ADDRESSES.contains(remoteAddress)) {
            return remoteAddress + "|" + forwarded;
        }
        return remoteAddress.isBlank() ? "unknown" : remoteAddress;
    }

    private String firstForwardedAddress(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        return cleanPart(value.split(",", 2)[0]);
    }

    private String cleanPart(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        String cleaned = value.trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9.:@|_-]", "_");
        if (cleaned.length() > MAX_KEY_PART_LENGTH) {
            return cleaned.substring(0, MAX_KEY_PART_LENGTH);
        }
        return cleaned;
    }
}
