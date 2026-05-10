package kr.daboyeo.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import kr.daboyeo.backend.domain.LiveMovieSearchCriteria;
import kr.daboyeo.backend.domain.SeatState;
import kr.daboyeo.backend.repository.LiveMovieRepository;
import kr.daboyeo.backend.sync.nearby.NearbyShowtimeRefreshService;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;

class LiveMovieServiceDemoFallbackTests {

    private static final Clock FIXED_CLOCK = Clock.fixed(Instant.parse("2026-04-23T00:00:00Z"), ZoneId.of("Asia/Seoul"));

    @Test
    void nearbyReturnsDatabaseFailureInsteadOfDemoDataWhenDatabaseLookupFails() {
        LiveMovieRepository repository = mock(LiveMovieRepository.class);
        LiveMovieSearchCriteria criteria = sampleCriteria(List.of(), List.of(), List.of(), SeatState.ALL, "");
        when(repository.findNearbySchedules(criteria)).thenThrow(new DataAccessResourceFailureException("db down"));
        LiveMovieService service = new LiveMovieService(
            repository,
            new SeatStateCalculator(),
            new LiveMovieDemoDataService(),
            mock(NearbyShowtimeRefreshService.class),
            true,
            Duration.ZERO,
            FIXED_CLOCK
        );

        LiveMovieService.LiveMovieResponse response = service.findNearby(criteria);

        assertThat(response.search().databaseAvailable()).isFalse();
        assertThat(response.search().warning()).isEqualTo("database lookup failed.");
        assertThat(response.results()).isEmpty();
    }

    @Test
    void nearbyReturnsPendingWarningWhenRefreshTimesOutOnEmptyLookup() {
        LiveMovieRepository repository = mock(LiveMovieRepository.class);
        NearbyShowtimeRefreshService refreshService = mock(NearbyShowtimeRefreshService.class);
        LiveMovieSearchCriteria criteria = sampleCriteria(List.of(), List.of(), List.of(), SeatState.COMFORTABLE, "");
        when(repository.findNearbySchedules(criteria)).thenReturn(List.of());
        when(refreshService.requestRefreshAndAwait(criteria, Duration.ofMillis(2500)))
            .thenReturn(NearbyShowtimeRefreshService.RefreshWaitOutcome.TIMED_OUT);

        LiveMovieService service = new LiveMovieService(
            repository,
            new SeatStateCalculator(),
            new LiveMovieDemoDataService(),
            refreshService,
            true,
            Duration.ofMillis(2500),
            FIXED_CLOCK
        );

        LiveMovieService.LiveMovieResponse response = service.findNearby(criteria);

        assertThat(response.search().pendingRefresh()).isTrue();
        assertThat(response.search().warning()).contains("still being collected");
        assertThat(response.results()).isEmpty();
    }

    @Test
    void schedulesReturnUnavailableWarningInsteadOfDemoDataWhenDatabaseLookupFails() {
        LiveMovieRepository repository = mock(LiveMovieRepository.class);
        LiveMovieSearchCriteria criteria = sampleCriteria(List.of(), List.of(), List.of(), SeatState.ALL, "");
        when(repository.findMovieSchedules("CGV:demo_dune", criteria)).thenThrow(new DataAccessResourceFailureException("db down"));
        LiveMovieService service = new LiveMovieService(
            repository,
            new SeatStateCalculator(),
            new LiveMovieDemoDataService(),
            mock(NearbyShowtimeRefreshService.class),
            true,
            Duration.ZERO,
            FIXED_CLOCK
        );

        LiveMovieService.MovieSchedulesResponse response = service.findMovieSchedules("CGV:demo_dune", criteria);

        assertThat(response.search().databaseAvailable()).isFalse();
        assertThat(response.search().warning()).isEqualTo("database lookup failed.");
        assertThat(response.movie().movie_name()).isNull();
        assertThat(response.theaters()).isEmpty();
    }

    private static LiveMovieSearchCriteria sampleCriteria(
        List<String> providers,
        List<String> formats,
        List<String> seatTypes,
        SeatState seatState,
        String query
    ) {
        return LiveMovieSearchCriteria.of(
            new BigDecimal("37.4979"),
            new BigDecimal("127.0276"),
            LocalDate.of(2026, 4, 23),
            LocalTime.of(6, 0),
            LocalTime.of(23, 59),
            new BigDecimal("8"),
            providers,
            formats,
            seatTypes,
            seatState,
            query,
            300,
            FIXED_CLOCK
        );
    }
}
