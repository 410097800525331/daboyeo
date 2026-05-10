package kr.daboyeo.backend.repository.recommendation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import kr.daboyeo.backend.domain.recommendation.RecommendationModels.SearchFilters;
import kr.daboyeo.backend.domain.recommendation.RecommendationModels.ShowtimeCandidate;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ResultSetExtractor;

class ShowtimeRecommendationRepositoryTests {

    @Test
    void findUpcomingCandidatesFiltersByUsableStartTime() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        ShowtimeRecommendationRepository repository = new ShowtimeRecommendationRepository(jdbcTemplate);
        LocalDateTime cutoff = LocalDateTime.of(2026, 4, 17, 16, 10);

        repository.findUpcomingCandidates(10, cutoff);

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate).query(
            sql.capture(),
            org.mockito.ArgumentMatchers.<ResultSetExtractor<List<ShowtimeCandidate>>>any(),
            eq(Timestamp.valueOf(cutoff)),
            eq(10)
        );
        assertThat(sql.getValue()).contains("s.starts_at >= ?");
        assertThat(sql.getValue()).doesNotContain("s.show_date >= CURRENT_DATE()");
    }

    @Test
    void countStoredShowtimesReturnsZeroWhenDatabaseCountIsNull() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        ShowtimeRecommendationRepository repository = new ShowtimeRecommendationRepository(jdbcTemplate);
        when(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM showtimes", Integer.class)).thenReturn(null);

        assertThat(repository.countStoredShowtimes()).isZero();
    }

    @Test
    void findUpcomingCandidatesAddsSearchFilterClauses() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        ShowtimeRecommendationRepository repository = new ShowtimeRecommendationRepository(jdbcTemplate);
        LocalDateTime cutoff = LocalDateTime.of(2026, 4, 17, 16, 10);
        SearchFilters filters = new SearchFilters("Gangnam", LocalDate.of(2026, 4, 17), "night", 2);

        repository.findUpcomingCandidates(10, cutoff, filters);

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate).query(
            sql.capture(),
            org.mockito.ArgumentMatchers.<ResultSetExtractor<List<ShowtimeCandidate>>>any(),
            eq(Timestamp.valueOf(LocalDateTime.of(2026, 4, 17, 17, 0))),
            eq("%gangnam%"),
            eq("%gangnam%"),
            eq(Timestamp.valueOf(LocalDateTime.of(2026, 4, 18, 2, 0))),
            eq(2),
            eq(10)
        );
        assertThat(sql.getValue()).contains("LOWER(s.region_name) LIKE ? OR LOWER(s.theater_name) LIKE ?");
        assertThat(sql.getValue()).contains("s.starts_at < ?");
        assertThat(sql.getValue()).contains("s.remaining_seat_count IS NULL OR s.remaining_seat_count >= ?");
        assertThat(sql.getValue()).doesNotContain("s.show_date = ?");
    }

    @Test
    void findUpcomingCandidatesCanPrefilterByCanonicalGenreTags() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        ShowtimeRecommendationRepository repository = new ShowtimeRecommendationRepository(jdbcTemplate);
        LocalDateTime cutoff = LocalDateTime.of(2026, 5, 6, 16, 10);

        repository.findUpcomingCandidates(20, cutoff, null, new LinkedHashSet<>(List.of("genre:family", "music")));

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate).query(
            sql.capture(),
            org.mockito.ArgumentMatchers.<ResultSetExtractor<List<ShowtimeCandidate>>>any(),
            eq("family"),
            eq("music"),
            eq(Timestamp.valueOf(cutoff)),
            eq(20)
        );
        assertThat(sql.getValue()).contains("JOIN movie_tags mt_filter");
        assertThat(sql.getValue()).contains("mt_filter.tag_type = 'genre'");
        assertThat(sql.getValue()).contains("mt_filter.tag_value IN (");
        assertThat(sql.getValue()).contains("?, ?");
    }
}
