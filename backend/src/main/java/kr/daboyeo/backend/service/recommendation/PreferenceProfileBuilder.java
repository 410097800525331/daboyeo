package kr.daboyeo.backend.service.recommendation;

import java.util.Map;
import java.util.Set;
import kr.daboyeo.backend.domain.recommendation.RecommendationModels.PosterChoices;
import kr.daboyeo.backend.domain.recommendation.RecommendationModels.PosterSeedMovie;
import kr.daboyeo.backend.domain.recommendation.RecommendationModels.RecommendationSurvey;
import kr.daboyeo.backend.domain.recommendation.RecommendationModels.TagProfile;
import org.springframework.stereotype.Component;

@Component
public class PreferenceProfileBuilder {

    private static final int MIN_LIKED_POSTERS = 3;
    private static final int MAX_LIKED_POSTERS = 5;
    private static final Set<String> AUDIENCES = Set.of("alone", "friends", "date", "family", "child");
    private static final Set<String> MOODS = Set.of("light", "immersive", "exciting", "calm", "tense");
    private static final Set<String> GENRES = Set.of(
        "action",
        "adventure",
        "animation",
        "comedy",
        "crime",
        "drama",
        "family",
        "fantasy",
        "history",
        "horror",
        "music",
        "musical",
        "mystery",
        "romance",
        "sf",
        "thriller"
    );

    private final PosterSeedService posterSeedService;

    public PreferenceProfileBuilder(PosterSeedService posterSeedService) {
        this.posterSeedService = posterSeedService;
    }

    public TagProfile build(
        RecommendationSurvey survey,
        PosterChoices choices,
        Map<String, Integer> persistedWeights
    ) {
        validateSurvey(survey);
        validatePosterChoices(choices);

        TagProfile profile = new TagProfile();
        profile.setAudience(survey.audience());
        profile.setMood(survey.mood());
        profile.addAvoid(survey.avoid());
        profile.addWeights(persistedWeights);

        applyAudience(profile, survey.audience());
        applyMood(profile, survey.mood());
        survey.preferredGenres().forEach(value -> applyPreferredGenre(profile, value));
        survey.avoid().forEach(value -> profile.addWeight("content:" + value, -3));

        choices.likedSeedMovieIds().stream()
            .map(posterSeedService::findById)
            .flatMap(OptionalUtils::stream)
            .forEach(seed -> applyLikedSeed(profile, seed));

        return profile;
    }

    private void validateSurvey(RecommendationSurvey survey) {
        if (survey == null) {
            throw new IllegalArgumentException("설문 답변이 필요해.");
        }
        if (!AUDIENCES.contains(survey.audience())) {
            throw new IllegalArgumentException("누구랑 볼지 선택해줘.");
        }
        if (!MOODS.contains(survey.mood())) {
            throw new IllegalArgumentException("오늘 컨디션을 선택해줘.");
        }
        if (survey.avoid().size() > 5) {
            throw new IllegalArgumentException("피하고 싶은 요소가 너무 많아.");
        }
        if (survey.preferredGenres().size() > 5) {
            throw new IllegalArgumentException("선호 장르는 5개까지만 골라줘.");
        }
        if (!GENRES.containsAll(survey.preferredGenres())) {
            throw new IllegalArgumentException("지원하지 않는 선호 장르가 있어.");
        }
    }

    private void validatePosterChoices(PosterChoices choices) {
        if (choices == null) {
            throw new IllegalArgumentException("포스터 선택이 필요해.");
        }
        int likedCount = choices.likedSeedMovieIds().size();
        if (likedCount < MIN_LIKED_POSTERS) {
            throw new IllegalArgumentException("끌리는 포스터를 3개 이상 골라줘.");
        }
        if (likedCount > MAX_LIKED_POSTERS) {
            throw new IllegalArgumentException("끌리는 포스터는 5개까지만 골라줘.");
        }
    }

    private void applyAudience(TagProfile profile, String audience) {
        switch (audience) {
            case "alone" -> {
                profile.addWeight("audience:alone", 4);
                profile.addWeight("mood:immersive", 1);
            }
            case "friends" -> {
                profile.addWeight("audience:friends", 4);
                profile.addWeight("mood:funny", 2);
                profile.addWeight("mood:exciting", 2);
            }
            case "date" -> {
                profile.addWeight("audience:date", 4);
                profile.addWeight("mood:warm", 2);
                profile.addWeight("genre:romance", 1);
            }
            case "family" -> {
                profile.addWeight("audience:family", 4);
                profile.addWeight("mood:comfort", 2);
            }
            case "child" -> {
                profile.addWeight("audience:child", 5);
                profile.addWeight("audience:family", 3);
                profile.addWeight("genre:animation", 2);
            }
            default -> {
            }
        }
    }

    private void applyMood(TagProfile profile, String mood) {
        switch (mood) {
            case "light" -> {
                profile.addWeight("mood:light", 4);
                profile.addWeight("mood:funny", 3);
                profile.addWeight("pace:medium", 1);
            }
            case "immersive" -> {
                profile.addWeight("mood:immersive", 4);
                profile.addWeight("genre:drama", 1);
                profile.addWeight("genre:mystery", 1);
            }
            case "exciting" -> {
                profile.addWeight("mood:exciting", 4);
                profile.addWeight("pace:fast", 3);
                profile.addWeight("genre:action", 2);
            }
            case "calm" -> {
                profile.addWeight("mood:calm", 4);
                profile.addWeight("mood:warm", 2);
                profile.addWeight("pace:slow", 2);
            }
            case "tense" -> {
                profile.addWeight("mood:tense", 4);
                profile.addWeight("genre:thriller", 2);
                profile.addWeight("pace:medium", 1);
            }
            default -> {
            }
        }
    }

    private void applyPreferredGenre(TagProfile profile, String genre) {
        profile.addWeight("genre:" + genre, 6);
        profile.addPreferredGenre(genre);
    }

    private void applyLikedSeed(TagProfile profile, PosterSeedMovie seed) {
        seed.genres().forEach(value -> {
            profile.addWeight("genre:" + value, 5);
            if (!"popular".equals(value)) {
                profile.addLikedGenre(value);
            }
        });
        seed.moods().forEach(value -> profile.addWeight("mood:" + value, 5));
        if (!seed.pace().isBlank()) {
            profile.addWeight("pace:" + seed.pace(), 3);
        }
        seed.audiences().forEach(value -> profile.addWeight("audience:" + value, 2));
    }

    private static final class OptionalUtils {
        private OptionalUtils() {
        }

        static <T> java.util.stream.Stream<T> stream(java.util.Optional<T> optional) {
            return optional.stream();
        }
    }
}
