package kr.daboyeo.backend.sync.seat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.Map;
import javax.sql.DataSource;
import kr.daboyeo.backend.sync.bridge.CollectorProvider;
import kr.daboyeo.backend.sync.bridge.SeatCollectionResult;
import org.springframework.stereotype.Service;

@Service
public class SeatSnapshotPersistenceService {

    private final DataSource dataSource;
    private final ObjectMapper objectMapper;
    private final SeatSnapshotStatusNormalizer statusNormalizer;

    public SeatSnapshotPersistenceService(DataSource dataSource, ObjectMapper objectMapper, SeatSnapshotStatusNormalizer statusNormalizer) {
        this.dataSource = dataSource;
        this.objectMapper = objectMapper;
        this.statusNormalizer = statusNormalizer;
    }

    public void persist(SeatSnapshotTarget target, SeatCollectionResult result) {
        try (Connection connection = dataSource.getConnection()) {
            boolean originalAutoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try {
                long snapshotId = insertSnapshot(connection, target, result);
                insertSnapshotItems(connection, snapshotId, target.provider(), result.seats());
                connection.commit();
            } catch (Exception exception) {
                connection.rollback();
                throw exception;
            } finally {
                connection.setAutoCommit(originalAutoCommit);
            }
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to persist seat snapshot for " + target.externalShowtimeKey(), exception);
        }
    }

    private long insertSnapshot(Connection connection, SeatSnapshotTarget target, SeatCollectionResult result) throws Exception {
        String sql = """
            INSERT INTO seat_snapshots (
              showtime_id, provider_code, external_showtime_key, total_seat_count, remaining_seat_count,
              sold_seat_count, unavailable_seat_count, special_seat_count, raw_summary_json
            )
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, CAST(? AS JSON))
            """;

        int unavailableCount = 0;
        int soldCountFromSeats = 0;
        for (Map<String, Object> seat : result.seats()) {
            String normalized = statusNormalizer.normalize(target.provider(), seat);
            if ("unavailable".equals(normalized)) {
                unavailableCount++;
            } else if ("sold".equals(normalized)) {
                soldCountFromSeats++;
            }
        }

        Integer totalSeatCount = firstInteger(result.summary(), "total_seat_count", "seat_count", "booking_seat_count");
        if (totalSeatCount == null) {
            totalSeatCount = target.totalSeatCount();
        }
        Integer remainingSeatCount = firstInteger(result.summary(), "remaining_seat_count");
        if (remainingSeatCount == null) {
            remainingSeatCount = target.remainingSeatCount();
        }
        Integer soldSeatCount = totalSeatCount != null && remainingSeatCount != null
            ? Math.max(totalSeatCount - remainingSeatCount, 0)
            : (soldCountFromSeats > 0 ? soldCountFromSeats : null);

        try (PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            statement.setLong(1, target.showtimeId());
            statement.setString(2, target.provider().name());
            statement.setString(3, target.externalShowtimeKey());
            setInteger(statement, 4, totalSeatCount);
            setInteger(statement, 5, remainingSeatCount);
            setInteger(statement, 6, soldSeatCount);
            setInteger(statement, 7, unavailableCount == 0 ? null : unavailableCount);
            setInteger(statement, 8, countSpecialSeats(result.seats()));
            statement.setString(9, objectMapper.writeValueAsString(result.summary()));
            statement.executeUpdate();
            try (var generatedKeys = statement.getGeneratedKeys()) {
                if (!generatedKeys.next()) {
                    throw new IllegalStateException("No seat snapshot id was generated.");
                }
                return generatedKeys.getLong(1);
            }
        }
    }

    private void insertSnapshotItems(Connection connection, long snapshotId, CollectorProvider provider, List<Map<String, Object>> seats) throws Exception {
        String sql = """
            INSERT INTO seat_snapshot_items (
              seat_snapshot_id, seat_key, seat_label, seat_row, seat_column, normalized_status,
              provider_status_code, seat_type, zone_name, x, y, width, height, provider_meta_json, raw_json
            )
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, CAST(? AS JSON), CAST(? AS JSON))
            ON DUPLICATE KEY UPDATE
              seat_label = VALUES(seat_label),
              seat_row = VALUES(seat_row),
              seat_column = VALUES(seat_column),
              normalized_status = VALUES(normalized_status),
              provider_status_code = VALUES(provider_status_code),
              seat_type = VALUES(seat_type),
              zone_name = VALUES(zone_name),
              x = VALUES(x),
              y = VALUES(y),
              width = VALUES(width),
              height = VALUES(height),
              provider_meta_json = VALUES(provider_meta_json),
              raw_json = VALUES(raw_json)
            """;

        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            for (Map<String, Object> seat : seats) {
                String seatKey = firstText(seat, "seat_loc_no", "seat_id", "seat_no", "seat_label");
                if (seatKey.isBlank()) {
                    continue;
                }
                statement.setLong(1, snapshotId);
                statement.setString(2, seatKey);
                statement.setString(3, blankToNull(firstText(seat, "seat_label")));
                statement.setString(4, blankToNull(firstText(seat, "seat_row")));
                statement.setString(5, blankToNull(firstText(seat, "seat_column", "seat_number")));
                statement.setString(6, blankToNull(statusNormalizer.normalize(provider, seat)));
                statement.setString(7, blankToNull(firstText(seat, "seat_status_code", "row_status_code")));
                statement.setString(8, blankToNull(firstText(seat, "seat_kind_name", "seat_class_code", "seat_type_code")));
                statement.setString(9, blankToNull(firstText(seat, "seat_zone_name", "seat_zone_code")));
                setDecimal(statement, 10, decimalOrNull(firstNonNull(seat, "x")));
                setDecimal(statement, 11, decimalOrNull(firstNonNull(seat, "y")));
                setDecimal(statement, 12, decimalOrNull(firstNonNull(seat, "width", "width_rate")));
                setDecimal(statement, 13, decimalOrNull(firstNonNull(seat, "height")));
                statement.setString(14, objectMapper.writeValueAsString(providerMeta(seat)));
                statement.setString(15, objectMapper.writeValueAsString(seat));
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }

    private int countSpecialSeats(List<Map<String, Object>> seats) {
        int count = 0;
        for (Map<String, Object> seat : seats) {
            String seatType = firstText(seat, "seat_kind_name", "seat_class_code", "seat_type_code");
            if (!seatType.isBlank() && !seatType.equalsIgnoreCase("STANDARD") && !seatType.equals("일반")) {
                count++;
            }
        }
        return count == 0 ? 0 : count;
    }

    private static Map<String, Object> providerMeta(Map<String, Object> seat) {
        return Map.of(
            "seat_status_name", firstText(seat, "seat_status_name"),
            "seat_sale_yn", firstText(seat, "seat_sale_yn"),
            "customer_division_code", firstText(seat, "customer_division_code"),
            "seat_group_name", firstText(seat, "seat_group_name")
        );
    }

    private static Integer firstInteger(Map<String, Object> values, String... keys) {
        for (String key : keys) {
            Integer value = integerOrNull(values.get(key));
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private static Object firstNonNull(Map<String, Object> values, String... keys) {
        for (String key : keys) {
            Object value = values.get(key);
            if (value != null && !String.valueOf(value).isBlank()) {
                return value;
            }
        }
        return null;
    }

    private static String firstText(Map<String, Object> values, String... keys) {
        Object value = firstNonNull(values, keys);
        return value == null ? "" : String.valueOf(value).trim();
    }

    private static Integer integerOrNull(Object value) {
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).trim();
        if (text.isBlank()) {
            return null;
        }
        return new BigDecimal(text.replace(",", "")).intValue();
    }

    private static BigDecimal decimalOrNull(Object value) {
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).trim();
        if (text.isBlank()) {
            return null;
        }
        return new BigDecimal(text.replace(",", ""));
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private static void setInteger(PreparedStatement statement, int index, Integer value) throws SQLException {
        if (value == null) {
            statement.setObject(index, null);
        } else {
            statement.setInt(index, value);
        }
    }

    private static void setDecimal(PreparedStatement statement, int index, BigDecimal value) throws SQLException {
        if (value == null) {
            statement.setObject(index, null);
        } else {
            statement.setBigDecimal(index, value);
        }
    }
}
