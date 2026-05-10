package kr.daboyeo.backend.sync.nearby;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import kr.daboyeo.backend.sync.bridge.CollectorProvider;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class NearbyRefreshRepository {

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public NearbyRefreshRepository(NamedParameterJdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    public List<TheaterSyncMetadata> findTheaterSyncMetadata(CollectorProvider provider, Collection<String> externalTheaterIds) {
        if (externalTheaterIds == null || externalTheaterIds.isEmpty()) {
            return List.of();
        }
        String sql = """
            SELECT provider_code, external_theater_id, name, region_code, raw_json
            FROM theaters
            WHERE provider_code = :providerCode
              AND external_theater_id IN (:externalTheaterIds)
            """;
        return jdbcTemplate.query(
            sql,
            new MapSqlParameterSource()
                .addValue("providerCode", provider.name())
                .addValue("externalTheaterIds", externalTheaterIds),
            new TheaterSyncMetadataRowMapper(provider, objectMapper)
        );
    }

    public Map<String, LocalDateTime> findLatestShowtimeCollectedAt(CollectorProvider provider, LocalDate showDate, Collection<String> externalTheaterIds) {
        if (externalTheaterIds == null || externalTheaterIds.isEmpty()) {
            return Map.of();
        }
        String sql = """
            SELECT external_theater_id, MAX(last_collected_at) AS last_collected_at
            FROM showtimes
            WHERE provider_code = :providerCode
              AND show_date = :showDate
              AND external_theater_id IN (:externalTheaterIds)
            GROUP BY external_theater_id
            """;
        return jdbcTemplate.query(
            sql,
            new MapSqlParameterSource()
                .addValue("providerCode", provider.name())
                .addValue("showDate", showDate)
                .addValue("externalTheaterIds", externalTheaterIds),
            rs -> {
                java.util.LinkedHashMap<String, LocalDateTime> rows = new java.util.LinkedHashMap<>();
                while (rs.next()) {
                    Timestamp timestamp = rs.getTimestamp("last_collected_at");
                    rows.put(rs.getString("external_theater_id"), timestamp == null ? null : timestamp.toLocalDateTime());
                }
                return rows;
            }
        );
    }

    public List<String> findRecentExternalMovieIds(
        CollectorProvider provider,
        Collection<String> externalTheaterIds,
        LocalDate fromDate,
        LocalDate toDate,
        int limit
    ) {
        if (externalTheaterIds == null || externalTheaterIds.isEmpty() || limit <= 0) {
            return List.of();
        }
        String sql = """
            SELECT external_movie_id
            FROM showtimes
            WHERE provider_code = :providerCode
              AND external_theater_id IN (:externalTheaterIds)
              AND show_date BETWEEN :fromDate AND :toDate
              AND external_movie_id IS NOT NULL
              AND external_movie_id <> ''
            GROUP BY external_movie_id
            ORDER BY MAX(last_collected_at) DESC, COUNT(*) DESC, external_movie_id ASC
            LIMIT :limit
            """;
        return jdbcTemplate.query(
            sql,
            new MapSqlParameterSource()
                .addValue("providerCode", provider.name())
                .addValue("externalTheaterIds", externalTheaterIds)
                .addValue("fromDate", fromDate)
                .addValue("toDate", toDate)
                .addValue("limit", limit),
            (rs, rowNum) -> rs.getString("external_movie_id")
        );
    }

    record TheaterSyncMetadata(
        CollectorProvider provider,
        String externalTheaterId,
        String name,
        String cinemaSelector,
        String areaCode
    ) {
    }

    private record TheaterSyncMetadataRowMapper(
        CollectorProvider provider,
        ObjectMapper objectMapper
    ) implements RowMapper<TheaterSyncMetadata> {

        @Override
        public TheaterSyncMetadata mapRow(ResultSet rs, int rowNum) throws SQLException {
            Map<String, Object> raw = parseRaw(rs.getString("raw_json"));
            return new TheaterSyncMetadata(
                provider,
                rs.getString("external_theater_id"),
                rs.getString("name"),
                buildCinemaSelector(raw),
                firstNonBlank(text(raw.get("area_code")), rs.getString("region_code"))
            );
        }

        private Map<String, Object> parseRaw(String rawJson) {
            if (rawJson == null || rawJson.isBlank()) {
                return Map.of();
            }
            try {
                return objectMapper.readValue(rawJson, MAP_TYPE);
            } catch (IOException exception) {
                return Map.of();
            }
        }

        private static String buildCinemaSelector(Map<String, Object> raw) {
            String divisionCode = text(raw.get("DivisionCode"));
            String detailDivisionCode = text(raw.get("DetailDivisionCode"));
            String cinemaId = text(raw.get("CinemaID"));
            if (divisionCode.isBlank() || detailDivisionCode.isBlank() || cinemaId.isBlank()) {
                return "";
            }
            return String.join("|", divisionCode, detailDivisionCode, cinemaId);
        }

        private static String firstNonBlank(String... values) {
            for (String value : values) {
                if (value != null && !value.isBlank()) {
                    return value.trim();
                }
            }
            return "";
        }

        private static String text(Object value) {
            return value == null ? "" : String.valueOf(value).trim();
        }
    }
}
