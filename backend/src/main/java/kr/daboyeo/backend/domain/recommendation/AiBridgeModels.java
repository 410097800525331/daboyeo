package kr.daboyeo.backend.domain.recommendation;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public final class AiBridgeModels {

    private AiBridgeModels() {
    }

    public record AiBridgeHeartbeatRequest(
        String bridgeId,
        List<String> providers
    ) {
        public AiBridgeHeartbeatRequest {
            bridgeId = normalize(bridgeId);
            providers = providers == null ? List.of() : providers.stream()
                .map(AiBridgeModels::normalize)
                .filter(value -> !value.isBlank())
                .distinct()
                .toList();
        }
    }

    public record AiBridgeJobResponse(
        String jobId,
        String provider,
        String mode,
        String model,
        Map<String, Object> request,
        Instant createdAt
    ) {
        public AiBridgeJobResponse {
            request = request == null ? Map.of() : Map.copyOf(request);
        }
    }

    public record AiBridgeResultRequest(
        String rawJson,
        String error
    ) {
        public AiBridgeResultRequest {
            rawJson = rawJson == null ? "" : rawJson.trim();
            error = error == null ? "" : error.trim();
        }
    }

    public record AiBridgeAckResponse(boolean accepted) {
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase();
    }
}
