package kr.daboyeo.backend.sync.seat;

import java.time.LocalDateTime;
import java.util.Map;
import kr.daboyeo.backend.sync.bridge.CollectorProvider;

public record SeatSnapshotTarget(
    long showtimeId,
    CollectorProvider provider,
    String externalShowtimeKey,
    LocalDateTime startsAt,
    Integer totalSeatCount,
    Integer remainingSeatCount,
    Map<String, Object> bookingKey
) {
}
