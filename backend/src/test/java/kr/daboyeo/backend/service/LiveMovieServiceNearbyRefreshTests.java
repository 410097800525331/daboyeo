package kr.daboyeo.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Locale;
import java.util.List;
import kr.daboyeo.backend.domain.LiveMovieSchedule;
import kr.daboyeo.backend.domain.LiveMovieSearchCriteria;
import kr.daboyeo.backend.domain.SeatState;
import kr.daboyeo.backend.repository.LiveMovieRepository;
import kr.daboyeo.backend.sync.nearby.NearbyShowtimeRefreshService;
import org.junit.jupiter.api.Test;

class LiveMovieServiceNearbyRefreshTests {

    private static final Clock FIXED_CLOCK = Clock.fixed(Instant.parse("2026-04-30T00:00:00Z"), ZoneId.of("Asia/Seoul"));

    @Test
    void nearbySearchTriggersBackgroundRefreshWhenResultsAlreadyExist() {
        LiveMovieRepository repository = mock(LiveMovieRepository.class);
        NearbyShowtimeRefreshService refreshService = mock(NearbyShowtimeRefreshService.class);
        LiveMovieSearchCriteria criteria = sampleCriteria(List.of("LOTTE"));
        when(repository.findNearbySchedules(criteria)).thenReturn(List.of(sampleSchedule()));

        LiveMovieService service = new LiveMovieService(
            repository,
            new SeatStateCalculator(),
            new LiveMovieDemoDataService(),
            refreshService,
            false,
            Duration.ZERO,
            FIXED_CLOCK
        );

        LiveMovieService.LiveMovieResponse response = service.findNearby(criteria);

        verify(refreshService, times(1)).requestRefresh(criteria);
        assertThat(response.results()).hasSize(1);
        assertThat(response.search().databaseAvailable()).isTrue();
    }

    @Test
    void nearbySearchDoesNotTriggerRefreshWhenPublicRefreshIsDisabled() {
        LiveMovieRepository repository = mock(LiveMovieRepository.class);
        NearbyShowtimeRefreshService refreshService = mock(NearbyShowtimeRefreshService.class);
        LiveMovieSearchCriteria criteria = sampleCriteria(List.of("LOTTE"));
        when(repository.findNearbySchedules(criteria)).thenReturn(List.of());

        LiveMovieService service = new LiveMovieService(
            repository,
            new SeatStateCalculator(),
            new LiveMovieDemoDataService(),
            refreshService,
            false,
            false,
            Duration.ofMillis(2500),
            FIXED_CLOCK
        );

        LiveMovieService.LiveMovieResponse response = service.findNearby(criteria);

        verifyNoInteractions(refreshService);
        assertThat(response.results()).isEmpty();
        assertThat(response.search().pendingRefresh()).isFalse();
    }

    @Test
    void nearbySearchKeepsOnlyNearestTheaterForEachProvider() {
        LiveMovieRepository repository = mock(LiveMovieRepository.class);
        NearbyShowtimeRefreshService refreshService = mock(NearbyShowtimeRefreshService.class);
        LiveMovieSearchCriteria criteria = sampleCriteria(List.of("LOTTE", "MEGA"));
        when(repository.findNearbySchedules(criteria)).thenReturn(List.of(
            sampleSchedule("LOTTE_CINEMA", "LOTTE", "lotte-gangnam", "LOTTE Gangnam", "08:20", "1.23"),
            sampleSchedule("LOTTE_CINEMA", "LOTTE", "lotte-gangnam", "LOTTE Gangnam", "10:40", "1.23"),
            sampleSchedule("LOTTE_CINEMA", "LOTTE", "lotte-world", "LOTTE World", "11:15", "2.10"),
            sampleSchedule("MEGABOX", "MEGA", "mega-coex", "MEGA COEX", "09:10", "1.80"),
            sampleSchedule("MEGABOX", "MEGA", "mega-coex", "MEGA COEX", "12:20", "1.80"),
            sampleSchedule("MEGABOX", "MEGA", "mega-sinsa", "MEGA Sinsa", "13:50", "2.70")
        ));

        LiveMovieService service = new LiveMovieService(
            repository,
            new SeatStateCalculator(),
            new LiveMovieDemoDataService(),
            refreshService,
            false,
            Duration.ZERO,
            FIXED_CLOCK
        );

        LiveMovieService.LiveMovieResponse response = service.findNearby(criteria);

        assertThat(response.results()).hasSize(4);
        assertThat(response.results())
            .extracting(LiveMovieService.LiveMovieScheduleItem::theater_id)
            .containsExactly("lotte-gangnam", "lotte-gangnam", "mega-coex", "mega-coex");
    }

    @Test
    void nearbySearchWaitsForRefreshAndReturnsRealDataWhenSecondLookupSucceeds() {
        LiveMovieRepository repository = mock(LiveMovieRepository.class);
        NearbyShowtimeRefreshService refreshService = mock(NearbyShowtimeRefreshService.class);
        LiveMovieSearchCriteria criteria = sampleCriteria(List.of("LOTTE"));
        when(repository.findNearbySchedules(criteria))
            .thenReturn(List.of())
            .thenReturn(List.of(sampleSchedule()));
        when(refreshService.requestRefreshAndAwait(criteria, Duration.ofMillis(2500)))
            .thenReturn(NearbyShowtimeRefreshService.RefreshWaitOutcome.COMPLETED);

        LiveMovieService service = new LiveMovieService(
            repository,
            new SeatStateCalculator(),
            new LiveMovieDemoDataService(),
            refreshService,
            false,
            Duration.ofMillis(2500),
            FIXED_CLOCK
        );

        LiveMovieService.LiveMovieResponse response = service.findNearby(criteria);

        verify(refreshService, times(1)).requestRefreshAndAwait(criteria, Duration.ofMillis(2500));
        assertThat(response.results()).hasSize(1);
        assertThat(response.search().pendingRefresh()).isFalse();
        assertThat(response.search().warning()).isNull();
    }

    @Test
    void nearbySearchIgnoresRefreshFailuresAndReturnsNonDemoWarning() {
        LiveMovieRepository repository = mock(LiveMovieRepository.class);
        NearbyShowtimeRefreshService refreshService = mock(NearbyShowtimeRefreshService.class);
        LiveMovieSearchCriteria criteria = sampleCriteria(List.of("LOTTE"));
        when(repository.findNearbySchedules(criteria)).thenReturn(List.of());
        doThrow(new IllegalStateException("refresh failed")).when(refreshService).requestRefreshAndAwait(criteria, Duration.ofMillis(2500));

        LiveMovieService service = new LiveMovieService(
            repository,
            new SeatStateCalculator(),
            new LiveMovieDemoDataService(),
            refreshService,
            false,
            Duration.ofMillis(2500),
            FIXED_CLOCK
        );

        LiveMovieService.LiveMovieResponse response = service.findNearby(criteria);

        assertThat(response.results()).isEmpty();
        assertThat(response.search().warning()).contains("refresh failed");
        assertThat(response.search().databaseAvailable()).isTrue();
    }

    @Test
    void nearbySearchKeepsPartialResultsPendingWhenMegaboxIsStillMissing() {
        LiveMovieRepository repository = mock(LiveMovieRepository.class);
        NearbyShowtimeRefreshService refreshService = mock(NearbyShowtimeRefreshService.class);
        LiveMovieSearchCriteria criteria = sampleCriteria(List.of());
        when(repository.findNearbySchedules(criteria))
            .thenReturn(List.of(sampleSchedule()))
            .thenReturn(List.of(sampleSchedule()));
        when(refreshService.requestRefreshAndAwait(criteria, Duration.ofMillis(2500)))
            .thenReturn(NearbyShowtimeRefreshService.RefreshWaitOutcome.TIMED_OUT);

        LiveMovieService service = new LiveMovieService(
            repository,
            new SeatStateCalculator(),
            new LiveMovieDemoDataService(),
            refreshService,
            false,
            Duration.ofMillis(2500),
            FIXED_CLOCK
        );

        LiveMovieService.LiveMovieResponse response = service.findNearby(criteria);

        assertThat(response.results()).hasSize(1);
        assertThat(response.search().pendingRefresh()).isTrue();
        assertThat(response.search().warning()).contains("MEGA");
    }

    @Test
    void nearbySearchTreatsCgvAsRefreshBackedWhenExplicitlyRequested() {
        LiveMovieRepository repository = mock(LiveMovieRepository.class);
        NearbyShowtimeRefreshService refreshService = mock(NearbyShowtimeRefreshService.class);
        LiveMovieSearchCriteria criteria = sampleCriteria(List.of("CGV"));
        when(repository.findNearbySchedules(criteria)).thenReturn(List.of(sampleCgvSchedule()));

        LiveMovieService service = new LiveMovieService(
            repository,
            new SeatStateCalculator(),
            new LiveMovieDemoDataService(),
            refreshService,
            false,
            Duration.ZERO,
            FIXED_CLOCK
        );

        LiveMovieService.LiveMovieResponse response = service.findNearby(criteria);

        verify(refreshService, times(1)).requestRefresh(criteria);
        assertThat(response.results()).hasSize(1);
        assertThat(response.search().databaseAvailable()).isTrue();
    }

    private static LiveMovieSchedule sampleSchedule() {
        return sampleSchedule("LOTTE_CINEMA", "LOTTE", "lotte-gangnam", "LOTTE Gangnam", "08:20", "1.23");
    }

    private static LiveMovieSchedule sampleSchedule(
        String providerCode,
        String provider,
        String theaterId,
        String theaterName,
        String startTime,
        String distanceKm
    ) {
        return new LiveMovieSchedule(
            providerCode + ":123",
            "Movie",
            provider,
            providerCode,
            theaterId,
            theaterName,
            "screen-1",
            "Recliner",
            "Recliner",
            List.of("RECLINER"),
            "12",
            startTime,
            "10:30",
            LocalDate.of(2026, 4, 30),
            100,
            60,
            60,
            new BigDecimal("0.600"),
            "",
            new BigDecimal(distanceKm),
            "https://booking.example/" + provider.toLowerCase(Locale.ROOT) + "/123",
            null
        );
    }

    private static LiveMovieSchedule sampleCgvSchedule() {
        return new LiveMovieSchedule(
            "CGV:30001067",
            "Movie",
            "CGV",
            "CGV",
            "0056",
            "CGV Gangnam",
            "001",
            "1관",
            "2D",
            List.of(),
            "ALL",
            "08:20",
            "10:30",
            LocalDate.of(2026, 4, 30),
            100,
            60,
            60,
            new BigDecimal("0.600"),
            "",
            new BigDecimal("1.23"),
            "https://booking.example/cgv/123",
            null
        );
    }

    private static LiveMovieSearchCriteria sampleCriteria(List<String> providers) {
        return LiveMovieSearchCriteria.of(
            new BigDecimal("37.4979"),
            new BigDecimal("127.0276"),
            LocalDate.of(2026, 4, 30),
            LocalTime.of(6, 0),
            LocalTime.of(23, 59),
            new BigDecimal("8"),
            providers,
            List.of(),
            List.of(),
            SeatState.ALL,
            "",
            300,
            FIXED_CLOCK
        );
    }
}
