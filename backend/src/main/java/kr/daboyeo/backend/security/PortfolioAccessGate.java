package kr.daboyeo.backend.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import kr.daboyeo.backend.config.PortfolioSecurityProperties;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
public class PortfolioAccessGate {

    public static final String ADMIN_TOKEN_HEADER = "X-DABOYEO-ADMIN-TOKEN";

    private final PortfolioSecurityProperties properties;

    public PortfolioAccessGate(PortfolioSecurityProperties properties) {
        this.properties = properties;
    }

    public void requireCollectionAccess(String token) {
        if (properties.publicCollectionEnabled()) {
            return;
        }
        requireAdminToken(token);
    }

    public void requireSeatLayoutAccess(String token) {
        if (properties.publicSeatLayoutEnabled()) {
            return;
        }
        requireAdminToken(token);
    }

    public boolean publicNearbyRefreshEnabled() {
        return properties.publicNearbyRefreshEnabled();
    }

    private void requireAdminToken(String token) {
        if (properties.adminToken().isBlank() || token == null || token.isBlank()) {
            throw notFound();
        }
        byte[] expected = properties.adminToken().getBytes(StandardCharsets.UTF_8);
        byte[] actual = token.trim().getBytes(StandardCharsets.UTF_8);
        if (!MessageDigest.isEqual(expected, actual)) {
            throw notFound();
        }
    }

    private static ResponseStatusException notFound() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND);
    }
}
