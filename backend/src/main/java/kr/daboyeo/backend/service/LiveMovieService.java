package kr.daboyeo.backend.service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import kr.daboyeo.backend.domain.LiveMovieSchedule;
import kr.daboyeo.backend.domain.LiveMovieSearchCriteria;
import kr.daboyeo.backend.domain.SeatState;
import kr.daboyeo.backend.repository.LiveMovieRepository;
import kr.daboyeo.backend.security.PortfolioAccessGate;
import kr.daboyeo.backend.sync.nearby.NearbyShowtimeRefreshService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;

@Service
public class LiveMovieService {

    private static final Logger logger = LoggerFactory.getLogger(LiveMovieService.class);
    private static final String PENDING_WARNING = "nearby showtimes are still being collected for this area. retry shortly.";
    private static final String NO_RESULTS_WARNING = "no nearby showtimes are available for the selected conditions yet.";
    private static final String SCHEDULES_UNAVAILABLE_WARNING = "showtime details are not available for the selected movie yet.";
    private static final String DATABASE_FAILURE_WARNING = "database lookup failed.";
    private static final String REFRESH_FAILURE_WARNING = "nearby refresh failed before data became available. retry shortly.";

    private final LiveMovieRepository repository;
    private final SeatStateCalculator seatStateCalculator;
    private final LiveMovieDemoDataService demoDataService;
    private final NearbyShowtimeRefreshService nearbyShowtimeRefreshService;
    private final boolean liveFallbackEnabled;
    private final boolean publicNearbyRefreshEnabled;
    private final Duration nearbyRefreshWaitTimeout;
    private final Clock clock;

    @Autowired
    public LiveMovieService(
        LiveMovieRepository repository,
        SeatStateCalculator seatStateCalculator,
        LiveMovieDemoDataService demoDataService,
        NearbyShowtimeRefreshService nearbyShowtimeRefreshService,
        PortfolioAccessGate accessGate,
        @Value("${daboyeo.demo.live-fallback-enabled:true}") boolean liveFallbackEnabled,
        @Value("${daboyeo.sync.showtimes.nearby-refresh-wait-millis:2500}") long nearbyRefreshWaitMillis
    ) {
        this(
            repository,
            seatStateCalculator,
            demoDataService,
            nearbyShowtimeRefreshService,
            liveFallbackEnabled,
            accessGate.publicNearbyRefreshEnabled(),
            Duration.ofMillis(Math.max(0L, nearbyRefreshWaitMillis)),
            Clock.system(ZoneId.of("Asia/Seoul"))
        );
    }

    LiveMovieService(
        LiveMovieRepository repository,
        SeatStateCalculator seatStateCalculator,
        LiveMovieDemoDataService demoDataService,
        NearbyShowtimeRefreshService nearbyShowtimeRefreshService,
        boolean liveFallbackEnabled,
        Duration nearbyRefreshWaitTimeout,
        Clock clock
    ) {
        this(
            repository,
            seatStateCalculator,
            demoDataService,
            nearbyShowtimeRefreshService,
            liveFallbackEnabled,
            true,
            nearbyRefreshWaitTimeout,
            clock
        );
    }

    LiveMovieService(
        LiveMovieRepository repository,
        SeatStateCalculator seatStateCalculator,
        LiveMovieDemoDataService demoDataService,
        NearbyShowtimeRefreshService nearbyShowtimeRefreshService,
        boolean liveFallbackEnabled,
        boolean publicNearbyRefreshEnabled,
        Duration nearbyRefreshWaitTimeout,
        Clock clock
    ) {
        this.repository = repository;
        this.seatStateCalculator = seatStateCalculator;
        this.demoDataService = demoDataService;
        this.nearbyShowtimeRefreshService = nearbyShowtimeRefreshService;
        this.liveFallbackEnabled = liveFallbackEnabled;
        this.publicNearbyRefreshEnabled = publicNearbyRefreshEnabled;
        this.nearbyRefreshWaitTimeout = nearbyRefreshWaitTimeout == null ? Duration.ZERO : nearbyRefreshWaitTimeout;
        this.clock = clock;
    }

    public LiveMovieResponse findNearby(LiveMovieSearchCriteria criteria) {
        try {
            List<LiveMovieScheduleItem> results = findNearbyItems(criteria);
            if (!results.isEmpty() && hasExpectedRefreshProviderCoverage(results, criteria)) {
                triggerNearbyRefresh(criteria);
                return nearbyResponse(criteria, results, true, null, false);
            }

            NearbyShowtimeRefreshService.RefreshWaitOutcome refreshOutcome = triggerNearbyRefreshAndAwait(criteria);
            List<LiveMovieScheduleItem> refreshedResults = refreshOutcome == NearbyShowtimeRefreshService.RefreshWaitOutcome.SKIPPED
                ? results
                : findNearbyItems(criteria);

            if (!refreshedResults.isEmpty()) {
                if (hasExpectedRefreshProviderCoverage(refreshedResults, criteria)) {
                    return nearbyResponse(criteria, refreshedResults, true, null, false);
                }
                return nearbyResponse(
                    criteria,
                    refreshedResults,
                    true,
                    warningForPartialNearbyResults(refreshedResults, criteria),
                    true
                );
            }

            return nearbyResponse(
                criteria,
                List.of(),
                true,
                warningForNearbyEmpty(refreshOutcome),
                refreshOutcome == NearbyShowtimeRefreshService.RefreshWaitOutcome.TIMED_OUT
            );
        } catch (DataAccessException exception) {
            return nearbyResponse(criteria, List.of(), false, DATABASE_FAILURE_WARNING, false);
        }
    }

    public MovieSchedulesResponse findMovieSchedules(String movieKey, LiveMovieSearchCriteria criteria) {
        try {
            List<LiveMovieScheduleItem> items = toItems(repository.findMovieSchedules(movieKey, criteria), criteria);
            if (items.isEmpty()) {
                return schedulesResponse(movieKey, criteria, List.of(), true, SCHEDULES_UNAVAILABLE_WARNING, false);
            }
            return schedulesResponse(movieKey, criteria, items, true, null, false);
        } catch (DataAccessException exception) {
            return schedulesResponse(movieKey, criteria, List.of(), false, DATABASE_FAILURE_WARNING, false);
        }
    }

    public LiveMovieSearchCriteria buildCriteria(
        BigDecimal lat,
        BigDecimal lng,
        java.time.LocalDate date,
        java.time.LocalTime timeStart,
        java.time.LocalTime timeEnd,
        BigDecimal radiusKm,
        List<String> providers,
        List<String> formats,
        List<String> seatTypes,
        SeatState seatState,
        String query,
        Integer limit
    ) {
        return LiveMovieSearchCriteria.of(
            lat,
            lng,
            date,
            timeStart,
            timeEnd,
            radiusKm,
            providers,
            formats,
            seatTypes,
            seatState,
            query,
            limit,
            clock
        );
    }

    private void triggerNearbyRefresh(LiveMovieSearchCriteria criteria) {
        if (!publicNearbyRefreshEnabled) {
            return;
        }
        try {
            nearbyShowtimeRefreshService.requestRefresh(criteria);
        } catch (RuntimeException exception) {
            logger.warn("Nearby refresh request failed for date={} lat={} lng={}", criteria.date(), criteria.lat(), criteria.lng(), exception);
        }
    }

    private NearbyShowtimeRefreshService.RefreshWaitOutcome triggerNearbyRefreshAndAwait(LiveMovieSearchCriteria criteria) {
        if (!publicNearbyRefreshEnabled) {
            return NearbyShowtimeRefreshService.RefreshWaitOutcome.SKIPPED;
        }
        try {
            return nearbyShowtimeRefreshService.requestRefreshAndAwait(criteria, nearbyRefreshWaitTimeout);
        } catch (RuntimeException exception) {
            logger.warn("Nearby refresh wait request failed for date={} lat={} lng={}", criteria.date(), criteria.lat(), criteria.lng(), exception);
            return NearbyShowtimeRefreshService.RefreshWaitOutcome.FAILED;
        }
    }

    private List<LiveMovieScheduleItem> findNearbyItems(LiveMovieSearchCriteria criteria) {
        return keepNearestTheaterPerProvider(toItems(repository.findNearbySchedules(criteria), criteria));
    }

    private List<LiveMovieScheduleItem> keepNearestTheaterPerProvider(List<LiveMovieScheduleItem> items) {
        if (items.isEmpty()) {
            return items;
        }

        Map<String, LiveMovieScheduleItem> nearestByProvider = new LinkedHashMap<>();
        for (LiveMovieScheduleItem item : items) {
            nearestByProvider.putIfAbsent(normalizeProviderValue(item.provider()), item);
        }

        return items.stream()
            .filter(item -> sameTheater(item, nearestByProvider.get(normalizeProviderValue(item.provider()))))
            .toList();
    }

    private boolean sameTheater(LiveMovieScheduleItem left, LiveMovieScheduleItem right) {
        if (left == null || right == null) {
            return false;
        }
        if (hasText(left.provider_code()) && hasText(left.theater_id()) && hasText(right.provider_code()) && hasText(right.theater_id())) {
            return left.provider_code().equalsIgnoreCase(right.provider_code())
                && left.theater_id().equalsIgnoreCase(right.theater_id());
        }
        return normalizeText(left.provider()).equals(normalizeText(right.provider()))
            && normalizeText(left.theater_name()).equals(normalizeText(right.theater_name()));
    }

    private String normalizeText(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private List<LiveMovieScheduleItem> toItems(List<LiveMovieSchedule> schedules, LiveMovieSearchCriteria criteria) {
        return schedules.stream()
            .filter(schedule -> seatStateCalculator.matchesFilter(criteria.seatState(), schedule.totalSeatCount(), schedule.availableSeatCount()))
            .map(this::toItem)
            .toList();
    }

    private String warningForNearbyEmpty(NearbyShowtimeRefreshService.RefreshWaitOutcome outcome) {
        return switch (outcome) {
            case TIMED_OUT -> PENDING_WARNING;
            case FAILED -> REFRESH_FAILURE_WARNING;
            case COMPLETED, SKIPPED -> NO_RESULTS_WARNING;
        };
    }

    private String warningForPartialNearbyResults(List<LiveMovieScheduleItem> results, LiveMovieSearchCriteria criteria) {
        Set<String> missingProviders = expectedRefreshBackedProviders(criteria);
        results.stream()
            .map(item -> normalizeProviderValue(item.provider()))
            .forEach(missingProviders::remove);
        if (missingProviders.isEmpty()) {
            return null;
        }
        String providerLabels = String.join(", ", missingProviders);
        return providerLabels + " showtimes are still being collected for this area. retry shortly.";
    }

    private boolean hasExpectedRefreshProviderCoverage(List<LiveMovieScheduleItem> results, LiveMovieSearchCriteria criteria) {
        Set<String> expectedProviders = expectedRefreshBackedProviders(criteria);
        if (expectedProviders.isEmpty()) {
            return true;
        }
        results.stream()
            .map(item -> normalizeProviderValue(item.provider()))
            .forEach(expectedProviders::remove);
        return expectedProviders.isEmpty();
    }

    private Set<String> expectedRefreshBackedProviders(LiveMovieSearchCriteria criteria) {
        Set<String> selectedProviders = new LinkedHashSet<>();
        if (criteria.providers().isEmpty()) {
            selectedProviders.add("CGV");
            selectedProviders.add("LOTTE");
            selectedProviders.add("MEGA");
            return selectedProviders;
        }
        criteria.providers().stream()
            .map(this::normalizeProviderValue)
            .filter(value -> value.equals("CGV") || value.equals("LOTTE") || value.equals("MEGA"))
            .forEach(selectedProviders::add);
        return selectedProviders;
    }

    private String normalizeProviderValue(String provider) {
        String normalized = provider == null ? "" : provider.trim().toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "LOTTE_CINEMA" -> "LOTTE";
            case "MEGABOX" -> "MEGA";
            default -> normalized;
        };
    }

    private LiveMovieScheduleItem toItem(LiveMovieSchedule schedule) {
        SeatState seatState = seatStateCalculator.resolve(schedule.totalSeatCount(), schedule.availableSeatCount());
        BigDecimal seatRatio = seatStateCalculator.calculateRatio(schedule.totalSeatCount(), schedule.availableSeatCount());

        return new LiveMovieScheduleItem(
            schedule.movieKey(),
            schedule.movieName(),
            schedule.provider(),
            schedule.providerCode(),
            schedule.theaterId(),
            schedule.theaterName(),
            schedule.screenId(),
            schedule.screenName(),
            schedule.formatName(),
            schedule.seatTypeTags(),
            schedule.ageRating(),
            schedule.startTime(),
            schedule.endTime(),
            schedule.showDate().toString(),
            schedule.totalSeatCount(),
            schedule.availableSeatCount(),
            schedule.remainingSeatCount(),
            seatRatio,
            seatState.name().toLowerCase(),
            schedule.distanceKm() == null ? BigDecimal.ZERO : schedule.distanceKm().setScale(2, java.math.RoundingMode.HALF_UP),
            schedule.bookingUrl(),
            schedule.updatedAt() == null ? OffsetDateTime.now(clock).toString() : schedule.updatedAt().atOffset(OffsetDateTime.now(clock).getOffset()).toString()
        );
    }

    private LiveMovieResponse nearbyResponse(
        LiveMovieSearchCriteria criteria,
        List<LiveMovieScheduleItem> results,
        boolean databaseAvailable,
        String warning,
        boolean pendingRefresh
    ) {
        return new LiveMovieResponse(
            new LiveMovieSearchMeta(
                criteria.lat(),
                criteria.lng(),
                criteria.date().toString(),
                criteria.timeStart().toString(),
                criteria.timeEnd().toString(),
                criteria.radiusKm(),
                results.size(),
                databaseAvailable,
                pendingRefresh,
                warning
            ),
            results
        );
    }

    private MovieSchedulesResponse schedulesResponse(
        String movieKey,
        LiveMovieSearchCriteria criteria,
        List<LiveMovieScheduleItem> items,
        boolean databaseAvailable,
        String warning,
        boolean pendingRefresh
    ) {
        List<TheaterScheduleGroup> theaters = items.stream()
            .collect(java.util.stream.Collectors.groupingBy(
                item -> item.provider() + "::" + item.theater_name(),
                java.util.LinkedHashMap::new,
                java.util.stream.Collectors.toList()
            ))
            .values().stream()
            .map(group -> {
                LiveMovieScheduleItem first = group.get(0);
                List<ScheduleCard> schedules = group.stream()
                    .sorted(Comparator.comparing(LiveMovieScheduleItem::start_time))
                    .map(item -> new ScheduleCard(
                        item.start_time(),
                        item.end_time(),
                        item.format_name(),
                        item.available_seat_count(),
                        item.total_seat_count(),
                        item.seat_state(),
                        item.booking_url()
                    ))
                    .toList();

                return new TheaterScheduleGroup(
                    first.provider(),
                    first.provider_code(),
                    first.theater_id(),
                    first.theater_name(),
                    first.distance_km(),
                    schedules
                );
            })
            .sorted(Comparator.comparing(TheaterScheduleGroup::theater_name).thenComparing(TheaterScheduleGroup::provider))
            .toList();

        MovieSummary movie = items.isEmpty()
            ? new MovieSummary(movieKey, null, null)
            : new MovieSummary(movieKey, items.get(0).movie_name(), items.get(0).age_rating());

        return new MovieSchedulesResponse(
            new LiveMovieSearchMeta(
                criteria.lat(),
                criteria.lng(),
                criteria.date().toString(),
                criteria.timeStart().toString(),
                criteria.timeEnd().toString(),
                criteria.radiusKm(),
                items.size(),
                databaseAvailable,
                pendingRefresh,
                warning
            ),
            movie,
            theaters
        );
    }

    public record LiveMovieResponse(
        LiveMovieSearchMeta search,
        List<LiveMovieScheduleItem> results
    ) {
    }

    public record LiveMovieSearchMeta(
        BigDecimal lat,
        BigDecimal lng,
        String date,
        String timeStart,
        String timeEnd,
        BigDecimal radiusKm,
        int resultCount,
        boolean databaseAvailable,
        boolean pendingRefresh,
        String warning
    ) {
    }

    public record LiveMovieScheduleItem(
        String movie_key,
        String movie_name,
        String provider,
        String provider_code,
        String theater_id,
        String theater_name,
        String screen_id,
        String screen_name,
        String format_name,
        List<String> seat_type_tags,
        String age_rating,
        String start_time,
        String end_time,
        String show_date,
        Integer total_seat_count,
        Integer available_seat_count,
        Integer remaining_seat_count,
        BigDecimal seat_ratio,
        String seat_state,
        BigDecimal distance_km,
        String booking_url,
        String updated_at
    ) {
    }

    public record MovieSchedulesResponse(
        LiveMovieSearchMeta search,
        MovieSummary movie,
        List<TheaterScheduleGroup> theaters
    ) {
    }

    public record MovieSummary(
        String movie_key,
        String movie_name,
        String age_rating
    ) {
    }

    public record TheaterScheduleGroup(
        String provider,
        String provider_code,
        String theater_id,
        String theater_name,
        BigDecimal distance_km,
        List<ScheduleCard> schedules
    ) {
    }

    public record ScheduleCard(
        String start_time,
        String end_time,
        String format_name,
        Integer available_seat_count,
        Integer total_seat_count,
        String seat_state,
        String booking_url
    ) {
    }
}
