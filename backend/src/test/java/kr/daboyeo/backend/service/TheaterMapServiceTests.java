package kr.daboyeo.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import kr.daboyeo.backend.repository.TheaterMapRepository;
import org.junit.jupiter.api.Test;

class TheaterMapServiceTests {

    @Test
    void findAllSyncSourcesIncludesFallbackRowsWhenDatabaseIsMissingProviderEntries() {
        TheaterMapRepository repository = mock(TheaterMapRepository.class);
        when(repository.findAllWithCoordinates()).thenReturn(List.of(
            new TheaterMapRepository.TheaterMapRow(
                "LOTTE_CINEMA",
                "1003",
                "LOTTE Gangnam",
                new BigDecimal("37.4980"),
                new BigDecimal("127.0270"),
                "Seoul"
            )
        ));

        TheaterMapService service = new TheaterMapService(
            repository,
            List.of(
                new TheaterMapService.TheaterMapReference(
                    "CGV",
                    "0056",
                    "CGV Gangnam",
                    new BigDecimal("37.5016573944824"),
                    new BigDecimal("127.026391177132"),
                    "Seoul"
                )
            )
        );

        assertThat(service.findAllSyncSources())
            .extracting(TheaterMapService.TheaterSyncSource::providerCode, TheaterMapService.TheaterSyncSource::externalTheaterId)
            .contains(
                org.assertj.core.groups.Tuple.tuple("CGV", "0056"),
                org.assertj.core.groups.Tuple.tuple("LOTTE_CINEMA", "1003")
            );
    }

    @Test
    void findAllSyncSourcesPrefersDatabaseRowsOverFallbackForSameTheater() {
        TheaterMapRepository repository = mock(TheaterMapRepository.class);
        when(repository.findAllWithCoordinates()).thenReturn(List.of(
            new TheaterMapRepository.TheaterMapRow(
                "CGV",
                "0056",
                "CGV Gangnam DB",
                new BigDecimal("37.6000"),
                new BigDecimal("127.1000"),
                "DB Address"
            )
        ));

        TheaterMapService service = new TheaterMapService(
            repository,
            List.of(
                new TheaterMapService.TheaterMapReference(
                    "CGV",
                    "0056",
                    "CGV Gangnam Fallback",
                    new BigDecimal("37.5016573944824"),
                    new BigDecimal("127.026391177132"),
                    "Fallback Address"
                )
            )
        );

        assertThat(service.findAllSyncSources())
            .singleElement()
            .satisfies(item -> {
                assertThat(item.providerCode()).isEqualTo("CGV");
                assertThat(item.externalTheaterId()).isEqualTo("0056");
                assertThat(item.name()).isEqualTo("CGV Gangnam DB");
                assertThat(item.latitude()).isEqualByComparingTo("37.6000");
                assertThat(item.longitude()).isEqualByComparingTo("127.1000");
            });
    }
}
