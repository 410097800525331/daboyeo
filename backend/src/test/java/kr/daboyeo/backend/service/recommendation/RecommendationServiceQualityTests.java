package kr.daboyeo.backend.service.recommendation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import kr.daboyeo.backend.config.RecommendationProperties;
import kr.daboyeo.backend.domain.recommendation.RecommendationModels.AiPick;
import kr.daboyeo.backend.domain.recommendation.RecommendationModels.AiResult;
import kr.daboyeo.backend.domain.recommendation.RecommendationModels.PosterChoices;
import kr.daboyeo.backend.domain.recommendation.RecommendationModels.RecommendationMode;
import kr.daboyeo.backend.domain.recommendation.RecommendationModels.RecommendationProfile;
import kr.daboyeo.backend.domain.recommendation.RecommendationModels.RecommendationRequest;
import kr.daboyeo.backend.domain.recommendation.RecommendationModels.RecommendationResponse;
import kr.daboyeo.backend.domain.recommendation.RecommendationModels.RecommendationSurvey;
import kr.daboyeo.backend.domain.recommendation.RecommendationModels.ScoredCandidate;
import kr.daboyeo.backend.domain.recommendation.RecommendationModels.ShowtimeCandidate;
import kr.daboyeo.backend.domain.recommendation.RecommendationModels.TagProfile;
import kr.daboyeo.backend.repository.recommendation.RecommendationProfileRepository;
import kr.daboyeo.backend.repository.recommendation.ShowtimeRecommendationRepository;
import org.junit.jupiter.api.Test;

class RecommendationServiceQualityTests {

    private final PosterSeedService posterSeedService = mock(PosterSeedService.class);
    private final PreferenceProfileBuilder profileBuilder = mock(PreferenceProfileBuilder.class);
    private final RecommendationScorer scorer = mock(RecommendationScorer.class);
    private final CodexRecommendationClient codexClient = mock(CodexRecommendationClient.class);
    private final RecommendationProfileRepository profileRepository = mock(RecommendationProfileRepository.class);
    private final ShowtimeRecommendationRepository showtimeRepository = mock(ShowtimeRecommendationRepository.class);

    @Test
    void preciseAiResultsPreferDistinctMoviesWhenEnoughDistinctChoicesExist() {
        RecommendationService service = service();
        ShowtimeCandidate first = candidate(1L, 10L, "First A");
        ShowtimeCandidate second = candidate(2L, 10L, "First A");
        ShowtimeCandidate third = candidate(3L, 11L, "Second");
        ShowtimeCandidate fourth = candidate(4L, 12L, "Third");
        List<ShowtimeCandidate> candidates = List.of(first, second, third, fourth);
        List<ScoredCandidate> scored = List.of(
            scored(first, 94),
            scored(second, 91),
            scored(third, 88),
            scored(fourth, 80)
        );
        when(showtimeRepository.findUpcomingCandidates(anyInt(), any(LocalDateTime.class))).thenReturn(candidates);
        when(showtimeRepository.countStoredShowtimes()).thenReturn(4);
        when(scorer.score(any(TagProfile.class), anyList(), any())).thenReturn(scored);
        when(codexClient.rankAndExplain(eq(RecommendationMode.PRECISE), any(TagProfile.class), anyList()))
            .thenReturn(Optional.of(new AiResult(
                "{\"recommendations\":[]}",
                "codex-test",
                List.of(
                    new AiPick(1L, "first", "ok", "time"),
                    new AiPick(2L, "second", "ok", "time"),
                    new AiPick(3L, "third", "ok", "time")
                )
            )));

        RecommendationResponse response = service.recommend(request("precise"));

        assertThat(response.recommendations()).hasSize(3);
        assertThat(response.recommendations()).extracting("movieId").containsExactly(10L, 11L, 12L);
    }

    @Test
    void fallbackResultsPreferDistinctMoviesWhenEnoughDistinctChoicesExist() {
        RecommendationService service = service();
        ShowtimeCandidate first = candidate(1L, 10L, "First A");
        ShowtimeCandidate second = candidate(2L, 10L, "First A");
        ShowtimeCandidate third = candidate(3L, 11L, "Second");
        ShowtimeCandidate fourth = candidate(4L, 12L, "Third");
        List<ShowtimeCandidate> candidates = List.of(first, second, third, fourth);
        List<ScoredCandidate> scored = List.of(
            scored(first, 94),
            scored(second, 91),
            scored(third, 88),
            scored(fourth, 80)
        );
        when(showtimeRepository.findUpcomingCandidates(anyInt(), any(LocalDateTime.class))).thenReturn(candidates);
        when(showtimeRepository.countStoredShowtimes()).thenReturn(4);
        when(scorer.score(any(TagProfile.class), anyList(), any())).thenReturn(scored);
        when(codexClient.rankAndExplain(any(), any(), anyList())).thenReturn(Optional.empty());

        RecommendationResponse response = service.recommend(request());

        assertThat(response.status()).isEqualTo("fallback");
        assertThat(response.recommendations()).hasSize(3);
        assertThat(response.recommendations()).extracting("movieId").containsExactly(10L, 11L, 12L);
    }

    @Test
    void recommendationDisplayTextDecodesHtmlEntities() {
        RecommendationService service = service();
        ShowtimeCandidate first = candidateWithDisplayText(
            1L,
            10L,
            "&#40;Dub&#41; A&amp;B",
            "A&#39;s Cinema",
            "Seoul &gt; East",
            "1&amp;2"
        );
        List<ShowtimeCandidate> candidates = List.of(first);
        List<ScoredCandidate> scored = List.of(scored(first, 94));
        when(showtimeRepository.findUpcomingCandidates(anyInt(), any(LocalDateTime.class))).thenReturn(candidates);
        when(showtimeRepository.countStoredShowtimes()).thenReturn(1);
        when(scorer.score(any(TagProfile.class), anyList(), any())).thenReturn(scored);
        when(codexClient.rankAndExplain(any(), any(), anyList())).thenReturn(Optional.empty());

        RecommendationResponse response = service.recommend(request());

        assertThat(response.recommendations()).hasSize(1);
        assertThat(response.recommendations().get(0).title()).isEqualTo("(Dub) A&B");
        assertThat(response.recommendations().get(0).theaterName()).isEqualTo("A's Cinema");
        assertThat(response.recommendations().get(0).regionName()).isEqualTo("Seoul > East");
        assertThat(response.recommendations().get(0).screenName()).isEqualTo("1&2");
    }

    @Test
    void preciseAiResultsTreatSameTitleAsSameMovieWhenIdsDiffer() {
        RecommendationService service = service();
        ShowtimeCandidate first = candidate(1L, 10L, "Same Movie");
        ShowtimeCandidate second = candidate(2L, 20L, "Same Movie");
        ShowtimeCandidate third = candidate(3L, 30L, "Other A");
        ShowtimeCandidate fourth = candidate(4L, 40L, "Other B");
        List<ShowtimeCandidate> candidates = List.of(first, second, third, fourth);
        List<ScoredCandidate> scored = List.of(
            scored(first, 94),
            scored(second, 91),
            scored(third, 88),
            scored(fourth, 80)
        );
        when(showtimeRepository.findUpcomingCandidates(anyInt(), any(LocalDateTime.class))).thenReturn(candidates);
        when(showtimeRepository.countStoredShowtimes()).thenReturn(4);
        when(scorer.score(any(TagProfile.class), anyList(), any())).thenReturn(scored);
        when(codexClient.rankAndExplain(eq(RecommendationMode.PRECISE), any(TagProfile.class), anyList()))
            .thenReturn(Optional.of(new AiResult(
                "{\"recommendations\":[]}",
                "codex-test",
                List.of(
                    new AiPick(1L, "first", "ok", "time"),
                    new AiPick(2L, "second", "ok", "time"),
                    new AiPick(3L, "third", "ok", "time")
                )
            )));

        RecommendationResponse response = service.recommend(request("precise"));

        assertThat(response.recommendations()).hasSize(3);
        assertThat(response.recommendations()).extracting("title").containsExactly("Same Movie", "Other A", "Other B");
    }

    @Test
    void aiTextIsSanitizedBeforeReturningToClient() {
        RecommendationService service = service();
        ShowtimeCandidate first = candidate(1L, 10L, "First A");
        List<ShowtimeCandidate> candidates = List.of(first);
        List<ScoredCandidate> scored = List.of(scored(first, 94));
        when(showtimeRepository.findUpcomingCandidates(anyInt(), any(LocalDateTime.class))).thenReturn(candidates);
        when(showtimeRepository.countStoredShowtimes()).thenReturn(1);
        when(scorer.score(any(TagProfile.class), anyList(), any())).thenReturn(scored);
        when(codexClient.rankAndExplain(eq(RecommendationMode.PRECISE), any(TagProfile.class), anyList()))
            .thenReturn(Optional.of(new AiResult(
                "{\"recommendations\":[]}",
                "codex-test",
                List.of(new AiPick(
                    1L,
                    "score 60 mood:exciting matchedTags content:loud",
                    "penalties score 60",
                    "matchedTags score 60 content:loud"
                ))
            )));

        RecommendationResponse response = service.recommend(request("precise"));

        assertThat(response.recommendations()).hasSize(1);
        assertThat(response.recommendations().get(0).reason()).doesNotContain("score 60", "mood:exciting", "matchedTags", "content:loud");
        assertThat(response.recommendations().get(0).reason()).contains("First A");
        assertThat(response.recommendations().get(0).analysisPoint()).isNotBlank();
        assertThat(response.recommendations().get(0).caution()).isBlank();
        assertThat(response.recommendations().get(0).valuePoint()).doesNotContain("score 60", "matchedTags", "content:loud");
        assertThat(response.recommendations().get(0).valuePoint()).contains("상영", "12000원");
    }

    @Test
    void weakAiReasonAndTheaterOnlyValuePointAreReplaced() {
        RecommendationService service = service();
        ShowtimeCandidate first = candidate(1L, 10L, "First A");
        List<ShowtimeCandidate> candidates = List.of(first);
        List<ScoredCandidate> scored = List.of(scored(first, 94));
        when(showtimeRepository.findUpcomingCandidates(anyInt(), any(LocalDateTime.class))).thenReturn(candidates);
        when(showtimeRepository.countStoredShowtimes()).thenReturn(1);
        when(scorer.score(any(TagProfile.class), anyList(), any())).thenReturn(scored);
        when(codexClient.rankAndExplain(eq(RecommendationMode.PRECISE), any(TagProfile.class), anyList()))
            .thenReturn(Optional.of(new AiResult(
                "{\"r\":[]}",
                "codex-test",
                List.of(new AiPick(
                    1L,
                    "선택한 분위기와 겹치는 신호가 있어서 우선 추천했어.",
                    "",
                    "Test Theater"
                ))
            )));

        RecommendationResponse response = service.recommend(request("precise"));

        assertThat(response.recommendations()).hasSize(1);
        assertThat(response.recommendations().get(0).reason()).doesNotContain("겹치는 신호", "우선 추천");
        assertThat(response.recommendations().get(0).reason()).contains("First A", "취향");
        assertThat(response.recommendations().get(0).analysisPoint()).isNotBlank();
        assertThat(response.recommendations().get(0).valuePoint()).isNotEqualTo("Test Theater");
        assertThat(response.recommendations().get(0).valuePoint()).contains("상영", "12000원", "Test Theater");
    }

    @Test
    void terseTimeOnlyValuePointIsReplaced() {
        RecommendationService service = service();
        ShowtimeCandidate first = candidate(1L, 10L, "First A");
        List<ShowtimeCandidate> candidates = List.of(first);
        List<ScoredCandidate> scored = List.of(scored(first, 94));
        when(showtimeRepository.findUpcomingCandidates(anyInt(), any(LocalDateTime.class))).thenReturn(candidates);
        when(showtimeRepository.countStoredShowtimes()).thenReturn(1);
        when(scorer.score(any(TagProfile.class), anyList(), any())).thenReturn(scored);
        when(codexClient.rankAndExplain(eq(RecommendationMode.PRECISE), any(TagProfile.class), anyList()))
            .thenReturn(Optional.of(new AiResult(
                "{\"r\":[]}",
                "codex-test",
                List.of(new AiPick(
                    1L,
                    "친구랑 가볍게 보기 좋아.",
                    "",
                    "15:40 상영"
                ))
            )));

        RecommendationResponse response = service.recommend(request("precise"));

        assertThat(response.recommendations()).hasSize(1);
        assertThat(response.recommendations().get(0).valuePoint()).isNotEqualTo("15:40 상영");
        assertThat(response.recommendations().get(0).valuePoint()).contains("상영", "12000원");
    }

    @Test
    void mixedAiTagsAreSeparatedIntoReasonAndValuePointTags() {
        RecommendationService service = service();
        ShowtimeCandidate first = candidate(1L, 10L, "First A");
        List<ShowtimeCandidate> candidates = List.of(first);
        List<ScoredCandidate> scored = List.of(scored(first, 94));
        when(showtimeRepository.findUpcomingCandidates(anyInt(), any(LocalDateTime.class))).thenReturn(candidates);
        when(showtimeRepository.countStoredShowtimes()).thenReturn(1);
        when(scorer.score(any(TagProfile.class), anyList(), any())).thenReturn(scored);
        when(codexClient.rankAndExplain(eq(RecommendationMode.PRECISE), any(TagProfile.class), anyList()))
            .thenReturn(Optional.of(new AiResult(
                "{\"r\":[]}",
                "codex-test",
                List.of(new AiPick(
                    1L,
                    "#가볍게 #17:00상영 #좌석여유",
                    "",
                    "#신나는 #12세 #17:00상영 #좌석여유"
                ))
            )));

        RecommendationResponse response = service.recommend(request("precise"));

        assertThat(response.recommendations()).hasSize(1);
        assertThat(response.recommendations().get(0).reason()).contains("First A");
        assertThat(response.recommendations().get(0).analysisPoint()).isNotBlank();
        assertThat(response.recommendations().get(0).valuePoint()).contains("상영", "좌석");
    }

    @Test
    void preciseModeUsesAiGeneratedAnalysisPoint() {
        RecommendationService service = service();
        ShowtimeCandidate first = candidate(1L, 10L, "First A");
        List<ShowtimeCandidate> candidates = List.of(first);
        List<ScoredCandidate> scored = List.of(scored(first, 94));
        when(showtimeRepository.findUpcomingCandidates(anyInt(), any(LocalDateTime.class))).thenReturn(candidates);
        when(showtimeRepository.countStoredShowtimes()).thenReturn(1);
        when(scorer.score(any(TagProfile.class), anyList(), any())).thenReturn(scored);
        when(codexClient.rankAndExplain(eq(RecommendationMode.PRECISE), any(TagProfile.class), anyList()))
            .thenReturn(Optional.of(new AiResult(
                "{\"r\":[1]}",
                "codex-test",
                List.of(new AiPick(1L, "", "", "", "#몰입취향"))
            )));

        RecommendationResponse response = service.recommend(request("precise"));

        assertThat(response.recommendations()).hasSize(1);
        assertThat(response.recommendations().get(0).analysisPoint()).isEqualTo("#몰입취향");
    }

    @Test
    void gptPreciseKeepsNarrativeReasonAnalysisValueAndCaution() {
        RecommendationService service = service();
        ShowtimeCandidate first = candidate(1L, 10L, "First A");
        List<ShowtimeCandidate> candidates = List.of(first);
        List<ScoredCandidate> scored = List.of(scored(first, 94));
        when(showtimeRepository.findUpcomingCandidates(anyInt(), any(LocalDateTime.class))).thenReturn(candidates);
        when(showtimeRepository.countStoredShowtimes()).thenReturn(1);
        when(scorer.score(any(TagProfile.class), anyList(), any())).thenReturn(scored);
        when(codexClient.rankAndExplain(eq(RecommendationMode.PRECISE), any(TagProfile.class), anyList()))
            .thenReturn(Optional.of(new AiResult(
                "{\"r\":[]}",
                "gpt-test",
                List.of(new AiPick(
                    1L,
                    "친구와 가볍게 보기 좋은 리듬이라 지금 조건에 잘 맞아.",
                    "복잡한 이야기를 피하고 싶다면 중반 정보량만 살짝 보면 돼.",
                    "17:00 상영이고 좌석 여유가 있어 바로 예매 판단이 쉬워.",
                    "선택한 포스터 취향과 오늘 컨디션이 둘 다 밝은 톤으로 이어져."
                ))
            )));

        RecommendationResponse response = service.recommend(request("precise", "gpt"));

        assertThat(response.recommendations()).hasSize(1);
        assertThat(response.recommendations().get(0).reason()).contains("친구", "조건");
        assertThat(response.recommendations().get(0).analysisPoint()).contains("포스터", "컨디션");
        assertThat(response.recommendations().get(0).valuePoint()).contains("17:00", "좌석");
        assertThat(response.recommendations().get(0).caution()).contains("복잡한");
    }

    private RecommendationService service() {
        when(codexClient.bridgeTokenConfigured()).thenReturn(true);
        when(profileRepository.findProfile("anon_test"))
            .thenReturn(Optional.of(new RecommendationProfile("anon_test", Map.of())));
        TagProfile profile = new TagProfile();
        profile.addLikedGenre("animation");
        when(profileBuilder.build(any(), any(), any())).thenReturn(profile);
        RecommendationProperties properties = new RecommendationProperties(
            20,
            5,
            5,
            900,
            1700,
            List.of("http://localhost:5173")
        );
        return new RecommendationService(
            properties,
            posterSeedService,
            profileBuilder,
            scorer,
            codexClient,
            profileRepository,
            showtimeRepository
        );
    }

    private ScoredCandidate scored(ShowtimeCandidate candidate, int score) {
        return new ScoredCandidate(candidate, score, List.of("mood:light"), List.of());
    }

    private ShowtimeCandidate candidateWithDisplayText(
        long showtimeId,
        long movieId,
        String title,
        String theaterName,
        String regionName,
        String screenName
    ) {
        return new ShowtimeCandidate(
            movieId,
            showtimeId,
            title,
            "lotte",
            "external-" + showtimeId,
            theaterName,
            regionName,
            screenName,
            "",
            "",
            LocalDate.now(),
            LocalDateTime.now().plusHours(2),
            null,
            50,
            100,
            12_000,
            "KRW",
            "",
            "",
            "12",
            120,
            Set.of("mood:light", "genre:animation")
        );
    }

    private ShowtimeCandidate candidate(long showtimeId, long movieId, String title) {
        return new ShowtimeCandidate(
            movieId,
            showtimeId,
            title,
            "lotte",
            "external-" + showtimeId,
            "Test Theater",
            "Seoul",
            "1관",
            "",
            "",
            LocalDate.now(),
            LocalDateTime.now().plusHours(2),
            null,
            50,
            100,
            12_000,
            "KRW",
            "",
            "",
            "12",
            120,
            Set.of("mood:light", "genre:animation")
        );
    }

    private RecommendationRequest request() {
        return request("fast");
    }

    private RecommendationRequest request(String mode) {
        return request(mode, null);
    }

    private RecommendationRequest request(String mode, String aiProvider) {
        return new RecommendationRequest(
            "anon_test",
            mode,
            new RecommendationSurvey("friends", "light", List.of("too_long")),
            new PosterChoices(List.of("barbie", "aladdin_2019", "inside_out_2"), List.of()),
            null,
            aiProvider
        );
    }
}
