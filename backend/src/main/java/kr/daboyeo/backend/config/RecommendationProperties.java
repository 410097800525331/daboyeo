package kr.daboyeo.backend.config;

import java.util.List;
import kr.daboyeo.backend.domain.recommendation.RecommendationModels.AiProvider;
import kr.daboyeo.backend.domain.recommendation.RecommendationModels.RecommendationMode;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.ConstructorBinding;

@ConfigurationProperties(prefix = "daboyeo.recommendation")
public record RecommendationProperties(
    String codexModel,
    String codexFastModel,
    String codexPreciseModel,
    String codexFastReasoningEffort,
    String codexPreciseReasoningEffort,
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
            null,
            null,
            null,
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
        codexModel = defaultString(codexModel, "");
        codexFastModel = defaultString(codexFastModel, codexModel.isBlank() ? "gpt-5.4-mini" : codexModel);
        codexPreciseModel = defaultString(codexPreciseModel, codexModel.isBlank() ? "gpt-5.5" : codexModel);
        codexFastReasoningEffort = normalizeReasoningEffort(codexFastReasoningEffort, "");
        codexPreciseReasoningEffort = normalizeReasoningEffort(codexPreciseReasoningEffort, "xhigh");
        codexFastAiCandidateLimit = clamp(codexFastAiCandidateLimit, 3, 3, 24);
        codexPreciseAiCandidateLimit = clamp(codexPreciseAiCandidateLimit, 20, 3, 30);
        codexFastMaxTokens = clamp(codexFastMaxTokens, 420, 320, 1400);
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
        return mode == RecommendationMode.FAST ? codexFastModel : codexPreciseModel;
    }

    public String modelFor(AiProvider provider, RecommendationMode mode) {
        return modelFor(mode);
    }

    public String reasoningEffortFor(RecommendationMode mode) {
        return mode == RecommendationMode.FAST ? codexFastReasoningEffort : codexPreciseReasoningEffort;
    }

    public String reasoningEffortFor(AiProvider provider, RecommendationMode mode) {
        return reasoningEffortFor(mode);
    }

    public List<String> expectedModelsFor(AiProvider provider) {
        return List.of(
                modelFor(provider, RecommendationMode.FAST),
                modelFor(provider, RecommendationMode.PRECISE)
            ).stream()
            .filter(model -> model != null && !model.isBlank())
            .distinct()
            .toList();
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

    private static String normalizeReasoningEffort(String value, String fallback) {
        String resolved = defaultString(value, fallback).toLowerCase();
        return switch (resolved) {
            case "", "none", "default" -> "";
            case "minimal", "low", "medium", "high", "xhigh" -> resolved;
            default -> fallback == null ? "" : fallback;
        };
    }

    private static int clamp(Integer value, int fallback, int min, int max) {
        int resolved = value == null ? fallback : value;
        return Math.max(min, Math.min(max, resolved));
    }
}
