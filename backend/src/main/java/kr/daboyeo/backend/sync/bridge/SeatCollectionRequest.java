package kr.daboyeo.backend.sync.bridge;

import java.util.Map;

public record SeatCollectionRequest(
    CollectorProvider provider,
    String externalShowtimeKey,
    Map<String, Object> bookingKey
) {
}
