package kr.daboyeo.backend.sync.bridge;

import java.time.LocalDate;

public record ShowtimeCollectionRequest(
    CollectorProvider provider,
    LocalDate playDate,
    String siteNo,
    String movieNo,
    String cinemaSelector,
    String representationMovieCode,
    String areaCode
) {
}
