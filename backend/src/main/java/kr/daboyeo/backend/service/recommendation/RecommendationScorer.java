package kr.daboyeo.backend.service.recommendation;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import kr.daboyeo.backend.domain.recommendation.RecommendationModels.ScoredCandidate;
import kr.daboyeo.backend.domain.recommendation.RecommendationModels.SearchFilters;
import kr.daboyeo.backend.domain.recommendation.RecommendationModels.ShowtimeCandidate;
import kr.daboyeo.backend.domain.recommendation.RecommendationModels.TagProfile;
import org.springframework.stereotype.Component;

@Component
public class RecommendationScorer {

    private static final int BASE_SCORE = 46;
    private static final int NO_DIRECT_TASTE_MATCH_CAP = 68;

    public List<ScoredCandidate> score(TagProfile profile, List<ShowtimeCandidate> candidates) {
        return score(profile, candidates, null);
    }

    public List<ScoredCandidate> score(TagProfile profile, List<ShowtimeCandidate> candidates, SearchFilters filters) {
        return candidates.stream()
            .map(candidate -> scoreOne(profile, candidate, filters))
            .flatMap(Optional::stream)
            .sorted(Comparator.comparingInt(ScoredCandidate::score).reversed())
            .toList();
    }

    public Optional<ScoredCandidate> scoreOne(TagProfile profile, ShowtimeCandidate candidate) {
        return scoreOne(profile, candidate, null);
    }

    public Optional<ScoredCandidate> scoreOne(TagProfile profile, ShowtimeCandidate candidate, SearchFilters filters) {
        if (isBlockedForChild(profile, candidate)) {
            return Optional.empty();
        }
        if (hasTooFewSeats(filters, candidate)) {
            return Optional.empty();
        }

        int score = BASE_SCORE;
        List<String> matchedTags = new ArrayList<>();
        List<String> penalties = new ArrayList<>();
        boolean hasTasteAnchor = profile != null && !tasteAnchorGenres(profile).isEmpty();
        boolean hasDirectTasteMatch = hasDirectTasteAnchorMatch(profile, candidate);

        for (String tag : candidate.allTags()) {
            int weight = profile.weight(tag);
            if (weight != 0) {
                score += weight * 4;
                matchedTags.add(tag);
            }
        }

        score += priceBonus(candidate);
        score += seatBonus(candidate, filters);
        score += timeBonus(profile, candidate, filters);
        score -= avoidPenalty(profile, candidate, penalties);

        if (candidate.runtimeMinutes() != null && candidate.runtimeMinutes() <= 110) {
            score += 2;
        }

        if (hasTasteAnchor) {
            if (hasDirectTasteMatch) {
                score += 12;
            } else {
                score -= 18;
                penalties.add("taste_mismatch");
            }
        }

        int bounded = Math.max(0, Math.min(100, score));
        if (hasTasteAnchor && !hasDirectTasteMatch) {
            bounded = Math.min(bounded, NO_DIRECT_TASTE_MATCH_CAP);
        }
        return Optional.of(new ScoredCandidate(candidate, bounded, matchedTags, penalties));
    }

    private boolean hasDirectTasteAnchorMatch(TagProfile profile, ShowtimeCandidate candidate) {
        var tasteAnchors = tasteAnchorGenres(profile);
        if (tasteAnchors.isEmpty()) {
            return false;
        }
        return candidate.allTags().stream()
            .map(this::normalizeTag)
            .anyMatch(tag -> tag.startsWith("genre:") && tasteAnchors.contains(tag));
    }

    private java.util.Set<String> tasteAnchorGenres(TagProfile profile) {
        if (profile == null) {
            return java.util.Set.of();
        }
        if (!profile.preferredGenres().isEmpty()) {
            return profile.preferredGenres();
        }
        return profile.likedGenres();
    }

    private boolean isBlockedForChild(TagProfile profile, ShowtimeCandidate candidate) {
        if (!"child".equals(profile.audience())) {
            return false;
        }
        String age = normalize(candidate.ageRating());
        if (isUnsafeForChild(age)) {
            return true;
        }
        return candidate.allTags().stream().anyMatch(tag ->
            tag.equals("genre:horror")
                || tag.equals("content:violence")
                || tag.equals("content:adult")
                || tag.equals("content:dark")
        );
    }

    private boolean isUnsafeForChild(String age) {
        if (age.isBlank()) {
            return false;
        }
        if (age.contains("전체") || age.contains("all") || age.contains("12")) {
            return false;
        }
        return age.contains("청불")
            || age.contains("청소년")
            || age.contains("불가")
            || age.contains("adult")
            || age.contains("restricted")
            || age.contains("19")
            || age.contains("18")
            || age.contains("15");
    }

    private int priceBonus(ShowtimeCandidate candidate) {
        Integer price = candidate.minPriceAmount();
        if (price == null || price <= 0) {
            return 0;
        }
        if (price <= 10_000) {
            return 5;
        }
        if (price <= 13_000) {
            return 3;
        }
        if (price <= 16_000) {
            return 1;
        }
        return 0;
    }

    private int seatBonus(ShowtimeCandidate candidate) {
        return seatBonus(candidate, null);
    }

    private int seatBonus(ShowtimeCandidate candidate, SearchFilters filters) {
        Integer remaining = candidate.remainingSeatCount();
        Integer total = candidate.totalSeatCount();
        int value = 0;
        if (remaining == null || total == null || total <= 0) {
            if (filters != null && filters.hasPersonCount()) {
                value -= 2;
            }
            return value;
        }
        double ratio = remaining / (double) total;
        if (ratio >= 0.4) {
            value += 4;
        } else if (ratio >= 0.2) {
            value += 1;
        } else {
            value -= 4;
        }

        if (filters != null && filters.hasPersonCount()) {
            if (remaining >= filters.personCount() * 4) {
                value += 1;
            } else if (remaining == filters.personCount()) {
                value -= 1;
            }
        }
        return value;
    }

    private int timeBonus(TagProfile profile, ShowtimeCandidate candidate) {
        return timeBonus(profile, candidate, null);
    }

    private int timeBonus(TagProfile profile, ShowtimeCandidate candidate, SearchFilters filters) {
        if (candidate.startsAt() == null) {
            return 0;
        }
        int hour = candidate.startsAt().getHour();
        int value = 0;
        if (hour >= 1 && hour < 6) {
            value -= 6;
        }
        if ("child".equals(profile.audience()) && hour >= 20) {
            value -= 10;
        } else if ("family".equals(profile.audience()) && hour >= 21) {
            value -= 6;
        }
        if ("light".equals(profile.mood()) && hour >= 10 && hour <= 22) {
            value += 2;
        }
        if ("exciting".equals(profile.mood()) && hour >= 17 && hour <= 23) {
            value += 2;
        }
        if ("calm".equals(profile.mood()) && hour >= 9 && hour <= 18) {
            value += 2;
        }
        if (filters != null && filters.hasTimeRange()) {
            if ("morning".equals(filters.timeRange()) && "light".equals(profile.mood())) {
                value += 2;
            }
            if ("night".equals(filters.timeRange())) {
                if ("immersive".equals(profile.mood()) || "tense".equals(profile.mood())) {
                    value += 2;
                }
                if ("child".equals(profile.audience())) {
                    value -= 4;
                }
                if (profile.avoids("too_long")) {
                    value -= 3;
                }
            }
        }
        return value;
    }

    private boolean hasTooFewSeats(SearchFilters filters, ShowtimeCandidate candidate) {
        return filters != null
            && filters.hasPersonCount()
            && candidate.remainingSeatCount() != null
            && candidate.remainingSeatCount() < filters.personCount();
    }

    private int avoidPenalty(TagProfile profile, ShowtimeCandidate candidate, List<String> penalties) {
        int penalty = 0;
        if (profile.avoids("too_long") && candidate.runtimeMinutes() != null && candidate.runtimeMinutes() > 130) {
            int value = Math.min(25, 10 + ((candidate.runtimeMinutes() - 130) / 5));
            penalty += value;
            penalties.add("too_long");
        }
        if (profile.avoids("violence") && hasAny(candidate, "content:violence", "genre:horror", "genre:thriller")) {
            penalty += 22;
            penalties.add("violence");
        }
        if (profile.avoids("complex") && hasAny(candidate, "content:complex", "mood:serious", "mood:immersive")) {
            penalty += 12;
            penalties.add("complex");
        }
        if (profile.avoids("sad_ending") && hasAny(candidate, "content:sad_ending", "mood:sad")) {
            penalty += 14;
            penalties.add("sad_ending");
        }
        if (profile.avoids("loud") && hasAny(candidate, "content:loud", "mood:exciting")) {
            penalty += 9;
            penalties.add("loud");
        }
        return penalty;
    }

    private boolean hasAny(ShowtimeCandidate candidate, String... tags) {
        var all = candidate.allTags();
        for (String tag : tags) {
            if (all.contains(tag)) {
                return true;
            }
        }
        return false;
    }

    private String normalize(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT);
    }

    private String normalizeTag(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }
}
