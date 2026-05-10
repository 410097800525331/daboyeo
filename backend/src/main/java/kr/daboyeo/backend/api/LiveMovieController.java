package kr.daboyeo.backend.api;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Pattern;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Arrays;
import java.util.List;
import kr.daboyeo.backend.domain.SeatState;
import kr.daboyeo.backend.service.LiveMovieService;
import kr.daboyeo.backend.service.LiveMovieService.LiveMovieResponse;
import kr.daboyeo.backend.service.LiveMovieService.MovieCatalogResponse;
import kr.daboyeo.backend.service.LiveMovieService.MovieSchedulesResponse;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/live")
public class LiveMovieController {

    private final LiveMovieService liveMovieService;

    public LiveMovieController(LiveMovieService liveMovieService) {
        this.liveMovieService = liveMovieService;
    }

    @GetMapping("/nearby")
    public LiveMovieResponse nearby(
        @RequestParam
        @DecimalMin(value = "-90.0")
        @DecimalMax(value = "90.0")
        BigDecimal lat,
        @RequestParam
        @DecimalMin(value = "-180.0")
        @DecimalMax(value = "180.0")
        BigDecimal lng,
        @RequestParam(required = false)
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        LocalDate date,
        @RequestParam(required = false)
        @DateTimeFormat(pattern = "HH:mm")
        LocalTime timeStart,
        @RequestParam(required = false)
        @DateTimeFormat(pattern = "HH:mm")
        LocalTime timeEnd,
        @RequestParam(required = false)
        BigDecimal radiusKm,
        @RequestParam(required = false) String providers,
        @RequestParam(required = false) String formats,
        @RequestParam(required = false) String seatTypes,
        @RequestParam(required = false)
        @Pattern(regexp = "all|spacious|comfortable|closing|group|sold_out", flags = Pattern.Flag.CASE_INSENSITIVE, message = "seatState 값이 잘못됐다.")
        String seatState,
        @RequestParam(required = false) String query,
        @RequestParam(required = false) Integer limit
    ) {
        return liveMovieService.findNearby(
            liveMovieService.buildCriteria(
                lat,
                lng,
                date,
                timeStart,
                timeEnd,
                radiusKm,
                parseCsv(providers),
                parseCsv(formats),
                parseCsv(seatTypes),
                seatState == null ? SeatState.ALL : SeatState.fromQueryValue(seatState),
                query,
                limit
            )
        );
    }

    @GetMapping("/movies")
    public MovieCatalogResponse movies(
        @RequestParam(required = false) Integer limit,
        @RequestParam(required = false) String query,
        @RequestParam(required = false) String section,
        @RequestParam(required = false)
        @Pattern(regexp = "all|now|current|playing|released|now_playing|now-playing|coming|coming_soon|coming-soon|scheduled|upcoming|unknown", flags = Pattern.Flag.CASE_INSENSITIVE, message = "releaseState 값이 잘못됐다.")
        String releaseState
    ) {
        return liveMovieService.findPopularMovies(limit, query, section, releaseState);
    }

    @GetMapping("/movies/{movieKey}/schedules")
    public MovieSchedulesResponse schedules(
        @org.springframework.web.bind.annotation.PathVariable String movieKey,
        @RequestParam
        @DecimalMin(value = "-90.0")
        @DecimalMax(value = "90.0")
        BigDecimal lat,
        @RequestParam
        @DecimalMin(value = "-180.0")
        @DecimalMax(value = "180.0")
        BigDecimal lng,
        @RequestParam(required = false)
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        LocalDate date,
        @RequestParam(required = false)
        @DateTimeFormat(pattern = "HH:mm")
        LocalTime timeStart,
        @RequestParam(required = false)
        @DateTimeFormat(pattern = "HH:mm")
        LocalTime timeEnd,
        @RequestParam(required = false)
        BigDecimal radiusKm,
        @RequestParam(required = false) String providers,
        @RequestParam(required = false) String formats,
        @RequestParam(required = false) String seatTypes,
        @RequestParam(required = false)
        @Pattern(regexp = "all|spacious|comfortable|closing|group|sold_out", flags = Pattern.Flag.CASE_INSENSITIVE, message = "seatState 값이 잘못됐다.")
        String seatState,
        @RequestParam(required = false) String query,
        @RequestParam(required = false) Integer limit
    ) {
        return liveMovieService.findMovieSchedules(
            movieKey,
            liveMovieService.buildCriteria(
                lat,
                lng,
                date,
                timeStart,
                timeEnd,
                radiusKm,
                parseCsv(providers),
                parseCsv(formats),
                parseCsv(seatTypes),
                seatState == null ? SeatState.ALL : SeatState.fromQueryValue(seatState),
                query,
                limit
            )
        );
    }

    private List<String> parseCsv(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        return Arrays.stream(value.split(","))
            .map(String::trim)
            .filter(part -> !part.isBlank())
            .toList();
    }
}
