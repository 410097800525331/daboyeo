package kr.daboyeo.backend.api.recommendation;

import java.util.List;
import kr.daboyeo.backend.domain.recommendation.RecommendationModels.AiProviderStatus;
import kr.daboyeo.backend.domain.recommendation.RecommendationModels.FeedbackRequest;
import kr.daboyeo.backend.domain.recommendation.RecommendationModels.FeedbackResponse;
import kr.daboyeo.backend.domain.recommendation.RecommendationModels.PosterSeedMovie;
import kr.daboyeo.backend.domain.recommendation.RecommendationModels.RecommendationRequest;
import kr.daboyeo.backend.domain.recommendation.RecommendationModels.RecommendationResponse;
import kr.daboyeo.backend.domain.recommendation.RecommendationModels.SessionRequest;
import kr.daboyeo.backend.domain.recommendation.RecommendationModels.SessionResponse;
import kr.daboyeo.backend.service.recommendation.RecommendationService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class RecommendationController {

    private final RecommendationService recommendationService;

    public RecommendationController(RecommendationService recommendationService) {
        this.recommendationService = recommendationService;
    }

    @PostMapping("/recommendation/sessions")
    public SessionResponse createSession(@RequestBody(required = false) SessionRequest request) {
        return recommendationService.ensureSession(request == null ? null : request.anonymousId());
    }

    @DeleteMapping("/recommendation/sessions/{anonymousId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteSession(@PathVariable String anonymousId) {
        recommendationService.resetSession(anonymousId);
    }

    @GetMapping("/recommendation/poster-seed")
    public List<PosterSeedMovie> posterSeed(
        @RequestParam(name = "limit", defaultValue = "10") int limit,
        @RequestParam(name = "genres", required = false) List<String> genres
    ) {
        return recommendationService.posterSeed(limit, genres);
    }

    @GetMapping("/recommendation/providers/health")
    public List<AiProviderStatus> providerHealth() {
        return recommendationService.providerHealth();
    }

    @PostMapping("/recommendations")
    public RecommendationResponse recommend(@RequestBody RecommendationRequest request) {
        return recommendationService.recommend(request);
    }

    @PostMapping("/recommendations/{runId}/feedback")
    public FeedbackResponse feedback(@PathVariable String runId, @RequestBody FeedbackRequest request) {
        return recommendationService.feedback(runId, request);
    }
}
