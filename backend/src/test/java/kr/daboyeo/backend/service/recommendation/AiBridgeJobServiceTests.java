package kr.daboyeo.backend.service.recommendation;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import kr.daboyeo.backend.config.RecommendationProperties;
import kr.daboyeo.backend.domain.recommendation.AiBridgeModels.AiBridgeHeartbeatRequest;
import kr.daboyeo.backend.domain.recommendation.AiBridgeModels.AiBridgeResultRequest;
import kr.daboyeo.backend.domain.recommendation.RecommendationModels.AiProvider;
import kr.daboyeo.backend.domain.recommendation.RecommendationModels.RecommendationMode;
import org.junit.jupiter.api.Test;

class AiBridgeJobServiceTests {

    @Test
    void codexProviderRequiresHeartbeatBeforeJobsAreCreated() {
        AiBridgeJobService service = new AiBridgeJobService(properties("secret"));

        assertThat(service.bridgeStatus(AiProvider.CODEX).available()).isFalse();
        assertThat(service.bridgeStatus(AiProvider.CODEX).expectedModels())
            .containsExactly("gpt-5.4-mini", "gpt-5.5");
        assertThat(service.submitAndAwait(
            AiProvider.CODEX,
            RecommendationMode.FAST,
            "codex",
            Map.of("messages", List.of())
        )).isEmpty();

        service.heartbeat(new AiBridgeHeartbeatRequest("bridge-test", List.of("codex")));

        assertThat(service.bridgeStatus(AiProvider.CODEX).available()).isTrue();
    }

    @Test
    void workerCanClaimAndCompleteBridgeJob() throws Exception {
        AiBridgeJobService service = new AiBridgeJobService(properties("secret"));
        service.heartbeat(new AiBridgeHeartbeatRequest("bridge-test", List.of("codex")));

        CompletableFuture<String> result = CompletableFuture.supplyAsync(() -> service.submitAndAwait(
            AiProvider.CODEX,
            RecommendationMode.FAST,
            "codex",
            Map.of("messages", List.of(Map.of("role", "user", "content", "pick"))))
            .orElseThrow()
        );

        var job = java.util.stream.IntStream.range(0, 20)
            .mapToObj(ignored -> {
                var claimed = service.claimJob("codex", "bridge-test");
                if (claimed.isEmpty()) {
                    try {
                        Thread.sleep(25);
                    } catch (InterruptedException exception) {
                        Thread.currentThread().interrupt();
                    }
                }
                return claimed;
            })
            .filter(java.util.Optional::isPresent)
            .map(java.util.Optional::orElseThrow)
            .findFirst()
            .orElseThrow();
        assertThat(job.provider()).isEqualTo("codex");
        assertThat(job.request()).containsKey("messages");

        service.completeJob(job.jobId(), new AiBridgeResultRequest("{\"r\":[]}", ""));

        assertThat(result.get(2, TimeUnit.SECONDS)).isEqualTo("{\"r\":[]}");
    }

    private RecommendationProperties properties(String bridgeToken) {
        return new RecommendationProperties(
            null,
            "gpt-5.4-mini",
            "gpt-5.5",
            "",
            "xhigh",
            8,
            12,
            720,
            1300,
            bridgeToken,
            2,
            30,
            60,
            20,
            List.of("http://localhost:5173")
        );
    }
}
