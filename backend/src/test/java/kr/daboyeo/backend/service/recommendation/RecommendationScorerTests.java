package kr.daboyeo.backend.service.recommendation;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Set;
import kr.daboyeo.backend.domain.recommendation.RecommendationModels.ShowtimeCandidate;
import kr.daboyeo.backend.domain.recommendation.RecommendationModels.TagProfile;
import org.junit.jupiter.api.Test;

class RecommendationScorerTests {

    private final RecommendationScorer scorer = new RecommendationScorer();

    @Test
    void childAudienceBlocksUnsafeAgeRating() {
        TagProfile profile = new TagProfile();
        profile.setAudience("child");

        assertThat(scorer.scoreOne(profile, candidate("15", 100, Set.of("genre:animation")))).isEmpty();
    }

    @Test
    void childAudienceBlocksViolenceTags() {
        TagProfile profile = new TagProfile();
        profile.setAudience("child");

        assertThat(scorer.scoreOne(profile, candidate("12", 100, Set.of("content:violence")))).isEmpty();
    }

    @Test
    void avoidTooLongAppliesPenaltyInsteadOfDroppingCandidate() {
        TagProfile profile = new TagProfile();
        profile.addAvoid(Set.of("too_long"));

        var scored = scorer.scoreOne(profile, candidate("12", 165, Set.of("mood:immersive")));

        assertThat(scored).isPresent();
        assertThat(scored.orElseThrow().penalties()).contains("too_long");
    }

    @Test
    void matchingTagsIncreaseScore() {
        TagProfile profile = new TagProfile();
        profile.addWeight("genre:action", 4);
        profile.addWeight("mood:exciting", 3);

        var scored = scorer.scoreOne(profile, candidate("12", 105, Set.of("genre:action", "mood:exciting")));

        assertThat(scored).isPresent();
        assertThat(scored.orElseThrow().score()).isGreaterThan(70);
    }

    @Test
    void explicitAnimationTagsBoostChildAndFamilyCandidates() {
        TagProfile profile = new TagProfile();
        profile.setAudience("child");
        profile.addWeight("audience:child", 4);
        profile.addWeight("genre:animation", 4);
        profile.addWeight("mood:light", 3);

        var scored = scorer.scoreOne(profile, candidate(
            "Super Mario Galaxy",
            "all",
            100,
            Set.of("genre:animation", "age_rating:all")
        ));

        assertThat(scored).isPresent();
        assertThat(scored.orElseThrow().matchedTags()).contains("audience:child", "genre:animation", "mood:light");
        assertThat(scored.orElseThrow().score()).isGreaterThan(80);
    }

    @Test
    void explicitHorrorTagsBlockChildAudience() {
        TagProfile profile = new TagProfile();
        profile.setAudience("child");

        var scored = scorer.scoreOne(profile, candidate(
            "Horror Sample",
            "12",
            100,
            Set.of("genre:horror", "age_rating:12")
        ));

        assertThat(scored).isEmpty();
    }

    @Test
    void explicitTenseTagsFavorTenseProfiles() {
        TagProfile profile = new TagProfile();
        profile.setAudience("friends");
        profile.setMood("tense");
        profile.addWeight("audience:friends", 4);
        profile.addWeight("mood:tense", 4);
        profile.addWeight("genre:thriller", 2);

        var horror = scorer.scoreOne(profile, candidate(
            "Horror Sample",
            "15",
            100,
            Set.of("audience:friends", "genre:thriller", "mood:tense", "age_rating:15")
        ));
        var animation = scorer.scoreOne(profile, candidate(
            "Super Mario Galaxy",
            "all",
            100,
            Set.of("genre:animation", "age_rating:all")
        ));

        assertThat(horror).isPresent();
        assertThat(animation).isPresent();
        assertThat(horror.orElseThrow().score()).isGreaterThan(animation.orElseThrow().score());
        assertThat(horror.orElseThrow().matchedTags()).contains("audience:friends", "mood:tense", "genre:thriller");
    }

    @Test
    void titleOnlyAliasesDoNotExposeSelectedGenreTags() {
        TagProfile sfProfile = new TagProfile();
        sfProfile.addWeight("genre:sf", 6);
        sfProfile.addWeight("genre:adventure", 4);

        var project = scorer.scoreOne(sfProfile, candidate("Project Hail Mary", "12", 120, Set.of()));

        assertThat(project).isPresent();
        assertThat(project.orElseThrow().matchedTags()).doesNotContain("genre:sf", "genre:adventure");

        TagProfile actionProfile = new TagProfile();
        actionProfile.addWeight("genre:action", 6);
        actionProfile.addWeight("genre:animation", 3);

        var demonSlayer = scorer.scoreOne(actionProfile, candidate("Demon Slayer: Infinity Castle", "15", 110, Set.of()));

        assertThat(demonSlayer).isPresent();
        assertThat(demonSlayer.orElseThrow().matchedTags()).doesNotContain("genre:action", "genre:animation");
        assertThat(demonSlayer.orElseThrow().candidate().allTags()).doesNotContain("content:violence", "genre:thriller");
    }

    @Test
    void titleOnlyAnimeLikeNameDoesNotCountAsDirectAnimationMatch() {
        TagProfile profile = new TagProfile();
        profile.addWeight("genre:animation", 6);
        profile.addPreferredGenre("animation");

        var titleOnly = scorer.scoreOne(profile, candidate(
            "Super Mario Galaxy",
            "all",
            100,
            Set.of("age_rating:all")
        ));
        var explicitAnimation = scorer.scoreOne(profile, candidate(
            "Tagged Animation",
            "all",
            100,
            Set.of("genre:animation", "age_rating:all")
        ));

        assertThat(titleOnly).isPresent();
        assertThat(explicitAnimation).isPresent();
        assertThat(titleOnly.orElseThrow().matchedTags()).doesNotContain("genre:animation");
        assertThat(titleOnly.orElseThrow().penalties()).contains("taste_mismatch");
        assertThat(titleOnly.orElseThrow().score()).isLessThanOrEqualTo(68);
        assertThat(explicitAnimation.orElseThrow().matchedTags()).contains("genre:animation");
        assertThat(explicitAnimation.orElseThrow().score()).isGreaterThan(titleOnly.orElseThrow().score());
    }

    @Test
    void genericMoodFitWithoutLikedGenreOverlapCannotReachPerfectScore() {
        TagProfile profile = new TagProfile();
        profile.setAudience("friends");
        profile.setMood("light");
        profile.addWeight("audience:friends", 4);
        profile.addWeight("mood:light", 4);
        profile.addWeight("mood:funny", 3);
        profile.addWeight("genre:action", 6);
        profile.addWeight("genre:sf", 6);
        profile.addWeight("genre:drama", 5);
        profile.addWeight("genre:comedy", 5);
        profile.addPreferredGenre("action");
        profile.addPreferredGenre("sf");
        profile.addLikedGenre("drama");
        profile.addLikedGenre("comedy");

        var genericFit = scorer.scoreOne(profile, candidate(
            "Soft Comedy",
            "12",
            105,
            Set.of("genre:drama", "genre:comedy", "mood:light", "mood:funny")
        )).orElseThrow();
        var directFit = scorer.scoreOne(profile, candidate(
            "Tagged SF",
            "12",
            120,
            Set.of("genre:sf", "genre:adventure")
        )).orElseThrow();

        assertThat(genericFit.matchedTags()).contains("audience:friends", "mood:light", "mood:funny", "genre:drama", "genre:comedy");
        assertThat(genericFit.matchedTags()).doesNotContain("genre:action", "genre:sf");
        assertThat(genericFit.penalties()).contains("taste_mismatch");
        assertThat(genericFit.score()).isLessThan(100);
        assertThat(genericFit.score()).isLessThanOrEqualTo(68);
        assertThat(directFit.matchedTags()).contains("genre:sf");
        assertThat(directFit.score()).isGreaterThan(genericFit.score());
    }

    @Test
    void noDirectTasteMismatchKeepsScoreSpreadBelowDirectMatchBand() {
        TagProfile profile = new TagProfile();
        profile.setMood("light");
        profile.addPreferredGenre("romance");
        profile.addWeight("genre:romance", 6);
        profile.addWeight("mood:light", 4);
        profile.addWeight("mood:funny", 3);
        profile.addWeight("content:comfort", 3);

        var strongReserve = scorer.scoreOne(profile, candidate(
            "Strong Reserve",
            "12",
            105,
            Set.of("mood:light", "mood:funny", "content:comfort")
        )).orElseThrow();
        var weakReserve = scorer.scoreOne(profile, candidate(
            "Weak Reserve",
            "12",
            150,
            Set.of()
        )).orElseThrow();

        assertThat(strongReserve.penalties()).contains("taste_mismatch");
        assertThat(weakReserve.penalties()).contains("taste_mismatch");
        assertThat(strongReserve.score()).isGreaterThan(weakReserve.score());
        assertThat(strongReserve.score()).isLessThan(68);
        assertThat(weakReserve.score()).isLessThan(strongReserve.score());
    }

    private ShowtimeCandidate candidate(String ageRating, Integer runtimeMinutes, Set<String> tags) {
        return candidate("Test Movie", ageRating, runtimeMinutes, tags);
    }

    private ShowtimeCandidate candidate(String title, String ageRating, Integer runtimeMinutes, Set<String> tags) {
        return new ShowtimeCandidate(
            1L,
            10L,
            title,
            "CGV",
            "movie-1",
            "Test Theater",
            "Seoul",
            "Screen 1",
            "2D",
            "2D",
            LocalDate.now(),
            LocalDateTime.now().plusHours(2),
            LocalDateTime.now().plusHours(4),
            40,
            100,
            12_000,
            "KRW",
            "https://example.test/booking",
            "https://example.test/poster.jpg",
            ageRating,
            runtimeMinutes,
            tags
        );
    }
}
