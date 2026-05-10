package kr.daboyeo.backend.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record LiveMovieSchedule(
    String movieKey,
    String movieName,
    String provider,
    String providerCode,
    String theaterId,
    String theaterName,
    String screenId,
    String screenName,
    String formatName,
    List<String> seatTypeTags,
    String ageRating,
    String startTime,
    String endTime,
    LocalDate showDate,
    Integer totalSeatCount,
    Integer availableSeatCount,
    Integer remainingSeatCount,
    BigDecimal seatRatio,
    String seatState,
    BigDecimal distanceKm,
    String bookingUrl,
    LocalDateTime updatedAt
) {
}
