package kr.daboyeo.backend.config;

import java.util.List;
import kr.daboyeo.backend.domain.recommendation.RecommendationModels.AiProvider;
import kr.daboyeo.backend.domain.recommendation.RecommendationModels.RecommendationMode;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.ConstructorBinding;

@ConfigurationProperties(prefix = "daboyeo.recommendation")
public record RecommendationProperties(
    String codexModel,
    Integer codexFastAiCandidateLimit,
    Integer codexPreciseAiCandidateLimit,
    Integer codexFastMaxTokens,
    Integer codexPreciseMaxTokens,
    String bridgeToken,
    Integer bridgeResultTimeoutSeconds,
    Integer bridgeHeartbeatTtlSeconds,
    Integer bridgeJobTtlSeconds,
    Integer minStartBufferMinutes,
    List<String> frontendOrigins
) {

    public RecommendationProperties(
        Integer minStartBufferMinutes,
        Integer codexFastAiCandidateLimit,
        Integer codexPreciseAiCandidateLimit,
        Integer codexFastMaxTokens,
        Integer codexPreciseMaxTokens,
        List<String> frontendOrigins
    ) {
        this(
            null,
            codexFastAiCandidateLimit,
            codexPreciseAiCandidateLimit,
            codexFastMaxTokens,
            codexPreciseMaxTokens,
            null,
            null,
            null,
            null,
            minStartBufferMinutes,
            frontendOrigins
        );
    }

    @ConstructorBinding
    public RecommendationProperties {
        codexModel = defaultString(codexModel, "codex");
        codexFastAiCandidateLimit = clamp(codexFastAiCandidateLimit, 6, 3, 24);
        codexPreciseAiCandidateLimit = clamp(codexPreciseAiCandidateLimit, 20, 3, 30);
        codexFastMaxTokens = clamp(codexFastMaxTokens, 650, 420, 1400);
        codexPreciseMaxTokens = clamp(codexPreciseMaxTokens, 1700, 1000, 2400);
        bridgeToken = defaultString(bridgeToken, "");
        bridgeResultTimeoutSeconds = clamp(bridgeResultTimeoutSeconds, 90, 5, 300);
        bridgeHeartbeatTtlSeconds = clamp(bridgeHeartbeatTtlSeconds, 45, 5, 300);
        bridgeJobTtlSeconds = clamp(bridgeJobTtlSeconds, 180, 30, 600);
        minStartBufferMinutes = minStartBufferMinutes == null ? 20 : Math.max(0, minStartBufferMinutes);
        if (frontendOrigins == null || frontendOrigins.isEmpty()) {
            frontendOrigins = List.of("http://localhost:5173", "http://127.0.0.1:5173", "http://*:5173");
        }
    }

    public String modelFor(RecommendationMode mode) {
        return codexModel;
    }

    public String modelFor(AiProvider provider, RecommendationMode mode) {
        return codexModel;
    }

    public String providerLabel(AiProvider provider) {
        return "GPT (Codex)";
    }

    public int aiCandidateLimitFor(RecommendationMode mode) {
        return mode == RecommendationMode.FAST ? codexFastAiCandidateLimit : codexPreciseAiCandidateLimit;
    }

    public int aiCandidateLimitFor(AiProvider provider, RecommendationMode mode) {
        return aiCandidateLimitFor(mode);
    }

    public int maxTokensFor(RecommendationMode mode) {
        return mode == RecommendationMode.FAST ? codexFastMaxTokens : codexPreciseMaxTokens;
    }

    public int maxTokensFor(AiProvider provider, RecommendationMode mode) {
        return maxTokensFor(mode);
    }

    public int responseTextMaxLengthFor(AiProvider provider, RecommendationMode mode) {
        return mode == RecommendationMode.FAST ? 180 : 320;
    }

    private static String defaultString(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private static int clamp(Integer value, int fallback, int min, int max) {
        int resolved = value == null ? fallback : value;
        return Math.max(min, Math.min(max, resolved));
    }
}
