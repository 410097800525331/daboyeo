package kr.daboyeo.backend.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import kr.daboyeo.backend.domain.LiveMovieSearchCriteria;
import kr.daboyeo.backend.domain.SeatState;
import kr.daboyeo.backend.security.PublicApiRateLimiter;
import kr.daboyeo.backend.service.LiveMovieService;
import kr.daboyeo.backend.service.LiveMovieService.LiveMovieResponse;
import kr.daboyeo.backend.service.LiveMovieService.LiveMovieScheduleItem;
import kr.daboyeo.backend.service.LiveMovieService.LiveMovieSearchMeta;
import kr.daboyeo.backend.service.LiveMovieService.MovieCatalogItem;
import kr.daboyeo.backend.service.LiveMovieService.MovieCatalogMeta;
import kr.daboyeo.backend.service.LiveMovieService.MovieCatalogResponse;
import kr.daboyeo.backend.service.LiveMovieService.MovieSchedulesResponse;
import kr.daboyeo.backend.service.LiveMovieService.MovieSummary;
import kr.daboyeo.backend.service.LiveMovieService.ScheduleCard;
import kr.daboyeo.backend.service.LiveMovieService.TheaterScheduleGroup;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(LiveMovieController.class)
class LiveMovieControllerTests {

    private static final Clock FIXED_CLOCK = Clock.fixed(Instant.parse("2026-04-23T00:00:00Z"), ZoneId.of("Asia/Seoul"));

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private LiveMovieService liveMovieService;

    @MockitoBean
    private PublicApiRateLimiter rateLimiter;

    @Test
    void nearbyReturnsExpectedContractShape() throws Exception {
        LiveMovieSearchCriteria criteria = sampleCriteria();
        given(liveMovieService.buildCriteria(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
            .willReturn(criteria);
        given(liveMovieService.findNearby(criteria))
            .willReturn(new LiveMovieResponse(
                new LiveMovieSearchMeta(
                    new BigDecimal("37.4979"),
                    new BigDecimal("127.0276"),
                    "2026-04-23",
                    "06:00",
                    "23:59",
                    new BigDecimal("8"),
                    1,
                    true,
                    false,
                    null
                ),
                List.of(sampleItem())
            ));

        mockMvc.perform(
                get("/api/live/nearby")
                    .queryParam("lat", "37.4979")
                    .queryParam("lng", "127.0276")
                    .queryParam("date", "2026-04-23")
                    .accept(MediaType.APPLICATION_JSON)
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.search.resultCount").value(1))
            .andExpect(jsonPath("$.search.databaseAvailable").value(true))
            .andExpect(jsonPath("$.search.pendingRefresh").value(false))
            .andExpect(jsonPath("$.results[0].movie_key").value("LOTTE_CINEMA:123"))
            .andExpect(jsonPath("$.results[0].provider").value("LOTTE"))
            .andExpect(jsonPath("$.results[0].provider_code").value("LOTTE_CINEMA"))
            .andExpect(jsonPath("$.results[0].seat_state").value("comfortable"))
            .andExpect(jsonPath("$.results[0].booking_url").value("https://booking.example/lotte/123"))
            .andExpect(jsonPath("$.results[0].poster_url").value("https://poster.example/lotte.jpg"));

        verify(liveMovieService).findNearby(criteria);
    }

    @Test
    void nearbyRejectsInvalidLatitude() throws Exception {
        mockMvc.perform(
                get("/api/live/nearby")
                    .queryParam("lat", "200")
                    .queryParam("lng", "127.0276")
                    .accept(MediaType.APPLICATION_JSON)
            )
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("BAD_REQUEST"))
            .andExpect(jsonPath("$.path").value("/api/live/nearby"))
            .andExpect(jsonPath("$.details").isArray())
            .andExpect(jsonPath("$.time").isString());
    }

    @Test
    void nearbyRejectsInvalidSeatStateWithCleanCode() throws Exception {
        mockMvc.perform(
                get("/api/live/nearby")
                    .queryParam("lat", "37.4979")
                    .queryParam("lng", "127.0276")
                    .queryParam("seatState", "weird")
                    .accept(MediaType.APPLICATION_JSON)
            )
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("BAD_REQUEST"));
    }

    @Test
    void moviesReturnsPopularCatalogWithPosters() throws Exception {
        given(liveMovieService.findPopularMovies(3, "movie", "alone", "now_playing"))
            .willReturn(new MovieCatalogResponse(
                new MovieCatalogMeta(1, true, null),
                List.of(new MovieCatalogItem(
                    "LOTTE_CINEMA:123",
                    "Movie",
                    "Movie",
                    "15",
                    122,
                    "2026-04-01",
                    "now_playing",
                    new BigDecimal("28.500"),
                    1,
                    "https://cdn.example/movie.webp",
                    12000,
                    18,
                    List.of("LOTTE", "MEGA"),
                    List.of("genre:thriller")
                ))
            ));

        mockMvc.perform(
                get("/api/live/movies")
                    .queryParam("limit", "3")
                    .queryParam("query", "movie")
                    .queryParam("section", "alone")
                    .queryParam("releaseState", "now_playing")
                    .accept(MediaType.APPLICATION_JSON)
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.meta.resultCount").value(1))
            .andExpect(jsonPath("$.meta.databaseAvailable").value(true))
            .andExpect(jsonPath("$.movies[0].movie_key").value("LOTTE_CINEMA:123"))
            .andExpect(jsonPath("$.movies[0].title").value("Movie"))
            .andExpect(jsonPath("$.movies[0].release_state").value("now_playing"))
            .andExpect(jsonPath("$.movies[0].poster_url").value("https://cdn.example/movie.webp"))
            .andExpect(jsonPath("$.movies[0].providers[0]").value("LOTTE"));
    }

    @Test
    void schedulesReturnsGroupedTheaters() throws Exception {
        LiveMovieSearchCriteria criteria = sampleCriteria();
        given(liveMovieService.buildCriteria(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
            .willReturn(criteria);
        given(liveMovieService.findMovieSchedules(eq("LOTTE_CINEMA:123"), eq(criteria)))
            .willReturn(new MovieSchedulesResponse(
                new LiveMovieSearchMeta(
                    new BigDecimal("37.4979"),
                    new BigDecimal("127.0276"),
                    "2026-04-23",
                    "06:00",
                    "23:59",
                    new BigDecimal("8"),
                    1,
                    true,
                    false,
                    null
                ),
                new MovieSummary("LOTTE_CINEMA:123", "Movie", "15"),
                List.of(new TheaterScheduleGroup(
                    "LOTTE",
                    "LOTTE_CINEMA",
                    "lotte-gangnam",
                    "LOTTE Gangnam",
                    new BigDecimal("2.31"),
                    List.of(new ScheduleCard(
                        "19:40",
                        "22:01",
                        "2D",
                        48,
                        120,
                        "comfortable",
                        "https://booking.example/lotte/123"
                    ))
                ))
            ));

        mockMvc.perform(
                get("/api/live/movies/{movieKey}/schedules", "LOTTE_CINEMA:123")
                    .queryParam("lat", "37.4979")
                    .queryParam("lng", "127.0276")
                    .queryParam("date", "2026-04-23")
                    .accept(MediaType.APPLICATION_JSON)
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.movie.movie_key").value("LOTTE_CINEMA:123"))
            .andExpect(jsonPath("$.movie.movie_name").value("Movie"))
            .andExpect(jsonPath("$.theaters[0].theater_name").value("LOTTE Gangnam"))
            .andExpect(jsonPath("$.theaters[0].schedules[0].seat_state").value("comfortable"));

        verify(liveMovieService).findMovieSchedules("LOTTE_CINEMA:123", criteria);
    }

    @Test
    void schedulesReturnsInternalErrorShapeWhenServiceFails() throws Exception {
        LiveMovieSearchCriteria criteria = sampleCriteria();
        given(liveMovieService.buildCriteria(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
            .willReturn(criteria);
        given(liveMovieService.findMovieSchedules(eq("LOTTE_CINEMA:123"), eq(criteria)))
            .willThrow(new IllegalStateException("boom"));

        mockMvc.perform(
                get("/api/live/movies/{movieKey}/schedules", "LOTTE_CINEMA:123")
                    .queryParam("lat", "37.4979")
                    .queryParam("lng", "127.0276")
                    .accept(MediaType.APPLICATION_JSON)
            )
            .andExpect(status().isInternalServerError())
            .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
            .andExpect(jsonPath("$.path").value("/api/live/movies/LOTTE_CINEMA:123/schedules"))
            .andExpect(jsonPath("$.details[0]").value("IllegalStateException"));
    }

    private static LiveMovieScheduleItem sampleItem() {
        return new LiveMovieScheduleItem(
            "LOTTE_CINEMA:123",
            "Movie",
            "LOTTE",
            "LOTTE_CINEMA",
            "lotte-gangnam",
            "LOTTE Gangnam",
            "screen-1",
            "2D",
            "2D",
            List.of("RECLINER"),
            "15",
            "19:40",
            "22:01",
            "2026-04-23",
            120,
            48,
            48,
            new BigDecimal("0.400"),
            "comfortable",
            new BigDecimal("2.31"),
            "https://booking.example/lotte/123",
            "2026-04-23T20:00:00+09:00",
            "https://poster.example/lotte.jpg"
        );
    }

    private static LiveMovieSearchCriteria sampleCriteria() {
        return LiveMovieSearchCriteria.of(
            new BigDecimal("37.4979"),
            new BigDecimal("127.0276"),
            LocalDate.of(2026, 4, 23),
            LocalTime.of(6, 0),
            LocalTime.of(23, 59),
            new BigDecimal("8"),
            List.of("LOTTE"),
            List.of(),
            List.of(),
            SeatState.ALL,
            "",
            300,
            FIXED_CLOCK
        );
    }
}
