package kr.daboyeo.backend.sync.seat;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import kr.daboyeo.backend.sync.bridge.CollectorProvider;
import org.junit.jupiter.api.Test;

class SeatSnapshotStatusNormalizerTests {

    private final SeatSnapshotStatusNormalizer normalizer = new SeatSnapshotStatusNormalizer();

    @Test
    void normalizesCgvStatuses() {
        assertThat(normalizer.normalize(CollectorProvider.CGV, Map.of("seat_sale_yn", "Y"))).isEqualTo("available");
        assertThat(normalizer.normalize(CollectorProvider.CGV, Map.of("seat_status_name", "예매완료"))).isEqualTo("sold");
        assertThat(normalizer.normalize(CollectorProvider.CGV, Map.of("seat_status_name", "판매불가"))).isEqualTo("unavailable");
    }

    @Test
    void normalizesLotteStatuses() {
        assertThat(normalizer.normalize(CollectorProvider.LOTTE_CINEMA, Map.of("seat_status_code", "SALE_END"))).isEqualTo("sold");
        assertThat(normalizer.normalize(CollectorProvider.LOTTE_CINEMA, Map.of("logical_block_code", "BLOCK"))).isEqualTo("unavailable");
        assertThat(normalizer.normalize(CollectorProvider.LOTTE_CINEMA, Map.of("seat_status_code", "OK"))).isEqualTo("available");
    }

    @Test
    void normalizesMegaboxStatuses() {
        assertThat(normalizer.normalize(CollectorProvider.MEGABOX, Map.of("seat_status_code", "BOOKED"))).isEqualTo("sold");
        assertThat(normalizer.normalize(CollectorProvider.MEGABOX, Map.of("row_status_code", "BLOCK"))).isEqualTo("unavailable");
        assertThat(normalizer.normalize(CollectorProvider.MEGABOX, Map.of("seat_status_code", "NORMAL"))).isEqualTo("available");
    }
}
