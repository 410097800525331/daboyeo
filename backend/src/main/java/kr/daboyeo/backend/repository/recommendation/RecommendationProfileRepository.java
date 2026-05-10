package kr.daboyeo.backend.repository.recommendation;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import java.util.Optional;
import kr.daboyeo.backend.domain.recommendation.RecommendationModels.FeedbackAction;
import kr.daboyeo.backend.domain.recommendation.RecommendationModels.RecommendationProfile;
import kr.daboyeo.backend.domain.recommendation.RecommendationModels.RecommendationRequest;
import kr.daboyeo.backend.domain.recommendation.RecommendationModels.RecommendationResponse;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class RecommendationProfileRepository {

    private static final TypeReference<Map<String, Integer>> WEIGHTS_TYPE = new TypeReference<>() {
    };

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public RecommendationProfileRepository(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    public void ensureProfile(String anonymousId) {
        jdbcTemplate.update(
            """
            INSERT INTO recommendation_profiles (anonymous_id, tag_weights_json)
            VALUES (?, ?)
            ON DUPLICATE KEY UPDATE updated_at = updated_at
            """,
            anonymousId,
            "{}"
        );
    }

    public Optional<RecommendationProfile> findProfile(String anonymousId) {
        return jdbcTemplate.query(
            """
            SELECT anonymous_id, tag_weights_json
            FROM recommendation_profiles
            WHERE anonymous_id = ?
            """,
            rs -> {
                if (!rs.next()) {
                    return Optional.empty();
                }
                return Optional.of(new RecommendationProfile(
                    rs.getString("anonymous_id"),
                    readWeights(rs.getString("tag_weights_json"))
                ));
            },
            anonymousId
        );
    }

    public void upsertSurvey(String anonymousId, RecommendationRequest request) {
        jdbcTemplate.update(
            """
            INSERT INTO recommendation_profiles (
              anonymous_id, survey_json, liked_seed_json, disliked_seed_json, tag_weights_json
            )
            VALUES (?, ?, ?, ?, ?)
            ON DUPLICATE KEY UPDATE
              survey_json = VALUES(survey_json),
              liked_seed_json = VALUES(liked_seed_json),
              disliked_seed_json = VALUES(disliked_seed_json),
              updated_at = CURRENT_TIMESTAMP(3)
            """,
            anonymousId,
            json(request.survey()),
            json(request.posterChoices().likedSeedMovieIds()),
            json(request.posterChoices().dislikedSeedMovieIds()),
            "{}"
        );
    }

    public void updateWeights(String anonymousId, Map<String, Integer> tagWeights) {
        jdbcTemplate.update(
            """
            UPDATE recommendation_profiles
            SET tag_weights_json = ?, updated_at = CURRENT_TIMESTAMP(3)
            WHERE anonymous_id = ?
            """,
            json(tagWeights),
            anonymousId
        );
    }

    public void saveRun(
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
        jdbcTemplate.update(
            """
            INSERT INTO recommendation_runs (
              run_id, anonymous_id, mode, model_name, request_json,
              candidate_scores_json, ai_response_json, response_json, elapsed_ms
            )
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
            """,
            runId,
            anonymousId,
            mode,
            modelName,
            json(request),
            json(candidateScores),
            aiResponseJson == null || aiResponseJson.isBlank() ? null : aiResponseJson,
            json(response),
            elapsedMs
        );
    }

    public void saveFeedback(
        String runId,
        String anonymousId,
        Long movieId,
        Long showtimeId,
        FeedbackAction action,
        Map<String, Integer> tagDelta
    ) {
        jdbcTemplate.update(
            """
            INSERT INTO recommendation_feedback (
              run_id, anonymous_id, movie_id, showtime_id, action, tag_delta_json
            )
            VALUES (?, ?, ?, ?, ?, ?)
            """,
            runId,
            anonymousId,
            movieId,
            showtimeId,
            action.wireValue(),
            json(tagDelta)
        );
    }

    public void deleteProfileData(String anonymousId) {
        jdbcTemplate.update("DELETE FROM recommendation_feedback WHERE anonymous_id = ?", anonymousId);
        jdbcTemplate.update("DELETE FROM recommendation_runs WHERE anonymous_id = ?", anonymousId);
        jdbcTemplate.update("DELETE FROM recommendation_profiles WHERE anonymous_id = ?", anonymousId);
    }

    private Map<String, Integer> readWeights(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, WEIGHTS_TYPE);
        } catch (JsonProcessingException e) {
            return Map.of();
        }
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("추천 데이터를 JSON으로 바꾸지 못했어.", e);
        }
    }
}
