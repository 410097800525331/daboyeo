package kr.daboyeo.backend.api.recommendation;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import kr.daboyeo.backend.domain.recommendation.RecommendationModels.RecommendationResponse;
import kr.daboyeo.backend.domain.recommendation.RecommendationModels.SessionResponse;
import kr.daboyeo.backend.security.PublicApiRateLimiter;
import kr.daboyeo.backend.service.recommendation.RecommendationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@WebMvcTest(
    value = RecommendationController.class,
    properties = {
        "daboyeo.security.recommendation-rate-limit-per-minute=2",
        "daboyeo.security.session-rate-limit-per-minute=2",
        "daboyeo.security.feedback-rate-limit-per-minute=2"
    }
)
@Import(PublicApiRateLimiter.class)
class RecommendationControllerSecurityTests {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private RecommendationService recommendationService;

    @Test
    void recommendationRequestsAreRateLimitedPerClientAndSession() throws Exception {
        given(recommendationService.recommend(any()))
            .willReturn(new RecommendationResponse("run-1", "fast", "fallback", "fallback", "ok", List.of()));
        String payload = recommendationPayload("anon_rate_limit");

        mockMvc.perform(post("/api/recommendations")
                .with(remote("203.0.113.10"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload))
            .andExpect(status().isOk());
        mockMvc.perform(post("/api/recommendations")
                .with(remote("203.0.113.10"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload))
            .andExpect(status().isOk());
        mockMvc.perform(post("/api/recommendations")
                .with(remote("203.0.113.10"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload))
            .andExpect(status().isTooManyRequests());
    }

    @Test
    void recommendationRateLimitCannotBeBypassedByRotatingAnonymousId() throws Exception {
        given(recommendationService.recommend(any()))
            .willReturn(new RecommendationResponse("run-1", "fast", "fallback", "fallback", "ok", List.of()));

        mockMvc.perform(post("/api/recommendations")
                .with(remote("203.0.113.12"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(recommendationPayload("anon_a")))
            .andExpect(status().isOk());
        mockMvc.perform(post("/api/recommendations")
                .with(remote("203.0.113.12"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(recommendationPayload("anon_b")))
            .andExpect(status().isOk());
        mockMvc.perform(post("/api/recommendations")
                .with(remote("203.0.113.12"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(recommendationPayload("anon_c")))
            .andExpect(status().isTooManyRequests());
    }

    @Test
    void sessionCreationIsRateLimitedBeforeProfileWork() throws Exception {
        given(recommendationService.ensureSession(any())).willReturn(new SessionResponse("anon_session"));

        mockMvc.perform(post("/api/recommendation/sessions")
                .with(remote("203.0.113.11"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isOk());
        mockMvc.perform(post("/api/recommendation/sessions")
                .with(remote("203.0.113.11"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isOk());
        mockMvc.perform(post("/api/recommendation/sessions")
                .with(remote("203.0.113.11"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isTooManyRequests());
    }

    private static RequestPostProcessor remote(String address) {
        return request -> {
            request.setRemoteAddr(address);
            return request;
        };
    }

    private static String recommendationPayload(String anonymousId) {
        return """
            {
              "anonymousId": "%s",
              "mode": "fast",
              "survey": {"audience": "friends", "mood": "light", "avoid": []},
              "posterChoices": {"likedSeedMovieIds": [], "dislikedSeedMovieIds": []}
            }
            """.formatted(anonymousId);
    }
}
