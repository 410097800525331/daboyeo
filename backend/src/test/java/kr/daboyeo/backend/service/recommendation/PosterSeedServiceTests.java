package kr.daboyeo.backend.service.recommendation;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.Test;

class PosterSeedServiceTests {

    private final PosterSeedService posterSeedService = new PosterSeedService(new ObjectMapper());

    @Test
    void defaultSeedKeepsGeneralMoviePosterPool() {
        var seeds = posterSeedService.randomSeed(12);

        assertThat(seeds).hasSize(12);
        assertThat(seeds).allSatisfy(seed -> {
            assertThat(seed.id()).doesNotStartWith("anime:");
            assertThat(seed.posterUrl()).contains("/src/assets/R2/posters/movie/");
        });
    }

    @Test
    void animationGenrePrioritizesNamespacedAnimePosterPool() {
        var seeds = posterSeedService.randomSeed(8, List.of("animation"));

        assertThat(seeds).hasSize(8);
        assertThat(seeds).allSatisfy(seed -> {
            assertThat(seed.id()).startsWith("anime:");
            assertThat(seed.posterUrl()).contains("/src/assets/R2/posters/anime/");
            assertThat(seed.genres()).contains("animation");
        });
    }

    @Test
    void animationPoolCanFillToMaxWithoutMovieCodeCollisions() {
        var seeds = posterSeedService.randomSeed(40, List.of("animation"));

        assertThat(seeds).hasSize(40);
        assertThat(seeds.subList(0, 30)).allSatisfy(seed -> {
            assertThat(seed.id()).startsWith("anime:");
            assertThat(seed.posterUrl()).contains("/src/assets/R2/posters/anime/");
        });
        assertThat(seeds.stream().map(seed -> seed.id().replaceFirst("^anime:", "")).toList())
            .doesNotHaveDuplicates();
    }

    @Test
    void animeDuplicateIsOwnedByNamespacedAnimePoolOnly() {
        var animeSeed = posterSeedService.findById("anime:20197803").orElseThrow();

        assertThat(animeSeed.title()).isEqualTo("겨울왕국 2");
        assertThat(animeSeed.genres()).contains("animation");
        assertThat(animeSeed.posterUrl()).contains("/src/assets/R2/posters/anime/");
        assertThat(posterSeedService.findById("20197803")).isEmpty();
    }

    @Test
    void findByIdStillResolvesGeneralMovieSeed() {
        var generalSeed = posterSeedService.findById("20129370").orElseThrow();

        assertThat(generalSeed.id()).isEqualTo("20129370");
        assertThat(generalSeed.posterUrl()).contains("/src/assets/R2/posters/movie/");
        assertThat(generalSeed.posterUrl()).endsWith("/the-admiral-roaring-currents.webp");
    }
}
