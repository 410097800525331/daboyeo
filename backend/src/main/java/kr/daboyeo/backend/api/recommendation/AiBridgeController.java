package kr.daboyeo.backend.api.recommendation;

import kr.daboyeo.backend.domain.recommendation.AiBridgeModels.AiBridgeAckResponse;
import kr.daboyeo.backend.domain.recommendation.AiBridgeModels.AiBridgeHeartbeatRequest;
import kr.daboyeo.backend.domain.recommendation.AiBridgeModels.AiBridgeJobResponse;
import kr.daboyeo.backend.domain.recommendation.AiBridgeModels.AiBridgeResultRequest;
import kr.daboyeo.backend.service.recommendation.AiBridgeJobService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/internal/ai-bridge")
public class AiBridgeController {

    private final AiBridgeJobService bridgeJobService;

    public AiBridgeController(AiBridgeJobService bridgeJobService) {
        this.bridgeJobService = bridgeJobService;
    }

    @PostMapping("/heartbeat")
    public AiBridgeAckResponse heartbeat(
        @RequestHeader(name = AiBridgeJobService.TOKEN_HEADER, required = false) String token,
        @RequestBody(required = false) AiBridgeHeartbeatRequest request
    ) {
        bridgeJobService.requireAuthorized(token);
        bridgeJobService.heartbeat(request);
        return new AiBridgeAckResponse(true);
    }

    @GetMapping("/jobs")
    public ResponseEntity<AiBridgeJobResponse> claimJob(
        @RequestHeader(name = AiBridgeJobService.TOKEN_HEADER, required = false) String token,
        @RequestParam(name = "provider") String provider,
        @RequestParam(name = "bridgeId", defaultValue = "") String bridgeId
    ) {
        bridgeJobService.requireAuthorized(token);
        return bridgeJobService.claimJob(provider, bridgeId)
            .map(ResponseEntity::ok)
            .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @PostMapping("/jobs/{jobId}/result")
    public AiBridgeAckResponse completeJob(
        @RequestHeader(name = AiBridgeJobService.TOKEN_HEADER, required = false) String token,
        @PathVariable String jobId,
        @RequestBody(required = false) AiBridgeResultRequest request
    ) {
        bridgeJobService.requireAuthorized(token);
        bridgeJobService.completeJob(jobId, request);
        return new AiBridgeAckResponse(true);
    }
}
