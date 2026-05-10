package kr.daboyeo.backend.sync.seat;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import kr.daboyeo.backend.sync.bridge.CollectorProvider;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class SeatSnapshotRepository {

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public SeatSnapshotRepository(NamedParameterJdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    public List<SeatSnapshotTarget> findUpcomingTargets(LocalDateTime from, LocalDateTime to, int limit) {
        String sql = """
            SELECT
              id,
              provider_code,
              external_showtime_key,
              starts_at,
              total_seat_count,
              remaining_seat_count,
              booking_key_json
            FROM showtimes
            WHERE starts_at BETWEEN :from AND :to
              AND booking_key_json IS NOT NULL
            ORDER BY starts_at ASC
            LIMIT :limit
            """;

        MapSqlParameterSource params = new MapSqlParameterSource()
            .addValue("from", from)
            .addValue("to", to)
            .addValue("limit", limit);

        return jdbcTemplate.query(sql, params, new SeatSnapshotTargetRowMapper(objectMapper));
    }

    private record SeatSnapshotTargetRowMapper(ObjectMapper objectMapper) implements RowMapper<SeatSnapshotTarget> {

        @Override
        public SeatSnapshotTarget mapRow(ResultSet rs, int rowNum) throws SQLException {
            String bookingKeyJson = rs.getString("booking_key_json");
            Map<String, Object> bookingKey;
            try {
                bookingKey = bookingKeyJson == null || bookingKeyJson.isBlank()
                    ? Map.of()
                    : objectMapper.readValue(bookingKeyJson, MAP_TYPE);
            } catch (Exception exception) {
                throw new SQLException("Failed to parse booking_key_json for showtime " + rs.getLong("id"), exception);
            }

            return new SeatSnapshotTarget(
                rs.getLong("id"),
                CollectorProvider.fromValue(rs.getString("provider_code")),
                rs.getString("external_showtime_key"),
                rs.getObject("starts_at", LocalDateTime.class),
                getNullableInt(rs, "total_seat_count"),
                getNullableInt(rs, "remaining_seat_count"),
                bookingKey
            );
        }

        private static Integer getNullableInt(ResultSet rs, String column) throws SQLException {
            int value = rs.getInt(column);
            return rs.wasNull() ? null : value;
        }
    }
}
