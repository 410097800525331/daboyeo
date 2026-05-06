package kr.daboyeo.backend.service.recommendation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import kr.daboyeo.backend.domain.recommendation.RecommendationModels.PosterChoices;
import kr.daboyeo.backend.domain.recommendation.RecommendationModels.RecommendationSurvey;
import org.junit.jupiter.api.Test;

class PreferenceProfileBuilderTests {

    private final PosterSeedService posterSeedService = new PosterSeedService(new ObjectMapper());
    private final PreferenceProfileBuilder builder = new PreferenceProfileBuilder(posterSeedService);

    @Test
    void buildsWeightsFromSurveyPosterAndStoredFeedback() {
        var profile = builder.build(
            new RecommendationSurvey("friends", "exciting", List.of("too_long")),
            new PosterChoices(
                List.of("20129370", "20182530", "20150976"),
                List.of()
            ),
            Map.of("genre:action", 2)
        );

        assertThat(profile.audience()).isEqualTo("friends");
        assertThat(profile.mood()).isEqualTo("exciting");
        assertThat(profile.avoids("too_long")).isTrue();
        assertThat(profile.weight("genre:action")).isEqualTo(14);
        assertThat(profile.weight("genre:popular")).isZero();
        assertThat(profile.weight("genre:history")).isEqualTo(5);
        assertThat(profile.weight("genre:comedy")).isEqualTo(5);
        assertThat(profile.weight("genre:fantasy")).isEqualTo(5);
        assertThat(profile.weight("audience:friends")).isEqualTo(10);
        assertThat(profile.weight("audience:family")).isEqualTo(4);
        assertThat(profile.weight("mood:visual")).isEqualTo(15);
        assertThat(profile.likedGenres()).doesNotContain("genre:popular");
        assertThat(profile.likedGenres()).contains("genre:history", "genre:comedy", "genre:fantasy");
    }

    @Test
    void posterSeedsExposeMovieSpecificTags() {
        var seed = posterSeedService.findById("20182530").orElseThrow();

        assertThat(seed.title()).isEqualTo("극한직업");
        assertThat(seed.genres()).contains("comedy", "action", "crime");
        assertThat(seed.moods()).contains("funny", "light", "exciting");
        assertThat(seed.pace()).isEqualTo("fast");
        assertThat(seed.audiences()).containsExactly("friends", "family");
        assertThat(seed.ageRating()).isEqualTo("15");
    }

    @Test
    void selectedGenresBecomeExplicitPreferenceSignals() {
        var profile = builder.build(
            new RecommendationSurvey("friends", "light", List.of(), List.of("sf", "horror")),
            new PosterChoices(
                List.of("20129370", "20182530", "20150976"),
                List.of()
            ),
            Map.of()
        );

        assertThat(profile.weight("genre:sf")).isEqualTo(6);
        assertThat(profile.weight("genre:horror")).isEqualTo(6);
        assertThat(profile.likedGenres()).contains("genre:sf", "genre:horror");
        assertThat(profile.preferredGenres()).containsExactly("genre:sf", "genre:horror");
    }

    @Test
    void requiresAtLeastThreeLikedPosters() {
        assertThatThrownBy(() -> builder.build(
            new RecommendationSurvey("friends", "exciting", List.of()),
            new PosterChoices(List.of("20129370", "20182530"), List.of()),
            Map.of()
        )).isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("끌리는 포스터를 3개 이상");
    }

    @Test
    void rejectsMoreThanFiveLikedPosters() {
        assertThatThrownBy(() -> builder.build(
            new RecommendationSurvey("friends", "exciting", List.of()),
            new PosterChoices(
                List.of("20129370", "20182530", "20150976", "20137048", "20184889", "20197803"),
                List.of()
            ),
            Map.of()
        )).isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("끌리는 포스터는 5개까지만");
    }

    @Test
    void rejectsMoreThanFiveSelectedGenres() {
        assertThatThrownBy(() -> builder.build(
            new RecommendationSurvey(
                "friends",
                "exciting",
                List.of(),
                List.of("action", "sf", "adventure", "animation", "thriller", "comedy")
            ),
            new PosterChoices(List.of("20129370", "20182530", "20150976"), List.of()),
            Map.of()
        )).isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("선호 장르는 5개까지만");
    }

    @Test
    void ignoresDislikedPostersForPosterDiagnosis() {
        var profile = builder.build(
            new RecommendationSurvey("friends", "exciting", List.of()),
            new PosterChoices(
                List.of("20129370", "20182530", "20150976"),
                List.of("20137048", "20184889", "20197803")
            ),
            Map.of()
        );

        assertThat(profile.weight("genre:sf")).isZero();
    }
}
