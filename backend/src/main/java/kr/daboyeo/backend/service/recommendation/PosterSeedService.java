package kr.daboyeo.backend.service.recommendation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;
import kr.daboyeo.backend.config.RecommendationProperties;
import kr.daboyeo.backend.domain.recommendation.RecommendationModels.PosterSeedMovie;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

@Service
public class PosterSeedService {

    private static final int MAX_LIMIT = 40;
    private static final String MOVIE_POSTER_MANIFEST = "recommendation/korea-boxoffice-top50-posters.json";
    private static final String MOVIE_POSTER_TAGS = "recommendation/korea-boxoffice-top50-poster-tags.json";
    private static final String ANIME_POSTER_MANIFEST = "recommendation/korea-animation-boxoffice-top30-posters.json";
    private static final String ANIME_POSTER_TAGS = "recommendation/korea-animation-boxoffice-top30-poster-tags.json";
    private static final String ANIME_ID_PREFIX = "anime:";
    private static final String GENRE_ANIMATION = "animation";
    private static final List<String> DEFAULT_GENRES = List.of("popular");
    private static final List<String> DEFAULT_MOODS = List.of();
    private static final List<String> DEFAULT_AUDIENCES = List.of();
    private static final PosterSeedTags DEFAULT_TAGS = new PosterSeedTags(
        DEFAULT_GENRES,
        DEFAULT_MOODS,
        "",
        DEFAULT_AUDIENCES,
        List.of(),
        ""
    );

    private final List<PosterSeedMovie> movieSeedMovies;
    private final List<PosterSeedMovie> animeSeedMovies;
    private final Map<String, PosterSeedMovie> seedById;
    private final RecommendationProperties properties;
    private final SecureRandom random = new SecureRandom();

    @Autowired
    public PosterSeedService(ObjectMapper objectMapper, RecommendationProperties properties) {
        this.properties = properties;
        this.movieSeedMovies = loadSeed(
            objectMapper,
            MOVIE_POSTER_MANIFEST,
            loadTags(objectMapper, MOVIE_POSTER_TAGS, "movie"),
            "",
            "movie"
        );
        this.animeSeedMovies = loadSeed(
            objectMapper,
            ANIME_POSTER_MANIFEST,
            loadTags(objectMapper, ANIME_POSTER_TAGS, "anime"),
            ANIME_ID_PREFIX,
            "anime"
        );
        this.seedById = StreamSupport.stream(List.of(movieSeedMovies, animeSeedMovies).spliterator(), false)
            .flatMap(List::stream)
            .collect(Collectors.toUnmodifiableMap(PosterSeedMovie::id, Function.identity(), (left, right) -> left));
    }

    PosterSeedService(ObjectMapper objectMapper) {
        this(objectMapper, null);
    }

    public List<PosterSeedMovie> randomSeed(int limit) {
        return randomSeed(limit, List.of());
    }

    public List<PosterSeedMovie> randomSeed(int limit, List<String> preferredGenres) {
        if (containsAnimationGenre(preferredGenres)) {
            return prioritizedRandomSeed(limit, animeSeedMovies, movieSeedMovies);
        }
        return shuffledSeed(limit, movieSeedMovies);
    }

    public Optional<PosterSeedMovie> findById(String id) {
        return Optional.ofNullable(seedById.get(id));
    }

    private List<PosterSeedMovie> prioritizedRandomSeed(
        int limit,
        List<PosterSeedMovie> primary,
        List<PosterSeedMovie> fallback
    ) {
        int boundedLimit = boundedLimit(limit, primary.size() + fallback.size());
        List<PosterSeedMovie> selected = new ArrayList<>();
        Set<String> seenMovieCodes = new LinkedHashSet<>();

        appendDistinctByMovieCode(selected, seenMovieCodes, shuffledSeed(primary.size(), primary), boundedLimit);
        if (selected.size() < boundedLimit) {
            appendDistinctByMovieCode(selected, seenMovieCodes, shuffledSeed(fallback.size(), fallback), boundedLimit);
        }

        return List.copyOf(selected);
    }

    private void appendDistinctByMovieCode(
        List<PosterSeedMovie> selected,
        Set<String> seenMovieCodes,
        List<PosterSeedMovie> candidates,
        int limit
    ) {
        for (PosterSeedMovie candidate : candidates) {
            if (selected.size() >= limit) {
                return;
            }
            if (seenMovieCodes.add(seedMovieCode(candidate.id()))) {
                selected.add(candidate);
            }
        }
    }

    private List<PosterSeedMovie> shuffledSeed(int limit, List<PosterSeedMovie> source) {
        int boundedLimit = boundedLimit(limit, source.size());
        List<PosterSeedMovie> copy = new ArrayList<>(source);
        for (int i = copy.size() - 1; i > 0; i--) {
            int j = random.nextInt(i + 1);
            PosterSeedMovie current = copy.get(i);
            copy.set(i, copy.get(j));
            copy.set(j, current);
        }
        return copy.stream().limit(boundedLimit).toList();
    }

    private int boundedLimit(int limit, int availableSize) {
        return Math.max(1, Math.min(limit, Math.min(MAX_LIMIT, availableSize)));
    }

    private boolean containsAnimationGenre(List<String> preferredGenres) {
        return normalizeGenres(preferredGenres).contains(GENRE_ANIMATION);
    }

    private Set<String> normalizeGenres(List<String> genres) {
        if (genres == null || genres.isEmpty()) {
            return Set.of();
        }
        return genres.stream()
            .flatMap(value -> Arrays.stream((value == null ? "" : value).split(",")))
            .map(value -> value.trim().toLowerCase(Locale.ROOT))
            .map(value -> value.startsWith("genre:") ? value.substring("genre:".length()) : value)
            .filter(value -> !value.isBlank())
            .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private String seedMovieCode(String seedId) {
        if (seedId == null || seedId.isBlank()) {
            return "";
        }
        String normalized = seedId.trim();
        return normalized.startsWith(ANIME_ID_PREFIX)
            ? normalized.substring(ANIME_ID_PREFIX.length())
            : normalized;
    }

    private List<PosterSeedMovie> loadSeed(
        ObjectMapper objectMapper,
        String manifestPath,
        Map<String, PosterSeedTags> seedTags,
        String idPrefix,
        String label
    ) {
        ClassPathResource resource = new ClassPathResource(manifestPath);
        try (InputStream inputStream = resource.getInputStream()) {
            JsonNode moviesNode = objectMapper.readTree(inputStream).path("movies");
            if (!moviesNode.isArray()) {
                throw new IllegalStateException("R2 " + label + " poster manifest has no movies array.");
            }
            List<PosterSeedMovie> movies = StreamSupport.stream(moviesNode.spliterator(), false)
                .map(node -> toPosterSeedMovie(node, seedTags, idPrefix))
                .filter(movie -> !movie.id().isBlank() && !movie.posterUrl().isBlank())
                .toList();
            return List.copyOf(movies);
        } catch (IOException e) {
            throw new IllegalStateException("R2 " + label + " poster seed data could not be loaded.", e);
        }
    }

    private Map<String, PosterSeedTags> loadTags(ObjectMapper objectMapper, String tagsPath, String label) {
        ClassPathResource resource = new ClassPathResource(tagsPath);
        try (InputStream inputStream = resource.getInputStream()) {
            JsonNode moviesNode = objectMapper.readTree(inputStream).path("movies");
            if (!moviesNode.isObject()) {
                throw new IllegalStateException("R2 " + label + " poster tag data has no movies object.");
            }
            Map<String, PosterSeedTags> tagsByMovieCode = new LinkedHashMap<>();
            moviesNode.fields().forEachRemaining(entry ->
                tagsByMovieCode.put(entry.getKey(), toPosterSeedTags(entry.getValue()))
            );
            return Map.copyOf(tagsByMovieCode);
        } catch (IOException e) {
            throw new IllegalStateException("R2 " + label + " poster tag data could not be loaded.", e);
        }
    }

    private PosterSeedMovie toPosterSeedMovie(JsonNode node, Map<String, PosterSeedTags> seedTags, String idPrefix) {
        String movieCode = text(node, "movieCd");
        String rank = text(node, "rank");
        String id = movieCode.isBlank() ? "kobis-rank-" + rank : movieCode;
        PosterSeedTags tags = seedTags.getOrDefault(movieCode, DEFAULT_TAGS);
        return new PosterSeedMovie(
            idPrefix + id,
            text(node, "titleKo"),
            resolvePosterUrl(text(node, "posterPath")),
            tags.genres(),
            tags.moods(),
            tags.pace(),
            tags.audiences(),
            tags.avoid(),
            tags.ageRating()
        );
    }

    private PosterSeedTags toPosterSeedTags(JsonNode node) {
        return new PosterSeedTags(
            stringList(node.path("genres")),
            stringList(node.path("moods")),
            text(node, "pace"),
            stringList(node.path("audiences")),
            stringList(node.path("avoid")),
            text(node, "ageRating")
        );
    }

    private List<String> stringList(JsonNode node) {
        if (!node.isArray()) {
            return List.of();
        }
        return StreamSupport.stream(node.spliterator(), false)
            .map(value -> value.asText(""))
            .filter(value -> !value.isBlank())
            .distinct()
            .toList();
    }

    private String text(JsonNode node, String fieldName) {
        JsonNode value = node.path(fieldName);
        return value.isMissingNode() || value.isNull() ? "" : value.asText("");
    }

    private String resolvePosterUrl(String manifestPath) {
        String path = manifestPath == null ? "" : manifestPath.trim();
        String baseUrl = properties == null ? "" : properties.posterPublicBaseUrl();
        if (path.isBlank() || baseUrl == null || baseUrl.isBlank() || path.startsWith("http://") || path.startsWith("https://")) {
            return path;
        }
        String normalized = path.startsWith("/") ? path.substring(1) : path;
        String prefix = "src/assets/R2/";
        if (!normalized.startsWith(prefix)) {
            return path;
        }
        return baseUrl + "/" + normalized.substring(prefix.length());
    }

    private record PosterSeedTags(
        List<String> genres,
        List<String> moods,
        String pace,
        List<String> audiences,
        List<String> avoid,
        String ageRating
    ) {
        private PosterSeedTags {
            genres = genres == null ? List.of() : List.copyOf(genres);
            moods = moods == null ? List.of() : List.copyOf(moods);
            pace = pace == null ? "" : pace.trim();
            audiences = audiences == null ? List.of() : List.copyOf(audiences);
            avoid = avoid == null ? List.of() : List.copyOf(avoid);
            ageRating = ageRating == null ? "" : ageRating.trim();
        }
    }
}
