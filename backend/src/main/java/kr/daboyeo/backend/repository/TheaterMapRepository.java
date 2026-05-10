package kr.daboyeo.backend.repository;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class TheaterMapRepository {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public TheaterMapRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<TheaterMapRow> findAllWithCoordinates() {
        String sql = """
            SELECT provider_code, external_theater_id, name, latitude, longitude, address
            FROM theaters
            WHERE latitude IS NOT NULL
              AND longitude IS NOT NULL
            ORDER BY provider_code ASC, name ASC, external_theater_id ASC
            """;
        return jdbcTemplate.query(sql, new TheaterMapRowMapper());
    }

    private static final class TheaterMapRowMapper implements RowMapper<TheaterMapRow> {

        @Override
        public TheaterMapRow mapRow(ResultSet rs, int rowNum) throws SQLException {
            return new TheaterMapRow(
                rs.getString("provider_code"),
                rs.getString("external_theater_id"),
                rs.getString("name"),
                rs.getBigDecimal("latitude"),
                rs.getBigDecimal("longitude"),
                rs.getString("address")
            );
        }
    }

    public record TheaterMapRow(
        String providerCode,
        String externalTheaterId,
        String name,
        BigDecimal latitude,
        BigDecimal longitude,
        String address
    ) {
    }
}
