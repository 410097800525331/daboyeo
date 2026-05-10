package kr.daboyeo.backend.sync.nearby;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import kr.daboyeo.backend.config.CollectorSyncProperties;
import kr.daboyeo.backend.domain.LiveMovieSearchCriteria;
import kr.daboyeo.backend.domain.SeatState;
import kr.daboyeo.backend.service.TheaterMapService;
import kr.daboyeo.backend.service.TheaterMapService.TheaterSyncSource;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class NearbyTheaterTargetResolverTests {

    private static final Clock FIXED_CLOCK = Clock.fixed(Instant.parse("2026-04-30T00:00:00Z"), ZoneId.of("Asia/Seoul"));

    @Test
    void resolveCapsRefreshTargetsToConfiguredRadiusWhenSearchRadiusIsLarger() {
        CollectorSyncProperties properties = new CollectorSyncProperties();
        properties.getShowtimes().setNearbyRefreshMaxTheatersPerProvider(6);
        properties.getShowtimes().setNearbyRefreshRadiusKm(new BigDecimal("3"));
        TheaterMapService theaterMapService = Mockito.mock(TheaterMapService.class);
        given(theaterMapService.findAllSyncSources()).willReturn(List.of(
            new TheaterSyncSource("MEGABOX", "near", "Near", new BigDecimal("37.4979"), new BigDecimal("127.0276"), ""),
            new TheaterSyncSource("MEGABOX", "far", "Far", new BigDecimal("37.5339"), new BigDecimal("127.0276"), "")
        ));
        NearbyTheaterTargetResolver resolver = new NearbyTheaterTargetResolver(
            properties,
            theaterMapService
        );

        NearbyTheaterTargetResolver.Resolution resolution = resolver.resolve(sampleCriteria(new BigDecimal("8")));

        assertThat(resolution.megaboxEntries())
            .extracting(NearbyTheaterTargetResolver.TheaterMapEntry::externalTheaterId)
            .containsExactly("near");
    }

    @Test
    void resolveIncludesCgvEntriesWhenProviderFilterAllowsThem() {
        CollectorSyncProperties properties = new CollectorSyncProperties();
        properties.getShowtimes().setNearbyRefreshMaxTheatersPerProvider(6);
        properties.getShowtimes().setNearbyRefreshRadiusKm(new BigDecimal("3"));
        TheaterMapService theaterMapService = Mockito.mock(TheaterMapService.class);
        given(theaterMapService.findAllSyncSources()).willReturn(List.of(
            new TheaterSyncSource("CGV", "0056", "CGV Gangnam", new BigDecimal("37.4979"), new BigDecimal("127.0276"), ""),
            new TheaterSyncSource("LOTTE_CINEMA", "1003", "LOTTE Gangnam", new BigDecimal("37.4979"), new BigDecimal("127.0276"), "")
        ));
        NearbyTheaterTargetResolver resolver = new NearbyTheaterTargetResolver(
            properties,
            theaterMapService
        );

        NearbyTheaterTargetResolver.Resolution resolution = resolver.resolve(sampleCriteria(new BigDecimal("8"), List.of("CGV")));

        assertThat(resolution.cgvEntries())
            .extracting(NearbyTheaterTargetResolver.TheaterMapEntry::externalTheaterId)
            .containsExactly("0056");
        assertThat(resolution.lotteEntries()).isEmpty();
    }

    private static LiveMovieSearchCriteria sampleCriteria(BigDecimal radiusKm) {
        return sampleCriteria(radiusKm, List.of());
    }

    private static LiveMovieSearchCriteria sampleCriteria(BigDecimal radiusKm, List<String> providers) {
        return LiveMovieSearchCriteria.of(
            new BigDecimal("37.4979"),
            new BigDecimal("127.0276"),
            LocalDate.of(2026, 4, 30),
            LocalTime.of(6, 0),
            LocalTime.of(23, 59),
            radiusKm,
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
