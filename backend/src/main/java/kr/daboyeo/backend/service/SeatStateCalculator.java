package kr.daboyeo.backend.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import kr.daboyeo.backend.domain.SeatState;
import org.springframework.stereotype.Component;

@Component
public class SeatStateCalculator {

    public BigDecimal calculateRatio(Integer totalSeatCount, Integer availableSeatCount) {
        if (totalSeatCount == null || availableSeatCount == null || totalSeatCount <= 0) {
            return BigDecimal.ZERO;
        }
        return BigDecimal.valueOf((double) availableSeatCount / totalSeatCount).setScale(3, RoundingMode.HALF_UP);
    }

    public SeatState resolve(Integer totalSeatCount, Integer availableSeatCount) {
        if (availableSeatCount == null || availableSeatCount <= 0) {
            return SeatState.SOLD_OUT;
        }

        BigDecimal ratio = calculateRatio(totalSeatCount, availableSeatCount);
        if (ratio.compareTo(BigDecimal.valueOf(0.5d)) >= 0) {
            return SeatState.SPACIOUS;
        }
        if (ratio.compareTo(BigDecimal.valueOf(0.3d)) >= 0) {
            return SeatState.COMFORTABLE;
        }
        if (ratio.compareTo(BigDecimal.valueOf(0.1d)) < 0) {
            return SeatState.CLOSING;
        }
        return SeatState.COMFORTABLE;
    }

    public boolean matchesFilter(SeatState filter, Integer totalSeatCount, Integer availableSeatCount) {
        if (filter == null || filter == SeatState.ALL) {
            return true;
        }
        SeatState resolved = resolve(totalSeatCount, availableSeatCount);
        return switch (filter) {
            case SPACIOUS -> resolved == SeatState.SPACIOUS;
            case COMFORTABLE -> resolved == SeatState.COMFORTABLE || resolved == SeatState.SPACIOUS;
            case CLOSING -> resolved == SeatState.CLOSING;
            case GROUP -> availableSeatCount != null && availableSeatCount >= 20;
            case SOLD_OUT -> resolved == SeatState.SOLD_OUT;
            case ALL -> true;
        };
    }
}
