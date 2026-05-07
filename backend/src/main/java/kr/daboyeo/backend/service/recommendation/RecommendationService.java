package kr.daboyeo.backend.service.recommendation;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import kr.daboyeo.backend.config.RecommendationProperties;
import kr.daboyeo.backend.domain.recommendation.RecommendationModels.AiPick;
import kr.daboyeo.backend.domain.recommendation.RecommendationModels.AiProvider;
import kr.daboyeo.backend.domain.recommendation.RecommendationModels.AiProviderStatus;
import kr.daboyeo.backend.domain.recommendation.RecommendationModels.AiResult;
import kr.daboyeo.backend.domain.recommendation.RecommendationModels.FeedbackAction;
import kr.daboyeo.backend.domain.recommendation.RecommendationModels.FeedbackRequest;
import kr.daboyeo.backend.domain.recommendation.RecommendationModels.FeedbackResponse;
import kr.daboyeo.backend.domain.recommendation.RecommendationModels.PosterSeedMovie;
import kr.daboyeo.backend.domain.recommendation.RecommendationModels.RecommendationItem;
import kr.daboyeo.backend.domain.recommendation.RecommendationModels.RecommendationMode;
import kr.daboyeo.backend.domain.recommendation.RecommendationModels.RecommendationProfile;
import kr.daboyeo.backend.domain.recommendation.RecommendationModels.RecommendationRequest;
import kr.daboyeo.backend.domain.recommendation.RecommendationModels.RecommendationResponse;
import kr.daboyeo.backend.domain.recommendation.RecommendationModels.SearchFilters;
import kr.daboyeo.backend.domain.recommendation.RecommendationModels.ScoredCandidate;
import kr.daboyeo.backend.domain.recommendation.RecommendationModels.SessionResponse;
import kr.daboyeo.backend.domain.recommendation.RecommendationModels.ShowtimeCandidate;
import kr.daboyeo.backend.domain.recommendation.RecommendationModels.TagProfile;
import kr.daboyeo.backend.repository.recommendation.RecommendationProfileRepository;
import kr.daboyeo.backend.repository.recommendation.ShowtimeRecommendationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import static kr.daboyeo.backend.domain.recommendation.RecommendationModels.newAnonymousId;
import static kr.daboyeo.backend.domain.recommendation.RecommendationModels.newRunId;
import static kr.daboyeo.backend.domain.recommendation.RecommendationModels.normalizeAnonymousId;

@Service
public class RecommendationService {

    private static final Logger logger = LoggerFactory.getLogger(RecommendationService.class);
    private static final int CANDIDATE_LIMIT = 240;
    private static final int SELECTED_GENRE_CANDIDATE_LIMIT = 1200;
    private static final int RESULT_LIMIT = 3;
    private static final int NO_DIRECT_TASTE_MATCH_CAP = 68;
    private static final String RELAXED_FILTER_MESSAGE = "선택 조건과 정확히 맞는 상영이 없어 조건을 넓혀 추천했어.";
    private static final String SELECTED_GENRE_RELAXED_MESSAGE =
        "\uC120\uD0DD\uD55C \uC7A5\uB974\uC640 \uC9C1\uC811 \uC77C\uCE58\uD558\uB294 \uC0C1\uC601\uC774 \uC5C6\uC5B4 \uC870\uAC74\uC744 \uB113\uD600 \uC608\uBE44 \uCD94\uCC9C\uD588\uC5B4.";
    private static final Pattern INTERNAL_TEXT_PATTERN = Pattern.compile(
        "(?i)\\bscore\\s*[:=]?\\s*\\d+\\b|\\bmatchedtags\\b|\\bpenalties\\b|\\b(?:audience|mood|genre|content|pace):[a-z0-9_]+\\b"
    );
    private static final Pattern GENERIC_REASON_PATTERN = Pattern.compile(
        "(선택한\\s*분위기|겹치는\\s*신호|신호가\\s*있|우선\\s*추천|분위기\\s*잘\\s*맞|조건과\\s*가까운\\s*후보)"
    );
    private static final Pattern USER_TAG_PATTERN = Pattern.compile("#[\\p{L}\\p{N}:_\\-]+");
    private static final Pattern HTML_ENTITY_PATTERN = Pattern.compile(
        "&(#\\d+|#x[0-9a-fA-F]+|amp|lt|gt|quot|apos);"
    );

    private final RecommendationProperties properties;
    private final PosterSeedService posterSeedService;
    private final PreferenceProfileBuilder preferenceProfileBuilder;
    private final RecommendationScorer scorer;
    private final CodexRecommendationClient codexClient;
    private final RecommendationProfileRepository profileRepository;
    private final ShowtimeRecommendationRepository showtimeRepository;

    public RecommendationService(
        RecommendationProperties properties,
        PosterSeedService posterSeedService,
        PreferenceProfileBuilder preferenceProfileBuilder,
        RecommendationScorer scorer,
        CodexRecommendationClient codexClient,
        RecommendationProfileRepository profileRepository,
        ShowtimeRecommendationRepository showtimeRepository
    ) {
        this.properties = properties;
        this.posterSeedService = posterSeedService;
        this.preferenceProfileBuilder = preferenceProfileBuilder;
        this.scorer = scorer;
        this.codexClient = codexClient;
        this.profileRepository = profileRepository;
        this.showtimeRepository = showtimeRepository;
    }

    public SessionResponse ensureSession(String requestedAnonymousId) {
        String anonymousId = normalizeAnonymousId(requestedAnonymousId);
        if (anonymousId.isBlank()) {
            anonymousId = newAnonymousId();
        }
        profileRepository.ensureProfile(anonymousId);
        return new SessionResponse(anonymousId);
    }

    public void resetSession(String anonymousId) {
        String normalized = normalizeAnonymousId(anonymousId);
        if (normalized.isBlank()) {
            throw new IllegalArgumentException("anonymousId가 필요해.");
        }
        profileRepository.deleteProfileData(normalized);
    }

    public List<PosterSeedMovie> posterSeed(int limit) {
        return posterSeedService.randomSeed(limit <= 0 ? 10 : limit);
    }

    public List<PosterSeedMovie> posterSeed(int limit, List<String> preferredGenres) {
        return posterSeedService.randomSeed(limit <= 0 ? 10 : limit, preferredGenres);
    }

    public List<AiProviderStatus> providerHealth() {
        return List.of(codexClient.providerStatus(AiProvider.CODEX));
    }

    public RecommendationResponse recommend(RecommendationRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("추천 요청이 필요해.");
        }
        Instant startedAt = Instant.now();
        RecommendationMode mode = RecommendationMode.from(request.mode());
        AiProvider aiProvider = AiProvider.from(request.aiProvider());
        String anonymousId = normalizeAnonymousId(request.anonymousId());
        if (anonymousId.isBlank()) {
            anonymousId = ensureSession(null).anonymousId();
        } else {
            profileRepository.ensureProfile(anonymousId);
        }

        final String resolvedAnonymousId = anonymousId;
        RecommendationProfile storedProfile = profileRepository.findProfile(resolvedAnonymousId)
            .orElseGet(() -> new RecommendationProfile(resolvedAnonymousId, Map.of()));
        TagProfile tagProfile = preferenceProfileBuilder.build(
            request.survey(),
            request.posterChoices(),
            storedProfile.tagWeights()
        );
        SearchFilters searchFilters = request.searchFilters();
        RecommendationRequest normalizedRequest = new RecommendationRequest(
            resolvedAnonymousId,
            mode.wireValue(),
            request.survey(),
            request.posterChoices(),
            searchFilters,
            aiProvider.wireValue()
        );
        profileRepository.upsertSurvey(resolvedAnonymousId, normalizedRequest);

        String runId = newRunId();
        int startBufferMinutes = properties.minStartBufferMinutes();
        LocalDateTime minStartsAt = LocalDateTime.now().plusMinutes(startBufferMinutes);
        Set<String> selectedGenreValues = selectedGenreValues(tagProfile);
        int candidateLimit = hasSelectedGenreAnchor(tagProfile)
            ? SELECTED_GENRE_CANDIDATE_LIMIT
            : CANDIDATE_LIMIT;
        CandidateSearchResult candidateSearch = findCandidatesWithFallback(
            searchFilters,
            minStartsAt,
            candidateLimit,
            selectedGenreValues
        );
        SearchFilters effectiveSearchFilters = candidateSearch.filters();
        List<ShowtimeCandidate> candidates = candidateSearch.candidates();
        String modelName = properties.modelFor(aiProvider, mode);

        if (candidates.isEmpty()) {
            if (searchFilters != null && searchFilters.active()) {
                return noCandidateResponse(
                    startedAt,
                    runId,
                    resolvedAnonymousId,
                    mode,
                    modelName,
                    normalizedRequest,
                    "no_filtered_candidates",
                    "선택한 지역, 날짜, 시간대, 인원수에 맞는 상영이 없어."
                );
            }
            boolean hasStoredShowtimes = showtimeRepository.countStoredShowtimes() > 0;
            String status = hasStoredShowtimes ? "no_usable_showtimes" : "no_showtime_data";
            String message = hasStoredShowtimes
                ? "수집된 상영 정보는 있지만 지금 예매하기에 충분히 여유 있는 후보가 없어."
                : "수집된 상영 정보가 아직 없어. 수집 스크립트를 먼저 실행해줘.";
            return noCandidateResponse(
                startedAt,
                runId,
                resolvedAnonymousId,
                mode,
                modelName,
                normalizedRequest,
                status,
                message
            );
        }

        List<ScoredCandidate> scored = scorer.score(tagProfile, candidates, effectiveSearchFilters);
        if (scored.isEmpty()) {
            return noCandidateResponse(
                startedAt,
                runId,
                resolvedAnonymousId,
                mode,
                modelName,
                normalizedRequest,
                "no_matching_candidates",
                "조건에 맞는 상영 정보가 부족해. 피하고 싶은 요소를 조금 줄이면 후보가 늘어날 수 있어."
            );
        }

        List<ScoredCandidate> tasteAwareScored = rankTasteAwareCandidates(scored, tagProfile);
        List<ScoredCandidate> aiCandidates = selectDistinctMovieItems(
            tasteAwareScored,
            properties.aiCandidateLimitFor(aiProvider, mode)
        );
        var aiResult = codexClient.rankAndExplain(mode, tagProfile, aiCandidates);
        List<RecommendationItem> items = aiResult
            .map(result -> itemsFromAi(aiCandidates, result.picks(), tagProfile, mode, aiProvider))
            .filter(list -> !list.isEmpty())
            .orElseGet(() -> fallbackItems(tasteAwareScored, tagProfile, mode, aiProvider));

        RecommendationResponse response = new RecommendationResponse(
            runId,
            mode.wireValue(),
            aiResult.map(result -> result.modelName()).orElse(modelName == null || modelName.isBlank() ? "not-configured" : modelName),
            aiResult.isPresent() ? "ok" : "fallback",
            responseMessage(candidateSearch.message(), aiResult.isPresent(), aiProvider),
            items
        );
        saveRunBestEffort(
            runId,
            resolvedAnonymousId,
            mode.wireValue(),
            response.model(),
            normalizedRequest,
            aiCandidates,
            aiResult.map(result -> result.rawJson()).orElse(null),
            response,
            elapsedMs(startedAt)
        );
        return response;
    }

    private String responseMessage(String candidateSearchMessage, boolean aiResultPresent, AiProvider provider) {
        if (candidateSearchMessage != null && !candidateSearchMessage.isBlank()) {
            return candidateSearchMessage;
        }
        if (aiResultPresent) {
            return properties.providerLabel(provider) + "가 후보를 비교하고 이유를 작성했어.";
        }
        return "GPT 응답이 지연돼서 코드 점수 기반으로 임시 추천했어. 취향 태그와 상영 조건이 같이 맞는 순서로 정렬했어.";
    }

    private CandidateSearchResult findCandidatesWithFallback(
        SearchFilters filters,
        LocalDateTime minStartsAt,
        int candidateLimit,
        Set<String> requiredGenreValues
    ) {
        boolean hasRequiredGenres = requiredGenreValues != null && !requiredGenreValues.isEmpty();
        List<ShowtimeCandidate> exactCandidates = findUpcomingCandidates(
            filters,
            minStartsAt,
            candidateLimit,
            requiredGenreValues
        );
        if (!exactCandidates.isEmpty()) {
            return new CandidateSearchResult(exactCandidates, filters, "");
        }
        if ((filters == null || !filters.active()) && !hasRequiredGenres) {
            return new CandidateSearchResult(List.of(), filters, "");
        }

        if (filters != null && filters.active()) {
            for (SearchFilters relaxedFilters : relaxedSearchFilters(filters)) {
                List<ShowtimeCandidate> relaxedCandidates = findUpcomingCandidates(
                    relaxedFilters,
                    minStartsAt,
                    candidateLimit,
                    requiredGenreValues
                );
                if (!relaxedCandidates.isEmpty()) {
                    return new CandidateSearchResult(relaxedCandidates, relaxedFilters, RELAXED_FILTER_MESSAGE);
                }
            }
        }

        List<ShowtimeCandidate> broadCandidates = findUpcomingCandidates(
            null,
            minStartsAt,
            candidateLimit,
            requiredGenreValues
        );
        if (!broadCandidates.isEmpty()) {
            return new CandidateSearchResult(broadCandidates, null, RELAXED_FILTER_MESSAGE);
        }

        if (hasRequiredGenres) {
            List<ShowtimeCandidate> genreRelaxedCandidates = findUpcomingCandidates(
                filters,
                minStartsAt,
                candidateLimit,
                Set.of()
            );
            if (!genreRelaxedCandidates.isEmpty()) {
                return new CandidateSearchResult(genreRelaxedCandidates, filters, SELECTED_GENRE_RELAXED_MESSAGE);
            }
            if (filters != null && filters.active()) {
                for (SearchFilters relaxedFilters : relaxedSearchFilters(filters)) {
                    List<ShowtimeCandidate> relaxedCandidates = findUpcomingCandidates(
                        relaxedFilters,
                        minStartsAt,
                        candidateLimit,
                        Set.of()
                    );
                    if (!relaxedCandidates.isEmpty()) {
                        return new CandidateSearchResult(relaxedCandidates, relaxedFilters, SELECTED_GENRE_RELAXED_MESSAGE);
                    }
                }
            }
            List<ShowtimeCandidate> broadGenreRelaxedCandidates = findUpcomingCandidates(
                null,
                minStartsAt,
                candidateLimit,
                Set.of()
            );
            if (!broadGenreRelaxedCandidates.isEmpty()) {
                return new CandidateSearchResult(broadGenreRelaxedCandidates, null, SELECTED_GENRE_RELAXED_MESSAGE);
            }
        }
        return new CandidateSearchResult(List.of(), filters, "");
    }

    private List<SearchFilters> relaxedSearchFilters(SearchFilters filters) {
        List<SearchFilters> relaxedFilters = new ArrayList<>();
        Set<SearchFilters> seenFilters = new LinkedHashSet<>();

        if (filters.hasTimeRange()) {
            addRelaxedFilter(relaxedFilters, seenFilters, filters, new SearchFilters(
                filters.region(),
                filters.date(),
                "",
                filters.personCount()
            ));
        }
        if (filters.hasRegion()) {
            addRelaxedFilter(relaxedFilters, seenFilters, filters, new SearchFilters(
                "",
                filters.date(),
                filters.timeRange(),
                filters.personCount()
            ));
        }
        if (filters.hasRegion() || filters.hasTimeRange()) {
            addRelaxedFilter(relaxedFilters, seenFilters, filters, new SearchFilters(
                "",
                filters.date(),
                "",
                filters.personCount()
            ));
        }
        if (filters.hasPersonCount()) {
            addRelaxedFilter(relaxedFilters, seenFilters, filters, new SearchFilters(
                filters.region(),
                filters.date(),
                filters.timeRange(),
                null
            ));
        }
        if (filters.hasTimeRange() || filters.hasPersonCount()) {
            addRelaxedFilter(relaxedFilters, seenFilters, filters, new SearchFilters(
                filters.region(),
                filters.date(),
                "",
                null
            ));
        }
        if (filters.hasRegion() || filters.hasPersonCount()) {
            addRelaxedFilter(relaxedFilters, seenFilters, filters, new SearchFilters(
                "",
                filters.date(),
                filters.timeRange(),
                null
            ));
        }
        if (filters.hasDate()) {
            addRelaxedFilter(relaxedFilters, seenFilters, filters, new SearchFilters(
                filters.region(),
                null,
                filters.timeRange(),
                filters.personCount()
            ));
        }
        if (filters.hasDate() || filters.hasTimeRange()) {
            addRelaxedFilter(relaxedFilters, seenFilters, filters, new SearchFilters(
                filters.region(),
                null,
                "",
                filters.personCount()
            ));
        }
        if (filters.hasDate() || filters.hasRegion()) {
            addRelaxedFilter(relaxedFilters, seenFilters, filters, new SearchFilters(
                "",
                null,
                filters.timeRange(),
                filters.personCount()
            ));
        }
        if (filters.hasRegion() || filters.hasDate() || filters.hasTimeRange()) {
            addRelaxedFilter(relaxedFilters, seenFilters, filters, new SearchFilters(
                "",
                null,
                "",
                filters.personCount()
            ));
        }

        return relaxedFilters;
    }

    private void addRelaxedFilter(
        List<SearchFilters> relaxedFilters,
        Set<SearchFilters> seenFilters,
        SearchFilters originalFilters,
        SearchFilters candidateFilters
    ) {
        if (candidateFilters.active()
            && !candidateFilters.equals(originalFilters)
            && seenFilters.add(candidateFilters)) {
            relaxedFilters.add(candidateFilters);
        }
    }

    private List<ShowtimeCandidate> findUpcomingCandidates(
        SearchFilters filters,
        LocalDateTime minStartsAt,
        int candidateLimit,
        Set<String> requiredGenreValues
    ) {
        boolean hasRequiredGenres = requiredGenreValues != null && !requiredGenreValues.isEmpty();
        List<ShowtimeCandidate> candidates;
        if (filters != null && filters.active()) {
            candidates = hasRequiredGenres
                ? showtimeRepository.findUpcomingCandidates(candidateLimit, minStartsAt, filters, requiredGenreValues)
                : showtimeRepository.findUpcomingCandidates(candidateLimit, minStartsAt, filters);
        } else {
            candidates = hasRequiredGenres
                ? showtimeRepository.findUpcomingCandidates(candidateLimit, minStartsAt, null, requiredGenreValues)
                : showtimeRepository.findUpcomingCandidates(candidateLimit, minStartsAt);
        }
        return candidates == null ? List.of() : candidates;
    }

    private RecommendationResponse noCandidateResponse(
        Instant startedAt,
        String runId,
        String anonymousId,
        RecommendationMode mode,
        String modelName,
        RecommendationRequest normalizedRequest,
        String status,
        String message
    ) {
        RecommendationResponse response = new RecommendationResponse(
            runId,
            mode.wireValue(),
            modelName,
            status,
            message,
            List.of()
        );
        saveRunBestEffort(
            runId,
            anonymousId,
            mode.wireValue(),
            modelName,
            normalizedRequest,
            List.of(),
            null,
            response,
            elapsedMs(startedAt)
        );
        return response;
    }

    private void saveRunBestEffort(
        String runId,
        String anonymousId,
        String mode,
        String modelName,
        RecommendationRequest request,
        Object candidateScores,
        String aiResponseJson,
        RecommendationResponse response,
        long elapsedMs
    ) {
        try {
            profileRepository.saveRun(
                runId,
                anonymousId,
                mode,
                modelName,
                request,
                candidateScores,
                aiResponseJson,
                response,
                elapsedMs
            );
        } catch (RuntimeException exception) {
            logger.warn(
                "Recommendation run persistence failed. runId={} anonymousId={} status={}",
                runId,
                anonymousId,
                response == null ? "" : response.status(),
                exception
            );
        }
    }

    public FeedbackResponse feedback(String runId, FeedbackRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("피드백 요청이 필요해.");
        }
        String anonymousId = normalizeAnonymousId(request.anonymousId());
        if (anonymousId.isBlank()) {
            throw new IllegalArgumentException("anonymousId가 필요해.");
        }
        if (runId == null || runId.isBlank()) {
            throw new IllegalArgumentException("runId가 필요해.");
        }
        FeedbackAction action = FeedbackAction.from(request.action());
        Map<String, Integer> delta = showtimeRepository.findByShowtimeId(request.showtimeId())
            .map(candidate -> feedbackDelta(candidate, action))
            .orElseGet(Map::of);
        RecommendationProfile profile = profileRepository.findProfile(anonymousId)
            .orElseGet(() -> new RecommendationProfile(anonymousId, Map.of()));
        Map<String, Integer> merged = new LinkedHashMap<>(profile.tagWeights());
        delta.forEach((key, value) -> merged.merge(key, value, Integer::sum));
        profileRepository.updateWeights(anonymousId, merged);
        profileRepository.saveFeedback(
            runId,
            anonymousId,
            request.movieId(),
            request.showtimeId(),
            action,
            delta
        );
        return new FeedbackResponse(true, delta);
    }

    private List<RecommendationItem> itemsFromAi(
        List<ScoredCandidate> rankedCandidates,
        List<AiPick> picks,
        TagProfile profile,
        RecommendationMode mode,
        AiProvider provider
    ) {
        Map<Long, ScoredCandidate> byShowtime = rankedCandidates.stream()
            .collect(Collectors.toMap(
                candidate -> candidate.candidate().showtimeId(),
                Function.identity(),
                (left, right) -> left,
                LinkedHashMap::new
            ));
        Map<Long, AiPick> pickByShowtime = picks.stream()
            .collect(Collectors.toMap(
                AiPick::showtimeId,
                Function.identity(),
                (left, right) -> left,
                LinkedHashMap::new
            ));
        List<AiPick> sortedPicks = picks.stream()
            .filter(pick -> byShowtime.containsKey(pick.showtimeId()))
            .sorted(Comparator.comparingInt((AiPick pick) ->
                validatedModelScore(pick, byShowtime.get(pick.showtimeId()), profile)
            ).reversed())
            .toList();
        List<ScoredCandidate> ordered = new ArrayList<>();
        for (AiPick pick : sortedPicks) {
            ScoredCandidate scored = byShowtime.remove(pick.showtimeId());
            if (scored != null) {
                ordered.add(scored);
            }
        }
        ordered.addAll(byShowtime.values());
        return selectDistinctMovieItems(ordered)
            .stream()
            .map(scored -> {
                AiPick pick = pickByShowtime.get(scored.candidate().showtimeId());
                if (pick == null) {
                    return fallbackItem(scored, profile, mode, provider);
                }
                return toItem(
                    scored,
                    qualityReason(pick.reason(), scored, mode, provider),
                    pick.caution(),
                    qualityValuePoint(pick.valuePoint(), scored.candidate(), mode, provider),
                    profile,
                    mode,
                    provider,
                    qualityAnalysisPoint(pick.analysisPoint(), scored, profile, mode, provider),
                    validatedModelScore(pick, scored, profile)
                );
            })
            .toList();
    }

    private List<RecommendationItem> fallbackItems(
        List<ScoredCandidate> rankedCandidates,
        TagProfile profile,
        RecommendationMode mode,
        AiProvider provider
    ) {
        return selectDistinctMovieItems(rankedCandidates)
            .stream()
            .map(scored -> fallbackItem(scored, profile, mode, provider))
            .toList();
    }

    private int validatedModelScore(AiPick pick, ScoredCandidate scored, TagProfile profile) {
        int score = pick == null || pick.score() == null ? scored.score() : clampScore(pick.score());
        if (hasTasteAnchor(profile) && !hasGenreOverlap(scored, tasteAnchorGenres(profile))) {
            score = Math.min(score, NO_DIRECT_TASTE_MATCH_CAP);
        }
        return score;
    }

    private int fallbackScore(ScoredCandidate scored, TagProfile profile) {
        int score = clampScore(scored.score());
        if (hasTasteAnchor(profile) && !hasGenreOverlap(scored, tasteAnchorGenres(profile))) {
            return Math.min(score, NO_DIRECT_TASTE_MATCH_CAP);
        }
        if (hasTasteAnchor(profile)) {
            return Math.max(76, score);
        }
        return score;
    }

    private int clampScore(int score) {
        return Math.max(0, Math.min(100, score));
    }

    private boolean hasTasteAnchor(TagProfile profile) {
        return !tasteAnchorGenres(profile).isEmpty();
    }

    private boolean hasSelectedGenreAnchor(TagProfile profile) {
        return profile != null && !profile.preferredGenres().isEmpty();
    }

    private Set<String> selectedGenreValues(TagProfile profile) {
        if (profile == null || profile.preferredGenres().isEmpty()) {
            return Set.of();
        }
        return profile.preferredGenres().stream()
            .map(tag -> tag == null ? "" : tag.trim().toLowerCase(Locale.ROOT))
            .map(tag -> tag.startsWith("genre:") ? tag.substring("genre:".length()) : tag)
            .filter(tag -> !tag.isBlank())
            .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private String selectedGenreNoMatchMessage(TagProfile profile) {
        String genreText = profile.preferredGenres().stream()
            .map(tag -> tag == null ? "" : tag.trim().toLowerCase(Locale.ROOT))
            .filter(tag -> !tag.isBlank())
            .map(tag -> tag.startsWith("genre:") ? tag.substring("genre:".length()) : tag)
            .map(this::genreLabel)
            .distinct()
            .collect(Collectors.joining(", "));
        if (genreText.isBlank()) {
            genreText = "선택한 장르";
        }
        return genreText + " 장르 태그가 붙은 현재 상영 후보가 없어. 제목만 보고 장르를 추정하지 않도록 추천을 멈췄어.";
    }

    private String qualityReason(String reason, ScoredCandidate scored, RecommendationMode mode, AiProvider provider) {
        String sanitized = sanitizeUserFacingText(reason);
        if (isNarrativeProvider(provider)) {
            if (!isWeakNarrativeReason(sanitized, scored.candidate())) {
                return limitText(sanitized, mode == RecommendationMode.PRECISE ? 220 : 170);
            }
            return groundedReason(scored);
        }
        if (isWeakReason(sanitized, scored.candidate())) {
            return groundedReason(scored);
        }
        String tags = normalizeReasonTags(sanitized);
        return tags.isBlank() ? groundedReason(scored) : tags;
    }

    private boolean isNarrativeProvider(AiProvider provider) {
        return provider == AiProvider.CODEX;
    }

    private boolean isWeakReason(String reason, ShowtimeCandidate candidate) {
        if (reason == null || reason.isBlank()) {
            return true;
        }
        String normalized = reason.trim();
        if (sameText(normalized, candidate.title())) {
            return true;
        }
        return GENERIC_REASON_PATTERN.matcher(normalized).find() || !hasUserFacingTag(normalized);
    }

    private boolean isWeakNarrativeReason(String reason, ShowtimeCandidate candidate) {
        if (reason == null || reason.isBlank()) {
            return true;
        }
        String normalized = reason.trim();
        if (sameText(normalized, candidate.title())) {
            return true;
        }
        return normalized.codePointCount(0, normalized.length()) < 10
            || isTagOnlyText(normalized)
            || GENERIC_REASON_PATTERN.matcher(normalized).find()
            || INTERNAL_TEXT_PATTERN.matcher(normalized).find();
    }

    private String groundedReason(ScoredCandidate scored) {
        ShowtimeCandidate candidate = scored.candidate();
        List<String> signals = contentSignals(scored, 2);
        String signalText = signals.isEmpty() ? "상영 조건" : String.join(", ", signals);
        if (scored.penalties().contains("taste_mismatch")) {
            return "선택한 장르와 직접 겹치지는 않지만 " + signalText + " 쪽 조건이 좋아서 예비 추천으로 넣었어.";
        }
        String title = displayText(candidate.title());
        String subject = title.isBlank() ? "이 후보" : title;
        return subject + "은 선택한 취향 신호와 " + signalText + "이 잘 맞아서 추천 순위를 높였어.";
    }

    private String qualityValuePoint(String valuePoint, ShowtimeCandidate candidate, RecommendationMode mode, AiProvider provider) {
        String sanitized = sanitizeUserFacingText(valuePoint);
        if (isNarrativeProvider(provider)) {
            if (!isWeakNarrativeValuePoint(sanitized, candidate)) {
                return limitText(sanitized, mode == RecommendationMode.PRECISE ? 170 : 140);
            }
            return groundedValuePoint(candidate);
        }
        if (isWeakValuePoint(sanitized, candidate)) {
            return groundedValuePoint(candidate);
        }
        String tags = normalizeValueTags(sanitized);
        return tags.isBlank() ? groundedValuePoint(candidate) : tags;
    }

    private boolean isWeakValuePoint(String valuePoint, ShowtimeCandidate candidate) {
        if (valuePoint == null || valuePoint.isBlank()) {
            return true;
        }
        String normalized = valuePoint.trim();
        if (sameText(normalized, candidate.theaterName())
            || sameText(normalized, candidate.screenName())
            || sameText(normalized, candidate.regionName())) {
            return true;
        }
        return !hasUserFacingTag(normalized);
    }

    private boolean isWeakNarrativeValuePoint(String valuePoint, ShowtimeCandidate candidate) {
        if (valuePoint == null || valuePoint.isBlank()) {
            return true;
        }
        String normalized = valuePoint.trim();
        if (sameText(normalized, candidate.theaterName())
            || sameText(normalized, candidate.screenName())
            || sameText(normalized, candidate.regionName())) {
            return true;
        }
        return normalized.codePointCount(0, normalized.length()) < 6
            || isTagOnlyText(normalized)
            || isTerseTimeOnlyValue(normalized)
            || INTERNAL_TEXT_PATTERN.matcher(normalized).find();
    }

    private boolean isTagOnlyText(String value) {
        if (value == null || value.isBlank() || !hasUserFacingTag(value)) {
            return false;
        }
        return USER_TAG_PATTERN.matcher(value).replaceAll("").replaceAll("[\\s·,./-]+", "").isBlank();
    }

    private boolean isTerseTimeOnlyValue(String value) {
        if (value == null || value.isBlank() || !value.matches(".*\\d{1,2}:\\d{2}.*")) {
            return false;
        }
        return value.replaceAll("[0-9:상영\\s./\\-]+", "").isBlank();
    }

    private String groundedValuePoint(ShowtimeCandidate candidate) {
        List<String> parts = new ArrayList<>();
        if (candidate.startsAt() != null) {
            parts.add(DateTimeFormatter.ofPattern("MM-dd HH:mm").format(candidate.startsAt()) + " 상영");
        }
        if (candidate.minPriceAmount() != null) {
            parts.add(candidate.minPriceAmount() + "원부터");
        }
        String seat = seatHint(candidate.remainingSeatCount(), candidate.totalSeatCount());
        if ("enough".equals(seat)) {
            parts.add("좌석 여유 있음");
        } else if ("limited".equals(seat)) {
            parts.add("좌석이 많지 않음");
        }
        if (!candidate.theaterName().isBlank()) {
            parts.add(displayText(candidate.theaterName()));
        }
        if (parts.isEmpty()) {
            return "예매 가능한 상영 후보라 바로 비교하기 좋아.";
        }
        return String.join(" · ", parts) + " 기준으로 예매 판단이 쉬워.";
    }

    private String groundedCaution(ScoredCandidate scored, TagProfile profile) {
        List<String> cautions = new ArrayList<>();
        if (hasTasteAnchor(profile) && !hasGenreOverlap(scored, tasteAnchorGenres(profile))) {
            cautions.add("선택한 장르와 직접 겹치는 후보는 아니라서 취향 우선순위는 한 단계 낮게 봤어");
        }
        scored.penalties().stream()
            .map(this::cautionLabel)
            .filter(value -> !value.isBlank())
            .forEach(cautions::add);
        ShowtimeCandidate candidate = scored.candidate();
        if ("limited".equals(seatHint(candidate.remainingSeatCount(), candidate.totalSeatCount()))) {
            cautions.add("남은 좌석이 많지 않아 예매 시점 확인이 필요해");
        }
        return cautions.stream().distinct().limit(2).collect(Collectors.joining(" "));
    }

    private String cautionLabel(String penalty) {
        if (penalty == null || penalty.isBlank()) {
            return "";
        }
        return switch (penalty.trim().toLowerCase(Locale.ROOT)) {
            case "taste_mismatch" -> "취향 직접 매칭은 약해";
            case "too_long" -> "러닝타임이 길 수 있어";
            case "violence" -> "폭력적인 장면이 부담될 수 있어";
            case "complex" -> "설정이나 전개가 복잡할 수 있어";
            case "sad_ending" -> "먹먹한 결말을 피하고 싶다면 주의가 필요해";
            case "loud" -> "큰 소리나 강한 자극이 있을 수 있어";
            default -> "";
        };
    }

    private List<String> contentSignals(ScoredCandidate scored, int limit) {
        List<String> signals = new ArrayList<>();
        scored.matchedTags().stream()
            .filter(this::isReasonSourceTag)
            .map(this::tagLabel)
            .filter(value -> !value.isBlank())
            .forEach(signals::add);
        if (signals.isEmpty()) {
            scored.candidate().allTags().stream()
                .filter(this::isReasonSourceTag)
                .map(this::tagLabel)
                .filter(value -> !value.isBlank())
                .forEach(signals::add);
        }
        ShowtimeCandidate candidate = scored.candidate();
        if (candidate.runtimeMinutes() != null && candidate.runtimeMinutes() <= 125) {
            signals.add("부담 적은 러닝타임");
        }
        if (!candidate.ageRating().isBlank()) {
            signals.add(displayText(candidate.ageRating()));
        }
        return signals.stream().distinct().limit(limit).toList();
    }

    private String tagLabel(String tag) {
        if (tag == null || tag.isBlank()) {
            return "";
        }
        String normalized = tag.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "audience:alone" -> "혼자 보기";
            case "audience:friends" -> "친구랑 보기";
            case "audience:date" -> "데이트";
            case "audience:family" -> "가족 관람";
            case "audience:child" -> "아이와 함께";
            case "mood:light" -> "가볍게 보기";
            case "mood:immersive" -> "몰입감";
            case "mood:exciting" -> "신나는 분위기";
            case "mood:calm" -> "잔잔한 분위기";
            case "mood:tense" -> "긴장감";
            case "mood:warm" -> "따뜻한 정서";
            case "mood:visual" -> "시각적 재미";
            case "pace:easy" -> "따라가기 쉬운 전개";
            case "pace:fast" -> "빠른 전개";
            case "pace:slow" -> "천천히 보는 흐름";
            default -> {
                if (normalized.startsWith("genre:")) {
                    yield genreLabel(normalized.substring("genre:".length())) + " 취향";
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
            case "pace:slow" -> "#천천히몰입";
            case "content:too_long" -> "#긴영화주의";
            case "content:complex" -> "#난도주의";
            case "content:violence" -> "#잔인함주의";
            case "content:sad_ending" -> "#슬픈결말주의";
            case "content:loud" -> "#큰소리주의";
            default -> {
                if (normalized.startsWith("genre:")) {
                    yield "#" + genreLabel(normalized.substring("genre:".length()));
                }
                yield "";
            }
        };
    }

    private boolean isReasonSourceTag(String tag) {
        if (tag == null) {
            return false;
        }
        String normalized = tag.trim().toLowerCase(Locale.ROOT);
        return !normalized.startsWith("content:") && !isGenericGenreTag(normalized);
    }

    private String genreLabel(String genre) {
        String normalized = genre == null ? "" : genre.trim().toLowerCase(Locale.ROOT).replace('_', '-');
        return switch (normalized) {
            case "popular" -> "\uC778\uAE30\uC791";
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
            default -> normalized.isBlank() ? "장르" : normalized;
        };
    }

    private boolean isGenericGenreTag(String tag) {
        if (tag == null || tag.isBlank()) {
            return false;
        }
        String normalized = tag.trim().toLowerCase(Locale.ROOT).replace('_', '-');
        if (normalized.startsWith("genre:")) {
            normalized = normalized.substring("genre:".length());
        }
        return normalized.equals("popular")
            || normalized.equals("general")
            || normalized.equals("general-content")
            || normalized.equals("mega only")
            || normalized.equals("mega-only")
            || normalized.equals("special-content")
            || normalized.equals("content-c")
            || normalized.equals("classic-content")
            || normalized.equals("classic-regular")
            || normalized.equals("concert-live")
            || normalized.equals("live-performance")
            || normalized.equals("\uC77C\uBC18\uCF58\uD150\uD2B8")
            || normalized.equals("\uC2A4\uD398\uC15C\uCF58\uD150\uD2B8")
            || normalized.equals("\uAD7F\uC988\uD328\uD0A4\uC9C0")
            || normalized.equals("\uCF58\uD150\uD2B8C")
            || normalized.equals("\uACF5\uC5F0\uC2E4\uD669")
            || normalized.equals("\uC5B4\uB9B0\uC774 \uACF5\uC5F0\uC2E4\uD669")
            || normalized.equals("\uD074\uB798\uC2DD-\uC815\uADDC")
            || normalized.equals("\uD074\uB798\uC2DD\uCF58\uD150\uD2B8");
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

    private String joinTags(List<String> tags, int limit) {
        return tags.stream()
            .filter(value -> value != null && !value.isBlank())
            .distinct()
            .limit(limit)
            .collect(Collectors.joining(" "));
    }

    private boolean hasUserFacingTag(String value) {
        return value != null && USER_TAG_PATTERN.matcher(value).find();
    }

    private String normalizeReasonTags(String value) {
        return joinTags(extractTags(value).stream()
            .filter(tag -> !isValueTag(tag))
            .toList(), 4);
    }

    private String normalizeValueTags(String value) {
        return joinTags(extractTags(value).stream()
            .filter(this::isValueTag)
            .toList(), 4);
    }

    private List<String> extractTags(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        List<String> tags = new ArrayList<>();
        var matcher = USER_TAG_PATTERN.matcher(value);
        while (matcher.find()) {
            tags.add(matcher.group());
        }
        return tags;
    }

    private boolean isValueTag(String tag) {
        if (tag == null || tag.isBlank()) {
            return false;
        }
        return tag.matches("#\\d{1,2}:\\d{2}(상영)?")
            || tag.matches("#\\d+원")
            || tag.equals("#좌석여유")
            || tag.equals("#좌석주의")
            || tag.equals("#예매가능")
            || tag.equals("#예매정보");
    }

    private boolean sameText(String left, String right) {
        if (left == null || right == null || left.isBlank() || right.isBlank()) {
            return false;
        }
        return left.trim().equalsIgnoreCase(right.trim());
    }

    private List<ScoredCandidate> selectDistinctMovieItems(List<ScoredCandidate> rankedCandidates) {
        return selectDistinctMovieItems(rankedCandidates, RESULT_LIMIT);
    }

    private List<ScoredCandidate> rankTasteAwareCandidates(
        List<ScoredCandidate> rankedCandidates,
        TagProfile profile
    ) {
        if (rankedCandidates == null || rankedCandidates.isEmpty() || profile == null) {
            return rankedCandidates == null ? List.of() : rankedCandidates;
        }

        List<ScoredCandidate> ordered = new ArrayList<>();
        Set<String> seenMovies = new LinkedHashSet<>();

        if (!profile.preferredGenres().isEmpty()) {
            List<ScoredCandidate> directPreferred = rankedCandidates.stream()
                .filter(scored -> hasGenreOverlap(scored, profile.preferredGenres()))
                .toList();
            appendDistinctMovieCandidates(ordered, seenMovies, directPreferred);
            appendDistinctMovieCandidates(ordered, seenMovies, rankedCandidates);
            return ordered;
        }

        Set<String> posterGenres = new LinkedHashSet<>(profile.likedGenres());
        posterGenres.removeAll(profile.preferredGenres());
        if (!posterGenres.isEmpty()) {
            List<ScoredCandidate> posterMatches = rankedCandidates.stream()
                .filter(scored -> hasGenreOverlap(scored, posterGenres))
                .toList();
            appendDistinctMovieCandidates(ordered, seenMovies, posterMatches);
        }

        if (profile.preferredGenres().isEmpty() && !profile.likedGenres().isEmpty()) {
            List<ScoredCandidate> likedMatches = rankedCandidates.stream()
                .filter(scored -> hasGenreOverlap(scored, profile.likedGenres()))
                .toList();
            appendDistinctMovieCandidates(ordered, seenMovies, likedMatches);
        }

        appendDistinctMovieCandidates(ordered, seenMovies, rankedCandidates);
        return ordered;
    }

    private void appendDistinctMovieCandidates(
        List<ScoredCandidate> ordered,
        Set<String> seenMovies,
        List<ScoredCandidate> candidates
    ) {
        for (ScoredCandidate scored : candidates) {
            if (seenMovies.add(movieKey(scored))) {
                ordered.add(scored);
            }
        }
    }

    private boolean hasGenreOverlap(ScoredCandidate scored, Set<String> genreTags) {
        if (scored == null || scored.candidate() == null || genreTags == null || genreTags.isEmpty()) {
            return false;
        }
        return scored.candidate().allTags().stream()
            .map(tag -> tag == null ? "" : tag.trim().toLowerCase(Locale.ROOT))
            .anyMatch(tag -> tag.startsWith("genre:") && genreTags.contains(tag));
    }

    private List<ScoredCandidate> selectDistinctMovieItems(List<ScoredCandidate> rankedCandidates, int limit) {
        List<ScoredCandidate> selected = new ArrayList<>();
        Set<String> seenMovies = new LinkedHashSet<>();
        for (ScoredCandidate scored : rankedCandidates) {
            if (selected.size() >= limit) {
                break;
            }
            String movieKey = movieKey(scored);
            if (seenMovies.add(movieKey)) {
                selected.add(scored);
            }
        }
        return selected;
    }

    private RecommendationItem fallbackItem(
        ScoredCandidate scored,
        TagProfile profile,
        RecommendationMode mode,
        AiProvider provider
    ) {
        ShowtimeCandidate candidate = scored.candidate();
        int resolvedScore = fallbackScore(scored, profile);
        String reason = groundedReason(scored);
        String caution = groundedCaution(scored, profile);
        String valuePoint = groundedValuePoint(candidate);
        String resolvedAnalysisPoint = analysisPoint(scored, profile);
        return new RecommendationItem(
            candidate.movieId(),
            candidate.showtimeId(),
            displayText(candidate.title()),
            resolvedScore,
            blankToDefault(sanitizeUserFacingText(reason), "조건에 가장 근접한 후보야."),
            resolvedAnalysisPoint,
            blankToDefault(sanitizeUserFacingText(caution), ""),
            blankToDefault(sanitizeUserFacingText(valuePoint), valuePoint(candidate)),
            candidate.providerCode(),
            displayText(candidate.theaterName()),
            displayText(candidate.regionName()),
            displayText(candidate.screenName()),
            candidate.showDate(),
            candidate.startsAt(),
            candidate.minPriceAmount(),
            candidate.currencyCode(),
            candidate.bookingUrl(),
            candidate.posterUrl()
        );
    }

    private RecommendationItem toItem(
        ScoredCandidate scored,
        String reason,
        String caution,
        String valuePoint,
        TagProfile profile,
        RecommendationMode mode,
        AiProvider provider,
        String analysisPoint,
        int resolvedScore
    ) {
        return toItemScored(scored, reason, caution, valuePoint, profile, mode, provider, analysisPoint, resolvedScore);
    }

    private RecommendationItem toItem(
        ScoredCandidate scored,
        String reason,
        String caution,
        String valuePoint,
        TagProfile profile,
        RecommendationMode mode,
        AiProvider provider,
        String analysisPoint
    ) {
        ShowtimeCandidate candidate = scored.candidate();
        int resolvedScore = scored.score();
        String resolvedAnalysisPoint = isNarrativeProvider(provider) || mode == RecommendationMode.PRECISE
            ? blankToDefault(sanitizeUserFacingText(analysisPoint), analysisPoint(scored, profile))
            : "";
        return new RecommendationItem(
            candidate.movieId(),
            candidate.showtimeId(),
            displayText(candidate.title()),
            resolvedScore,
            blankToDefault(sanitizeUserFacingText(reason), "조건에 가장 근접한 후보야."),
            resolvedAnalysisPoint,
            blankToDefault(sanitizeUserFacingText(caution), ""),
            blankToDefault(sanitizeUserFacingText(valuePoint), valuePoint(candidate)),
            candidate.providerCode(),
            displayText(candidate.theaterName()),
            displayText(candidate.regionName()),
            displayText(candidate.screenName()),
            candidate.showDate(),
            candidate.startsAt(),
            candidate.minPriceAmount(),
            candidate.currencyCode(),
            candidate.bookingUrl(),
            candidate.posterUrl()
        );
    }

    private RecommendationItem toItemScored(
        ScoredCandidate scored,
        String reason,
        String caution,
        String valuePoint,
        TagProfile profile,
        RecommendationMode mode,
        AiProvider provider,
        String analysisPoint,
        int resolvedScore
    ) {
        ShowtimeCandidate candidate = scored.candidate();
        String resolvedAnalysisPoint = isNarrativeProvider(provider) || mode == RecommendationMode.PRECISE
            ? blankToDefault(sanitizeUserFacingText(analysisPoint), analysisPoint(scored, profile))
            : "";
        return new RecommendationItem(
            candidate.movieId(),
            candidate.showtimeId(),
            displayText(candidate.title()),
            resolvedScore,
            blankToDefault(sanitizeUserFacingText(reason), "조건에 가장 근접한 후보야."),
            resolvedAnalysisPoint,
            blankToDefault(sanitizeUserFacingText(caution), ""),
            blankToDefault(sanitizeUserFacingText(valuePoint), valuePoint(candidate)),
            candidate.providerCode(),
            displayText(candidate.theaterName()),
            displayText(candidate.regionName()),
            displayText(candidate.screenName()),
            candidate.showDate(),
            candidate.startsAt(),
            candidate.minPriceAmount(),
            candidate.currencyCode(),
            candidate.bookingUrl(),
            candidate.posterUrl()
        );
    }

    private String valuePoint(ShowtimeCandidate candidate) {
        return groundedValuePoint(candidate);
    }

    private String qualityAnalysisPoint(
        String analysisPoint,
        ScoredCandidate scored,
        TagProfile profile,
        RecommendationMode mode,
        AiProvider provider
    ) {
        if (isNarrativeProvider(provider)) {
            String sanitized = sanitizeUserFacingText(analysisPoint);
            if (!sanitized.isBlank() && !INTERNAL_TEXT_PATTERN.matcher(sanitized).find()) {
                return limitText(sanitized, mode == RecommendationMode.PRECISE ? 320 : 180);
            }
            return analysisPoint(scored, profile);
        }
        if (mode != RecommendationMode.PRECISE) {
            return "";
        }
        String sanitized = sanitizeUserFacingText(analysisPoint);
        String tag = extractTags(sanitized).stream()
            .filter(this::isAnalysisTag)
            .findFirst()
            .orElse("");
        return tag.isBlank() ? analysisPoint(scored, profile) : tag;
    }

    private boolean isAnalysisTag(String tag) {
        if (tag == null || tag.isBlank() || isValueTag(tag)) {
            return false;
        }
        String normalized = tag.trim().toLowerCase(Locale.ROOT);
        int length = tag.codePointCount(0, tag.length());
        return length <= 18
            && !normalized.contains("_")
            && !normalized.contains("주의")
            && !normalized.contains("잔인")
            && !normalized.contains("폭력")
            && !normalized.contains("시끄")
            && !normalized.contains("슬픔")
            && !normalized.contains("긴영화");
    }

    private String analysisPoint(ScoredCandidate scored, TagProfile profile) {
        ShowtimeCandidate candidate = scored.candidate();
        Set<String> likedGenres = tasteAnchorGenres(profile);
        for (String tag : candidate.allTags()) {
            String normalized = tag == null ? "" : tag.toLowerCase(Locale.ROOT);
            if (normalized.startsWith("genre:") && !isGenericGenreTag(normalized) && likedGenres.contains(normalized)) {
                return "선택한 " + genreLabel(normalized.substring("genre:".length())) + " 취향과 후보 장르가 직접 겹쳐서 코드 점수에서 우선순위를 높였어.";
            }
        }
        if (scored.penalties().contains("taste_mismatch")) {
            return "취향 장르 직접 매칭은 약하지만, 상영 조건과 다른 취향 신호가 보조 근거로 잡혔어.";
        }
        return candidate.allTags().stream()
            .filter(tag -> tag != null && tag.toLowerCase(Locale.ROOT).startsWith("genre:") && !isGenericGenreTag(tag))
            .map(tag -> genreLabel(tag.substring("genre:".length())) + " 계열 신호와 상영 조건을 같이 본 코드 점수 근거야.")
            .findFirst()
            .orElse("장르 신호와 예매 조건을 같이 본 코드 점수 근거야.");
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

    private Map<String, Integer> feedbackDelta(ShowtimeCandidate candidate, FeedbackAction action) {
        int direction = switch (action) {
            case LIKE -> 2;
            case DISLIKE -> -3;
            case BOOKING_VIEW -> 3;
        };
        Map<String, Integer> delta = new LinkedHashMap<>();
        candidate.allTags().forEach(tag -> delta.put(tag, direction));
        return delta;
    }

    private String blankToDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private String limitText(String value, int maxLength) {
        if (value == null || value.isBlank()) {
            return "";
        }
        String trimmed = value.trim();
        if (trimmed.codePointCount(0, trimmed.length()) <= maxLength) {
            return trimmed;
        }
        return trimmed.substring(0, trimmed.offsetByCodePoints(0, Math.max(1, maxLength - 1))).trim() + "…";
    }

    private String sanitizeUserFacingText(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        String sanitized = INTERNAL_TEXT_PATTERN.matcher(decodeHtmlEntities(value)).replaceAll(" ");
        sanitized = sanitized.replaceAll("\\s+", " ").trim();
        return sanitized.replaceAll("[\\p{Punct}\\s]+", "").isBlank() ? "" : sanitized;
    }

    private String displayText(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        return decodeHtmlEntities(value).replaceAll("\\s+", " ").trim();
    }

    private String decodeHtmlEntities(String value) {
        if (value == null || value.isBlank() || !value.contains("&")) {
            return value == null ? "" : value;
        }
        Matcher matcher = HTML_ENTITY_PATTERN.matcher(value);
        StringBuilder decoded = new StringBuilder();
        while (matcher.find()) {
            matcher.appendReplacement(decoded, Matcher.quoteReplacement(resolveHtmlEntity(matcher.group(1))));
        }
        matcher.appendTail(decoded);
        return decoded.toString();
    }

    private String resolveHtmlEntity(String entity) {
        if (entity == null || entity.isBlank()) {
            return "";
        }
        if (entity.startsWith("#x") || entity.startsWith("#X")) {
            return codePointEntity(entity.substring(2), 16, "&" + entity + ";");
        }
        if (entity.startsWith("#")) {
            return codePointEntity(entity.substring(1), 10, "&" + entity + ";");
        }
        return switch (entity) {
            case "amp" -> "&";
            case "lt" -> "<";
            case "gt" -> ">";
            case "quot" -> "\"";
            case "apos" -> "'";
            default -> "&" + entity + ";";
        };
    }

    private String codePointEntity(String value, int radix, String fallback) {
        try {
            int codePoint = Integer.parseInt(value, radix);
            if (!Character.isValidCodePoint(codePoint)) {
                return fallback;
            }
            return new String(Character.toChars(codePoint));
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }

    private String movieKey(ScoredCandidate scored) {
        ShowtimeCandidate candidate = scored.candidate();
        String title = candidate.title();
        if (title != null && !title.isBlank()) {
            return "title:" + title.trim().toLowerCase(Locale.ROOT);
        }
        String externalMovieId = candidate.externalMovieId();
        if (externalMovieId != null && !externalMovieId.isBlank()) {
            return candidate.providerCode() + ":" + externalMovieId.trim();
        }
        Long movieId = candidate.movieId();
        return movieId == null ? "showtime:" + candidate.showtimeId() : "movie:" + movieId;
    }

    private long elapsedMs(Instant startedAt) {
        return Duration.between(startedAt, Instant.now()).toMillis();
    }

    private record CandidateSearchResult(
        List<ShowtimeCandidate> candidates,
        SearchFilters filters,
        String message
    ) {
        CandidateSearchResult {
            candidates = candidates == null ? List.of() : List.copyOf(candidates);
            message = message == null ? "" : message;
        }
    }
}
