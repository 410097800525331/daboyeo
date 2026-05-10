package kr.daboyeo.backend.domain.recommendation;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class RecommendationModels {

    private RecommendationModels() {
    }

    public enum RecommendationMode {
        FAST,
        PRECISE;

        public static RecommendationMode from(String value) {
            if (value == null || value.isBlank()) {
                return PRECISE;
            }
            String normalized = value.trim().toUpperCase(Locale.ROOT);
            if ("FAST".equals(normalized)) {
                return FAST;
            }
            if ("PRECISE".equals(normalized)) {
                return PRECISE;
            }
            throw new IllegalArgumentException("지원하지 않는 추천 모드야.");
        }

        public String wireValue() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    public enum AiProvider {
        FALLBACK("fallback"),
        CODEX("codex"),
        OPENAI_API("openai-api");

        private final String wireValue;

        AiProvider(String wireValue) {
            this.wireValue = wireValue;
        }

        public static AiProvider from(String value) {
            if (value == null || value.isBlank()) {
                return FALLBACK;
            }
            String normalized = value.trim().toUpperCase(Locale.ROOT).replace('-', '_');
            if ("FALLBACK".equals(normalized)
                || "LOCAL_SCORE".equals(normalized)
                || "SCORER".equals(normalized)
                || "FREE".equals(normalized)) {
                return FALLBACK;
            }
            if ("OPENAI".equals(normalized)
                || "OPENAI_API".equals(normalized)
                || "API".equals(normalized)) {
                return OPENAI_API;
            }
            if ("CODEX".equals(normalized)
                || "CODEX_DEMO".equals(normalized)
                || "CODEX_BRIDGE".equals(normalized)
                || "CODEX_WORKER".equals(normalized)
                || "GPT".equals(normalized)
                || "REMOTE_GATEWAY".equals(normalized)
                || "CODEX_OAUTH_GATEWAY".equals(normalized)
                || "LOCAL".equals(normalized)
                || "LOCAL_OPENAI_COMPATIBLE".equals(normalized)) {
                return CODEX;
            }
            throw new IllegalArgumentException("지원하지 않는 AI provider야.");
        }

        public String wireValue() {
            return wireValue;
        }
    }

    public enum FeedbackAction {
        LIKE("like"),
        DISLIKE("dislike"),
        BOOKING_VIEW("booking_view");

        private final String wireValue;

        FeedbackAction(String wireValue) {
            this.wireValue = wireValue;
        }

        public static FeedbackAction from(String value) {
            if (value == null || value.isBlank()) {
                throw new IllegalArgumentException("피드백 action이 필요해.");
            }
            String normalized = normalize(value);
            for (FeedbackAction action : values()) {
                if (action.wireValue.equals(normalized)) {
                    return action;
                }
            }
            throw new IllegalArgumentException("지원하지 않는 피드백 action이야.");
        }

        public String wireValue() {
            return wireValue;
        }
    }

    public record SessionRequest(String anonymousId) {
    }

    public record SessionResponse(String anonymousId) {
    }

    public record RecommendationSurvey(String audience, String mood, List<String> avoid, List<String> preferredGenres) {
        public RecommendationSurvey(String audience, String mood, List<String> avoid) {
            this(audience, mood, avoid, List.of());
        }

        public RecommendationSurvey {
            audience = normalize(audience);
            mood = normalize(mood);
            avoid = safeNormalizedList(avoid);
            preferredGenres = safeNormalizedList(preferredGenres);
        }
    }

    public record PosterChoices(List<String> likedSeedMovieIds, List<String> dislikedSeedMovieIds) {
        public PosterChoices {
            likedSeedMovieIds = safeIdList(likedSeedMovieIds);
            dislikedSeedMovieIds = safeIdList(dislikedSeedMovieIds);
        }
    }

    public record SearchFilters(
        String region,
        LocalDate date,
        String timeRange,
        Integer personCount
    ) {
        public SearchFilters {
            region = normalizeRegion(region);
            timeRange = normalize(timeRange);
            personCount = personCount == null ? null : Math.max(1, personCount);
        }

        public boolean active() {
            return !region.isBlank() || date != null || !timeRange.isBlank() || personCount != null;
        }

        public boolean hasRegion() {
            return !region.isBlank();
        }

        public boolean hasDate() {
            return date != null;
        }

        public boolean hasTimeRange() {
            return !timeRange.isBlank();
        }

        public boolean hasPersonCount() {
            return personCount != null;
        }
    }

    public record RecommendationRequest(
        String anonymousId,
        String mode,
        RecommendationSurvey survey,
        PosterChoices posterChoices,
        SearchFilters searchFilters,
        String aiProvider
    ) {
        public RecommendationRequest(String anonymousId, String mode, RecommendationSurvey survey, PosterChoices posterChoices) {
            this(anonymousId, mode, survey, posterChoices, null, null);
        }

        public RecommendationRequest(
            String anonymousId,
            String mode,
            RecommendationSurvey survey,
            PosterChoices posterChoices,
            SearchFilters searchFilters
        ) {
            this(anonymousId, mode, survey, posterChoices, searchFilters, null);
        }
    }

    public record FeedbackRequest(
        String anonymousId,
        Long movieId,
        Long showtimeId,
        String action
    ) {
    }

    public record FeedbackResponse(boolean accepted, Map<String, Integer> appliedWeights) {
        public FeedbackResponse {
            appliedWeights = appliedWeights == null ? Map.of() : Map.copyOf(appliedWeights);
        }
    }

    public record AiProviderStatus(
        String provider,
        String label,
        List<String> expectedModels,
        boolean available,
        String status,
        String message
    ) {
        public AiProviderStatus {
            provider = normalize(provider);
            label = text(label);
            expectedModels = expectedModels == null ? List.of() : List.copyOf(expectedModels);
            status = normalize(status);
            message = text(message);
        }
    }

    public record PosterSeedMovie(
        String id,
        String title,
        String posterUrl,
        List<String> genres,
        List<String> moods,
        String pace,
        List<String> audiences,
        List<String> avoid,
        String ageRating
    ) {
        public PosterSeedMovie {
            id = normalizeId(id);
            title = title == null ? "" : title.trim();
            posterUrl = posterUrl == null ? "" : posterUrl.trim();
            genres = safeNormalizedList(genres);
            moods = safeNormalizedList(moods);
            pace = normalize(pace);
            audiences = safeNormalizedList(audiences);
            avoid = safeNormalizedList(avoid);
            ageRating = ageRating == null ? "" : ageRating.trim();
        }

        public List<String> preferenceTags() {
            List<String> tags = new ArrayList<>();
            genres.forEach(value -> tags.add("genre:" + value));
            moods.forEach(value -> tags.add("mood:" + value));
            if (!pace.isBlank()) {
                tags.add("pace:" + pace);
            }
            audiences.forEach(value -> tags.add("audience:" + value));
            return tags;
        }

        public List<String> contentTags() {
            List<String> tags = new ArrayList<>();
            avoid.forEach(value -> tags.add("content:" + value));
            return tags;
        }
    }

    public record ShowtimeCandidate(
        Long movieId,
        Long showtimeId,
        String title,
        String providerCode,
        String externalMovieId,
        String theaterName,
        String regionName,
        String screenName,
        String screenType,
        String formatName,
        LocalDate showDate,
        LocalDateTime startsAt,
        LocalDateTime endsAt,
        Integer remainingSeatCount,
        Integer totalSeatCount,
        Integer minPriceAmount,
        String currencyCode,
        String bookingUrl,
        String posterUrl,
        String ageRating,
        Integer runtimeMinutes,
        Set<String> tags
    ) {
        public ShowtimeCandidate {
            title = text(title);
            providerCode = text(providerCode);
            externalMovieId = text(externalMovieId);
            theaterName = text(theaterName);
            regionName = text(regionName);
            screenName = text(screenName);
            screenType = text(screenType);
            formatName = text(formatName);
            currencyCode = currencyCode == null || currencyCode.isBlank() ? "KRW" : currencyCode.trim();
            bookingUrl = text(bookingUrl);
            posterUrl = text(posterUrl);
            ageRating = text(ageRating);
            tags = tags == null ? Set.of() : Set.copyOf(tags);
        }

        public Set<String> allTags() {
            Set<String> all = new LinkedHashSet<>(tags);
            String screen = (screenType + " " + formatName).toLowerCase(Locale.ROOT);
            if (screen.contains("imax") || screen.contains("dolby") || screen.contains("4dx")) {
                all.add("mood:exciting");
                all.add("mood:visual");
                all.add("content:loud");
            }
            addMetadataDerivedTags(all, screen);
            return all;
        }

        private void addMetadataDerivedTags(Set<String> all, String screen) {
            String age = normalizeForTagInference(ageRating + " " + String.join(" ", all));

            if (containsAny(age, "age_rating:all", "전체", "all")) {
                all.add("audience:child");
                all.add("audience:family");
                all.add("mood:light");
            } else if (containsAny(age, "age_rating:12", "12")) {
                all.add("audience:friends");
                all.add("audience:date");
            } else if (containsAny(age, "age_rating:19", "19", "청불", "adult")) {
                all.add("mood:tense");
                all.add("content:adult");
            }

            if (screen.contains("atmos") || screen.contains("dolby")) {
                all.add("mood:visual");
                all.add("mood:exciting");
                all.add("content:loud");
            }
        }

        private String normalizeForTagInference(String value) {
            return value == null ? "" : value.toLowerCase(Locale.ROOT);
        }

        private boolean containsAny(String value, String... needles) {
            if (value == null || value.isBlank()) {
                return false;
            }
            for (String needle : needles) {
                if (needle != null && !needle.isBlank() && value.contains(needle.toLowerCase(Locale.ROOT))) {
                    return true;
                }
            }
            return false;
        }
    }

    public record ScoredCandidate(
        ShowtimeCandidate candidate,
        int score,
        List<String> matchedTags,
        List<String> penalties
    ) {
        public ScoredCandidate {
            matchedTags = matchedTags == null ? List.of() : List.copyOf(matchedTags);
            penalties = penalties == null ? List.of() : List.copyOf(penalties);
        }
    }

    public record RecommendationItem(
        Long movieId,
        Long showtimeId,
        String title,
        int score,
        String reason,
        String analysisPoint,
        String caution,
        String valuePoint,
        String providerCode,
        String theaterName,
        String regionName,
        String screenName,
        LocalDate showDate,
        LocalDateTime startsAt,
        Integer minPriceAmount,
        String currencyCode,
        String bookingUrl,
        String posterUrl
    ) {
    }

    public record RecommendationResponse(
        String runId,
        String mode,
        String model,
        String status,
        String message,
        List<RecommendationItem> recommendations
    ) {
        public RecommendationResponse {
            recommendations = recommendations == null ? List.of() : List.copyOf(recommendations);
        }
    }

    public record AiPick(Long showtimeId, Integer score, String reason, String caution, String valuePoint, String analysisPoint) {
        public AiPick(Long showtimeId, String reason, String caution, String valuePoint, String analysisPoint) {
            this(showtimeId, null, reason, caution, valuePoint, analysisPoint);
        }

        public AiPick(Long showtimeId, String reason, String caution, String valuePoint) {
            this(showtimeId, reason, caution, valuePoint, "");
        }

        public AiPick {
            score = score == null ? null : Math.max(0, Math.min(100, score));
        }
    }

    public record AiResult(String rawJson, String modelName, List<AiPick> picks) {
        public AiResult {
            rawJson = rawJson == null ? "" : rawJson;
            modelName = modelName == null ? "" : modelName;
            picks = picks == null ? List.of() : List.copyOf(picks);
        }
    }

    public record RecommendationProfile(String anonymousId, Map<String, Integer> tagWeights) {
        public RecommendationProfile {
            anonymousId = normalizeAnonymousId(anonymousId);
            tagWeights = tagWeights == null ? Map.of() : Map.copyOf(tagWeights);
        }
    }

    public static final class TagProfile {
        private final Map<String, Integer> weights = new LinkedHashMap<>();
        private final Set<String> avoid = new LinkedHashSet<>();
        private final Set<String> likedGenres = new LinkedHashSet<>();
        private final Set<String> preferredGenres = new LinkedHashSet<>();
        private String audience = "";
        private String mood = "";

        public void setAudience(String audience) {
            this.audience = normalize(audience);
        }

        public String audience() {
            return audience;
        }

        public void setMood(String mood) {
            this.mood = normalize(mood);
        }

        public String mood() {
            return mood;
        }

        public void addAvoid(Collection<String> values) {
            safeNormalizedList(values).forEach(avoid::add);
        }

        public boolean avoids(String value) {
            return avoid.contains(normalize(value));
        }

        public Set<String> avoid() {
            return Set.copyOf(avoid);
        }

        public void addWeight(String key, int delta) {
            String normalized = normalizeTagKey(key);
            if (normalized.isBlank() || delta == 0) {
                return;
            }
            weights.merge(normalized, delta, Integer::sum);
        }

        public void addWeights(Map<String, Integer> deltas) {
            if (deltas == null) {
                return;
            }
            deltas.forEach((key, value) -> addWeight(key, value == null ? 0 : value));
        }

        public int weight(String key) {
            return weights.getOrDefault(normalizeTagKey(key), 0);
        }

        public Map<String, Integer> weights() {
            return Map.copyOf(weights);
        }

        public void addLikedGenre(String value) {
            String normalized = normalizeTagKey(value);
            if (!normalized.isBlank()) {
                likedGenres.add("genre:" + normalized.replaceFirst("^genre:", ""));
            }
        }

        public Set<String> likedGenres() {
            return Collections.unmodifiableSet(likedGenres);
        }

        public void addPreferredGenre(String value) {
            String normalized = normalizeTagKey(value);
            if (!normalized.isBlank()) {
                String tag = "genre:" + normalized.replaceFirst("^genre:", "");
                preferredGenres.add(tag);
                likedGenres.add(tag);
            }
        }

        public Set<String> preferredGenres() {
            return Collections.unmodifiableSet(preferredGenres);
        }
    }

    public static String newAnonymousId() {
        return "anon_" + UUID.randomUUID().toString().replace("-", "");
    }

    public static String newRunId() {
        return "rec_" + UUID.randomUUID().toString().replace("-", "");
    }

    public static String normalizeAnonymousId(String value) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.length() > 80) {
            throw new IllegalArgumentException("anonymousId가 너무 길어.");
        }
        return normalized;
    }

    public static String normalizeId(String value) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.length() > 120) {
            return normalized.substring(0, 120);
        }
        return normalized;
    }

    public static String normalizeRegion(String value) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isBlank()) {
            return "";
        }
        String lowered = normalized.toLowerCase(Locale.ROOT);
        if ("전체".equals(normalized) || "all".equals(lowered)) {
            return "";
        }
        return normalized;
    }

    public static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    public static String normalizeTagKey(String value) {
        return normalize(value).replace(' ', '_');
    }

    private static List<String> safeIdList(Collection<String> values) {
        if (values == null) {
            return List.of();
        }
        return values.stream()
            .map(RecommendationModels::normalizeId)
            .filter(value -> !value.isBlank())
            .distinct()
            .limit(64)
            .toList();
    }

    private static List<String> safeNormalizedList(Collection<String> values) {
        if (values == null) {
            return List.of();
        }
        return values.stream()
            .map(RecommendationModels::normalize)
            .filter(value -> !value.isBlank())
            .distinct()
            .limit(64)
            .toList();
    }

    private static String text(String value) {
        return value == null ? "" : value.trim();
    }
}
