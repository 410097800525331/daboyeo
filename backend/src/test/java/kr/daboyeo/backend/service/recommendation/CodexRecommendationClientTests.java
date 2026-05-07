package kr.daboyeo.backend.service.recommendation;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import kr.daboyeo.backend.config.RecommendationProperties;
import kr.daboyeo.backend.domain.recommendation.RecommendationModels.AiPick;
import kr.daboyeo.backend.domain.recommendation.RecommendationModels.AiProvider;
import kr.daboyeo.backend.domain.recommendation.RecommendationModels.AiResult;
import kr.daboyeo.backend.domain.recommendation.RecommendationModels.RecommendationMode;
import kr.daboyeo.backend.domain.recommendation.RecommendationModels.ScoredCandidate;
import kr.daboyeo.backend.domain.recommendation.RecommendationModels.ShowtimeCandidate;
import kr.daboyeo.backend.domain.recommendation.RecommendationModels.TagProfile;
import org.junit.jupiter.api.Test;

class CodexRecommendationClientTests {

    private final CodexRecommendationClient client = new CodexRecommendationClient(properties(), new ObjectMapper());

    @Test
    void fastPromptDoesNotExposeInternalScoreTokens() {
        TagProfile profile = new TagProfile();
        profile.setAudience("friends");
        profile.setMood("exciting");
        profile.addAvoid(List.of("too_long"));
        String prompt = client.buildPrompt(RecommendationMode.FAST, profile, List.of(scoredCandidate()));

        assertThat(prompt).contains("Test Movie", "\"id\":1", "User profile:", "Candidates:");
        assertThat(prompt).contains("Return only", "tasteMatch", "fitHints", "practicalValue");
        assertThat(prompt).doesNotContain("\"showtimeId\"", "\"recommendations\"", "\"reason\"", "\"valuePoint\"");
        assertThat(prompt).doesNotContain("\"score\"", "\"matchedTags\"", "\"penalties\"", "\"tg\"", "\"rt\"", "\"th\"", "\"time\"", "\"seat\"");
        assertThat(prompt.length()).isLessThan(2600);
    }

    @Test
    void precisePromptRequestsComparativeAiAnalysisResponse() {
        TagProfile profile = new TagProfile();
        profile.setAudience("friends");
        profile.setMood("exciting");
        profile.addAvoid(List.of("too_long"));
        profile.addLikedGenre("sf");

        String prompt = client.buildPrompt(RecommendationMode.PRECISE, profile, List.of(scoredCandidate()));

        assertThat(prompt).contains("Test Movie", "\"id\":1", "Decision style: CODEX_PRECISE", "tradeoffHints");
        assertThat(prompt).contains(
            "s=integer 0-100 final recommendation score",
            "why=2 Korean sentences",
            "a=2-3 Korean sentences",
            "v=one Korean sentence",
            "c=short Korean caution or empty string"
        );
        assertThat(prompt).doesNotContain("\"score\"", "\"matchedTags\"", "\"penalties\"");
        assertThat(prompt.length()).isLessThan(3200);
    }

    @Test
    void gptFastPromptRequestsRicherAnalysisFields() {
        TagProfile profile = new TagProfile();
        profile.setAudience("friends");
        profile.setMood("light");
        profile.addAvoid(List.of("too_long"));
        profile.addLikedGenre("sf");

        String prompt = client.buildPrompt(AiProvider.CODEX, RecommendationMode.FAST, profile, List.of(scoredCandidate()));

        assertThat(prompt).contains(
            "User profile:",
            "Candidates:",
            "Decision style: CODEX_FAST",
            "small candidate set",
            "avoid generic caution-first wording",
            "\"why\"",
            "\"s\"",
            "\"a\"",
            "\"v\"",
            "\"c\"",
            "tasteMatch",
            "fitHints",
            "scheduleFit",
            "practicalValue",
            "watchRisks"
        );
        assertThat(prompt).doesNotContain("tradeoffHints", "\"score\"", "\"matchedTags\"", "\"penalties\"");
    }

    @Test
    void gptPromptDoesNotCopyUserPosterHintsIntoUnmatchedCandidateTasteMatch() {
        TagProfile profile = new TagProfile();
        profile.setAudience("friends");
        profile.setMood("light");
        profile.addPreferredGenre("action");
        profile.addPreferredGenre("sf");
        profile.addLikedGenre("drama");
        profile.addLikedGenre("comedy");
        ScoredCandidate candidate = scoredCandidate(
            Set.of("genre:comedy", "genre:drama", "mood:light"),
            List.of("genre:comedy", "genre:drama", "mood:light")
        );

        String prompt = client.buildPrompt(AiProvider.CODEX, RecommendationMode.FAST, profile, List.of(candidate));

        assertThat(prompt).contains(
            "preference_genre_hints=",
            "preference_genre_hints=[#\uC561\uC158\uCDE8\uD5A5, #SF\uCDE8\uD5A5]",
            "\"tasteMatch\":[]",
            "Claim direct genre/poster match only when candidate tasteMatch is non-empty.",
            "empty-tasteMatch reserve must stay at or below 68",
            "instead of leading with \"direct evidence is missing\""
        );
    }

    @Test
    void gptPromptKeepsDirectCandidateGenreOverlapInTasteMatch() {
        TagProfile profile = new TagProfile();
        profile.setAudience("friends");
        profile.setMood("light");
        profile.addLikedGenre("sf");
        ScoredCandidate candidate = scoredCandidate(
            Set.of("genre:sf", "mood:light"),
            List.of("genre:sf", "mood:light")
        );

        String prompt = client.buildPrompt(AiProvider.CODEX, RecommendationMode.FAST, profile, List.of(candidate));

        assertThat(prompt).contains("\"tasteMatch\":[\"#SF");
    }

    @Test
    void gptPrecisePromptRequestsComparativeTradeoffAnalysis() {
        TagProfile profile = new TagProfile();
        profile.setAudience("friends");
        profile.setMood("immersive");
        profile.addAvoid(List.of("too_long", "loud"));
        profile.addLikedGenre("sf");

        String prompt = client.buildPrompt(AiProvider.CODEX, RecommendationMode.PRECISE, profile, List.of(scoredCandidate()));

        assertThat(prompt).contains(
            "Decision style: CODEX_PRECISE",
            "Evaluate every supplied candidate",
            "why each selected candidate beats a nearby alternative",
            "selected genre intent",
            "poster taste",
            "avoid-risk handling",
            "tradeoff versus another candidate",
            "s=integer 0-100 final recommendation score",
            "tasteMatch",
            "scheduleFit",
            "tradeoffHints"
        );
        assertThat(prompt).contains("Never invent movies, theaters, prices, seats, runtimes, showtimes, or booking availability.");
        assertThat(prompt).doesNotContain("\"score\"", "\"matchedTags\"", "\"penalties\"");
    }

    @Test
    void modeSpecificModelDefaultsAndReasoningAreSeparated() throws Exception {
        RecommendationProperties properties = properties();
        CodexRecommendationClient client = new CodexRecommendationClient(properties, new ObjectMapper());

        assertThat(properties.modelFor(AiProvider.CODEX, RecommendationMode.FAST)).isEqualTo("gpt-5.4-mini");
        assertThat(properties.reasoningEffortFor(AiProvider.CODEX, RecommendationMode.FAST)).isBlank();
        assertThat(properties.modelFor(AiProvider.CODEX, RecommendationMode.PRECISE)).isEqualTo("gpt-5.5");
        assertThat(properties.reasoningEffortFor(AiProvider.CODEX, RecommendationMode.PRECISE)).isEqualTo("xhigh");
        assertThat(properties.expectedModelsFor(AiProvider.CODEX)).containsExactly("gpt-5.4-mini", "gpt-5.5");

        var fastRequest = client.openAiCompatibleRequest(
            RecommendationMode.FAST,
            new TagProfile(),
            List.of(scoredCandidate()),
            properties.modelFor(AiProvider.CODEX, RecommendationMode.FAST)
        );
        var preciseRequest = client.openAiCompatibleRequest(
            RecommendationMode.PRECISE,
            new TagProfile(),
            List.of(scoredCandidate()),
            properties.modelFor(AiProvider.CODEX, RecommendationMode.PRECISE)
        );

        assertThat(fastRequest).containsEntry("model", "gpt-5.4-mini");
        assertThat(fastRequest).doesNotContainKey("reasoning_effort");
        assertThat(fastRequest).containsEntry("max_tokens", 420);
        assertThat(preciseRequest).containsEntry("model", "gpt-5.5");
        assertThat(preciseRequest).containsEntry("reasoning_effort", "xhigh");
        assertThat(preciseRequest).containsEntry("max_tokens", 1700);
    }

    @Test
    void compactAiResponseIsMappedToExistingAiPickShape() throws Exception {
        String json = "{\"r\":[{\"id\":1,\"why\":\"#가볍게 #친구랑\",\"v\":\"#17:00상영 #좌석여유\"}]}";

        AiResult result = client.parseResult(json, "codex-test").orElseThrow();

        AiPick pick = result.picks().get(0);
        assertThat(pick.showtimeId()).isEqualTo(1L);
        assertThat(pick.reason()).isEqualTo("#가볍게 #친구랑");
        assertThat(pick.caution()).isBlank();
        assertThat(pick.valuePoint()).isEqualTo("#17:00상영 #좌석여유");
        assertThat(pick.analysisPoint()).isBlank();
        assertThat(result.rawJson()).isEqualTo(json);
    }

    @Test
    void preciseAnalysisResponseIsMappedToAiPickAnalysisPoint() throws Exception {
        String json = "{\"r\":[{\"id\":1,\"a\":\"#SF취향\"},{\"id\":2,\"a\":\"#몰입취향\"}]}";

        AiResult result = client.parseResult(json, "codex-test").orElseThrow();

        assertThat(result.picks()).extracting(AiPick::showtimeId).containsExactly(1L, 2L);
        assertThat(result.picks()).extracting(AiPick::analysisPoint).containsExactly("#SF취향", "#몰입취향");
        assertThat(result.picks()).allSatisfy(pick -> {
            assertThat(pick.reason()).isBlank();
            assertThat(pick.valuePoint()).isBlank();
            assertThat(pick.caution()).isBlank();
        });
        assertThat(result.rawJson()).isEqualTo(json);
    }

    @Test
    void gptRichResponseIsMappedToAiPickFields() throws Exception {
        String json = "{\"r\":[{\"id\":1,\"why\":\"조건과 취향이 같이 맞아.\",\"a\":\"포스터 취향과 가벼운 컨디션이 이어져.\",\"v\":\"17:00 상영에 좌석 여유가 있어.\",\"c\":\"긴 러닝타임은 아니야.\"}]}";

        AiResult result = client.parseResult(json, "gpt-test").orElseThrow();

        AiPick pick = result.picks().get(0);
        assertThat(pick.showtimeId()).isEqualTo(1L);
        assertThat(pick.reason()).contains("조건");
        assertThat(pick.analysisPoint()).contains("포스터");
        assertThat(pick.valuePoint()).contains("17:00");
        assertThat(pick.caution()).contains("러닝타임");
    }

    @Test
    void gptRichResponseScoreIsMappedAndClamped() throws Exception {
        String json = "{\"r\":[{\"id\":1,\"s\":188,\"why\":\"direct fit\",\"a\":\"poster fit\",\"v\":\"17:00 seats\",\"c\":\"short caution\"}]}";

        AiResult result = client.parseResult(json, "gpt-test").orElseThrow();

        AiPick pick = result.picks().get(0);
        assertThat(pick.showtimeId()).isEqualTo(1L);
        assertThat(pick.score()).isEqualTo(100);
        assertThat(pick.reason()).contains("direct");
    }

    private RecommendationProperties properties() {
        return new RecommendationProperties(
            20,
            3,
            20,
            420,
            1700,
            List.of("http://localhost:5173")
        );
    }

    private ScoredCandidate scoredCandidate() {
        return scoredCandidate(Set.of("mood:exciting", "content:loud"), List.of("mood:exciting"));
    }

    private ScoredCandidate scoredCandidate(Set<String> tags, List<String> matchedTags) {
        ShowtimeCandidate candidate = new ShowtimeCandidate(
            10L,
            1L,
            "Test Movie",
            "MEGABOX",
            "movie-1",
            "Test Theater",
            "Seoul",
            "1관",
            "IMAX",
            "IMAX",
            LocalDate.now(),
            LocalDateTime.now().plusHours(2),
            null,
            50,
            100,
            12_000,
            "KRW",
            "https://example.test/booking",
            "https://example.test/poster.jpg",
            "12",
            120,
            tags
        );
        return new ScoredCandidate(candidate, 96, matchedTags, List.of("content:loud"));
    }
}
