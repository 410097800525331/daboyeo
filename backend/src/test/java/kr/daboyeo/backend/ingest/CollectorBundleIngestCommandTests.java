package kr.daboyeo.backend.ingest;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class CollectorBundleIngestCommandTests {

    @Test
    void parsesCompactDateAndTimeValues() {
        assertThat(CollectorBundleIngestCommand.parseDate("20260423")).isEqualTo(LocalDate.of(2026, 4, 23));
        assertThat(CollectorBundleIngestCommand.parseDate("2026-02-04 오전 12:00:00")).isEqualTo(LocalDate.of(2026, 2, 4));
        assertThat(CollectorBundleIngestCommand.parseDate("2026/02/04 00:00:00")).isEqualTo(LocalDate.of(2026, 2, 4));
        assertThat(CollectorBundleIngestCommand.parseTime("0915")).isEqualTo(LocalTime.of(9, 15));
        assertThat(CollectorBundleIngestCommand.parseTime("09:15")).isEqualTo(LocalTime.of(9, 15));
        assertThat(CollectorBundleIngestCommand.parseTime("2400")).isEqualTo(LocalTime.of(0, 0));
        assertThat(CollectorBundleIngestCommand.parseShowtimeTime("2609").dayOffset()).isEqualTo(1);
        assertThat(CollectorBundleIngestCommand.parseShowtimeTime("24:17").time()).isEqualTo(LocalTime.of(0, 17));
    }

    @Test
    void normalizesCgvBundleIntoSharedRows() {
        CollectorBundleIngestCommand.NormalizedBundle bundle = CollectorBundleIngestCommand.normalizeBundle(
            "CGV",
            Map.of(
                "movies", List.of(Map.of(
                    "movie_no", "20042000",
                    "movie_name", "테스트 영화",
                    "age_rating_code", "15",
                    "runtime_minutes", "120",
                    "booking_rate", "12.3",
                    "raw", Map.of("movie_no", "20042000")
                )),
                "sites", List.of(Map.of(
                    "site_no", "0013",
                    "site_name", "CGV 강남",
                    "region_code", "01",
                    "region_name", "서울",
                    "raw", Map.of("site_no", "0013")
                )),
                "schedules", List.of(Map.ofEntries(
                    Map.entry("site_no", "0013"),
                    Map.entry("site_name", "CGV 강남"),
                    Map.entry("movie_no", "20042000"),
                    Map.entry("movie_name", "테스트 영화"),
                    Map.entry("screen_no", "02"),
                    Map.entry("screen_name", "IMAX관"),
                    Map.entry("screen_sequence", "3"),
                    Map.entry("screening_date", "20260423"),
                    Map.entry("start_time", "1030"),
                    Map.entry("end_time", "1230"),
                    Map.entry("format_name", "IMAX"),
                    Map.entry("screen_grade_name", "IMAX"),
                    Map.entry("total_seat_count", "100"),
                    Map.entry("available_seat_count", "35"),
                    Map.entry("booking_key", Map.of("site_no", "0013")),
                    Map.entry("raw", Map.of("screen_no", "02"))
                ))
            )
        );

        assertThat(bundle.movies()).hasSize(1);
        assertThat(bundle.theaters()).hasSize(1);
        assertThat(bundle.screens()).hasSize(1);
        assertThat(bundle.showtimes()).hasSize(1);

        CollectorBundleIngestCommand.ShowtimeRow showtime = bundle.showtimes().get(0);
        assertThat(showtime.externalShowtimeKey()).isEqualTo("CGV:0013:2026-04-23:02:3:20042000");
        assertThat(showtime.startsAt()).isEqualTo(LocalDateTime.of(2026, 4, 23, 10, 30));
        assertThat(showtime.endsAt()).isEqualTo(LocalDateTime.of(2026, 4, 23, 12, 30));
        assertThat(showtime.soldSeatCount()).isEqualTo(65);
        assertThat(showtime.seatOccupancyRate()).isEqualByComparingTo(new BigDecimal("0.650"));
    }

    @Test
    void normalizesPostMidnightCgvTimesAcrossDateBoundary() {
        CollectorBundleIngestCommand.NormalizedBundle bundle = CollectorBundleIngestCommand.normalizeBundle(
            "CGV",
            Map.of(
                "movies", List.of(Map.of(
                    "movie_no", "30001089",
                    "movie_name", "Late Movie",
                    "raw", Map.of("movie_no", "30001089")
                )),
                "sites", List.of(Map.of(
                    "site_no", "0056",
                    "site_name", "CGV Gangnam",
                    "raw", Map.of("site_no", "0056")
                )),
                "schedules", List.of(Map.ofEntries(
                    Map.entry("site_no", "0056"),
                    Map.entry("site_name", "CGV Gangnam"),
                    Map.entry("movie_no", "30001089"),
                    Map.entry("movie_name", "Late Movie"),
                    Map.entry("screen_no", "001"),
                    Map.entry("screen_name", "1"),
                    Map.entry("screen_sequence", "8"),
                    Map.entry("screening_date", "20260429"),
                    Map.entry("start_time", "2400"),
                    Map.entry("end_time", "2609"),
                    Map.entry("total_seat_count", "144"),
                    Map.entry("available_seat_count", "144"),
                    Map.entry("raw", Map.of("scnsrtTm", "2400"))
                ))
            )
        );

        CollectorBundleIngestCommand.ShowtimeRow showtime = bundle.showtimes().get(0);
        assertThat(showtime.startsAt()).isEqualTo(LocalDateTime.of(2026, 4, 30, 0, 0));
        assertThat(showtime.endsAt()).isEqualTo(LocalDateTime.of(2026, 4, 30, 2, 9));
        assertThat(showtime.startTimeRaw()).isEqualTo("2400");
        assertThat(showtime.endTimeRaw()).isEqualTo("2609");
    }

    @Test
    void normalizesPostMidnightLotteEndTimesAcrossDateBoundary() {
        CollectorBundleIngestCommand.NormalizedBundle bundle = CollectorBundleIngestCommand.normalizeBundle(
            "LOTTE_CINEMA",
            Map.of(
                "movies", List.of(Map.of(
                    "movie_no", "23816",
                    "movie_name", "Late Lotte",
                    "raw", Map.of("movie_no", "23816")
                )),
                "cinemas", List.of(Map.of(
                    "cinema_id", "1013",
                    "cinema_name", "Gasan",
                    "raw", Map.of("cinema_id", "1013")
                )),
                "schedules", List.of(Map.ofEntries(
                    Map.entry("movie_no", "23816"),
                    Map.entry("movie_name", "Late Lotte"),
                    Map.entry("cinema_id", "1013"),
                    Map.entry("cinema_name", "Gasan"),
                    Map.entry("screen_id", "101302"),
                    Map.entry("screen_name", "2"),
                    Map.entry("play_date", "2026-04-27"),
                    Map.entry("play_sequence", "6"),
                    Map.entry("start_time", "22:10"),
                    Map.entry("end_time", "24:17"),
                    Map.entry("total_seat_count", 142),
                    Map.entry("remaining_seat_count", 2),
                    Map.entry("booked_seat_count", 140),
                    Map.entry("raw", Map.of("EndTime", "24:17"))
                ))
            )
        );

        CollectorBundleIngestCommand.ShowtimeRow showtime = bundle.showtimes().get(0);
        assertThat(showtime.startsAt()).isEqualTo(LocalDateTime.of(2026, 4, 27, 22, 10));
        assertThat(showtime.endsAt()).isEqualTo(LocalDateTime.of(2026, 4, 28, 0, 17));
        assertThat(showtime.endTimeRaw()).isEqualTo("24:17");
    }

    @Test
    void normalizesLotteBundleIntoSharedRows() {
        CollectorBundleIngestCommand.NormalizedBundle bundle = CollectorBundleIngestCommand.normalizeBundle(
            "LOTTE",
            Map.of(
                "movies", List.of(Map.of(
                    "movie_no", "L100",
                    "movie_name", "롯데 영화",
                    "age_rating", "12세이상관람가",
                    "release_date", "2026-04-01",
                    "booking_rate", "8.7",
                    "raw", Map.of("movie_no", "L100")
                )),
                "cinemas", List.of(Map.of(
                    "cinema_id", "1|101|0001",
                    "cinema_name", "롯데시네마 월드타워",
                    "cinema_area_code", "SEOUL",
                    "cinema_area_name", "서울",
                    "latitude", "37.5130",
                    "longitude", "127.1047",
                    "raw", Map.of("cinema_id", "1|101|0001")
                )),
                "schedules", List.of(Map.ofEntries(
                    Map.entry("movie_no", "L100"),
                    Map.entry("movie_name", "롯데 영화"),
                    Map.entry("cinema_id", "1|101|0001"),
                    Map.entry("cinema_name", "롯데시네마 월드타워"),
                    Map.entry("screen_id", "7"),
                    Map.entry("screen_name", "수퍼플렉스"),
                    Map.entry("screen_division_name", "수퍼플렉스"),
                    Map.entry("play_date", "20260423"),
                    Map.entry("play_sequence", "5"),
                    Map.entry("start_time", "1840"),
                    Map.entry("end_time", "2045"),
                    Map.entry("total_seat_count", 120),
                    Map.entry("remaining_seat_count", 44),
                    Map.entry("booked_seat_count", 76),
                    Map.entry("booking_available", "Y"),
                    Map.entry("booking_key", Map.of("cinema_id", "1|101|0001")),
                    Map.entry("raw", Map.of("play_sequence", "5"))
                ))
            )
        );

        assertThat(bundle.theaters()).hasSize(1);
        assertThat(bundle.screens()).hasSize(1);
        assertThat(bundle.showtimes()).hasSize(1);

        CollectorBundleIngestCommand.ShowtimeRow showtime = bundle.showtimes().get(0);
        assertThat(showtime.externalShowtimeKey()).isEqualTo("LOTTE_CINEMA:1|101|0001:2026-04-23:7:5:L100");
        assertThat(showtime.startsAt()).isEqualTo(LocalDateTime.of(2026, 4, 23, 18, 40));
        assertThat(showtime.soldSeatCount()).isEqualTo(76);
        assertThat(showtime.remainingSeatSource()).isEqualTo("derived");
        assertThat(showtime.regionName()).isEqualTo("서울");
    }

    @Test
    void normalizesMegaboxBundleIntoSharedRows() {
        CollectorBundleIngestCommand.NormalizedBundle bundle = CollectorBundleIngestCommand.normalizeBundle(
            "MEGA",
            Map.of(
                "movies", List.of(Map.of(
                    "movie_no", "240001",
                    "representative_movie_no", "R240001",
                    "movie_name", "메가 영화",
                    "box_office_rank", "3",
                    "raw", Map.of("movie_no", "240001")
                )),
                "areas", List.of(Map.of(
                    "branch_no", "1372",
                    "branch_name", "메가박스 코엑스",
                    "area_code", "11",
                    "area_name", "서울",
                    "raw", Map.of("branch_no", "1372")
                )),
                "schedules", List.of(Map.ofEntries(
                    Map.entry("movie_no", "240001"),
                    Map.entry("movie_name", "메가 영화"),
                    Map.entry("branch_no", "1372"),
                    Map.entry("branch_name", "메가박스 코엑스"),
                    Map.entry("theater_no", "8"),
                    Map.entry("screen_name", "Dolby Cinema"),
                    Map.entry("screen_type", "DOLBY CINEMA"),
                    Map.entry("play_date", "20260423"),
                    Map.entry("start_time", "2110"),
                    Map.entry("end_time", "2320"),
                    Map.entry("play_sequence", "7"),
                    Map.entry("play_schedule_no", "SCHD12345"),
                    Map.entry("remaining_seat_count", "28"),
                    Map.entry("total_seat_count", "150"),
                    Map.entry("booking_available", "Y"),
                    Map.entry("booking_url", "https://www.megabox.co.kr/example"),
                    Map.entry("raw", Map.of("play_schedule_no", "SCHD12345"))
                ))
            )
        );

        assertThat(bundle.theaters()).hasSize(1);
        assertThat(bundle.screens()).hasSize(1);
        assertThat(bundle.showtimes()).hasSize(1);

        CollectorBundleIngestCommand.ShowtimeRow showtime = bundle.showtimes().get(0);
        assertThat(showtime.externalShowtimeKey()).isEqualTo("MEGABOX:SCHD12345");
        assertThat(showtime.startsAt()).isEqualTo(LocalDateTime.of(2026, 4, 23, 21, 10));
        assertThat(showtime.remainingSeatCount()).isEqualTo(28);
        assertThat(showtime.soldSeatCount()).isEqualTo(122);
        assertThat(showtime.bookingUrl()).isEqualTo("https://www.megabox.co.kr/example");
    }
}
