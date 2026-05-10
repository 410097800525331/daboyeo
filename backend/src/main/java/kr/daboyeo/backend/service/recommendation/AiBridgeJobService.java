package kr.daboyeo.backend.service.recommendation;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import kr.daboyeo.backend.config.RecommendationProperties;
import kr.daboyeo.backend.domain.recommendation.AiBridgeModels.AiBridgeHeartbeatRequest;
import kr.daboyeo.backend.domain.recommendation.AiBridgeModels.AiBridgeJobResponse;
import kr.daboyeo.backend.domain.recommendation.AiBridgeModels.AiBridgeResultRequest;
import kr.daboyeo.backend.domain.recommendation.RecommendationModels.AiProvider;
import kr.daboyeo.backend.domain.recommendation.RecommendationModels.AiProviderStatus;
import kr.daboyeo.backend.domain.recommendation.RecommendationModels.RecommendationMode;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AiBridgeJobService {

    public static final String TOKEN_HEADER = "X-DABOYEO-BRIDGE-TOKEN";
    private static final int MAX_ERROR_LENGTH = 500;
    private final RecommendationProperties properties;
    private final ConcurrentMap<String, BridgeJob> jobs = new ConcurrentHashMap<>();
    private final ConcurrentMap<AiProvider, Heartbeat> heartbeats = new ConcurrentHashMap<>();

    public AiBridgeJobService(RecommendationProperties properties) {
        this.properties = properties;
    }

    public boolean tokenConfigured() {
        return properties.bridgeToken() != null && !properties.bridgeToken().isBlank();
    }

    public void requireAuthorized(String token) {
        if (!tokenConfigured()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "AI bridge token is not configured.");
        }
        if (token == null || !MessageDigest.isEqual(
            properties.bridgeToken().getBytes(StandardCharsets.UTF_8),
            token.getBytes(StandardCharsets.UTF_8)
        )) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid AI bridge token.");
        }
    }

    public void heartbeat(AiBridgeHeartbeatRequest request) {
        if (request == null || request.providers().isEmpty()) {
            return;
        }
        Instant now = Instant.now();
        request.providers().forEach(providerValue -> {
            AiProvider provider = safeProvider(providerValue);
            if (provider == AiProvider.CODEX) {
                heartbeats.put(provider, new Heartbeat(request.bridgeId(), now));
            }
        });
        cleanupExpiredJobs(now);
    }

    public boolean isBridgeAvailable(AiProvider provider) {
        return bridgeStatus(provider).available();
    }

    public AiProviderStatus bridgeStatus(AiProvider provider) {
        String wireValue = provider.wireValue();
        String label = properties.providerLabel(provider);
        List<String> expectedModels = properties.expectedModelsFor(provider);
        if (!tokenConfigured()) {
            return new AiProviderStatus(
                wireValue,
                label,
                expectedModels,
                false,
                "not_configured",
                "AI bridge token is not configured."
            );
        }
        Heartbeat heartbeat = heartbeats.get(provider);
        if (heartbeat == null || heartbeat.isExpired(properties.bridgeHeartbeatTtlSeconds())) {
            return new AiProviderStatus(
                wireValue,
                label,
                expectedModels,
                false,
                "offline",
                "No AI bridge worker heartbeat is active."
            );
        }
        return new AiProviderStatus(
            wireValue,
            label,
            expectedModels,
            true,
            "ready",
            "AI bridge worker is polling jobs."
        );
    }

    public Optional<AiBridgeJobResponse> claimJob(String providerValue, String bridgeId) {
        cleanupExpiredJobs(Instant.now());
        AiProvider provider = safeProvider(providerValue);
        if (provider == null) {
            return Optional.empty();
        }
        return jobs.values().stream()
            .filter(job -> job.provider() == provider)
            .filter(BridgeJob::isQueued)
            .sorted(Comparator.comparing(BridgeJob::createdAt))
            .filter(job -> job.claim(bridgeId))
            .findFirst()
            .map(BridgeJob::toResponse);
    }

    public void completeJob(String jobId, AiBridgeResultRequest request) {
        BridgeJob job = jobs.get(jobId);
        if (job == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "AI bridge job not found.");
        }
        if (request == null) {
            job.fail("Missing AI bridge result.");
            return;
        }
        if (!request.error().isBlank()) {
            job.fail(limitError(request.error()));
            return;
        }
        if (request.rawJson().isBlank()) {
            job.fail("AI bridge result is blank.");
            return;
        }
        job.complete(request.rawJson());
    }

    public Optional<String> submitAndAwait(
        AiProvider provider,
        RecommendationMode mode,
        String model,
        Map<String, Object> request
    ) {
        if (!isBridgeAvailable(provider)) {
            return Optional.empty();
        }
        BridgeJob job = new BridgeJob(provider, mode, model, request);
        jobs.put(job.jobId(), job);
        try {
            return Optional.of(job.future().get(properties.bridgeResultTimeoutSeconds(), TimeUnit.SECONDS));
        } catch (TimeoutException exception) {
            job.fail("AI bridge job timed out.");
            return Optional.empty();
        } catch (Exception exception) {
            return Optional.empty();
        } finally {
            jobs.remove(job.jobId());
        }
    }

    private void cleanupExpiredJobs(Instant now) {
        int ttl = properties.bridgeJobTtlSeconds();
        jobs.values().removeIf(job -> Duration.between(job.createdAt(), now).toSeconds() > ttl && job.expire());
    }

    private String limitError(String error) {
        if (error.length() <= MAX_ERROR_LENGTH) {
            return error;
        }
        return error.substring(0, MAX_ERROR_LENGTH);
    }

    private AiProvider safeProvider(String providerValue) {
        try {
            return AiProvider.from(providerValue);
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private record Heartbeat(String bridgeId, Instant seenAt) {
        boolean isExpired(int ttlSeconds) {
            return Duration.between(seenAt, Instant.now()).toSeconds() > ttlSeconds;
        }
    }

    private static final class BridgeJob {
        private final String jobId;
        private final AiProvider provider;
        private final RecommendationMode mode;
        private final String model;
        private final Map<String, Object> request;
        private final Instant createdAt;
        private final CompletableFuture<String> future = new CompletableFuture<>();
        private volatile String claimedBy = "";
        private volatile boolean queued = true;

        BridgeJob(AiProvider provider, RecommendationMode mode, String model, Map<String, Object> request) {
            this.jobId = "aib_" + UUID.randomUUID().toString().replace("-", "");
            this.provider = provider;
            this.mode = mode;
            this.model = model == null ? "" : model;
            this.request = request == null ? Map.of() : Map.copyOf(request);
            this.createdAt = Instant.now();
        }

        String jobId() {
            return jobId;
        }

        AiProvider provider() {
            return provider;
        }

        Instant createdAt() {
            return createdAt;
        }

        CompletableFuture<String> future() {
            return future;
        }

        boolean isQueued() {
            return queued && !future.isDone();
        }

        synchronized boolean claim(String bridgeId) {
            if (!isQueued()) {
                return false;
            }
            queued = false;
            claimedBy = bridgeId == null ? "" : bridgeId;
            return true;
        }

        void complete(String rawJson) {
            future.complete(rawJson);
        }

        void fail(String message) {
            future.completeExceptionally(new IllegalStateException(message));
        }

        boolean expire() {
            future.completeExceptionally(new TimeoutException("AI bridge job expired."));
            return true;
        }

        AiBridgeJobResponse toResponse() {
            return new AiBridgeJobResponse(
                jobId,
                provider.wireValue(),
                mode.wireValue(),
                model,
                request,
                createdAt
            );
        }
    }
}
