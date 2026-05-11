package kr.daboyeo.backend.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import kr.daboyeo.backend.domain.LiveMovieSearchCriteria;
import kr.daboyeo.backend.security.PublicApiRateLimiter;
import kr.daboyeo.backend.service.LiveMovieService;
import kr.daboyeo.backend.service.LiveMovieService.LiveMovieResponse;
import kr.daboyeo.backend.service.LiveMovieService.LiveMovieSearchMeta;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@WebMvcTest(
    value = LiveMovieController.class,
    properties = "daboyeo.security.nearby-refresh-rate-limit-per-minute=2"
)
@Import(PublicApiRateLimiter.class)
class LiveMovieControllerSecurityTests {

    private static final Clock FIXED_CLOCK = Clock.fixed(Instant.parse("2026-05-11T00:00:00Z"), ZoneId.of("Asia/Seoul"));

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private LiveMovieService liveMovieService;

    @Test
    void nearbyRequestsAreRateLimitedPerClient() throws Exception {
        LiveMovieSearchCriteria criteria = LiveMovieSearchCriteria.of(
            new BigDecimal("37.3500"),
            new BigDecimal("127.1090"),
            null,
            null,
            null,
            null,
            List.of(),
            List.of(),
            List.of(),
            null,
            "",
            null,
            FIXED_CLOCK
        );
        given(liveMovieService.buildCriteria(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
            .willReturn(criteria);
        given(liveMovieService.findNearby(criteria))
            .willReturn(new LiveMovieResponse(
                new LiveMovieSearchMeta(
                    criteria.lat(),
                    criteria.lng(),
                    "2026-05-11",
                    "06:00",
                    "23:59",
                    criteria.radiusKm(),
                    0,
                    true,
                    false,
                    null
                ),
                List.of()
            ));

        mockMvc.perform(nearbyRequest().with(remote("203.0.113.20")))
            .andExpect(status().isOk());
        mockMvc.perform(nearbyRequest().with(remote("203.0.113.20")))
            .andExpect(status().isOk());
        mockMvc.perform(nearbyRequest().with(remote("203.0.113.20")))
            .andExpect(status().isTooManyRequests());
    }

    private static org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder nearbyRequest() {
        return get("/api/live/nearby")
            .queryParam("lat", "37.3500")
            .queryParam("lng", "127.1090")
            .accept(MediaType.APPLICATION_JSON);
    }

    private static RequestPostProcessor remote(String address) {
        return request -> {
            request.setRemoteAddr(address);
            return request;
        };
    }
}
