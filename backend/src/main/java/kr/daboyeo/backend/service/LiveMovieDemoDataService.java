package kr.daboyeo.backend.service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Locale;
import kr.daboyeo.backend.domain.LiveMovieSchedule;
import kr.daboyeo.backend.domain.LiveMovieSearchCriteria;
import org.springframework.stereotype.Component;

@Component
public class LiveMovieDemoDataService {

    public List<LiveMovieSchedule> sampleSchedules(LiveMovieSearchCriteria criteria) {
        return baseSchedules(criteria).stream()
            .filter(schedule -> matchesProvider(criteria, schedule))
            .filter(schedule -> matchesQuery(criteria, schedule))
            .filter(schedule -> matchesFormats(criteria, schedule))
            .filter(schedule -> matchesSeatTypes(criteria, schedule))
            .filter(schedule -> matchesTimeRange(criteria, schedule))
            .toList();
    }

    private List<LiveMovieSchedule> baseSchedules(LiveMovieSearchCriteria criteria) {
        return List.of(
            sample(
                criteria,
                "CGV:demo_dune",
                "Dune Part Two",
                "CGV",
                "CGV",
                "cgv-gangnam",
                "CGV Gangnam",
                "screen-1",
                "IMAX",
                "IMAX",
                List.of("RECLINER"),
                "12",
                "18:20",
                "21:05",
                122,
                92,
                new BigDecimal("1.80"),
                "https://demo.example/cgv/dune"
            ),
            sample(
                criteria,
                "CGV:demo_dune",
                "Dune Part Two",
                "CGV",
                "CGV",
                "cgv-gangnam",
                "CGV Gangnam",
                "screen-2",
                "IMAX",
                "IMAX",
                List.of("RECLINER"),
                "12",
                "08:20",
                "11:05",
                220,
                150,
                new BigDecimal("1.80"),
                "https://demo.example/cgv/dune"
            ),
            sample(
                criteria,
                "CGV:demo_dune",
                "Dune Part Two",
                "CGV",
                "CGV",
                "cgv-gangnam",
                "CGV Gangnam",
                "screen-3",
                "IMAX",
                "IMAX",
                List.of("RECLINER"),
                "12",
                "13:20",
                "16:05",
                220,
                40,
                new BigDecimal("1.80"),
                "https://demo.example/cgv/dune"
            ),
            sample(
                criteria,
                "MEGABOX:demo_inside_out_2",
                "Inside Out 2",
                "MEGA",
                "MEGABOX",
                "megabox-coex",
                "MEGABOX COEX",
                "screen-5",
                "COMFORT",
                "2D",
                List.of("PRIVATE"),
                "all",
                "19:10",
                "20:55",
                140,
                44,
                new BigDecimal("3.40"),
                "https://demo.example/megabox/inside-out-2"
            ),
            sample(
                criteria,
                "MEGABOX:demo_inside_out_2",
                "Inside Out 2",
                "MEGA",
                "MEGABOX",
                "megabox-coex",
                "MEGABOX COEX",
                "screen-6",
                "COMFORT",
                "2D",
                List.of("PRIVATE"),
                "all",
                "09:10",
                "10:55",
                140,
                100,
                new BigDecimal("3.40"),
                "https://demo.example/megabox/inside-out-2"
            ),
            sample(
                criteria,
                "MEGABOX:demo_inside_out_2",
                "Inside Out 2",
                "MEGA",
                "MEGABOX",
                "megabox-coex",
                "MEGABOX COEX",
                "screen-7",
                "COMFORT",
                "2D",
                List.of("PRIVATE"),
                "all",
                "14:10",
                "15:55",
                140,
                20,
                new BigDecimal("3.40"),
                "https://demo.example/megabox/inside-out-2"
            ),
            sample(
                criteria,
                "LOTTE_CINEMA:demo_victory",
                "Victory",
                "LOTTE",
                "LOTTE_CINEMA",
                "lotte-worldtower",
                "LOTTE CINEMA World Tower",
                "screen-8",
                "SUPER PLEX",
                "4DX",
                List.of("CHEF"),
                "12",
                "20:00",
                "22:05",
                180,
                14,
                new BigDecimal("5.10"),
                "https://demo.example/lotte/victory"
            ),
            sample(
                criteria,
                "LOTTE_CINEMA:demo_victory",
                "Victory",
                "LOTTE",
                "LOTTE_CINEMA",
                "lotte-worldtower",
                "LOTTE CINEMA World Tower",
                "screen-9",
                "SUPER PLEX",
                "4DX",
                List.of("CHEF"),
                "12",
                "07:00",
                "09:05",
                180,
                120,
                new BigDecimal("5.10"),
                "https://demo.example/lotte/victory"
            ),
            sample(
                criteria,
                "LOTTE_CINEMA:demo_victory",
                "Victory",
                "LOTTE",
                "LOTTE_CINEMA",
                "lotte-worldtower",
                "LOTTE CINEMA World Tower",
                "screen-10",
                "SUPER PLEX",
                "4DX",
                List.of("CHEF"),
                "12",
                "15:00",
                "17:05",
                180,
                60,
                new BigDecimal("5.10"),
                "https://demo.example/lotte/victory"
            )
        );
    }

    private LiveMovieSchedule sample(
        LiveMovieSearchCriteria criteria,
        String movieKey,
        String movieName,
        String provider,
        String providerCode,
        String theaterId,
        String theaterName,
        String screenId,
        String screenName,
        String formatName,
        List<String> seatTypeTags,
        String ageRating,
        String startTime,
        String endTime,
        Integer totalSeatCount,
        Integer availableSeatCount,
        BigDecimal distanceKm,
        String bookingUrl
    ) {
        return new LiveMovieSchedule(
            movieKey,
            movieName,
            provider,
            providerCode,
            theaterId,
            theaterName,
            screenId,
            screenName,
            formatName,
            seatTypeTags,
            ageRating,
            startTime,
            endTime,
            criteria.date(),
            totalSeatCount,
            availableSeatCount,
            availableSeatCount,
            BigDecimal.ZERO,
            "",
            distanceKm,
            bookingUrl,
            LocalDateTime.of(criteria.date(), LocalTime.of(9, 0))
        );
    }

    private boolean matchesProvider(LiveMovieSearchCriteria criteria, LiveMovieSchedule schedule) {
        if (criteria.providers().isEmpty()) {
            return true;
        }
        return criteria.providers().stream().anyMatch(provider -> {
            String normalized = provider.toUpperCase(Locale.ROOT);
            return normalized.equals(schedule.provider().toUpperCase(Locale.ROOT))
                || normalized.equals(schedule.providerCode().toUpperCase(Locale.ROOT))
                || ("LOTTE".equals(normalized) && "LOTTE_CINEMA".equalsIgnoreCase(schedule.providerCode()))
                || ("MEGABOX".equals(normalized) && "MEGA".equalsIgnoreCase(schedule.provider()))
                || ("MEGA".equals(normalized) && "MEGABOX".equalsIgnoreCase(schedule.providerCode()));
        });
    }

    private boolean matchesQuery(LiveMovieSearchCriteria criteria, LiveMovieSchedule schedule) {
        if (criteria.query().isBlank()) {
            return true;
        }
        String needle = criteria.query().toLowerCase(Locale.ROOT);
        return schedule.movieName().toLowerCase(Locale.ROOT).contains(needle)
            || schedule.theaterName().toLowerCase(Locale.ROOT).contains(needle);
    }

    private boolean matchesFormats(LiveMovieSearchCriteria criteria, LiveMovieSchedule schedule) {
        if (criteria.formats().isEmpty()) {
            return true;
        }
        String haystack = (schedule.formatName() + " " + schedule.screenName()).toUpperCase(Locale.ROOT);
        return criteria.formats().stream()
            .map(value -> value.toUpperCase(Locale.ROOT))
            .anyMatch(haystack::contains);
    }

    private boolean matchesSeatTypes(LiveMovieSearchCriteria criteria, LiveMovieSchedule schedule) {
        if (criteria.seatTypes().isEmpty()) {
            return true;
        }
        String haystack = String.join(" ", schedule.seatTypeTags()).toUpperCase(Locale.ROOT);
        return criteria.seatTypes().stream()
            .map(value -> value.toUpperCase(Locale.ROOT))
            .anyMatch(haystack::contains);
    }

    private boolean matchesTimeRange(LiveMovieSearchCriteria criteria, LiveMovieSchedule schedule) {
        LocalTime start = LocalTime.parse(schedule.startTime());
        return criteria.matchesTime(start);
    }
}
