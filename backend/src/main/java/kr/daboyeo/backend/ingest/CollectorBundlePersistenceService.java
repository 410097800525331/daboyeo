package kr.daboyeo.backend.ingest;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Map;
import javax.sql.DataSource;
import org.springframework.stereotype.Service;

@Service
public class CollectorBundlePersistenceService {

    private final DataSource dataSource;
    private final ObjectMapper objectMapper;
    private final TheaterLocationEnricher theaterLocationEnricher;

    public CollectorBundlePersistenceService(
        DataSource dataSource,
        ObjectMapper objectMapper,
        TheaterLocationEnricher theaterLocationEnricher
    ) {
        this.dataSource = dataSource;
        this.objectMapper = objectMapper;
        this.theaterLocationEnricher = theaterLocationEnricher;
    }

    public CollectorBundleIngestCommand.IngestResult persist(String providerCode, Map<String, Object> bundle, boolean dryRun) {
        CollectorBundleIngestCommand.NormalizedBundle normalizedBundle = theaterLocationEnricher.enrich(
            CollectorBundleIngestCommand.normalizeBundle(providerCode, bundle)
        );
        CollectorBundleIngestCommand.IngestResult result = new CollectorBundleIngestCommand.IngestResult(
            normalizedBundle.movies().size(),
            normalizedBundle.theaters().size(),
            normalizedBundle.screens().size(),
            normalizedBundle.showtimes().size()
        );

        if (dryRun) {
            return result;
        }

        try (Connection connection = dataSource.getConnection()) {
            boolean originalAutoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try {
                upsertMovies(connection, normalizedBundle);
                upsertTheaters(connection, normalizedBundle);
                upsertScreens(connection, normalizedBundle);
                upsertShowtimes(connection, normalizedBundle);
                repairShowtimeLinks(connection, providerCode);
                connection.commit();
                return result;
            } catch (Exception exception) {
                connection.rollback();
                throw exception;
            } finally {
                connection.setAutoCommit(originalAutoCommit);
            }
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to persist collector bundle for " + providerCode, exception);
        }
    }

    private void upsertMovies(Connection connection, CollectorBundleIngestCommand.NormalizedBundle bundle) throws Exception {
        String sql = """
            INSERT INTO movies (
              provider_code, external_movie_id, representative_movie_id, title_ko, title_en, age_rating,
              runtime_minutes, release_date, booking_rate, box_office_rank, poster_url, poster_source_url,
              poster_r2_key, poster_etag, poster_storage_status, poster_stored_at, raw_json, last_collected_at
            )
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, CAST(? AS JSON), CURRENT_TIMESTAMP(3))
            ON DUPLICATE KEY UPDATE
              representative_movie_id = VALUES(representative_movie_id),
              title_ko = VALUES(title_ko),
              title_en = VALUES(title_en),
              age_rating = VALUES(age_rating),
              runtime_minutes = VALUES(runtime_minutes),
              release_date = VALUES(release_date),
              booking_rate = VALUES(booking_rate),
              box_office_rank = VALUES(box_office_rank),
              poster_url = VALUES(poster_url),
              poster_source_url = VALUES(poster_source_url),
              poster_r2_key = VALUES(poster_r2_key),
              poster_etag = VALUES(poster_etag),
              poster_storage_status = VALUES(poster_storage_status),
              poster_stored_at = VALUES(poster_stored_at),
              raw_json = VALUES(raw_json),
              last_collected_at = CURRENT_TIMESTAMP(3)
            """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            for (CollectorBundleIngestCommand.MovieRow movie : bundle.movies()) {
                if (movie.externalMovieId().isBlank() || movie.titleKo().isBlank()) {
                    continue;
                }
                statement.setString(1, movie.providerCode());
                statement.setString(2, movie.externalMovieId());
                statement.setString(3, blankToNull(movie.representativeMovieId()));
                statement.setString(4, movie.titleKo());
                statement.setString(5, blankToNull(movie.titleEn()));
                statement.setString(6, blankToNull(movie.ageRating()));
                setInteger(statement, 7, movie.runtimeMinutes());
                setDate(statement, 8, movie.releaseDate());
                setBigDecimal(statement, 9, movie.bookingRate());
                setInteger(statement, 10, movie.boxOfficeRank());
                statement.setString(11, blankToNull(movie.posterUrl()));
                statement.setString(12, blankToNull(movie.posterSourceUrl()));
                statement.setString(13, blankToNull(movie.posterR2Key()));
                statement.setString(14, blankToNull(movie.posterEtag()));
                statement.setString(15, blankToDefault(movie.posterStorageStatus(), blankToNull(movie.posterUrl()) == null ? "missing" : "source_only"));
                setTimestamp(statement, 16, movie.posterStoredAt());
                statement.setString(17, toJson(movie.raw()));
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }

    private void upsertTheaters(Connection connection, CollectorBundleIngestCommand.NormalizedBundle bundle) throws Exception {
        String sql = """
            INSERT INTO theaters (
              provider_code, external_theater_id, name, region_code, region_name, address,
              latitude, longitude, raw_json, last_collected_at
            )
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, CAST(? AS JSON), CURRENT_TIMESTAMP(3))
            ON DUPLICATE KEY UPDATE
              name = VALUES(name),
              region_code = VALUES(region_code),
              region_name = VALUES(region_name),
              address = COALESCE(VALUES(address), address),
              latitude = COALESCE(VALUES(latitude), latitude),
              longitude = COALESCE(VALUES(longitude), longitude),
              raw_json = VALUES(raw_json),
              last_collected_at = CURRENT_TIMESTAMP(3)
            """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            for (CollectorBundleIngestCommand.TheaterRow theater : bundle.theaters()) {
                if (theater.externalTheaterId().isBlank() || theater.name().isBlank()) {
                    continue;
                }
                statement.setString(1, theater.providerCode());
                statement.setString(2, theater.externalTheaterId());
                statement.setString(3, theater.name());
                statement.setString(4, blankToNull(theater.regionCode()));
                statement.setString(5, blankToNull(theater.regionName()));
                statement.setString(6, blankToNull(theater.address()));
                setBigDecimal(statement, 7, theater.latitude());
                setBigDecimal(statement, 8, theater.longitude());
                statement.setString(9, toJson(theater.raw()));
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }

    private void upsertScreens(Connection connection, CollectorBundleIngestCommand.NormalizedBundle bundle) throws Exception {
        String sql = """
            INSERT INTO screens (
              provider_code, theater_id, external_theater_id, external_screen_id, name,
              screen_type, floor_name, total_seat_count, raw_json, last_collected_at
            )
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, CAST(? AS JSON), CURRENT_TIMESTAMP(3))
            ON DUPLICATE KEY UPDATE
              theater_id = VALUES(theater_id),
              name = VALUES(name),
              screen_type = VALUES(screen_type),
              floor_name = VALUES(floor_name),
              total_seat_count = VALUES(total_seat_count),
              raw_json = VALUES(raw_json),
              last_collected_at = CURRENT_TIMESTAMP(3)
            """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            for (CollectorBundleIngestCommand.ScreenRow screen : bundle.screens()) {
                if (screen.externalTheaterId().isBlank() || screen.externalScreenId().isBlank() || screen.name().isBlank()) {
                    continue;
                }
                statement.setString(1, screen.providerCode());
                setLong(statement, 2, findId(connection, "theaters", "external_theater_id", screen.providerCode(), screen.externalTheaterId()));
                statement.setString(3, screen.externalTheaterId());
                statement.setString(4, screen.externalScreenId());
                statement.setString(5, screen.name());
                statement.setString(6, blankToNull(screen.screenType()));
                statement.setString(7, blankToNull(screen.floorName()));
                setInteger(statement, 8, screen.totalSeatCount());
                statement.setString(9, toJson(screen.raw()));
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }

    private void upsertShowtimes(Connection connection, CollectorBundleIngestCommand.NormalizedBundle bundle) throws Exception {
        String sql = """
            INSERT INTO showtimes (
              provider_code, external_showtime_key, movie_id, theater_id, screen_id,
              external_movie_id, external_theater_id, external_screen_id, movie_title,
              theater_name, region_name, region_code, screen_name, screen_type, format_name, show_date,
              starts_at, ends_at, start_time_raw, end_time_raw, total_seat_count,
              remaining_seat_count, sold_seat_count, seat_occupancy_rate, remaining_seat_source,
              booking_available, booking_key_json, booking_url, raw_json, last_collected_at
            )
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, CAST(? AS JSON), ?, CAST(? AS JSON), CURRENT_TIMESTAMP(3))
            ON DUPLICATE KEY UPDATE
              movie_id = VALUES(movie_id),
              theater_id = VALUES(theater_id),
              screen_id = VALUES(screen_id),
              external_movie_id = VALUES(external_movie_id),
              external_theater_id = VALUES(external_theater_id),
              external_screen_id = VALUES(external_screen_id),
              movie_title = VALUES(movie_title),
              theater_name = VALUES(theater_name),
              region_name = VALUES(region_name),
              region_code = VALUES(region_code),
              screen_name = VALUES(screen_name),
              screen_type = VALUES(screen_type),
              format_name = VALUES(format_name),
              show_date = VALUES(show_date),
              starts_at = VALUES(starts_at),
              ends_at = VALUES(ends_at),
              start_time_raw = VALUES(start_time_raw),
              end_time_raw = VALUES(end_time_raw),
              total_seat_count = VALUES(total_seat_count),
              remaining_seat_count = VALUES(remaining_seat_count),
              sold_seat_count = VALUES(sold_seat_count),
              seat_occupancy_rate = VALUES(seat_occupancy_rate),
              remaining_seat_source = VALUES(remaining_seat_source),
              booking_available = VALUES(booking_available),
              booking_key_json = VALUES(booking_key_json),
              booking_url = VALUES(booking_url),
              raw_json = VALUES(raw_json),
              last_collected_at = CURRENT_TIMESTAMP(3)
            """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            for (CollectorBundleIngestCommand.ShowtimeRow showtime : bundle.showtimes()) {
                if (!showtime.valid()) {
                    continue;
                }
                statement.setString(1, showtime.providerCode());
                statement.setString(2, showtime.externalShowtimeKey());
                setLong(statement, 3, findId(connection, "movies", "external_movie_id", showtime.providerCode(), showtime.externalMovieId()));
                setLong(statement, 4, findId(connection, "theaters", "external_theater_id", showtime.providerCode(), showtime.externalTheaterId()));
                setLong(statement, 5, findScreenId(connection, showtime.providerCode(), showtime.externalTheaterId(), showtime.externalScreenId()));
                statement.setString(6, blankToNull(showtime.externalMovieId()));
                statement.setString(7, blankToNull(showtime.externalTheaterId()));
                statement.setString(8, blankToNull(showtime.externalScreenId()));
                statement.setString(9, showtime.movieTitle());
                statement.setString(10, showtime.theaterName());
                statement.setString(11, blankToNull(showtime.regionName()));
                statement.setString(12, blankToNull(showtime.regionCode()));
                statement.setString(13, blankToNull(showtime.screenName()));
                statement.setString(14, blankToNull(showtime.screenType()));
                statement.setString(15, blankToNull(showtime.formatName()));
                setDate(statement, 16, showtime.showDate());
                setTimestamp(statement, 17, showtime.startsAt());
                setTimestamp(statement, 18, showtime.endsAt());
                statement.setString(19, blankToNull(showtime.startTimeRaw()));
                statement.setString(20, blankToNull(showtime.endTimeRaw()));
                setInteger(statement, 21, showtime.totalSeatCount());
                setInteger(statement, 22, showtime.remainingSeatCount());
                setInteger(statement, 23, showtime.soldSeatCount());
                setBigDecimal(statement, 24, showtime.seatOccupancyRate());
                statement.setString(25, blankToNull(showtime.remainingSeatSource()));
                statement.setString(26, blankToNull(showtime.bookingAvailable()));
                statement.setString(27, toJson(showtime.bookingKey()));
                statement.setString(28, blankToNull(showtime.bookingUrl()));
                statement.setString(29, toJson(showtime.raw()));
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }

    private void repairShowtimeLinks(Connection connection, String providerCode) throws SQLException {
        String sql = """
            UPDATE showtimes s
            JOIN theaters t
              ON t.provider_code = s.provider_code
             AND t.external_theater_id = s.external_theater_id
            LEFT JOIN screens sc
              ON sc.provider_code = s.provider_code
             AND sc.external_theater_id = s.external_theater_id
             AND sc.external_screen_id = s.external_screen_id
            SET s.theater_id = t.id,
                s.screen_id = COALESCE(sc.id, s.screen_id),
                s.region_name = COALESCE(NULLIF(s.region_name, ''), t.region_name),
                s.region_code = COALESCE(NULLIF(s.region_code, ''), t.region_code)
            WHERE s.provider_code = ?
              AND s.external_theater_id IS NOT NULL
              AND (
                s.theater_id IS NULL
                OR (sc.id IS NOT NULL AND s.screen_id IS NULL)
                OR s.region_name IS NULL
                OR s.region_name = ''
                OR s.region_code IS NULL
                OR s.region_code = ''
              )
            """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, normalizeProviderCode(providerCode));
            statement.executeUpdate();
        }
    }

    private Long findId(Connection connection, String table, String column, String providerCode, String externalId) throws SQLException {
        if (externalId == null || externalId.isBlank()) {
            return null;
        }
        String sql = "SELECT id FROM " + table + " WHERE provider_code = ? AND " + column + " = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, providerCode);
            statement.setString(2, externalId);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? resultSet.getLong(1) : null;
            }
        }
    }

    private Long findScreenId(Connection connection, String providerCode, String externalTheaterId, String externalScreenId) throws SQLException {
        if (externalTheaterId == null || externalTheaterId.isBlank() || externalScreenId == null || externalScreenId.isBlank()) {
            return null;
        }
        String sql = "SELECT id FROM screens WHERE provider_code = ? AND external_theater_id = ? AND external_screen_id = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, providerCode);
            statement.setString(2, externalTheaterId);
            statement.setString(3, externalScreenId);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? resultSet.getLong(1) : null;
            }
        }
    }

    private String toJson(Object value) throws Exception {
        return objectMapper.writeValueAsString(value == null ? Map.of() : value);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private static String blankToDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private static String normalizeProviderCode(String value) {
        return switch ((value == null ? "" : value.trim()).toUpperCase(Locale.ROOT)) {
            case "LOTTE", "LOTTE_CINEMA" -> "LOTTE_CINEMA";
            case "MEGA", "MEGABOX" -> "MEGABOX";
            default -> value;
        };
    }

    private static void setInteger(PreparedStatement statement, int index, Integer value) throws SQLException {
        if (value == null) {
            statement.setObject(index, null);
        } else {
            statement.setInt(index, value);
        }
    }

    private static void setLong(PreparedStatement statement, int index, Long value) throws SQLException {
        if (value == null) {
            statement.setObject(index, null);
        } else {
            statement.setLong(index, value);
        }
    }

    private static void setBigDecimal(PreparedStatement statement, int index, BigDecimal value) throws SQLException {
        if (value == null) {
            statement.setObject(index, null);
        } else {
            statement.setBigDecimal(index, value);
        }
    }

    private static void setDate(PreparedStatement statement, int index, LocalDate value) throws SQLException {
        if (value == null) {
            statement.setObject(index, null);
        } else {
            statement.setDate(index, java.sql.Date.valueOf(value));
        }
    }

    private static void setTimestamp(PreparedStatement statement, int index, LocalDateTime value) throws SQLException {
        if (value == null) {
            statement.setObject(index, null);
        } else {
            statement.setTimestamp(index, Timestamp.valueOf(value));
        }
    }
}
