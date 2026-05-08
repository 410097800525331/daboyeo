package kr.daboyeo.backend.service.recommendation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.IntStream;
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
import kr.daboyeo.backend.domain.recommendation.RecommendationModels.SearchFilters;
import kr.daboyeo.backend.domain.recommendation.RecommendationModels.ShowtimeCandidate;
import kr.daboyeo.backend.domain.recommendation.RecommendationModels.TagProfile;
import kr.daboyeo.backend.repository.recommendation.RecommendationProfileRepository;
import kr.daboyeo.backend.repository.recommendation.ShowtimeRecommendationRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class RecommendationServiceCandidateFilterTests {

    private final PosterSeedService posterSeedService = mock(PosterSeedService.class);
    private final PreferenceProfileBuilder profileBuilder = mock(PreferenceProfileBuilder.class);
    private final RecommendationScorer scorer = mock(RecommendationScorer.class);
    private final CodexRecommendationClient codexClient = mock(CodexRecommendationClient.class);
    private final RecommendationProfileRepository profileRepository = mock(RecommendationProfileRepository.class);
    private final ShowtimeRecommendationRepository showtimeRepository = mock(ShowtimeRecommendationRepository.class);

    @Test
    void noShowtimeDataStatusWhenDatabaseIsEmpty() {
        RecommendationService service = service(20);
        when(showtimeRepository.findUpcomingCandidates(anyInt(), any(LocalDateTime.class))).thenReturn(List.of());
        when(showtimeRepository.countStoredShowtimes()).thenReturn(0);

        RecommendationResponse response = service.recommend(request());

        assertThat(response.status()).isEqualTo("no_showtime_data");
        assertThat(response.recommendations()).isEmpty();
        verify(profileRepository).saveRun(
            eq(response.runId()),
            eq("anon_test"),
            eq("fast"),
            eq("gpt-5.4-mini"),
            any(),
            eq(List.of()),
            eq(null),
            eq(response),
            anyLong()
        );
    }

    @Test
    void noUsableShowtimesStatusWhenStoredDataIsOnlyPastOrTooSoon() {
        RecommendationService service = service(20);
        when(showtimeRepository.findUpcomingCandidates(anyInt(), any(LocalDateTime.class))).thenReturn(List.of());
        when(showtimeRepository.countStoredShowtimes()).thenReturn(7);

        RecommendationResponse response = service.recommend(request());

        assertThat(response.status()).isEqualTo("no_usable_showtimes");
        assertThat(response.message()).isNotBlank();
    }

    @Test
    void passesConfiguredStartBufferToCandidateQuery() {
        RecommendationService service = service(30);
        when(showtimeRepository.findUpcomingCandidates(anyInt(), any(LocalDateTime.class))).thenReturn(List.of());
        when(showtimeRepository.countStoredShowtimes()).thenReturn(1);
        LocalDateTime lowerBound = LocalDateTime.now().plusMinutes(29);

        service.recommend(request());

        ArgumentCaptor<LocalDateTime> cutoff = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(showtimeRepository).findUpcomingCandidates(anyInt(), cutoff.capture());
        assertThat(cutoff.getValue()).isAfterOrEqualTo(lowerBound);
    }

    @Test
    void noSelectedGenreKeepsDefaultCandidateFetchLimit() {
        RecommendationService service = service(20);
        ShowtimeCandidate candidate = candidate(1, "First");
        when(showtimeRepository.findUpcomingCandidates(anyInt(), any(LocalDateTime.class))).thenReturn(List.of(candidate));
        when(scorer.score(any(TagProfile.class), eq(List.of(candidate)), eq(null)))
            .thenReturn(List.of(scored(candidate, 90)));
        when(codexClient.rankAndExplain(any(), any(), any())).thenReturn(Optional.empty());

        service.recommend(request());

        verify(showtimeRepository).findUpcomingCandidates(eq(240), any(LocalDateTime.class));
    }

    @Test
    void selectedGenreUsesWiderCandidateFetchBeforeFiltering() {
        RecommendationService service = service(20);
        TagProfile profile = new TagProfile();
        profile.addPreferredGenre("animation");
        ShowtimeCandidate mario = candidate(1, "Super Mario Galaxy", Set.of("genre:animation", "mood:light"));
        ShowtimeCandidate kiki = candidate(2, "Kiki's Delivery Service", Set.of("genre:animation", "mood:warm"));
        when(profileBuilder.build(any(), any(), any())).thenReturn(profile);
        when(showtimeRepository.findUpcomingCandidates(anyInt(), any(LocalDateTime.class), isNull(), anySet()))
            .thenReturn(List.of(mario, kiki));
        when(scorer.score(eq(profile), eq(List.of(mario, kiki)), eq(null)))
            .thenReturn(List.of(scored(mario, 96), scored(kiki, 93)));
        when(codexClient.rankAndExplain(any(), any(), any())).thenReturn(Optional.empty());

        service.recommend(request());

        verify(showtimeRepository).findUpcomingCandidates(eq(1200), any(LocalDateTime.class), isNull(), eq(Set.of("animation")));
    }

    @Test
    void passesConfiguredFastAiCandidateLimitToCodexClient() {
        RecommendationService service = service(20, 3, 5);
        ShowtimeCandidate first = candidate(1, "First");
        ShowtimeCandidate second = candidate(2, "Second");
        ShowtimeCandidate third = candidate(3, "Third");
        ShowtimeCandidate fourth = candidate(4, "Fourth");
        List<ScoredCandidate> scored = List.of(
            scored(first, 90),
            scored(second, 80),
            scored(third, 70),
            scored(fourth, 60)
        );
        when(showtimeRepository.findUpcomingCandidates(anyInt(), any(LocalDateTime.class)))
            .thenReturn(List.of(first, second, third, fourth));
        when(scorer.score(any(TagProfile.class), any(), any())).thenReturn(scored);
        when(codexClient.rankAndExplain(any(), any(), any())).thenReturn(Optional.empty());

        RecommendationResponse response = service.recommend(request());

        assertThat(response.status()).isEqualTo("fallback");
        assertThat(response.recommendations()).hasSize(3);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<ScoredCandidate>> aiCandidates = ArgumentCaptor.forClass(List.class);
        verify(codexClient).rankAndExplain(eq(RecommendationMode.FAST), any(TagProfile.class), aiCandidates.capture());
        assertThat(aiCandidates.getValue()).hasSize(3);
    }

    @Test
    void returnsRecommendationWhenRunHistoryPersistenceFails() {
        RecommendationService service = service(20);
        ShowtimeCandidate candidate = candidate(1, "First");
        when(showtimeRepository.findUpcomingCandidates(anyInt(), any(LocalDateTime.class)))
            .thenReturn(List.of(candidate));
        when(scorer.score(any(TagProfile.class), any(), any()))
            .thenReturn(List.of(scored(candidate, 90)));
        when(codexClient.rankAndExplain(any(), any(), any())).thenReturn(Optional.empty());
        doThrow(new RuntimeException("TiDB timeout")).when(profileRepository).saveRun(
            any(),
            any(),
            any(),
            any(),
            any(),
            any(),
            any(),
            any(),
            any(Long.class)
        );

        RecommendationResponse response = service.recommend(request());

        assertThat(response.status()).isEqualTo("fallback");
        assertThat(response.recommendations()).hasSize(1);
    }

    @Test
    void passesConfiguredPreciseAiCandidateLimitToCodexClient() {
        RecommendationService service = service(20, 5, 5);
        ShowtimeCandidate first = candidate(1, "First");
        ShowtimeCandidate second = candidate(2, "Second");
        ShowtimeCandidate third = candidate(3, "Third");
        ShowtimeCandidate fourth = candidate(4, "Fourth");
        ShowtimeCandidate fifth = candidate(5, "Fifth");
        ShowtimeCandidate sixth = candidate(6, "Sixth");
        List<ScoredCandidate> scored = List.of(
            scored(first, 90),
            scored(second, 80),
            scored(third, 70),
            scored(fourth, 60),
            scored(fifth, 50),
            scored(sixth, 40)
        );
        when(showtimeRepository.findUpcomingCandidates(anyInt(), any(LocalDateTime.class)))
            .thenReturn(List.of(first, second, third, fourth, fifth, sixth));
        when(scorer.score(any(TagProfile.class), any(), any())).thenReturn(scored);
        when(codexClient.rankAndExplain(any(), any(), any())).thenReturn(Optional.empty());

        service.recommend(request("precise"));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<ScoredCandidate>> aiCandidates = ArgumentCaptor.forClass(List.class);
        verify(codexClient).rankAndExplain(eq(RecommendationMode.PRECISE), any(TagProfile.class), aiCandidates.capture());
        assertThat(aiCandidates.getValue()).hasSize(5);
    }

    @Test
    void legacyGptFastProviderInputUsesCodexConfiguredCandidatePool() {
        RecommendationService service = service(20, 4, 5);
        ShowtimeCandidate first = candidate(1, "First");
        ShowtimeCandidate second = candidate(2, "Second");
        ShowtimeCandidate third = candidate(3, "Third");
        ShowtimeCandidate fourth = candidate(4, "Fourth");
        List<ScoredCandidate> scored = List.of(
            scored(first, 90),
            scored(second, 80),
            scored(third, 70),
            scored(fourth, 60)
        );
        when(showtimeRepository.findUpcomingCandidates(anyInt(), any(LocalDateTime.class)))
            .thenReturn(List.of(first, second, third, fourth));
        when(scorer.score(any(TagProfile.class), any(), any())).thenReturn(scored);
        when(codexClient.rankAndExplain(any(), any(), any())).thenReturn(Optional.empty());

        RecommendationResponse response = service.recommend(request("fast", null, "gpt"));

        assertThat(response.status()).isEqualTo("fallback");
        assertThat(response.recommendations()).hasSize(3);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<ScoredCandidate>> aiCandidates = ArgumentCaptor.forClass(List.class);
        verify(codexClient).rankAndExplain(eq(RecommendationMode.FAST), any(TagProfile.class), aiCandidates.capture());
        assertThat(aiCandidates.getValue()).hasSize(4);
    }

    @Test
    void legacyGptPreciseInputUsesCodexConfiguredCandidatePool() {
        RecommendationService service = service(20);
        List<ShowtimeCandidate> candidates = IntStream.rangeClosed(1, 13)
            .mapToObj(index -> candidate(index, "Candidate " + index))
            .toList();
        List<ScoredCandidate> scored = IntStream.rangeClosed(1, 13)
            .mapToObj(index -> scored(candidates.get(index - 1), 100 - index))
            .toList();
        when(showtimeRepository.findUpcomingCandidates(anyInt(), any(LocalDateTime.class)))
            .thenReturn(candidates);
        when(scorer.score(any(TagProfile.class), any(), any())).thenReturn(scored);
        when(codexClient.rankAndExplain(any(), any(), any())).thenReturn(Optional.empty());

        service.recommend(request("precise", null, "gpt"));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<ScoredCandidate>> aiCandidates = ArgumentCaptor.forClass(List.class);
        verify(codexClient).rankAndExplain(eq(RecommendationMode.PRECISE), any(TagProfile.class), aiCandidates.capture());
        assertThat(aiCandidates.getValue()).hasSize(5);
    }

    @Test
    void codexPreciseUsesConfiguredCandidatePool() {
        RecommendationService service = service(20);
        List<ShowtimeCandidate> candidates = IntStream.rangeClosed(1, 25)
            .mapToObj(index -> candidate(index, "Candidate " + index))
            .toList();
        List<ScoredCandidate> scored = IntStream.rangeClosed(1, 25)
            .mapToObj(index -> scored(candidates.get(index - 1), 100 - index))
            .toList();
        when(showtimeRepository.findUpcomingCandidates(anyInt(), any(LocalDateTime.class)))
            .thenReturn(candidates);
        when(scorer.score(any(TagProfile.class), any(), any())).thenReturn(scored);
        when(codexClient.rankAndExplain(any(), any(), any())).thenReturn(Optional.empty());

        service.recommend(request("precise", null, "codex"));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<ScoredCandidate>> aiCandidates = ArgumentCaptor.forClass(List.class);
        verify(codexClient).rankAndExplain(eq(RecommendationMode.PRECISE), any(TagProfile.class), aiCandidates.capture());
        assertThat(aiCandidates.getValue()).hasSize(5);
    }

    @Test
    void selectedGenreMatchesStayFirstAndReservesCanFillFallbackCandidates() {
        RecommendationService service = service(20, 5, 5);
        TagProfile profile = new TagProfile();
        profile.addPreferredGenre("sf");
        profile.addPreferredGenre("action");
        profile.addLikedGenre("drama");
        profile.addLikedGenre("comedy");
        ShowtimeCandidate project = candidate(1, "Project Hail Mary", Set.of("genre:sf", "genre:adventure"));
        ShowtimeCandidate projectLater = candidate(2, "Project Hail Mary", Set.of("genre:sf", "genre:adventure"));
        ShowtimeCandidate prada = candidate(3, "Devil Wears Prada 2", Set.of("genre:drama", "genre:comedy", "mood:light"));
        ShowtimeCandidate mario = candidate(4, "Super Mario Galaxy", Set.of("genre:animation", "mood:light"));
        when(profileBuilder.build(any(), any(), any())).thenReturn(profile);
        when(showtimeRepository.findUpcomingCandidates(anyInt(), any(LocalDateTime.class), isNull(), anySet()))
            .thenReturn(List.of(project, projectLater, prada, mario));
        when(scorer.score(eq(profile), eq(List.of(project, projectLater, prada, mario)), eq(null)))
            .thenReturn(List.of(scored(project, 100), scored(projectLater, 98), scored(prada, 74), scored(mario, 68)));
        when(codexClient.rankAndExplain(any(), any(), any())).thenReturn(Optional.empty());

        RecommendationResponse response = service.recommend(request());

        assertThat(response.status()).isEqualTo("fallback");
        assertThat(response.recommendations())
            .extracting(item -> item.title())
            .containsExactly("Project Hail Mary", "Devil Wears Prada 2", "Super Mario Galaxy");
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<ScoredCandidate>> aiCandidates = ArgumentCaptor.forClass(List.class);
        verify(codexClient).rankAndExplain(eq(RecommendationMode.FAST), any(TagProfile.class), aiCandidates.capture());
        assertThat(aiCandidates.getValue()).extracting(scored -> scored.candidate().title())
            .containsExactly("Project Hail Mary", "Devil Wears Prada 2", "Super Mario Galaxy");
    }

    @Test
    void selectedGenreWithoutExplicitCandidateReturnsReserveRecommendations() {
        RecommendationService service = service(20, 5, 5);
        TagProfile profile = new TagProfile();
        profile.addWeight("genre:animation", 6);
        profile.addWeight("genre:drama", 5);
        profile.addWeight("genre:comedy", 5);
        profile.addPreferredGenre("animation");
        profile.addLikedGenre("drama");
        profile.addLikedGenre("comedy");
        ShowtimeCandidate prada = candidate(1, "Devil Wears Prada 2", Set.of("genre:drama", "genre:comedy", "mood:light"));
        ShowtimeCandidate marioTitleOnly = candidate(2, "Super Mario Galaxy", Set.of("mood:light"));
        when(profileBuilder.build(any(), any(), any())).thenReturn(profile);
        when(showtimeRepository.findUpcomingCandidates(anyInt(), any(LocalDateTime.class), isNull(), anySet()))
            .thenReturn(List.of(), List.of());
        when(showtimeRepository.findUpcomingCandidates(anyInt(), any(LocalDateTime.class)))
            .thenReturn(List.of(prada, marioTitleOnly));
        when(scorer.score(any(TagProfile.class), eq(List.of(prada, marioTitleOnly)), eq(null)))
            .thenReturn(List.of(scored(prada, 91), scored(marioTitleOnly, 80)));
        when(codexClient.rankAndExplain(any(), any(), any())).thenReturn(Optional.empty());

        RecommendationResponse response = service.recommend(request());

        assertThat(response.status()).isEqualTo("fallback");
        assertThat(response.recommendations())
            .extracting(item -> item.title())
            .containsExactly("Devil Wears Prada 2", "Super Mario Galaxy");
        assertThat(response.recommendations())
            .extracting(item -> item.score())
            .containsExactly(64, 59);
        assertThat(response.message()).contains("\uC870\uAC74\uC744 \uB113\uD600");
        ArgumentCaptor<TagProfile> scorerProfile = ArgumentCaptor.forClass(TagProfile.class);
        verify(scorer).score(scorerProfile.capture(), eq(List.of(prada, marioTitleOnly)), eq(null));
        assertThat(scorerProfile.getValue().preferredGenres()).isEmpty();
        assertThat(scorerProfile.getValue().likedGenres())
            .containsExactlyInAnyOrder("genre:drama", "genre:comedy");
        assertThat(scorerProfile.getValue().weights()).containsKeys("genre:drama", "genre:comedy");
        assertThat(scorerProfile.getValue().weights()).doesNotContainKey("genre:animation");
        ArgumentCaptor<TagProfile> codexProfile = ArgumentCaptor.forClass(TagProfile.class);
        verify(codexClient).rankAndExplain(eq(RecommendationMode.FAST), codexProfile.capture(), any());
        assertThat(codexProfile.getValue().preferredGenres()).isEmpty();
        assertThat(codexProfile.getValue().likedGenres())
            .containsExactlyInAnyOrder("genre:drama", "genre:comedy");
    }

    @Test
    void selectedGenreMissCompressesCodexScoresAgainstOriginalGenreProfile() {
        RecommendationService service = service(20, 5, 5);
        TagProfile profile = new TagProfile();
        profile.addWeight("genre:animation", 6);
        profile.addWeight("genre:drama", 5);
        profile.addPreferredGenre("animation");
        profile.addLikedGenre("drama");
        ShowtimeCandidate prada = candidate(1, "Devil Wears Prada 2", Set.of("genre:drama", "mood:light"));
        when(profileBuilder.build(any(), any(), any())).thenReturn(profile);
        when(showtimeRepository.findUpcomingCandidates(anyInt(), any(LocalDateTime.class), isNull(), anySet()))
            .thenReturn(List.of(), List.of());
        when(showtimeRepository.findUpcomingCandidates(anyInt(), any(LocalDateTime.class)))
            .thenReturn(List.of(prada));
        when(scorer.score(any(TagProfile.class), eq(List.of(prada)), eq(null)))
            .thenReturn(List.of(scored(prada, 92)));
        when(codexClient.rankAndExplain(any(), any(), any()))
            .thenReturn(Optional.of(new AiResult(
                "{\"r\":[]}",
                "codex",
                List.of(new AiPick(prada.showtimeId(), 99, "poster match", "", "value", "analysis"))
            )));

        RecommendationResponse response = service.recommend(request("precise", null, "codex"));

        assertThat(response.status()).isEqualTo("ok");
        assertThat(response.recommendations())
            .extracting(item -> item.score())
            .containsExactly(65);
        ArgumentCaptor<TagProfile> codexProfile = ArgumentCaptor.forClass(TagProfile.class);
        verify(codexClient).rankAndExplain(eq(RecommendationMode.PRECISE), codexProfile.capture(), any());
        assertThat(codexProfile.getValue().preferredGenres()).isEmpty();
        assertThat(codexProfile.getValue().likedGenres()).containsExactly("genre:drama");
    }

    @Test
    void codexModelScoresDriveOrderButNoDirectTasteReserveScoresAreCompressed() {
        RecommendationService service = service(20, 5, 5);
        TagProfile profile = new TagProfile();
        profile.addPreferredGenre("sf");
        profile.addLikedGenre("comedy");
        ShowtimeCandidate project = candidate(1, "Project Hail Mary", Set.of("genre:sf", "genre:adventure"));
        ShowtimeCandidate prada = candidate(2, "Devil Wears Prada 2", Set.of("genre:comedy", "genre:drama", "mood:light"));
        ShowtimeCandidate mario = candidate(3, "Super Mario Galaxy", Set.of("genre:animation", "mood:light"));
        when(profileBuilder.build(any(), any(), any())).thenReturn(profile);
        when(showtimeRepository.findUpcomingCandidates(anyInt(), any(LocalDateTime.class), isNull(), anySet()))
            .thenReturn(List.of(project, prada, mario));
        when(scorer.score(eq(profile), eq(List.of(project, prada, mario)), eq(null)))
            .thenReturn(List.of(scored(project, 91), scored(prada, 74), scored(mario, 65)));
        when(codexClient.rankAndExplain(any(), any(), any()))
            .thenReturn(Optional.of(new AiResult(
                "{\"r\":[]}",
                "codex",
                List.of(
                    new AiPick(project.showtimeId(), 96, "direct", "", "value", "analysis"),
                    new AiPick(prada.showtimeId(), 99, "reserve", "", "value", "analysis"),
                    new AiPick(mario.showtimeId(), 82, "reserve", "", "value", "analysis")
                )
            )));

        RecommendationResponse response = service.recommend(request("precise", null, "codex"));

        assertThat(response.recommendations())
            .extracting(item -> item.title())
            .containsExactly("Project Hail Mary", "Devil Wears Prada 2", "Super Mario Galaxy");
        assertThat(response.recommendations())
            .extracting(item -> item.score())
            .containsExactly(96, 56, 52);
    }

    @Test
    void passesSearchFiltersToCandidateQueryWhenPresent() {
        RecommendationService service = service(20);
        SearchFilters filters = new SearchFilters("Gangnam", LocalDate.of(2026, 4, 17), "night", 2);
        when(showtimeRepository.findUpcomingCandidates(anyInt(), any(LocalDateTime.class), eq(filters))).thenReturn(List.of());

        service.recommend(request("fast", filters));

        verify(showtimeRepository).findUpcomingCandidates(anyInt(), any(LocalDateTime.class), eq(filters));
    }

    @Test
    void noFilteredCandidatesStatusWhenSearchFiltersReturnNoResults() {
        RecommendationService service = service(20);
        SearchFilters filters = new SearchFilters("Gangnam", LocalDate.of(2026, 4, 17), "night", 2);
        when(showtimeRepository.findUpcomingCandidates(anyInt(), any(LocalDateTime.class), eq(filters))).thenReturn(List.of());

        RecommendationResponse response = service.recommend(request("fast", filters));

        assertThat(response.status()).isEqualTo("no_filtered_candidates");
        assertThat(response.message()).contains("지역", "날짜", "시간대", "인원수");
    }

    @Test
    void relaxesSearchFiltersWhenExactRegionAndTimeMiss() {
        RecommendationService service = service(20);
        LocalDate date = LocalDate.now().plusDays(1);
        SearchFilters filters = new SearchFilters("Nowhere", date, "night", 2);
        SearchFilters withoutTime = new SearchFilters("Nowhere", date, "", 2);
        SearchFilters withoutRegion = new SearchFilters("", date, "night", 2);
        ShowtimeCandidate candidate = candidate(1, "Recovered");
        when(showtimeRepository.findUpcomingCandidates(anyInt(), any(LocalDateTime.class), eq(filters))).thenReturn(List.of());
        when(showtimeRepository.findUpcomingCandidates(anyInt(), any(LocalDateTime.class), eq(withoutTime))).thenReturn(List.of());
        when(showtimeRepository.findUpcomingCandidates(anyInt(), any(LocalDateTime.class), eq(withoutRegion))).thenReturn(List.of(candidate));
        when(scorer.score(any(TagProfile.class), eq(List.of(candidate)), eq(withoutRegion)))
            .thenReturn(List.of(scored(candidate, 90)));
        when(codexClient.rankAndExplain(any(), any(), any())).thenReturn(Optional.empty());

        RecommendationResponse response = service.recommend(request("fast", filters));

        assertThat(response.status()).isEqualTo("fallback");
        assertThat(response.recommendations()).hasSize(1);
        assertThat(response.message()).isNotBlank();
        verify(showtimeRepository).findUpcomingCandidates(anyInt(), any(LocalDateTime.class), eq(filters));
        verify(showtimeRepository).findUpcomingCandidates(anyInt(), any(LocalDateTime.class), eq(withoutTime));
        verify(showtimeRepository).findUpcomingCandidates(anyInt(), any(LocalDateTime.class), eq(withoutRegion));
        verify(scorer).score(any(TagProfile.class), eq(List.of(candidate)), eq(withoutRegion));
    }

    @Test
    void fallsBackToBroadCandidateSearchWhenAllRelaxedFiltersMiss() {
        RecommendationService service = service(20);
        SearchFilters filters = new SearchFilters("Nowhere", LocalDate.now().plusDays(1), "morning", 4);
        ShowtimeCandidate candidate = candidate(1, "Recovered");
        when(showtimeRepository.findUpcomingCandidates(anyInt(), any(LocalDateTime.class), any(SearchFilters.class)))
            .thenReturn(List.of());
        when(showtimeRepository.findUpcomingCandidates(anyInt(), any(LocalDateTime.class))).thenReturn(List.of(candidate));
        when(scorer.score(any(TagProfile.class), eq(List.of(candidate)), eq(null)))
            .thenReturn(List.of(scored(candidate, 90)));
        when(codexClient.rankAndExplain(any(), any(), any())).thenReturn(Optional.empty());

        RecommendationResponse response = service.recommend(request("fast", filters));

        assertThat(response.status()).isEqualTo("fallback");
        assertThat(response.recommendations()).hasSize(1);
        assertThat(response.message()).isNotBlank();
        verify(scorer).score(any(TagProfile.class), eq(List.of(candidate)), eq(null));
    }

    @Test
    void retriesWithoutExpiredTimeRangeBeforeReturningNoFilteredCandidates() {
        RecommendationService service = service(20);
        SearchFilters expiredFilters = new SearchFilters("", LocalDate.now().minusDays(1), "morning", 1);
        SearchFilters relaxedFilters = new SearchFilters("", LocalDate.now().minusDays(1), "", 1);
        ShowtimeCandidate candidate = candidate(1, "Recovered");
        when(showtimeRepository.findUpcomingCandidates(anyInt(), any(LocalDateTime.class), eq(expiredFilters))).thenReturn(List.of());
        when(showtimeRepository.findUpcomingCandidates(anyInt(), any(LocalDateTime.class), eq(relaxedFilters))).thenReturn(List.of(candidate));
        when(scorer.score(any(TagProfile.class), eq(List.of(candidate)), eq(relaxedFilters)))
            .thenReturn(List.of(scored(candidate, 90)));
        when(codexClient.rankAndExplain(any(), any(), any())).thenReturn(Optional.empty());

        RecommendationResponse response = service.recommend(request("fast", expiredFilters));

        assertThat(response.status()).isEqualTo("fallback");
        assertThat(response.message()).isNotBlank();
        assertThat(response.recommendations()).hasSize(1);
        verify(showtimeRepository).findUpcomingCandidates(anyInt(), any(LocalDateTime.class), eq(expiredFilters));
        verify(showtimeRepository).findUpcomingCandidates(anyInt(), any(LocalDateTime.class), eq(relaxedFilters));
        verify(scorer).score(any(TagProfile.class), eq(List.of(candidate)), eq(relaxedFilters));
    }

    private RecommendationService service(int minStartBufferMinutes) {
        return service(minStartBufferMinutes, 5, 5);
    }

    private RecommendationService service(int minStartBufferMinutes, int fastAiCandidateLimit, int preciseAiCandidateLimit) {
        when(profileRepository.findProfile("anon_test"))
            .thenReturn(Optional.of(new RecommendationProfile("anon_test", Map.of())));
        when(profileBuilder.build(any(), any(), any())).thenReturn(new TagProfile());
        RecommendationProperties properties = new RecommendationProperties(
            minStartBufferMinutes,
            fastAiCandidateLimit,
            preciseAiCandidateLimit,
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

    private ShowtimeCandidate candidate(long id, String title) {
        return candidate(id, title, Set.of("mood:light"));
    }

    private ShowtimeCandidate candidate(long id, String title, Set<String> tags) {
        return new ShowtimeCandidate(
            id,
            100L + id,
            title,
            "lotte",
            "external-" + id,
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
            tags
        );
    }

    private RecommendationRequest request() {
        return request("fast");
    }

    private RecommendationRequest request(String mode) {
        return request(mode, null);
    }

    private RecommendationRequest request(String mode, SearchFilters filters) {
        return request(mode, filters, null);
    }

    private RecommendationRequest request(String mode, SearchFilters filters, String aiProvider) {
        return new RecommendationRequest(
            "anon_test",
            mode,
            new RecommendationSurvey("friends", "light", List.of("too_long")),
            new PosterChoices(List.of("barbie", "aladdin_2019", "inside_out_2"), List.of()),
            filters,
            aiProvider
        );
    }
}
