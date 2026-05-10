package kr.daboyeo.backend.service.recommendation;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import kr.daboyeo.backend.config.RecommendationProperties;
import kr.daboyeo.backend.domain.recommendation.RecommendationModels.AiPick;
import kr.daboyeo.backend.domain.recommendation.RecommendationModels.AiProvider;
import kr.daboyeo.backend.domain.recommendation.RecommendationModels.AiProviderStatus;
import kr.daboyeo.backend.domain.recommendation.RecommendationModels.AiResult;
import kr.daboyeo.backend.domain.recommendation.RecommendationModels.RecommendationMode;
import kr.daboyeo.backend.domain.recommendation.RecommendationModels.ScoredCandidate;
import kr.daboyeo.backend.domain.recommendation.RecommendationModels.ShowtimeCandidate;
import kr.daboyeo.backend.domain.recommendation.RecommendationModels.TagProfile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class CodexRecommendationClient {

    private static final Logger log = LoggerFactory.getLogger(CodexRecommendationClient.class);

    private final RecommendationProperties properties;
    private final ObjectMapper objectMapper;
    private final AiBridgeJobService bridgeJobService;

    @Autowired
    public CodexRecommendationClient(
        RecommendationProperties properties,
        ObjectMapper objectMapper,
        AiBridgeJobService bridgeJobService
    ) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.bridgeJobService = bridgeJobService;
    }

    CodexRecommendationClient(RecommendationProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.bridgeJobService = null;
    }

    public Optional<AiResult> rankAndExplain(
        RecommendationMode mode,
        TagProfile profile,
        List<ScoredCandidate> candidates
    ) {
        return rankAndExplain(AiProvider.CODEX, mode, profile, candidates);
    }

    public Optional<AiResult> rankAndExplain(
        AiProvider provider,
        RecommendationMode mode,
        TagProfile profile,
        List<ScoredCandidate> candidates
    ) {
        if (candidates == null || candidates.isEmpty()) {
            return Optional.empty();
        }
        String model = properties.modelFor(AiProvider.CODEX, mode);
        if (model.isBlank() || bridgeJobService == null) {
            return Optional.empty();
        }

        Map<String, Object> request;
        try {
            request = openAiCompatibleRequest(mode, profile, candidates, model);
        } catch (JsonProcessingException exception) {
            log.warn("Codex recommendation prompt build failed. mode={}, cause={}", mode.wireValue(), exception.toString());
            return Optional.empty();
        }

        return bridgeJobService.submitAndAwait(AiProvider.CODEX, mode, model, request)
            .flatMap(rawJson -> {
                try {
                    return parseResult(rawJson, model);
                } catch (JsonProcessingException exception) {
                    log.warn("Codex bridge result parsing failed. mode={}, model={}, cause={}", mode.wireValue(), model, exception.toString());
                    return Optional.empty();
                }
            });
    }

    public AiProviderStatus providerStatus(AiProvider provider) {
        List<String> expectedModels = expectedModels();
        if (bridgeJobService == null) {
            return new AiProviderStatus(
                AiProvider.CODEX.wireValue(),
                properties.providerLabel(AiProvider.CODEX),
                expectedModels,
                false,
                "not_configured",
                "AI bridge is not available in this runtime."
            );
        }
        return bridgeJobService.bridgeStatus(AiProvider.CODEX);
    }

    public boolean bridgeTokenConfigured() {
        return bridgeJobService != null && bridgeJobService.tokenConfigured();
    }

    private List<String> expectedModels() {
        return properties.expectedModelsFor(AiProvider.CODEX);
    }

    Map<String, Object> openAiCompatibleRequest(
        RecommendationMode mode,
        TagProfile profile,
        List<ScoredCandidate> candidates,
        String model
    ) throws JsonProcessingException {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("model", model);
        request.put("stream", false);
        request.put("temperature", mode == RecommendationMode.FAST ? 0.0 : 0.05);
        request.put("top_p", 0.85);
        request.put("max_tokens", properties.maxTokensFor(AiProvider.CODEX, mode));
        String reasoningEffort = properties.reasoningEffortFor(AiProvider.CODEX, mode);
        if (reasoningEffort != null && !reasoningEffort.isBlank()) {
            request.put("reasoning_effort", reasoningEffort);
        }
        request.put("messages", messages(mode, profile, candidates));
        request.put(
            "response_format",
            recommendationResponseFormat(
                mode,
                properties.responseTextMaxLengthFor(AiProvider.CODEX, mode),
                candidates.size()
            )
        );
        return request;
    }

    private List<Map<String, String>> messages(
        RecommendationMode mode,
        TagProfile profile,
        List<ScoredCandidate> candidates
    ) throws JsonProcessingException {
        return List.of(
            Map.of("role", "system", "content", systemPrompt(mode)),
            Map.of("role", "user", "content", buildPrompt(mode, profile, candidates))
        );
    }

    private String systemPrompt(RecommendationMode mode) {
        return mode == RecommendationMode.PRECISE
            ? "You are a careful Korean movie recommendation analyst. Use only the supplied candidates. Do deep comparative reasoning across selected genres, poster taste, avoided elements, practical showtime value, and tradeoffs between close candidates. Lead with concrete positive evidence, then mention limits only when they matter. Return JSON only. Do not invent movies, theaters, prices, scores, ids, seats, runtimes, or hidden fields."
            : "You are a concise Korean movie recommendation analyst. Use only the supplied candidates. Compare the supplied evidence with concrete fit reasons instead of generic caution-first wording. Return JSON only. Do not invent movies, theaters, prices, scores, ids, seats, runtimes, or hidden fields.";
    }

    Optional<AiResult> parseResult(String content, String model) throws JsonProcessingException {
        String json = extractJson(content);
        if (json.isBlank()) {
            return Optional.empty();
        }
        JsonNode root = objectMapper.readTree(json);
        JsonNode rows = root.path("r");
        List<AiPick> picks = new ArrayList<>();
        if (rows.isArray()) {
            rows.forEach(row -> {
                if (row.isIntegralNumber()) {
                    picks.add(new AiPick(row.asLong(), "", "", ""));
                } else if (row.isObject()) {
                    picks.add(new AiPick(
                        row.path("id").asLong(),
                        modelScore(row),
                        row.path("why").asText(""),
                        row.path("c").asText(""),
                        row.path("v").asText(""),
                        row.path("a").asText("")
                    ));
                }
            });
        }
        return Optional.of(new AiResult(json, model, picks));
    }

    private Integer modelScore(JsonNode row) {
        JsonNode score = row.path("s");
        if (score.isMissingNode() || score.isNull()) {
            score = row.path("score");
        }
        if (score.isIntegralNumber()) {
            return Math.max(0, Math.min(100, score.asInt()));
        }
        if (score.isFloatingPointNumber()) {
            return Math.max(0, Math.min(100, (int) Math.round(score.asDouble())));
        }
        if (score.isTextual()) {
            try {
                return Math.max(0, Math.min(100, Integer.parseInt(score.asText("").trim())));
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    String buildPrompt(RecommendationMode mode, TagProfile profile, List<ScoredCandidate> candidates) {
        return buildPrompt(AiProvider.CODEX, mode, profile, candidates);
    }

    String buildPrompt(
        AiProvider provider,
        RecommendationMode mode,
        TagProfile profile,
        List<ScoredCandidate> candidates
    ) {
        TagProfile safeProfile = profile == null ? new TagProfile() : profile;
        try {
            String candidateJson = objectMapper.writeValueAsString(candidates.stream()
                .map(scored -> candidateForPrompt(scored, safeProfile, mode))
                .toList());
            String pickInstruction = mode == RecommendationMode.PRECISE && candidates.size() >= 3
                ? "Pick exactly 3 objects from candidates."
                : "Pick 1 to " + Math.min(3, candidates.size()) + " objects from candidates.";
            String depth = mode == RecommendationMode.PRECISE
                ? "Decision style: CODEX_PRECISE. Evaluate every supplied candidate before choosing. Compare selected genre intent, poster taste, avoid risks, showtime practicality, and why each selected candidate beats a nearby alternative."
                : "Decision style: CODEX_FAST. Use the small candidate set as-is, make one ranking pass, and keep the answer short. Prioritize the strongest concrete fit and avoid generic caution-first wording.";
            String itemContract = mode == RecommendationMode.PRECISE
                ? "s=integer 0-100 final recommendation score; why=2 Korean sentences naming the decisive content fit and practical fit; a=2-3 Korean sentences covering selected genre/poster profile, avoid-risk handling, and tradeoff versus another candidate; v=one Korean sentence about practical showtime/theater value; c=short Korean caution or empty string."
                : "s=integer 0-100 final recommendation score; why=1 Korean sentence naming the decisive content and practical fit; a=1 short Korean sentence about selected genre/poster profile or context; v=one short Korean sentence about showtime/theater value; c=short Korean caution or empty string.";
            String comparisonFields = mode == RecommendationMode.PRECISE
                ? "- watchRisks/tradeoffHints: reasons to be careful or compare against nearby options"
                : "- watchRisks: reasons to be careful";
            return """
                User profile:
                - audience=%s
                - mood=%s
                - avoid=%s
                - preference_genre_hints=%s

                Candidates:
                %s

                Use these candidate fields:
                - tasteMatch: direct candidate-user selected/poster genre overlap only
                - fitHints: direct fit signals
                - scheduleFit/practicalValue: booking-time and theater practicality
                %s

                Method:
                %s
                %s
                Treat preference_genre_hints as user-level context only. Claim direct genre/poster match only when candidate tasteMatch is non-empty.
                Score candidates yourself from the supplied evidence before ranking: direct selected-genre/tasteMatch fit can be 90-100, strong concrete fit without tasteMatch is a reserve and should usually be 55-68, and any empty-tasteMatch reserve must stay at or below 68.
                If tasteMatch is empty, still explain concrete fit from fitHints, scheduleFit, and practicalValue instead of leading with "direct evidence is missing". Mention the missing match only if it changes the ranking.
                Return only {"r":[{"id":1,"s":92,"why":"...","a":"...","v":"...","c":"..."}]}.
                %s
                Do not write scores, raw tags, JSON field names, or unavailable facts inside why/a/v/c text.
                Never invent movies, theaters, prices, seats, runtimes, showtimes, or booking availability.
                """.formatted(
                safeProfile.audience(),
                safeProfile.mood(),
                safeProfile.avoid(),
                analysisHints(safeProfile),
                candidateJson,
                comparisonFields,
                depth,
                pickInstruction,
                itemContract
            );
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to build recommendation prompt.", e);
        }
    }

    private Map<String, Object> recommendationResponseFormat(
        RecommendationMode mode,
        int maxTextLength,
        int candidateCount
    ) {
        return Map.of(
            "type", "json_schema",
            "json_schema", Map.of(
                "name", "daboyeo_recommendation_response",
                "strict", true,
                "schema", recommendationResponseSchema(mode, maxTextLength, candidateCount)
            )
        );
    }

    private Map<String, Object> recommendationResponseSchema(
        RecommendationMode mode,
        int maxTextLength,
        int candidateCount
    ) {
        int minItems = mode == RecommendationMode.PRECISE ? Math.min(3, Math.max(1, candidateCount)) : 1;
        return Map.of(
            "type", "object",
            "additionalProperties", false,
            "required", List.of("r"),
            "properties", Map.of(
                "r", Map.of(
                    "type", "array",
                    "minItems", minItems,
                    "maxItems", 3,
                    "items", recommendationItemSchema(mode, maxTextLength)
                )
            )
        );
    }

    private Map<String, Object> recommendationItemSchema(RecommendationMode mode, int maxTextLength) {
        int analysisMax = mode == RecommendationMode.PRECISE ? maxTextLength : Math.min(maxTextLength, 220);
        int reasonMax = mode == RecommendationMode.PRECISE ? Math.min(maxTextLength, 240) : Math.min(maxTextLength, 180);
        int valueMax = mode == RecommendationMode.PRECISE ? Math.min(maxTextLength, 180) : Math.min(maxTextLength, 150);
        int cautionMax = mode == RecommendationMode.PRECISE ? Math.min(maxTextLength, 140) : Math.min(maxTextLength, 100);
        return Map.of(
            "type", "object",
            "additionalProperties", false,
            "required", List.of("id", "s", "why", "a", "v", "c"),
            "properties", Map.of(
                "id", Map.of("type", "integer"),
                "s", Map.of("type", "integer", "minimum", 0, "maximum", 100),
                "why", Map.of("type", "string", "maxLength", reasonMax),
                "a", Map.of("type", "string", "maxLength", analysisMax),
                "v", Map.of("type", "string", "maxLength", valueMax),
                "c", Map.of("type", "string", "maxLength", cautionMax)
            )
        );
    }

    private Map<String, Object> candidateForPrompt(ScoredCandidate scored, TagProfile profile, RecommendationMode mode) {
        ShowtimeCandidate candidate = scored.candidate();
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("id", candidate.showtimeId());
        value.put("title", candidate.title());
        value.put("theater", List.of(candidate.providerCode(), candidate.theaterName(), candidate.regionName(), candidate.screenName())
            .stream()
            .filter(part -> part != null && !part.isBlank())
            .toList());
        value.put("startsAt", candidate.startsAt() == null ? "" : DateTimeFormatter.ISO_LOCAL_DATE_TIME.format(candidate.startsAt()));
        value.put("price", priceSummary(candidate.minPriceAmount(), candidate.currencyCode()));
        value.put("seats", seatSummary(candidate.remainingSeatCount(), candidate.totalSeatCount()));
        value.put("age", candidate.ageRating());
        value.put("runtimeMinutes", candidate.runtimeMinutes());
        value.put("tasteMatch", tasteMatchHints(scored, profile));
        value.put("fitHints", reasonHints(scored));
        value.put("scheduleFit", scheduleFit(candidate));
        value.put("practicalValue", valueHints(candidate));
        value.put("watchRisks", cautionHints(scored));
        if (mode == RecommendationMode.PRECISE) {
            value.put("tradeoffHints", tradeoffHints(scored, profile));
        }
        return value;
    }

    private String priceSummary(Integer amount, String currencyCode) {
        if (amount == null) {
            return "";
        }
        String currency = currencyCode == null || currencyCode.isBlank() ? "KRW" : currencyCode.trim();
        return amount + " " + currency;
    }

    private List<String> tasteMatchHints(ScoredCandidate scored, TagProfile profile) {
        if (profile == null) {
            return List.of();
        }
        List<String> hints = new ArrayList<>();
        Set<String> likedGenres = tasteAnchorGenres(profile);
        scored.candidate().allTags().stream()
            .map(tag -> tag == null ? "" : tag.trim().toLowerCase(Locale.ROOT))
            .filter(tag -> tag.startsWith("genre:") && likedGenres.contains(tag))
            .map(this::genreAnalysisHint)
            .filter(value -> !value.isBlank())
            .forEach(hints::add);
        return hints.stream().distinct().limit(4).toList();
    }

    private String scheduleFit(ShowtimeCandidate candidate) {
        List<String> parts = new ArrayList<>();
        if (candidate.startsAt() != null) {
            parts.add(DateTimeFormatter.ofPattern("MM-dd HH:mm").format(candidate.startsAt()));
        }
        if (!candidate.theaterName().isBlank()) {
            parts.add(candidate.theaterName());
        }
        String seat = seatSummary(candidate.remainingSeatCount(), candidate.totalSeatCount());
        if (!seat.isBlank()) {
            parts.add(seat);
        }
        if (candidate.runtimeMinutes() != null) {
            parts.add(candidate.runtimeMinutes() + "min");
        }
        return String.join(" / ", parts);
    }

    private List<String> tradeoffHints(ScoredCandidate scored, TagProfile profile) {
        ShowtimeCandidate candidate = scored.candidate();
        List<String> hints = new ArrayList<>();
        List<String> risks = cautionHints(scored);
        if (!risks.isEmpty()) {
            hints.add("risk=" + String.join(" ", risks));
        }
        List<String> taste = tasteMatchHints(scored, profile);
        if (!taste.isEmpty()) {
            hints.add("taste=" + String.join(" ", taste));
        }
        String seat = seatHint(candidate.remainingSeatCount(), candidate.totalSeatCount());
        if ("limited".equals(seat)) {
            hints.add("practical=limited seats");
        } else if ("enough".equals(seat)) {
            hints.add("practical=seat-friendly");
        }
        if (candidate.runtimeMinutes() != null && candidate.runtimeMinutes() >= 140) {
            hints.add("runtime=long");
        }
        if (candidate.startsAt() != null) {
            hints.add("time=" + DateTimeFormatter.ofPattern("HH:mm").format(candidate.startsAt()));
        }
        return hints.stream().distinct().limit(5).toList();
    }

    private List<String> analysisHints(TagProfile profile) {
        Set<String> likedGenres = tasteAnchorGenres(profile);
        if (likedGenres.isEmpty()) {
            return List.of();
        }
        return likedGenres.stream()
            .map(this::genreAnalysisHint)
            .filter(value -> !value.isBlank())
            .distinct()
            .limit(4)
            .toList();
    }

    private Set<String> tasteAnchorGenres(TagProfile profile) {
        if (profile == null) {
            return Set.of();
        }
        if (!profile.preferredGenres().isEmpty()) {
            return profile.preferredGenres();
        }
        return profile.likedGenres();
    }

    private String genreAnalysisHint(String tag) {
        String normalized = tag == null ? "" : tag.trim().toLowerCase(Locale.ROOT).replace('_', '-');
        if (normalized.startsWith("genre:")) {
            normalized = normalized.substring("genre:".length());
        }
        String label = switch (normalized) {
            case "action" -> "액션";
            case "adventure" -> "어드벤처";
            case "animation" -> "애니메이션";
            case "comedy" -> "코미디";
            case "crime" -> "범죄";
            case "drama" -> "드라마";
            case "family" -> "가족";
            case "fantasy" -> "판타지";
            case "history" -> "역사";
            case "horror" -> "공포";
            case "music" -> "음악";
            case "musical" -> "뮤지컬";
            case "mystery" -> "미스터리";
            case "romance" -> "로맨스";
            case "sf", "sci-fi", "science-fiction" -> "SF";
            case "thriller" -> "스릴러";
            default -> normalized;
        };
        return label.isBlank() ? "" : "#" + label + "취향";
    }

    private List<String> reasonHints(ScoredCandidate scored) {
        ShowtimeCandidate candidate = scored.candidate();
        List<String> hints = new ArrayList<>();
        scored.matchedTags().stream()
            .filter(this::isReasonSourceTag)
            .map(this::tagPhrase)
            .filter(value -> !value.isBlank())
            .forEach(hints::add);
        if (hints.isEmpty()) {
            candidate.allTags().stream()
                .filter(this::isReasonSourceTag)
                .map(this::tagPhrase)
                .filter(value -> !value.isBlank())
                .limit(3)
                .forEach(hints::add);
        }
        if (candidate.runtimeMinutes() != null && candidate.runtimeMinutes() <= 125) {
            hints.add("#부담적은러닝타임");
        }
        if (!candidate.ageRating().isBlank()) {
            hints.add(ageTag(candidate.ageRating()));
        }
        if (hints.isEmpty()) {
            hints.add("#조건근접");
        }
        return hints.stream().distinct().limit(5).toList();
    }

    private List<String> cautionHints(ScoredCandidate scored) {
        ShowtimeCandidate candidate = scored.candidate();
        List<String> hints = new ArrayList<>();
        scored.penalties().stream()
            .map(this::tagPhrase)
            .filter(value -> !value.isBlank())
            .forEach(hints::add);
        candidate.allTags().stream()
            .filter(tag -> tag != null && tag.trim().toLowerCase(Locale.ROOT).startsWith("content:"))
            .map(this::tagPhrase)
            .filter(value -> !value.isBlank())
            .forEach(hints::add);
        if (candidate.remainingSeatCount() != null && candidate.remainingSeatCount() <= 10) {
            hints.add("#좌석주의");
        }
        return hints.stream().distinct().limit(4).toList();
    }

    private boolean isReasonSourceTag(String tag) {
        return tag != null && !tag.trim().toLowerCase(Locale.ROOT).startsWith("content:");
    }

    private List<String> valueHints(ShowtimeCandidate candidate) {
        List<String> hints = new ArrayList<>();
        if (candidate.startsAt() != null) {
            hints.add("#" + DateTimeFormatter.ofPattern("HH:mm").format(candidate.startsAt()) + "상영");
        }
        if (candidate.minPriceAmount() != null) {
            hints.add("#" + candidate.minPriceAmount() + "원");
        }
        String seat = seatHint(candidate.remainingSeatCount(), candidate.totalSeatCount());
        if ("enough".equals(seat)) {
            hints.add("#좌석여유");
        } else if ("limited".equals(seat)) {
            hints.add("#좌석주의");
        }
        if (!candidate.theaterName().isBlank()) {
            hints.add("#예매가능");
        }
        if (hints.isEmpty()) {
            hints.add("#예매정보");
        }
        return hints.stream().distinct().limit(4).toList();
    }

    private String tagPhrase(String tag) {
        if (tag == null || tag.isBlank()) {
            return "";
        }
        String normalized = tag.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "audience:alone" -> "#혼영";
            case "audience:friends" -> "#친구랑";
            case "audience:date" -> "#데이트";
            case "audience:family" -> "#가족";
            case "audience:child" -> "#아이와함께";
            case "mood:light" -> "#가볍게";
            case "mood:immersive" -> "#몰입";
            case "mood:exciting" -> "#신나는";
            case "mood:calm" -> "#잔잔한";
            case "mood:tense" -> "#긴장감";
            case "mood:warm" -> "#따뜻한";
            case "mood:visual" -> "#시각적재미";
            case "pace:easy" -> "#이해쉬움";
            case "pace:fast" -> "#빠른전개";
            case "pace:slow" -> "#천천히보는";
            case "content:too_long", "too_long" -> "#긴러닝타임주의";
            case "content:complex", "complex" -> "#난도주의";
            case "content:violence", "violence" -> "#폭력성주의";
            case "content:sad_ending", "sad_ending" -> "#먹먹한결말주의";
            case "content:loud", "loud" -> "#큰소리주의";
            case "taste_mismatch" -> "#취향직접매칭약함";
            default -> {
                if (normalized.startsWith("genre:")) {
                    yield "#" + normalized.substring("genre:".length()).replace('_', '-');
                }
                yield "";
            }
        };
    }

    private String ageTag(String ageRating) {
        String age = ageRating == null ? "" : ageRating.trim();
        if (age.isBlank()) {
            return "";
        }
        String compact = age
            .replace("이상", "")
            .replace("관람가", "")
            .replace("관람", "")
            .replaceAll("\\s+", "");
        if (compact.matches("\\d+")) {
            compact = compact + "세";
        }
        return "#" + compact;
    }

    private String seatHint(Integer remainingSeatCount, Integer totalSeatCount) {
        if (remainingSeatCount == null) {
            return "";
        }
        if (remainingSeatCount <= 0) {
            return "none";
        }
        if (totalSeatCount == null || totalSeatCount <= 0) {
            return remainingSeatCount >= 20 ? "enough" : "limited";
        }
        double ratio = remainingSeatCount / (double) totalSeatCount;
        if (remainingSeatCount >= 30 || ratio >= 0.3) {
            return "enough";
        }
        if (remainingSeatCount <= 10 || ratio <= 0.1) {
            return "limited";
        }
        return "normal";
    }

    private String seatSummary(Integer remainingSeatCount, Integer totalSeatCount) {
        if (remainingSeatCount == null) {
            return "";
        }
        if (totalSeatCount == null || totalSeatCount <= 0) {
            return remainingSeatCount + " seats left";
        }
        return remainingSeatCount + "/" + totalSeatCount + " seats left";
    }

    private String extractJson(String content) {
        String trimmed = content == null ? "" : content.trim();
        if (trimmed.startsWith("```")) {
            trimmed = trimmed.replaceFirst("^```json\\s*", "")
                .replaceFirst("^```\\s*", "")
                .replaceFirst("\\s*```$", "")
                .trim();
        }
        return trimmed;
    }
}
