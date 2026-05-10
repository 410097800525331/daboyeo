package kr.daboyeo.backend.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import kr.daboyeo.backend.config.PortfolioSecurityProperties;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

class PortfolioAccessGateTests {

    @Test
    void collectionAccessIsBlockedByDefault() {
        PortfolioAccessGate gate = new PortfolioAccessGate(new PortfolioSecurityProperties("", false, false, false));

        assertThatThrownBy(() -> gate.requireCollectionAccess(null))
            .isInstanceOf(ResponseStatusException.class)
            .extracting(exception -> ((ResponseStatusException) exception).getStatusCode().value())
            .isEqualTo(404);
    }

    @Test
    void collectionAccessAllowsMatchingAdminToken() {
        PortfolioAccessGate gate = new PortfolioAccessGate(new PortfolioSecurityProperties("secret-token", false, false, false));

        assertThatCode(() -> gate.requireCollectionAccess("secret-token")).doesNotThrowAnyException();
        assertThatThrownBy(() -> gate.requireCollectionAccess("wrong"))
            .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void publicFlagsAllowOnlyTheirOwnSurfaces() {
        PortfolioAccessGate gate = new PortfolioAccessGate(new PortfolioSecurityProperties("", true, false, false));

        assertThatCode(() -> gate.requireCollectionAccess(null)).doesNotThrowAnyException();
        assertThatThrownBy(() -> gate.requireSeatLayoutAccess(null))
            .isInstanceOf(ResponseStatusException.class);
        assertThat(gate.publicNearbyRefreshEnabled()).isFalse();
    }
}
