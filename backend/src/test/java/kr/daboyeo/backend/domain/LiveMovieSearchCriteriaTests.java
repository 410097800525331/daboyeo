package kr.daboyeo.backend.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.Test;

class LiveMovieSearchCriteriaTests {

    private final Clock fixedClock = Clock.fixed(Instant.parse("2026-04-21T00:00:00Z"), ZoneId.of("Asia/Seoul"));

    @Test
    void fillsDefaultsWhenOptionalValuesAreMissing() {
        LiveMovieSearchCriteria criteria = LiveMovieSearchCriteria.of(
            BigDecimal.valueOf(37.5d),
            BigDecimal.valueOf(127.0d),
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            fixedClock
        );

        assertThat(criteria.date()).isEqualTo(LocalDate.of(2026, 4, 21));
        assertThat(criteria.timeStart()).isEqualTo(LocalTime.of(6, 0));
        assertThat(criteria.timeEnd()).isEqualTo(LocalTime.of(23, 59));
        assertThat(criteria.radiusKm()).isEqualByComparingTo("8");
        assertThat(criteria.limit()).isEqualTo(300);
    }

    @Test
    void rejectsReversedTimeRange() {
        assertThatThrownBy(() -> LiveMovieSearchCriteria.of(
            BigDecimal.valueOf(37.5d),
            BigDecimal.valueOf(127.0d),
            LocalDate.of(2026, 4, 21),
            LocalTime.of(21, 0),
            LocalTime.of(18, 0),
            BigDecimal.valueOf(8),
            List.of(),
            List.of(),
            List.of(),
            SeatState.ALL,
            "",
            100,
            fixedClock
        )).isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("timeStart");
    }
}
