package kr.daboyeo.backend.domain;

public enum SeatState {
    ALL,
    SPACIOUS,
    COMFORTABLE,
    CLOSING,
    GROUP,
    SOLD_OUT;

    public static SeatState fromQueryValue(String value) {
        if (value == null || value.isBlank()) {
            return ALL;
        }
        return SeatState.valueOf(value.trim().toUpperCase());
    }
}
