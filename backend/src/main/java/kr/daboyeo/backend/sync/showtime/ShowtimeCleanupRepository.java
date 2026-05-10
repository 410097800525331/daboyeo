package kr.daboyeo.backend.sync.showtime;

import java.time.LocalDate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class ShowtimeCleanupRepository {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public ShowtimeCleanupRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public CleanupCounts cleanupOutsideWindow(LocalDate windowStart, LocalDate windowEnd) {
        MapSqlParameterSource params = new MapSqlParameterSource()
            .addValue("windowStart", windowStart)
            .addValue("windowEnd", windowEnd);

        int deletedSeatSnapshotItems = jdbcTemplate.update(
            """
                DELETE ssi
                FROM seat_snapshot_items ssi
                JOIN seat_snapshots ss ON ss.id = ssi.seat_snapshot_id
                LEFT JOIN showtimes st ON st.id = ss.showtime_id
                WHERE st.id IS NULL
                   OR st.show_date < :windowStart
                   OR st.show_date > :windowEnd
                """,
            params
        );

        int deletedSeatSnapshots = jdbcTemplate.update(
            """
                DELETE ss
                FROM seat_snapshots ss
                LEFT JOIN showtimes st ON st.id = ss.showtime_id
                WHERE st.id IS NULL
                   OR st.show_date < :windowStart
                   OR st.show_date > :windowEnd
                """,
            params
        );

        int deletedShowtimes = jdbcTemplate.update(
            """
                DELETE FROM showtimes
                WHERE show_date < :windowStart
                   OR show_date > :windowEnd
                """,
            params
        );

        return new CleanupCounts(deletedShowtimes, deletedSeatSnapshots, deletedSeatSnapshotItems);
    }

    public record CleanupCounts(
        int deletedShowtimes,
        int deletedSeatSnapshots,
        int deletedSeatSnapshotItems
    ) {
    }
}
