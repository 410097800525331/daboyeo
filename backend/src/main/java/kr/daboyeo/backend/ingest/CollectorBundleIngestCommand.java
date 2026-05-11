package kr.daboyeo.backend.ingest;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import kr.daboyeo.backend.config.RootDotenvLoader;

public class CollectorBundleIngestCommand {

    private static final DateTimeFormatter COMPACT_DATE = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final String providerCode;
    private final Connection connection;
    private final boolean dryRun;

    CollectorBundleIngestCommand(String providerCode, Connection connection, boolean dryRun) {
        this.providerCode = normalizeProviderCode(providerCode);
        this.connection = connection;
        this.dryRun = dryRun;
    }

    public static void main(String[] args) throws Exception {
        Arguments arguments = Arguments.parse(args);
        if (arguments.bundlePath() == null) {
            throw new IllegalArgumentException("--bundle <path> is required.");
        }

        if (arguments.dryRun()) {
            new CollectorBundleIngestCommand(arguments.providerCode(), null, true).ingest(arguments.bundlePath());
            return;
        }

        Map<String, Object> dotenv = RootDotenvLoader.load(Path.of(System.getProperty("user.dir", ".")));
        String url = getSetting("DABOYEO_DB_URL", dotenv);
        String username = getSetting("DABOYEO_DB_USERNAME", dotenv);
        String password = getSetting("DABOYEO_DB_PASSWORD", dotenv);

        try (Connection connection = DriverManager.getConnection(url, username, password)) {
            new CollectorBundleIngestCommand(arguments.providerCode(), connection, false).ingest(arguments.bundlePath());
        }
    }

    IngestResult ingest(Path bundlePath) throws Exception {
        Map<String, Object> bundle = objectMapper.readValue(bundlePath.toFile(), new TypeReference<>() {
        });
        NormalizedBundle normalizedBundle = normalizeBundle(providerCode, bundle);

        IngestResult result = new IngestResult(
            normalizedBundle.movies().size(),
            normalizedBundle.theaters().size(),
            normalizedBundle.screens().size(),
            normalizedBundle.showtimes().size()
        );
        System.out.printf(
            "%s ingest %s: movies=%d, theaters=%d, screens=%d, showtimes=%d%n",
            providerCode,
            dryRun ? "dry-run" : "write",
            result.movies(),
            result.theaters(),
            result.screens(),
            result.showtimes()
        );

        if (dryRun) {
            return result;
        }

        if (connection == null) {
            throw new IllegalStateException("DB connection is required in write mode.");
        }

        boolean originalAutoCommit = connection.getAutoCommit();
        connection.setAutoCommit(false);
        try {
            upsertMovies(normalizedBundle.movies());
            upsertTheaters(normalizedBundle.theaters());
            upsertScreens(normalizedBundle.screens());
            upsertShowtimes(normalizedBundle.showtimes());
            connection.commit();
            return result;
        } catch (Exception exception) {
            connection.rollback();
            throw exception;
        } finally {
            connection.setAutoCommit(originalAutoCommit);
        }
    }

    static NormalizedBundle normalizeBundle(String providerCode, Map<String, Object> bundle) {
        return switch (normalizeProviderCode(providerCode)) {
            case "LOTTE_CINEMA" -> new LotteNormalizer().normalize(bundle);
            case "MEGABOX" -> new MegaboxNormalizer().normalize(bundle);
            default -> throw new IllegalArgumentException("Unsupported provider: " + providerCode);
        };
    }

    static LocalDate parseDate(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String text = value.trim().replace('.', '-').replace('/', '-');
        if (text.length() >= 10 && text.charAt(4) == '-' && text.charAt(7) == '-') {
            return LocalDate.parse(text.substring(0, 10));
        }
        String digits = text.replaceAll("\\D", "");
        if (digits.length() >= 8) {
            return LocalDate.parse(digits.substring(0, 8), COMPACT_DATE);
        }
        return LocalDate.parse(text, COMPACT_DATE);
    }

    static LocalDateTime parseDateTime(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String text = value.trim().replace(' ', 'T');
        if (text.endsWith("Z")) {
            text = text.substring(0, text.length() - 1);
        }
        if (text.length() >= 19) {
            text = text.substring(0, 19);
        }
        try {
            return LocalDateTime.parse(text);
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    static LocalTime parseTime(String value) {
        ParsedShowtimeTime parsed = parseShowtimeTime(value);
        return parsed == null ? null : parsed.time();
    }

    static ParsedShowtimeTime parseShowtimeTime(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String text = value.trim();
        try {
            int hour;
            int minute;
            int second = 0;
            if (text.contains(":")) {
                String[] parts = text.split(":");
                if (parts.length < 2) {
                    return null;
                }
                hour = Integer.parseInt(parts[0]);
                minute = Integer.parseInt(parts[1]);
                if (parts.length >= 3 && !parts[2].isBlank()) {
                    second = Integer.parseInt(parts[2]);
                }
            } else {
                String normalized = text.replaceAll("\\D", "");
                if (normalized.length() < 4) {
                    return null;
                }
                hour = Integer.parseInt(normalized.substring(0, normalized.length() - 2));
                minute = Integer.parseInt(normalized.substring(normalized.length() - 2));
            }
            if (minute > 59 || second > 59) {
                return null;
            }
            int dayOffset = Math.floorDiv(hour, 24);
            int hourOfDay = Math.floorMod(hour, 24);
            return new ParsedShowtimeTime(LocalTime.of(hourOfDay, minute, second), dayOffset);
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private void upsertMovies(List<MovieRow> movies) throws Exception {
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
            for (MovieRow movie : movies) {
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

    private void upsertTheaters(List<TheaterRow> theaters) throws Exception {
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
              address = VALUES(address),
              latitude = VALUES(latitude),
              longitude = VALUES(longitude),
              raw_json = VALUES(raw_json),
              last_collected_at = CURRENT_TIMESTAMP(3)
            """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            for (TheaterRow theater : theaters) {
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

    private void upsertScreens(List<ScreenRow> screens) throws Exception {
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
            for (ScreenRow screen : screens) {
                if (screen.externalTheaterId().isBlank() || screen.externalScreenId().isBlank() || screen.name().isBlank()) {
                    continue;
                }
                statement.setString(1, screen.providerCode());
                setLong(statement, 2, findId("theaters", "external_theater_id", screen.providerCode(), screen.externalTheaterId()));
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

    private void upsertShowtimes(List<ShowtimeRow> showtimes) throws Exception {
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
            for (ShowtimeRow showtime : showtimes) {
                if (!showtime.valid()) {
                    continue;
                }
                statement.setString(1, showtime.providerCode());
                statement.setString(2, showtime.externalShowtimeKey());
                setLong(statement, 3, findId("movies", "external_movie_id", showtime.providerCode(), showtime.externalMovieId()));
                setLong(statement, 4, findId("theaters", "external_theater_id", showtime.providerCode(), showtime.externalTheaterId()));
                setLong(statement, 5, findScreenId(showtime.providerCode(), showtime.externalTheaterId(), showtime.externalScreenId()));
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

    private Long findId(String table, String column, String providerCode, String externalId) throws SQLException {
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

    private Long findScreenId(String providerCode, String externalTheaterId, String externalScreenId) throws SQLException {
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

    private static String getSetting(String key, Map<String, Object> dotenv) {
        String envValue = System.getenv(key);
        if (envValue != null && !envValue.isBlank()) {
            return envValue;
        }
        Object dotenvValue = dotenv.get(key);
        if (dotenvValue != null && !text(dotenvValue).isBlank()) {
            return text(dotenvValue);
        }
        throw new IllegalStateException(key + " is required.");
    }

    private String toJson(Object value) throws Exception {
        return objectMapper.writeValueAsString(value == null ? Map.of() : value);
    }

    private static String normalizeProviderCode(String providerCode) {
        if (providerCode == null || providerCode.isBlank()) {
            throw new IllegalArgumentException("--provider is required.");
        }
        return switch (providerCode.trim().toUpperCase(Locale.ROOT)) {
            case "LOTTE", "LOTTE_CINEMA" -> "LOTTE_CINEMA";
            case "MEGA", "MEGABOX" -> "MEGABOX";
            default -> throw new IllegalArgumentException("Unsupported provider: " + providerCode);
        };
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> listOfMaps(Object value) {
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        return list.stream()
            .filter(Map.class::isInstance)
            .map(item -> (Map<String, Object>) item)
            .toList();
    }

    private static String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private static String blankToDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private static Integer integerOrNull(Object value) {
        String text = text(value);
        if (text.isBlank()) {
            return null;
        }
        return new BigDecimal(text.replace(",", "")).intValue();
    }

    private static BigDecimal decimalOrNull(Object value) {
        String text = text(value);
        if (text.isBlank()) {
            return null;
        }
        return new BigDecimal(text.replace(",", ""));
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

    private static TimeRange toDateTimeRange(LocalDate date, String startTimeRaw, String endTimeRaw) {
        LocalDateTime startsAt = toDateTime(date, startTimeRaw);
        LocalDateTime endsAt = toDateTime(date, endTimeRaw);
        if (startsAt != null && endsAt != null && !endsAt.isAfter(startsAt)) {
            endsAt = endsAt.plusDays(1);
        }
        return new TimeRange(startsAt, endsAt);
    }

    private static LocalDateTime toDateTime(LocalDate date, String timeRaw) {
        ParsedShowtimeTime parsed = parseShowtimeTime(timeRaw);
        return date == null || parsed == null ? null : LocalDateTime.of(date.plusDays(parsed.dayOffset()), parsed.time());
    }

    private static Integer soldSeatCount(Integer totalSeatCount, Integer remainingSeatCount, Integer bookedSeatCount) {
        if (bookedSeatCount != null) {
            return Math.max(bookedSeatCount, 0);
        }
        if (totalSeatCount == null || remainingSeatCount == null) {
            return null;
        }
        return Math.max(totalSeatCount - remainingSeatCount, 0);
    }

    private static BigDecimal occupancyRate(Integer soldSeatCount, Integer totalSeatCount) {
        if (soldSeatCount == null || totalSeatCount == null || totalSeatCount <= 0) {
            return null;
        }
        return BigDecimal.valueOf((double) soldSeatCount / totalSeatCount)
            .setScale(3, java.math.RoundingMode.HALF_UP);
    }

    private static String firstNonBlank(Object... values) {
        for (Object value : values) {
            String text = text(value);
            if (!text.isBlank()) {
                return text;
            }
        }
        return "";
    }

    private interface ProviderNormalizer {
        NormalizedBundle normalize(Map<String, Object> bundle);
    }

    private static final class LotteNormalizer implements ProviderNormalizer {

        @Override
        public NormalizedBundle normalize(Map<String, Object> bundle) {
            List<MovieRow> movies = listOfMaps(bundle.get("movies")).stream()
                .map(movie -> new MovieRow(
                    "LOTTE_CINEMA",
                    text(movie.get("movie_no")),
                    null,
                    text(movie.get("movie_name")),
                    text(movie.get("movie_name_en")),
                    text(movie.get("age_rating")),
                    integerOrNull(movie.get("runtime_minutes")),
                    parseDate(text(movie.get("release_date"))),
                    decimalOrNull(movie.get("booking_rate")),
                    null,
                    text(movie.get("poster_url")),
                    firstNonBlank(movie.get("poster_source_url"), movie.get("poster_url")),
                    text(movie.get("poster_r2_key")),
                    text(movie.get("poster_etag")),
                    firstNonBlank(movie.get("poster_storage_status"), text(movie.get("poster_url")).isBlank() ? "missing" : "source_only"),
                    parseDateTime(text(movie.get("poster_stored_at"))),
                    movie.get("raw")
                ))
                .toList();

            List<TheaterRow> theaters = listOfMaps(bundle.get("cinemas")).stream()
                .map(cinema -> new TheaterRow(
                    "LOTTE_CINEMA",
                    text(cinema.get("cinema_id")),
                    text(cinema.get("cinema_name")),
                    firstNonBlank(cinema.get("cinema_area_code"), cinema.get("detail_division_code")),
                    firstNonBlank(cinema.get("cinema_area_name"), cinema.get("detail_division_name")),
                    text(cinema.get("address_summary")),
                    decimalOrNull(cinema.get("latitude")),
                    decimalOrNull(cinema.get("longitude")),
                    cinema.get("raw")
                ))
                .toList();

            Map<String, TheaterRow> theatersById = theaters.stream()
                .collect(java.util.stream.Collectors.toMap(TheaterRow::externalTheaterId, theater -> theater, (left, right) -> left, LinkedHashMap::new));

            List<Map<String, Object>> schedules = listOfMaps(bundle.get("schedules"));
            List<ScreenRow> screens = uniqueBy(schedules, schedule -> text(schedule.get("cinema_id")) + "::" + text(schedule.get("screen_id"))).stream()
                .map(schedule -> new ScreenRow(
                    "LOTTE_CINEMA",
                    text(schedule.get("cinema_id")),
                    text(schedule.get("screen_id")),
                    firstNonBlank(schedule.get("screen_name"), schedule.get("screen_id")),
                    firstNonBlank(schedule.get("screen_division_name"), schedule.get("film_name"), schedule.get("sound_type_name")),
                    text(schedule.get("screen_floor")),
                    integerOrNull(schedule.get("total_seat_count")),
                    schedule.get("raw")
                ))
                .toList();

            List<ShowtimeRow> showtimes = schedules.stream()
                .map(schedule -> {
                    String theaterId = text(schedule.get("cinema_id"));
                    String movieId = text(schedule.get("movie_no"));
                    String screenId = text(schedule.get("screen_id"));
                    String sequence = text(schedule.get("play_sequence"));
                    LocalDate showDate = parseDate(text(schedule.get("play_date")));
                    String startTimeRaw = text(schedule.get("start_time"));
                    String endTimeRaw = text(schedule.get("end_time"));
                    TimeRange timeRange = toDateTimeRange(showDate, startTimeRaw, endTimeRaw);
                    Integer totalSeatCount = integerOrNull(schedule.get("total_seat_count"));
                    Integer remainingSeatCount = integerOrNull(schedule.get("remaining_seat_count"));
                    Integer bookedSeatCount = integerOrNull(schedule.get("booked_seat_count"));
                    Integer soldSeatCount = soldSeatCount(totalSeatCount, remainingSeatCount, bookedSeatCount);
                    TheaterRow theater = theatersById.get(theaterId);

                    return new ShowtimeRow(
                        "LOTTE_CINEMA",
                        !theaterId.isBlank() && !movieId.isBlank() && !screenId.isBlank() && !sequence.isBlank() && showDate != null,
                        String.join(":", "LOTTE_CINEMA", theaterId, showDate == null ? "" : showDate.toString(), screenId, sequence, movieId),
                        movieId,
                        theaterId,
                        screenId,
                        firstNonBlank(schedule.get("movie_name"), movieId),
                        firstNonBlank(schedule.get("cinema_name"), theater == null ? "" : theater.name()),
                        theater == null ? "" : theater.regionName(),
                        theater == null ? "" : theater.regionCode(),
                        firstNonBlank(schedule.get("screen_name"), screenId),
                        firstNonBlank(schedule.get("screen_division_name"), schedule.get("film_name"), schedule.get("sound_type_name")),
                        firstNonBlank(schedule.get("screen_division_name"), schedule.get("film_name"), schedule.get("translation_division_name"), schedule.get("sound_type_name")),
                        showDate,
                        timeRange.startsAt(),
                        timeRange.endsAt(),
                        startTimeRaw,
                        endTimeRaw,
                        totalSeatCount,
                        remainingSeatCount,
                        soldSeatCount,
                        occupancyRate(soldSeatCount, totalSeatCount),
                        bookedSeatCount != null ? "derived" : "provider",
                        text(schedule.get("booking_available")),
                        schedule.get("booking_key"),
                        null,
                        schedule.get("raw")
                    );
                })
                .toList();

            return new NormalizedBundle(movies, theaters, screens, showtimes);
        }
    }

    private static final class MegaboxNormalizer implements ProviderNormalizer {

        @Override
        public NormalizedBundle normalize(Map<String, Object> bundle) {
            List<MovieRow> movies = listOfMaps(bundle.get("movies")).stream()
                .map(movie -> new MovieRow(
                    "MEGABOX",
                    text(movie.get("movie_no")),
                    text(movie.get("representative_movie_no")),
                    text(movie.get("movie_name")),
                    text(movie.get("movie_name_en")),
                    text(movie.get("age_rating")),
                    integerOrNull(movie.get("runtime_minutes")),
                    parseDate(text(movie.get("release_date"))),
                    decimalOrNull(movie.get("booking_rate")),
                    integerOrNull(movie.get("box_office_rank")),
                    text(movie.get("poster_url")),
                    firstNonBlank(movie.get("poster_source_url"), movie.get("poster_url")),
                    text(movie.get("poster_r2_key")),
                    text(movie.get("poster_etag")),
                    firstNonBlank(movie.get("poster_storage_status"), text(movie.get("poster_url")).isBlank() ? "missing" : "source_only"),
                    parseDateTime(text(movie.get("poster_stored_at"))),
                    movie.get("raw")
                ))
                .toList();

            List<TheaterRow> theaters = listOfMaps(bundle.get("areas")).stream()
                .map(area -> new TheaterRow(
                    "MEGABOX",
                    text(area.get("branch_no")),
                    text(area.get("branch_name")),
                    text(area.get("area_code")),
                    text(area.get("area_name")),
                    null,
                    null,
                    null,
                    area.get("raw")
                ))
                .toList();

            Map<String, TheaterRow> theatersById = theaters.stream()
                .collect(java.util.stream.Collectors.toMap(TheaterRow::externalTheaterId, theater -> theater, (left, right) -> left, LinkedHashMap::new));

            List<Map<String, Object>> schedules = listOfMaps(bundle.get("schedules"));
            List<ScreenRow> screens = uniqueBy(schedules, schedule -> text(schedule.get("branch_no")) + "::" + text(schedule.get("theater_no"))).stream()
                .map(schedule -> new ScreenRow(
                    "MEGABOX",
                    text(schedule.get("branch_no")),
                    text(schedule.get("theater_no")),
                    firstNonBlank(schedule.get("screen_name"), schedule.get("theater_no")),
                    firstNonBlank(schedule.get("screen_type"), schedule.get("times_division_name")),
                    null,
                    integerOrNull(schedule.get("total_seat_count")),
                    schedule.get("raw")
                ))
                .toList();

            List<ShowtimeRow> showtimes = schedules.stream()
                .map(schedule -> {
                    String theaterId = text(schedule.get("branch_no"));
                    String movieId = text(schedule.get("movie_no"));
                    String screenId = text(schedule.get("theater_no"));
                    String playScheduleNo = text(schedule.get("play_schedule_no"));
                    String sequence = text(schedule.get("play_sequence"));
                    LocalDate showDate = parseDate(text(schedule.get("play_date")));
                    String startTimeRaw = text(schedule.get("start_time"));
                    String endTimeRaw = text(schedule.get("end_time"));
                    TimeRange timeRange = toDateTimeRange(showDate, startTimeRaw, endTimeRaw);
                    Integer totalSeatCount = integerOrNull(schedule.get("total_seat_count"));
                    Integer remainingSeatCount = integerOrNull(schedule.get("remaining_seat_count"));
                    Integer soldSeatCount = soldSeatCount(totalSeatCount, remainingSeatCount, null);
                    TheaterRow theater = theatersById.get(theaterId);

                    String externalShowtimeKey = !playScheduleNo.isBlank()
                        ? "MEGABOX:" + playScheduleNo
                        : String.join(":", "MEGABOX", theaterId, showDate == null ? "" : showDate.toString(), screenId, sequence, movieId);

                    return new ShowtimeRow(
                        "MEGABOX",
                        !externalShowtimeKey.isBlank() && !movieId.isBlank() && !theaterId.isBlank() && showDate != null,
                        externalShowtimeKey,
                        movieId,
                        theaterId,
                        screenId,
                        firstNonBlank(schedule.get("movie_name"), movieId),
                        firstNonBlank(schedule.get("branch_name"), theater == null ? "" : theater.name()),
                        theater == null ? "" : theater.regionName(),
                        theater == null ? "" : theater.regionCode(),
                        firstNonBlank(schedule.get("screen_name"), screenId),
                        firstNonBlank(schedule.get("screen_type"), schedule.get("times_division_name")),
                        firstNonBlank(schedule.get("screen_type"), schedule.get("times_division_name")),
                        showDate,
                        timeRange.startsAt(),
                        timeRange.endsAt(),
                        startTimeRaw,
                        endTimeRaw,
                        totalSeatCount,
                        remainingSeatCount,
                        soldSeatCount,
                        occupancyRate(soldSeatCount, totalSeatCount),
                        "provider",
                        text(schedule.get("booking_available")),
                        Map.of(
                            "play_schedule_no", playScheduleNo,
                            "branch_no", theaterId
                        ),
                        text(schedule.get("booking_url")),
                        schedule.get("raw")
                    );
                })
                .toList();

            return new NormalizedBundle(movies, theaters, screens, showtimes);
        }
    }

    private static List<Map<String, Object>> uniqueBy(List<Map<String, Object>> rows, java.util.function.Function<Map<String, Object>, String> keyBuilder) {
        Map<String, Map<String, Object>> uniqueRows = new LinkedHashMap<>();
        for (Map<String, Object> row : rows) {
            String key = keyBuilder.apply(row);
            if (key == null || key.isBlank()) {
                continue;
            }
            uniqueRows.putIfAbsent(key, row);
        }
        return new ArrayList<>(uniqueRows.values());
    }

    public record IngestResult(int movies, int theaters, int screens, int showtimes) {
    }

    static record NormalizedBundle(
        List<MovieRow> movies,
        List<TheaterRow> theaters,
        List<ScreenRow> screens,
        List<ShowtimeRow> showtimes
    ) {
    }

    static record ParsedShowtimeTime(LocalTime time, int dayOffset) {
    }

    static record TimeRange(LocalDateTime startsAt, LocalDateTime endsAt) {
    }

    static record MovieRow(
        String providerCode,
        String externalMovieId,
        String representativeMovieId,
        String titleKo,
        String titleEn,
        String ageRating,
        Integer runtimeMinutes,
        LocalDate releaseDate,
        BigDecimal bookingRate,
        Integer boxOfficeRank,
        String posterUrl,
        String posterSourceUrl,
        String posterR2Key,
        String posterEtag,
        String posterStorageStatus,
        LocalDateTime posterStoredAt,
        Object raw
    ) {
    }

    static record TheaterRow(
        String providerCode,
        String externalTheaterId,
        String name,
        String regionCode,
        String regionName,
        String address,
        BigDecimal latitude,
        BigDecimal longitude,
        Object raw
    ) {
    }

    static record ScreenRow(
        String providerCode,
        String externalTheaterId,
        String externalScreenId,
        String name,
        String screenType,
        String floorName,
        Integer totalSeatCount,
        Object raw
    ) {
    }

    static record ShowtimeRow(
        String providerCode,
        boolean valid,
        String externalShowtimeKey,
        String externalMovieId,
        String externalTheaterId,
        String externalScreenId,
        String movieTitle,
        String theaterName,
        String regionName,
        String regionCode,
        String screenName,
        String screenType,
        String formatName,
        LocalDate showDate,
        LocalDateTime startsAt,
        LocalDateTime endsAt,
        String startTimeRaw,
        String endTimeRaw,
        Integer totalSeatCount,
        Integer remainingSeatCount,
        Integer soldSeatCount,
        BigDecimal seatOccupancyRate,
        String remainingSeatSource,
        String bookingAvailable,
        Object bookingKey,
        String bookingUrl,
        Object raw
    ) {
    }

    private record Arguments(String providerCode, Path bundlePath, boolean dryRun) {

        static Arguments parse(String[] args) {
            String providerCode = null;
            Path bundlePath = null;
            boolean dryRun = true;
            for (int index = 0; index < args.length; index++) {
                String arg = args[index];
                if ("--provider".equals(arg) && index + 1 < args.length) {
                    providerCode = args[++index];
                } else if ("--bundle".equals(arg) && index + 1 < args.length) {
                    bundlePath = Path.of(args[++index]);
                } else if ("--write".equals(arg)) {
                    dryRun = false;
                } else if ("--dry-run".equals(arg)) {
                    dryRun = true;
                }
            }
            return new Arguments(normalizeProviderCode(providerCode), bundlePath, dryRun);
        }
    }
}
