package kr.daboyeo.backend.repository;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import kr.daboyeo.backend.domain.LiveMovieSchedule;
import kr.daboyeo.backend.domain.LiveMovieSearchCriteria;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class LiveMovieRepository {

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");
    private static final double EARTH_RADIUS_KM = 6371.0d;

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public LiveMovieRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<LiveMovieSchedule> findNearbySchedules(LiveMovieSearchCriteria criteria) {
        QueryParts queryParts = buildBaseQuery(criteria);
        queryParts.sql().append("""
             HAVING distance_km <= :radiusKm
             ORDER BY distance_km ASC, st.starts_at ASC, st.movie_title ASC
             LIMIT :limit
            """);
        queryParts.params().addValue("radiusKm", criteria.radiusKm());
        return jdbcTemplate.query(queryParts.sql().toString(), queryParts.params(), new LiveMovieScheduleRowMapper());
    }

    public List<LiveMovieSchedule> findMovieSchedules(String movieKey, LiveMovieSearchCriteria criteria) {
        QueryParts queryParts = buildBaseQuery(criteria);
        MovieKeyParts movieKeyParts = MovieKeyParts.parse(movieKey);

        if (movieKeyParts.hasProviderAndExternalId()) {
            queryParts.sql().append(" AND st.provider_code = :movieProviderCode AND st.external_movie_id = :externalMovieId");
            queryParts.params()
                .addValue("movieProviderCode", movieKeyParts.providerCode())
                .addValue("externalMovieId", movieKeyParts.externalMovieId());
        } else {
            queryParts.sql().append(" AND st.movie_title = :movieTitle");
            queryParts.params().addValue("movieTitle", movieKeyParts.movieTitle());
        }

        queryParts.sql().append("""
             HAVING distance_km <= :radiusKm
             ORDER BY st.theater_name ASC, st.starts_at ASC
             LIMIT :limit
            """);
        queryParts.params()
            .addValue("radiusKm", criteria.radiusKm())
            .addValue("limit", Math.max(criteria.limit(), 500));

        return jdbcTemplate.query(queryParts.sql().toString(), queryParts.params(), new LiveMovieScheduleRowMapper());
    }

    public List<MovieCatalogRow> findPopularMovies(int limit, String query) {
        return findPopularMovies(limit, query, "", "");
    }

    public List<MovieCatalogRow> findPopularMovies(int limit, String query, String section) {
        return findPopularMovies(limit, query, section, "");
    }

    public List<MovieCatalogRow> findPopularMovies(int limit, String query, String section, String releaseState) {
        StringBuilder sql = new StringBuilder("""
            SELECT *
            FROM (
            SELECT
              SUBSTRING_INDEX(
                GROUP_CONCAT(
                  CONCAT(m.provider_code, ':', m.external_movie_id)
                  ORDER BY
                    CASE WHEN m.box_office_rank IS NULL THEN 999999 ELSE m.box_office_rank END ASC,
                    m.booking_rate DESC,
                    m.last_collected_at DESC
                  SEPARATOR ','
                ),
                ',',
                1
              ) AS movie_key,
              m.title_ko AS title_ko,
              SUBSTRING_INDEX(
                GROUP_CONCAT(NULLIF(m.title_en, '') ORDER BY m.last_collected_at DESC SEPARATOR '||'),
                '||',
                1
              ) AS title_en,
              SUBSTRING_INDEX(
                GROUP_CONCAT(NULLIF(m.age_rating, '') ORDER BY m.last_collected_at DESC SEPARATOR '||'),
                '||',
                1
              ) AS age_rating,
              MAX(m.runtime_minutes) AS runtime_minutes,
              MIN(m.release_date) AS release_date,
              CASE
                WHEN MIN(m.release_date) IS NULL THEN 'unknown'
                WHEN MIN(m.release_date) > CURRENT_DATE() THEN 'upcoming'
                ELSE 'now_playing'
              END AS release_state,
              MAX(m.booking_rate) AS booking_rate,
              MIN(m.box_office_rank) AS box_office_rank,
              SUBSTRING_INDEX(
                GROUP_CONCAT(NULLIF(m.poster_url, '') ORDER BY
                  CASE WHEN m.poster_url IS NULL OR m.poster_url = '' THEN 1 ELSE 0 END ASC,
                  CASE WHEN m.box_office_rank IS NULL THEN 999999 ELSE m.box_office_rank END ASC,
                  m.booking_rate DESC,
                  m.last_collected_at DESC
                  SEPARATOR '||'
                ),
                '||',
                1
              ) AS poster_url,
              MIN(COALESCE(sp.total_price_amount, st.min_price_amount)) AS min_price_amount,
              COUNT(DISTINCT st.id) AS showtime_count,
              GROUP_CONCAT(DISTINCT m.provider_code ORDER BY m.provider_code SEPARATOR ',') AS providers,
              GROUP_CONCAT(DISTINCT CONCAT(mt.tag_type, ':', mt.tag_value) ORDER BY mt.tag_type, mt.tag_value SEPARATOR ',') AS tags,
              MAX(CASE
                WHEN mt.tag_type = 'audience' AND mt.tag_value = 'alone' THEN 100
                WHEN mt.tag_type = 'genre' AND mt.tag_value IN ('sf', 'thriller', 'horror') THEN 72
                WHEN mt.tag_type = 'genre' AND mt.tag_value = 'drama' THEN 52
                WHEN mt.tag_type = 'pace' AND mt.tag_value IN ('slow_burn', 'intense') THEN 38
                ELSE 0
              END) + COALESCE(MAX(m.booking_rate), 0) AS alone_score,
              MAX(CASE
                WHEN mt.tag_type = 'audience' AND mt.tag_value = 'date' THEN 110
                WHEN mt.tag_type = 'genre' AND mt.tag_value = 'romance' THEN 100
                WHEN mt.tag_type = 'genre' AND mt.tag_value = 'drama' THEN 40
                WHEN mt.tag_type = 'genre' AND mt.tag_value = 'animation' THEN 24
                ELSE 0
              END) + COALESCE(MAX(m.booking_rate), 0) AS couple_score,
              MAX(m.last_collected_at) AS latest_collected_at
            FROM movies m
            LEFT JOIN showtimes st
              ON st.provider_code = m.provider_code
             AND st.external_movie_id = m.external_movie_id
             AND st.starts_at >= NOW()
            LEFT JOIN showtime_prices sp
              ON sp.provider_code = st.provider_code
             AND sp.external_showtime_key = st.external_showtime_key
            LEFT JOIN movie_tags mt
              ON mt.provider_code = m.provider_code
             AND mt.external_movie_id = m.external_movie_id
            WHERE m.title_ko IS NOT NULL
              AND m.title_ko <> ''
            """);

        MapSqlParameterSource params = new MapSqlParameterSource()
            .addValue("limit", Math.max(1, Math.min(limit, 80)));

        if (query != null && !query.isBlank()) {
            sql.append(" AND (LOWER(m.title_ko) LIKE :query OR LOWER(COALESCE(m.title_en, '')) LIKE :query)");
            params.addValue("query", "%" + query.trim().toLowerCase(Locale.ROOT) + "%");
        }

        sql.append("""
            GROUP BY m.title_ko
            ) catalog
            """);

        String normalizedReleaseState = normalizeReleaseState(releaseState);
        if ("now_playing".equals(normalizedReleaseState)) {
            sql.append(" WHERE release_state = 'now_playing'\n");
        } else if ("upcoming".equals(normalizedReleaseState)) {
            sql.append(" WHERE release_state = 'upcoming'\n");
        } else if ("unknown".equals(normalizedReleaseState)) {
            sql.append(" WHERE release_state = 'unknown'\n");
        }

        String normalizedSection = normalizeSection(section);
        if ("alone".equals(normalizedSection)) {
            sql.append("""
                ORDER BY
                  CASE WHEN release_state = 'upcoming' THEN 1 ELSE 0 END ASC,
                  alone_score DESC,
                  CASE WHEN box_office_rank IS NULL THEN 1 ELSE 0 END ASC,
                  box_office_rank ASC,
                  booking_rate DESC,
                  showtime_count DESC,
                  latest_collected_at DESC
                """);
        } else if ("couple".equals(normalizedSection)) {
            sql.append("""
                ORDER BY
                  CASE WHEN release_state = 'upcoming' THEN 1 ELSE 0 END ASC,
                  couple_score DESC,
                  RAND(),
                  CASE WHEN box_office_rank IS NULL THEN 1 ELSE 0 END ASC,
                  booking_rate DESC,
                  latest_collected_at DESC
                """);
        } else {
            sql.append("""
                ORDER BY
                  CASE WHEN box_office_rank IS NULL THEN 1 ELSE 0 END ASC,
                  box_office_rank ASC,
                  booking_rate DESC,
                  showtime_count DESC,
                  latest_collected_at DESC
                """);
        }

        sql.append("""
            LIMIT :limit
            """);

        return jdbcTemplate.query(sql.toString(), params, new MovieCatalogRowMapper());
    }

    private static String normalizeReleaseState(String releaseState) {
        if (releaseState == null) {
            return "";
        }
        String normalized = releaseState.trim().toLowerCase(Locale.ROOT).replace('-', '_');
        return switch (normalized) {
            case "now", "current", "playing", "released", "now_playing" -> "now_playing";
            case "coming", "coming_soon", "scheduled", "upcoming" -> "upcoming";
            case "unknown" -> "unknown";
            default -> "";
        };
    }

    private static String normalizeSection(String section) {
        if (section == null) {
            return "";
        }
        String normalized = section.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "alone", "solo" -> "alone";
            case "couple", "date", "romance" -> "couple";
            default -> "";
        };
    }

    private QueryParts buildBaseQuery(LiveMovieSearchCriteria criteria) {
        StringBuilder sql = new StringBuilder("""
            SELECT
              st.provider_code,
              st.external_movie_id,
              st.external_theater_id,
              st.external_screen_id,
              st.movie_title,
              st.theater_name,
              st.screen_name,
              st.screen_type,
              st.format_name,
              st.show_date,
              st.starts_at,
              st.ends_at,
              st.total_seat_count,
              st.remaining_seat_count,
              st.booking_url,
              st.updated_at,
              m.age_rating,
              m.poster_url,
              t.latitude,
              t.longitude,
              (
                :earthRadiusKm * ACOS(
                  LEAST(
                    1,
                    GREATEST(
                      -1,
                      COS(RADIANS(:lat)) * COS(RADIANS(t.latitude)) * COS(RADIANS(t.longitude) - RADIANS(:lng))
                      + SIN(RADIANS(:lat)) * SIN(RADIANS(t.latitude))
                    )
                  )
                )
              ) AS distance_km
            FROM showtimes st
            LEFT JOIN movies m ON m.id = st.movie_id
            LEFT JOIN theaters t ON t.id = st.theater_id
            LEFT JOIN screens sc ON sc.id = st.screen_id
            WHERE st.starts_at IS NOT NULL
              AND t.latitude IS NOT NULL
              AND t.longitude IS NOT NULL
              AND t.latitude BETWEEN :minLat AND :maxLat
              AND t.longitude BETWEEN :minLng AND :maxLng
              AND st.starts_at >= :startDateTime
              AND st.starts_at <= :endDateTime
            """);

        LocalDateTime startDt = LocalDateTime.of(criteria.date(), criteria.timeStart());
        LocalDateTime endDt = LocalDateTime.of(criteria.date(), criteria.timeEnd());
        if (criteria.timeEnd().isBefore(criteria.timeStart())) {
            endDt = endDt.plusDays(1);
        }

        MapSqlParameterSource params = new MapSqlParameterSource()
            .addValue("earthRadiusKm", EARTH_RADIUS_KM)
            .addValue("lat", criteria.lat())
            .addValue("lng", criteria.lng())
            .addValue("startDateTime", startDt)
            .addValue("endDateTime", endDt)
            .addValue("minLat", criteria.lat().doubleValue() - latitudeDelta(criteria.radiusKm().doubleValue()))
            .addValue("maxLat", criteria.lat().doubleValue() + latitudeDelta(criteria.radiusKm().doubleValue()))
            .addValue("minLng", criteria.lng().doubleValue() - longitudeDelta(criteria.radiusKm().doubleValue(), criteria.lat().doubleValue()))
            .addValue("maxLng", criteria.lng().doubleValue() + longitudeDelta(criteria.radiusKm().doubleValue(), criteria.lat().doubleValue()))
            .addValue("limit", criteria.limit());

        if (!criteria.providers().isEmpty()) {
            sql.append(" AND st.provider_code IN (:providerCodes)");
            params.addValue("providerCodes", mapProviderCodes(criteria.providers()));
        }

        if (!criteria.query().isBlank()) {
            sql.append(" AND (LOWER(st.movie_title) LIKE :query OR LOWER(st.theater_name) LIKE :query)");
            params.addValue("query", "%" + criteria.query().toLowerCase(Locale.ROOT) + "%");
        }

        if (!criteria.formats().isEmpty()) {
            List<String> formatConditions = new ArrayList<>();
            for (int index = 0; index < criteria.formats().size(); index++) {
                String key = "format" + index;
                formatConditions.add("(UPPER(COALESCE(st.format_name, '')) LIKE :" + key + " OR UPPER(COALESCE(st.screen_type, '')) LIKE :" + key + " OR UPPER(COALESCE(st.screen_name, '')) LIKE :" + key + ")");
                params.addValue(key, "%" + criteria.formats().get(index).toUpperCase(Locale.ROOT) + "%");
            }
            sql.append(" AND (").append(String.join(" OR ", formatConditions)).append(")");
        }

        if (!criteria.seatTypes().isEmpty()) {
            List<String> seatTypeConditions = new ArrayList<>();
            for (int index = 0; index < criteria.seatTypes().size(); index++) {
                String key = "seatType" + index;
                seatTypeConditions.add("(UPPER(COALESCE(st.format_name, '')) LIKE :" + key + " OR UPPER(COALESCE(st.screen_name, '')) LIKE :" + key + " OR UPPER(COALESCE(st.screen_type, '')) LIKE :" + key + ")");
                params.addValue(key, "%" + criteria.seatTypes().get(index).toUpperCase(Locale.ROOT) + "%");
            }
            sql.append(" AND (").append(String.join(" OR ", seatTypeConditions)).append(")");
        }

        return new QueryParts(sql, params);
    }

    private static List<String> mapProviderCodes(List<String> providers) {
        Map<String, String> mapping = new HashMap<>();
        mapping.put("LOTTE", "LOTTE_CINEMA");
        mapping.put("LOTTE_CINEMA", "LOTTE_CINEMA");
        mapping.put("MEGA", "MEGABOX");
        mapping.put("MEGABOX", "MEGABOX");
        return providers.stream()
            .map(value -> mapping.getOrDefault(value.toUpperCase(Locale.ROOT), value))
            .distinct()
            .toList();
    }

    private static double latitudeDelta(double radiusKm) {
        return radiusKm / 111.0d;
    }

    private static double longitudeDelta(double radiusKm, double latitude) {
        double divisor = 111.320d * Math.cos(Math.toRadians(latitude));
        return divisor == 0 ? radiusKm / 111.320d : radiusKm / divisor;
    }

    private static final class LiveMovieScheduleRowMapper implements RowMapper<LiveMovieSchedule> {

        @Override
        public LiveMovieSchedule mapRow(ResultSet rs, int rowNum) throws SQLException {
            LocalDate showDate = rs.getObject("show_date", LocalDate.class);
            LocalDateTime startsAt = rs.getObject("starts_at", LocalDateTime.class);
            LocalDateTime endsAt = rs.getObject("ends_at", LocalDateTime.class);
            Integer totalSeatCount = getNullableInt(rs, "total_seat_count");
            Integer remainingSeatCount = getNullableInt(rs, "remaining_seat_count");
            BigDecimal seatRatio = calculateSeatRatio(totalSeatCount, remainingSeatCount);
            Timestamp updatedAt = rs.getTimestamp("updated_at");

            return new LiveMovieSchedule(
                buildMovieKey(rs.getString("provider_code"), rs.getString("external_movie_id"), rs.getString("movie_title")),
                rs.getString("movie_title"),
                normalizeProvider(rs.getString("provider_code")),
                rs.getString("provider_code"),
                rs.getString("external_theater_id"),
                rs.getString("theater_name"),
                rs.getString("external_screen_id"),
                rs.getString("screen_name"),
                resolveFormatName(rs.getString("format_name"), rs.getString("screen_type"), rs.getString("screen_name")),
                inferSeatTypeTags(rs.getString("screen_name"), rs.getString("screen_type"), rs.getString("format_name")),
                defaultIfBlank(rs.getString("age_rating"), "ALL"),
                startsAt == null ? "00:00" : startsAt.toLocalTime().format(TIME_FORMATTER),
                endsAt == null ? null : endsAt.toLocalTime().format(TIME_FORMATTER),
                showDate,
                totalSeatCount,
                remainingSeatCount,
                remainingSeatCount,
                seatRatio,
                "",
                rs.getBigDecimal("distance_km"),
                rs.getString("booking_url"),
                updatedAt == null ? null : updatedAt.toLocalDateTime(),
                rs.getString("poster_url")
            );
        }

        private static String buildMovieKey(String providerCode, String externalMovieId, String movieTitle) {
            if (externalMovieId != null && !externalMovieId.isBlank()) {
                return providerCode + ":" + externalMovieId;
            }
            return "movie:" + movieTitle;
        }

        private static String normalizeProvider(String providerCode) {
            if ("LOTTE_CINEMA".equalsIgnoreCase(providerCode)) {
                return "LOTTE";
            }
            if ("MEGABOX".equalsIgnoreCase(providerCode)) {
                return "MEGA";
            }
            return providerCode == null ? "ETC" : providerCode.toUpperCase(Locale.ROOT);
        }

        private static String resolveFormatName(String formatName, String screenType, String screenName) {
            if (formatName != null && !formatName.isBlank()) {
                return formatName;
            }
            if (screenType != null && !screenType.isBlank()) {
                return screenType;
            }
            return defaultIfBlank(screenName, "2D");
        }

        private static List<String> inferSeatTypeTags(String screenName, String screenType, String formatName) {
            String joined = String.join(" ", defaultIfBlank(screenName, ""), defaultIfBlank(screenType, ""), defaultIfBlank(formatName, "")).toUpperCase(Locale.ROOT);
            List<String> tags = new ArrayList<>();
            if (joined.contains("RECLINER") || joined.contains("리클라이너")) {
                tags.add("RECLINER");
            }
            if (joined.contains("PRIVATE") || joined.contains("SUITE") || joined.contains("SWEET") || joined.contains("프라이빗") || joined.contains("스위트")) {
                tags.add("PRIVATE");
            }
            if (joined.contains("CHEF") || joined.contains("DINING") || joined.contains("다이닝") || joined.contains("셰프")) {
                tags.add("CHEF");
            }
            return tags;
        }

        private static Integer getNullableInt(ResultSet rs, String column) throws SQLException {
            int value = rs.getInt(column);
            return rs.wasNull() ? null : value;
        }

        private static BigDecimal calculateSeatRatio(Integer totalSeatCount, Integer remainingSeatCount) {
            if (totalSeatCount == null || remainingSeatCount == null || totalSeatCount <= 0) {
                return BigDecimal.ZERO;
            }
            return BigDecimal.valueOf((double) remainingSeatCount / totalSeatCount)
                .setScale(3, java.math.RoundingMode.HALF_UP);
        }

        private static String defaultIfBlank(String value, String defaultValue) {
            return value == null || value.isBlank() ? defaultValue : value;
        }
    }

    private static final class MovieCatalogRowMapper implements RowMapper<MovieCatalogRow> {

        @Override
        public MovieCatalogRow mapRow(ResultSet rs, int rowNum) throws SQLException {
            return new MovieCatalogRow(
                rs.getString("movie_key"),
                rs.getString("title_ko"),
                rs.getString("title_en"),
                defaultText(rs.getString("age_rating"), "ALL"),
                getNullableInt(rs, "runtime_minutes"),
                rs.getObject("release_date", LocalDate.class),
                defaultText(rs.getString("release_state"), "unknown"),
                rs.getBigDecimal("booking_rate"),
                getNullableInt(rs, "box_office_rank"),
                rs.getString("poster_url"),
                getNullableInt(rs, "min_price_amount"),
                rs.getInt("showtime_count"),
                splitCsv(rs.getString("providers")),
                splitCsv(rs.getString("tags"))
            );
        }

        private static Integer getNullableInt(ResultSet rs, String column) throws SQLException {
            int value = rs.getInt(column);
            return rs.wasNull() ? null : value;
        }
    }

    private static List<String> splitCsv(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        return java.util.Arrays.stream(value.split(","))
            .map(String::trim)
            .filter(part -> !part.isBlank())
            .distinct()
            .toList();
    }

    private static String defaultText(String value, String defaultValue) {
        return value == null || value.isBlank() ? defaultValue : value;
    }

    private record QueryParts(
        StringBuilder sql,
        MapSqlParameterSource params
    ) {
    }

    public record MovieCatalogRow(
        String movieKey,
        String titleKo,
        String titleEn,
        String ageRating,
        Integer runtimeMinutes,
        LocalDate releaseDate,
        String releaseState,
        BigDecimal bookingRate,
        Integer boxOfficeRank,
        String posterUrl,
        Integer minPriceAmount,
        int showtimeCount,
        List<String> providers,
        List<String> tags
    ) {
    }

    private record MovieKeyParts(
        String providerCode,
        String externalMovieId,
        String movieTitle
    ) {

        static MovieKeyParts parse(String movieKey) {
            if (movieKey == null || movieKey.isBlank()) {
                throw new IllegalArgumentException("movieKey가 비어 있어.");
            }

            int separatorIndex = movieKey.indexOf(':');
            if (separatorIndex <= 0 || separatorIndex == movieKey.length() - 1) {
                return new MovieKeyParts(null, null, movieKey);
            }

            String prefix = movieKey.substring(0, separatorIndex);
            String value = movieKey.substring(separatorIndex + 1);

            if ("movie".equalsIgnoreCase(prefix)) {
                return new MovieKeyParts(null, null, value);
            }

            return new MovieKeyParts(prefix, value, null);
        }

        boolean hasProviderAndExternalId() {
            return providerCode != null && !providerCode.isBlank() && externalMovieId != null && !externalMovieId.isBlank();
        }
    }
}
