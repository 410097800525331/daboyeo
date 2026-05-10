package kr.daboyeo.backend.service;

import static org.assertj.core.api.Assertions.assertThat;

import kr.daboyeo.backend.domain.SeatState;
import org.junit.jupiter.api.Test;

class SeatStateCalculatorTests {

    private final SeatStateCalculator calculator = new SeatStateCalculator();

    @Test
    void resolvesSeatStatesUsingPlannedThresholds() {
        assertThat(calculator.resolve(100, 60)).isEqualTo(SeatState.SPACIOUS);
        assertThat(calculator.resolve(100, 35)).isEqualTo(SeatState.COMFORTABLE);
        assertThat(calculator.resolve(100, 8)).isEqualTo(SeatState.CLOSING);
        assertThat(calculator.resolve(100, 0)).isEqualTo(SeatState.SOLD_OUT);
    }

    @Test
    void comfortableFilterAcceptsBroaderGoodAvailabilityStates() {
        assertThat(calculator.matchesFilter(SeatState.COMFORTABLE, 100, 35)).isTrue();
        assertThat(calculator.matchesFilter(SeatState.COMFORTABLE, 100, 60)).isTrue();
        assertThat(calculator.matchesFilter(SeatState.COMFORTABLE, 100, 8)).isFalse();
    }
}
