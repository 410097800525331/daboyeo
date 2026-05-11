package kr.daboyeo.backend.ingest;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class TheaterLocationEnricherTests {

    @Test
    void enrichFillsMissingCoordinatesFromTheaterMapCode() {
        TheaterLocationEnricher enricher = new TheaterLocationEnricher(List.of(
            new TheaterLocationEnricher.TheaterLocation(
                "MEGABOX",
                "1372",
                "MEGABOX Gangnam",
                new BigDecimal("37.4979560555237"),
                new BigDecimal("127.026417015264"),
                "Seoul Seocho"
            )
        ));

        CollectorBundleIngestCommand.TheaterRow theater = theater("MEGABOX", "1372", "Gangnam");

        CollectorBundleIngestCommand.TheaterRow enriched = enricher.enrich(bundle(theater)).theaters().getFirst();

        assertThat(enriched.latitude()).isEqualByComparingTo("37.4979560555237");
        assertThat(enriched.longitude()).isEqualByComparingTo("127.026417015264");
        assertThat(enriched.address()).isEqualTo("Seoul Seocho");
    }

    @Test
    void enrichCanMatchByNormalizedNameWhenProviderCodeIsMissingUsefulCode() {
        TheaterLocationEnricher enricher = new TheaterLocationEnricher(List.of(
            new TheaterLocationEnricher.TheaterLocation(
                "LOTTE_CINEMA",
                "9999",
                "Gangnam",
                new BigDecimal("37.524534257049396"),
                new BigDecimal("127.02867534167731"),
                "Seoul Gangnam"
            )
        ));

        CollectorBundleIngestCommand.TheaterRow theater = theater("LOTTE_CINEMA", "UNKNOWN", "Gangnam");

        CollectorBundleIngestCommand.TheaterRow enriched = enricher.enrich(bundle(theater)).theaters().getFirst();

        assertThat(enriched.latitude()).isEqualByComparingTo("37.524534257049396");
        assertThat(enriched.longitude()).isEqualByComparingTo("127.02867534167731");
    }

    @Test
    void enrichKeepsExistingCoordinatesFromCollector() {
        TheaterLocationEnricher enricher = new TheaterLocationEnricher(List.of(
            new TheaterLocationEnricher.TheaterLocation(
                "LOTTE_CINEMA",
                "1023",
                "LOTTE CINEMA Dogok",
                new BigDecimal("37.4874745085255"),
                new BigDecimal("127.047105267661"),
                "Seoul Gangnam"
            )
        ));

        CollectorBundleIngestCommand.TheaterRow theater = new CollectorBundleIngestCommand.TheaterRow(
            "LOTTE_CINEMA",
            "1023",
            "Dogok",
            "",
            "",
            "",
            new BigDecimal("37.4000"),
            new BigDecimal("127.0000"),
            Map.of()
        );

        CollectorBundleIngestCommand.TheaterRow enriched = enricher.enrich(bundle(theater)).theaters().getFirst();

        assertThat(enriched.latitude()).isEqualByComparingTo("37.4000");
        assertThat(enriched.longitude()).isEqualByComparingTo("127.0000");
    }

    private static CollectorBundleIngestCommand.NormalizedBundle bundle(CollectorBundleIngestCommand.TheaterRow theater) {
        return new CollectorBundleIngestCommand.NormalizedBundle(List.of(), List.of(theater), List.of(), List.of());
    }

    private static CollectorBundleIngestCommand.TheaterRow theater(String providerCode, String externalTheaterId, String name) {
        return new CollectorBundleIngestCommand.TheaterRow(
            providerCode,
            externalTheaterId,
            name,
            "",
            "",
            "",
            null,
            null,
            Map.of()
        );
    }
}
