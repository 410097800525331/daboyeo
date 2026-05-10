package kr.daboyeo.backend.domain;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

public record LiveMovieSearchCriteria(
    BigDecimal lat,
    BigDecimal lng,
    LocalDate date,
    LocalTime timeStart,
    LocalTime timeEnd,
    BigDecimal radiusKm,
    List<String> providers,
    List<String> formats,
    List<String> seatTypes,
    SeatState seatState,
    String query,
    int limit
) {

    private static final BigDecimal DEFAULT_RADIUS_KM = BigDecimal.valueOf(8);
    private static final int DEFAULT_LIMIT = 300;
    private static final int MAX_LIMIT = 500;

    public static LiveMovieSearchCriteria of(
        BigDecimal lat,
        BigDecimal lng,
        LocalDate date,
        LocalTime timeStart,
        LocalTime timeEnd,
        BigDecimal radiusKm,
        List<String> providers,
        List<String> formats,
        List<String> seatTypes,
        SeatState seatState,
        String query,
        Integer limit,
        Clock clock
    ) {
        if (lat == null || lng == null) {
            throw new IllegalArgumentException("lat, lng는 필수야.");
        }
        if (lat.doubleValue() < -90 || lat.doubleValue() > 90) {
            throw new IllegalArgumentException("lat 범위를 확인해.");
        }
        if (lng.doubleValue() < -180 || lng.doubleValue() > 180) {
            throw new IllegalArgumentException("lng 범위를 확인해.");
        }

        LocalDate resolvedDate = date == null ? LocalDate.now(clock) : date;
        LocalTime resolvedTimeStart = timeStart == null ? LocalTime.of(6, 0) : timeStart;
        LocalTime resolvedTimeEnd = timeEnd == null ? LocalTime.of(23, 59) : timeEnd;

        BigDecimal resolvedRadiusKm = radiusKm == null ? DEFAULT_RADIUS_KM : radiusKm;
        if (resolvedRadiusKm.signum() <= 0 || resolvedRadiusKm.doubleValue() > 50) {
            throw new IllegalArgumentException("radiusKm는 0보다 크고 50 이하여야 해.");
        }

        int resolvedLimit = limit == null ? DEFAULT_LIMIT : Math.min(limit, MAX_LIMIT);
        if (resolvedLimit <= 0) {
            throw new IllegalArgumentException("limit는 1 이상이어야 해.");
        }

        return new LiveMovieSearchCriteria(
            lat,
            lng,
            resolvedDate,
            resolvedTimeStart,
            resolvedTimeEnd,
            resolvedRadiusKm,
            normalizeList(providers),
            normalizeList(formats),
            normalizeList(seatTypes),
            seatState == null ? SeatState.ALL : seatState,
            query == null ? "" : query.trim(),
            resolvedLimit
        );
    }

    public boolean crossesMidnight() {
        return timeEnd.isBefore(timeStart);
    }

    public boolean matchesTime(LocalTime value) {
        if (value == null) {
            return false;
        }
        if (crossesMidnight()) {
            return !value.isBefore(timeStart) || !value.isAfter(timeEnd);
        }
        return !value.isBefore(timeStart) && !value.isAfter(timeEnd);
    }

    private static List<String> normalizeList(List<String> values) {
        if (values == null) {
            return List.of();
        }
        return values.stream()
            .filter(value -> value != null && !value.isBlank())
            .map(String::trim)
            .toList();
    }
}
