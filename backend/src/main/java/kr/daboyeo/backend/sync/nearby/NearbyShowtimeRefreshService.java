package kr.daboyeo.backend.sync.nearby;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import kr.daboyeo.backend.config.CollectorSyncProperties;
import kr.daboyeo.backend.domain.LiveMovieSearchCriteria;
import kr.daboyeo.backend.ingest.CollectorBundleIngestCommand;
import kr.daboyeo.backend.ingest.CollectorBundlePersistenceService;
import kr.daboyeo.backend.sync.bridge.CollectorProvider;
import kr.daboyeo.backend.sync.bridge.PythonCollectorBridge;
import kr.daboyeo.backend.sync.bridge.ShowtimeCollectionRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Service;

@Service
public class NearbyShowtimeRefreshService {

    private static final Logger logger = LoggerFactory.getLogger(NearbyShowtimeRefreshService.class);
    private static final int MEGABOX_NEARBY_TARGET_LIMIT = 8;

    private final CollectorSyncProperties properties;
    private final NearbyTheaterTargetResolver resolver;
    private final NearbyRefreshRepository repository;
    private final PythonCollectorBridge collectorBridge;
    private final CollectorBundlePersistenceService persistenceService;
    private final TaskExecutor taskExecutor;
    private final Clock clock;
    private final Set<String> inFlightKeys = ConcurrentHashMap.newKeySet();
    private final Map<String, CompletableFuture<Void>> requestFutures = new ConcurrentHashMap<>();

    @Autowired
    public NearbyShowtimeRefreshService(
        CollectorSyncProperties properties,
        NearbyTheaterTargetResolver resolver,
        NearbyRefreshRepository repository,
        PythonCollectorBridge collectorBridge,
        CollectorBundlePersistenceService persistenceService,
        @Qualifier("nearbyRefreshTaskExecutor") TaskExecutor taskExecutor
    ) {
        this(properties, resolver, repository, collectorBridge, persistenceService, taskExecutor, Clock.system(ZoneId.of("Asia/Seoul")));
    }

    NearbyShowtimeRefreshService(
        CollectorSyncProperties properties,
        NearbyTheaterTargetResolver resolver,
        NearbyRefreshRepository repository,
        PythonCollectorBridge collectorBridge,
        CollectorBundlePersistenceService persistenceService,
        TaskExecutor taskExecutor,
        Clock clock
    ) {
        this.properties = properties;
        this.resolver = resolver;
        this.repository = repository;
        this.collectorBridge = collectorBridge;
        this.persistenceService = persistenceService;
        this.taskExecutor = taskExecutor;
        this.clock = clock;
    }

    public void requestRefresh(LiveMovieSearchCriteria criteria) {
        startRefresh(criteria);
    }

    public RefreshWaitOutcome requestRefreshAndAwait(LiveMovieSearchCriteria criteria, Duration timeout) {
        RefreshExecution execution = startRefresh(criteria);
        if (execution.skipped()) {
            return RefreshWaitOutcome.SKIPPED;
        }

        long waitMillis = Math.max(0L, timeout == null ? 0L : timeout.toMillis());
        if (waitMillis == 0L) {
            return execution.future().isDone() ? RefreshWaitOutcome.COMPLETED : RefreshWaitOutcome.TIMED_OUT;
        }

        try {
            execution.future().get(waitMillis, TimeUnit.MILLISECONDS);
            return RefreshWaitOutcome.COMPLETED;
        } catch (TimeoutException exception) {
            return RefreshWaitOutcome.TIMED_OUT;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            logger.warn("Nearby refresh wait interrupted for date={}", criteria.date(), exception);
            return RefreshWaitOutcome.FAILED;
        } catch (ExecutionException exception) {
            logger.warn("Nearby refresh wait failed for date={}", criteria.date(), exception.getCause());
            return RefreshWaitOutcome.FAILED;
        }
    }

    private RefreshExecution startRefresh(LiveMovieSearchCriteria criteria) {
        if (!properties.isEnabled() || !properties.getShowtimes().isEnabled() || !properties.getShowtimes().isNearbyRefreshEnabled()) {
            return RefreshExecution.skippedExecution();
        }
        NearbyTheaterTargetResolver.Resolution resolution = resolver.resolve(criteria);
        if (resolution.isEmpty()) {
            return RefreshExecution.skippedExecution();
        }

        String requestKey = refreshRequestKey(criteria.date(), resolution);
        CompletableFuture<Void> created = new CompletableFuture<>();
        CompletableFuture<Void> existing = requestFutures.putIfAbsent(requestKey, created);
        if (existing != null) {
            return RefreshExecution.activeExecution(existing);
        }

        logger.info(
            "Nearby refresh requested date={} lotteCandidates={} megaboxCandidates={} radiusKm={}",
            criteria.date(),
            resolution.lotteEntries().size(),
            resolution.megaboxEntries().size(),
            criteria.radiusKm()
        );

        taskExecutor.execute(() -> {
            try {
                refreshNearby(criteria, resolution);
                created.complete(null);
            } catch (Throwable throwable) {
                created.completeExceptionally(throwable);
            } finally {
                requestFutures.remove(requestKey, created);
            }
        });

        return RefreshExecution.activeExecution(created);
    }

    private void refreshNearby(LiveMovieSearchCriteria criteria, NearbyTheaterTargetResolver.Resolution resolution) {
        try {
            refreshLotte(criteria.date(), resolution.lotteEntries());
        } catch (Exception exception) {
            logger.warn("Nearby refresh failed for provider={} date={}", CollectorProvider.LOTTE_CINEMA, criteria.date(), exception);
        }
        try {
            refreshMegabox(criteria.date(), resolution.megaboxEntries());
        } catch (Exception exception) {
            logger.warn("Nearby refresh failed for provider={} date={}", CollectorProvider.MEGABOX, criteria.date(), exception);
        }
    }

    private void refreshLotte(LocalDate showDate, List<NearbyTheaterTargetResolver.TheaterMapEntry> entries) {
        if (entries.isEmpty()) {
            return;
        }
        List<NearbyRefreshRepository.TheaterSyncMetadata> metadata = repository.findTheaterSyncMetadata(
            CollectorProvider.LOTTE_CINEMA,
            entries.stream().map(NearbyTheaterTargetResolver.TheaterMapEntry::externalTheaterId).toList()
        );
        Map<String, NearbyRefreshRepository.TheaterSyncMetadata> metadataById = metadata.stream()
            .filter(item -> item.cinemaSelector() != null && !item.cinemaSelector().isBlank())
            .collect(java.util.stream.Collectors.toMap(
                NearbyRefreshRepository.TheaterSyncMetadata::externalTheaterId,
                item -> item,
                (left, right) -> left,
                LinkedHashMap::new
            ));
        Map<String, LocalDateTime> latestCollectedAt = repository.findLatestShowtimeCollectedAt(
            CollectorProvider.LOTTE_CINEMA,
            showDate,
            metadataById.keySet()
        );
        List<NearbyRefreshRepository.TheaterSyncMetadata> staleTheaters = new ArrayList<>();
        for (NearbyRefreshRepository.TheaterSyncMetadata item : metadataById.values()) {
            if (!isStale(showDate, latestCollectedAt.get(item.externalTheaterId()))) {
                logger.info("Nearby refresh skipped provider={} theater={} date={} reason=fresh", item.provider(), item.externalTheaterId(), showDate);
                continue;
            }
            if (!acquire(inFlightKey(item.provider(), item.externalTheaterId(), showDate))) {
                logger.info("Nearby refresh skipped provider={} theater={} date={} reason=in_flight", item.provider(), item.externalTheaterId(), showDate);
                continue;
            }
            staleTheaters.add(item);
        }
        if (staleTheaters.isEmpty()) {
            return;
        }
        try {
            for (NearbyRefreshRepository.TheaterSyncMetadata theater : staleTheaters) {
                logger.info(
                    "Nearby refresh theater-first collection starting provider={} theater={} date={} selector={}",
                    CollectorProvider.LOTTE_CINEMA,
                    theater.externalTheaterId(),
                    showDate,
                    theater.cinemaSelector()
                );
                Map<String, Object> bundle = collectorBridge.collectLotteNearbyBundle(showDate, theater.cinemaSelector());
                int movieCount = listOfMaps(bundle.get("movies")).size();
                int scheduleCount = listOfMaps(bundle.get("schedules")).size();
                logger.info(
                    "Nearby refresh theater-first collected provider={} theater={} date={} movies={} schedules={}",
                    CollectorProvider.LOTTE_CINEMA,
                    theater.externalTheaterId(),
                    showDate,
                    movieCount,
                    scheduleCount
                );
                persistCollectedBundle(CollectorProvider.LOTTE_CINEMA, showDate, bundle);
            }
        } finally {
            releaseForTheaters(CollectorProvider.LOTTE_CINEMA, showDate, staleTheaters.stream().map(NearbyRefreshRepository.TheaterSyncMetadata::externalTheaterId).toList());
        }
    }

    private void refreshMegabox(LocalDate showDate, List<NearbyTheaterTargetResolver.TheaterMapEntry> entries) {
        if (entries.isEmpty()) {
            return;
        }
        Map<String, NearbyTheaterTargetResolver.TheaterMapEntry> entriesById = entries.stream()
            .collect(java.util.stream.Collectors.toMap(
                NearbyTheaterTargetResolver.TheaterMapEntry::externalTheaterId,
                entry -> entry,
                (left, right) -> left,
                LinkedHashMap::new
            ));
        List<NearbyRefreshRepository.TheaterSyncMetadata> metadata = repository.findTheaterSyncMetadata(
            CollectorProvider.MEGABOX,
            entries.stream().map(NearbyTheaterTargetResolver.TheaterMapEntry::externalTheaterId).toList()
        );
        Map<String, NearbyRefreshRepository.TheaterSyncMetadata> metadataById = metadata.stream()
            .filter(item -> item.areaCode() != null && !item.areaCode().isBlank())
            .collect(java.util.stream.Collectors.toMap(
                NearbyRefreshRepository.TheaterSyncMetadata::externalTheaterId,
                item -> item,
                (left, right) -> left,
                LinkedHashMap::new
            ));
        Map<String, LocalDateTime> latestCollectedAt = repository.findLatestShowtimeCollectedAt(
            CollectorProvider.MEGABOX,
            showDate,
            metadataById.keySet()
        );
        Map<String, List<NearbyRefreshRepository.TheaterSyncMetadata>> staleAreas = new LinkedHashMap<>();
        for (NearbyRefreshRepository.TheaterSyncMetadata item : metadataById.values()) {
            if (!isStale(showDate, latestCollectedAt.get(item.externalTheaterId()))) {
                logger.info("Nearby refresh skipped provider={} theater={} date={} reason=fresh", item.provider(), item.externalTheaterId(), showDate);
                continue;
            }
            staleAreas.computeIfAbsent(item.areaCode(), unused -> new ArrayList<>()).add(item);
        }
        if (staleAreas.isEmpty()) {
            return;
        }
        Map<String, List<NearbyRefreshRepository.TheaterSyncMetadata>> acquiredAreas = new LinkedHashMap<>();
        for (Map.Entry<String, List<NearbyRefreshRepository.TheaterSyncMetadata>> areaEntry : staleAreas.entrySet()) {
            String areaCode = areaEntry.getKey();
            if (!acquire(inFlightAreaKey(CollectorProvider.MEGABOX, areaCode, showDate))) {
                logger.info("Nearby refresh skipped provider={} areaCode={} date={} reason=in_flight", CollectorProvider.MEGABOX, areaCode, showDate);
                continue;
            }
            acquiredAreas.put(areaCode, areaEntry.getValue());
        }
        if (acquiredAreas.isEmpty()) {
            return;
        }
        try {
            for (Map.Entry<String, List<NearbyRefreshRepository.TheaterSyncMetadata>> areaEntry : acquiredAreas.entrySet()) {
                String areaCode = areaEntry.getKey();
                List<String> allowedTheaterIds = areaEntry.getValue().stream()
                    .map(NearbyRefreshRepository.TheaterSyncMetadata::externalTheaterId)
                    .toList();
                List<NearbyTheaterTargetResolver.TheaterMapEntry> allowedEntries = allowedTheaterIds.stream()
                    .map(entriesById::get)
                    .filter(java.util.Objects::nonNull)
                    .toList();
                logger.info(
                    "Nearby refresh discovery starting provider={} areaCode={} date={} theaters={}",
                    CollectorProvider.MEGABOX,
                    areaCode,
                    showDate,
                    areaEntry.getValue().size()
                );
                List<Map<String, Object>> targets = prioritizeMegaboxTargets(
                    resolveMegaboxTargets(showDate, areaCode, allowedTheaterIds)
                );
                logger.info(
                    "Nearby refresh discovered provider={} areaCode={} date={} matchedTargets={}",
                    CollectorProvider.MEGABOX,
                    areaCode,
                    showDate,
                    targets.size()
                );
                for (Map<String, Object> target : targets) {
                    try {
                        persistCollectedBundle(new ShowtimeCollectionRequest(
                            CollectorProvider.MEGABOX,
                            showDate,
                            null,
                            text(target.get("movie_no")),
                            null,
                            null,
                            text(target.get("area_code"))
                        ), allowedTheaterIds, allowedEntries);
                    } catch (Exception exception) {
                        logger.warn(
                            "Nearby refresh skipped MEGABOX target areaCode={} date={} movieNo={} reason=target_failed",
                            areaCode,
                            showDate,
                            text(target.get("movie_no")),
                            exception
                        );
                    }
                }
            }
        } finally {
            acquiredAreas.keySet().forEach(areaCode -> release(inFlightAreaKey(CollectorProvider.MEGABOX, areaCode, showDate)));
        }
    }

    private List<Map<String, Object>> resolveMegaboxTargets(LocalDate showDate, String areaCode, List<String> allowedTheaterIds) {
        try {
            PythonCollectorBridge.ProviderDiscoveryPayload discovery = collectorBridge.collectMegaboxNearbyDiscovery(showDate, areaCode);
            if (!discovery.targets().isEmpty()) {
                return discovery.targets();
            }
            logger.info(
                "Nearby refresh discovery fallback provider={} areaCode={} date={} reason=no_targets",
                CollectorProvider.MEGABOX,
                areaCode,
                showDate
            );
        } catch (Exception exception) {
            logger.warn(
                "Nearby refresh discovery fallback provider={} areaCode={} date={} reason=discovery_failed",
                CollectorProvider.MEGABOX,
                areaCode,
                showDate,
                exception
            );
        }

        List<String> fallbackMovieIds = repository.findRecentExternalMovieIds(
            CollectorProvider.MEGABOX,
            allowedTheaterIds,
            showDate.minusDays(7),
            showDate,
            12
        );
        if (fallbackMovieIds.isEmpty()) {
            return List.of();
        }
        logger.info(
            "Nearby refresh using fallback targets provider={} areaCode={} date={} fallbackMovieIds={}",
            CollectorProvider.MEGABOX,
            areaCode,
            showDate,
            fallbackMovieIds.size()
        );
        return fallbackMovieIds.stream()
            .map(movieNo -> Map.<String, Object>of(
                "movie_no", movieNo,
                "area_code", areaCode
            ))
            .toList();
    }

    private List<Map<String, Object>> prioritizeMegaboxTargets(List<Map<String, Object>> targets) {
        if (targets == null || targets.isEmpty()) {
            return List.of();
        }
        return targets.stream()
            .filter(target -> !text(target.get("movie_no")).isBlank())
            .limit(MEGABOX_NEARBY_TARGET_LIMIT)
            .toList();
    }

    private void persistCollectedBundle(ShowtimeCollectionRequest request) {
        persistCollectedBundle(request, List.of(), List.of());
    }

    private void persistCollectedBundle(CollectorProvider provider, LocalDate showDate, Map<String, Object> bundle) {
        CollectorBundleIngestCommand.IngestResult result = persistenceService.persist(provider.name(), bundle, false);
        logger.info(
            "Nearby refresh stored provider={} date={} theaters={} screens={} showtimes={}",
            provider,
            showDate,
            result.theaters(),
            result.screens(),
            result.showtimes()
        );
    }

    private void persistCollectedBundle(ShowtimeCollectionRequest request, Collection<String> allowedExternalTheaterIds) {
        persistCollectedBundle(request, allowedExternalTheaterIds, List.of());
    }

    private void persistCollectedBundle(
        ShowtimeCollectionRequest request,
        Collection<String> allowedExternalTheaterIds,
        Collection<NearbyTheaterTargetResolver.TheaterMapEntry> allowedTheaterEntries
    ) {
        Map<String, Object> bundle = collectorBridge.collectShowtimeBundle(request);
        Map<String, Object> scopedBundle = request.provider() == CollectorProvider.MEGABOX
            ? filterMegaboxBundleForTheaters(bundle, allowedExternalTheaterIds, allowedTheaterEntries)
            : bundle;
        CollectorBundleIngestCommand.IngestResult result = persistenceService.persist(request.provider().name(), scopedBundle, false);
        logger.info(
            "Nearby refresh stored provider={} date={} theaters={} screens={} showtimes={}",
            request.provider(),
            request.playDate(),
            result.theaters(),
            result.screens(),
            result.showtimes()
        );
    }

    private Map<String, Object> filterMegaboxBundleForTheaters(
        Map<String, Object> bundle,
        Collection<String> allowedExternalTheaterIds,
        Collection<NearbyTheaterTargetResolver.TheaterMapEntry> allowedTheaterEntries
    ) {
        Set<String> allowedIds = allowedExternalTheaterIds == null
            ? Set.of()
            : allowedExternalTheaterIds.stream()
                .map(NearbyShowtimeRefreshService::text)
                .filter(value -> !value.isBlank())
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        if (allowedIds.isEmpty()) {
            return bundle;
        }
        Map<String, NearbyTheaterTargetResolver.TheaterMapEntry> coordsById = allowedTheaterEntries == null
            ? Map.of()
            : allowedTheaterEntries.stream()
                .collect(java.util.stream.Collectors.toMap(
                    NearbyTheaterTargetResolver.TheaterMapEntry::externalTheaterId,
                    entry -> entry,
                    (left, right) -> left,
                    LinkedHashMap::new
                ));

        List<Map<String, Object>> schedules = listOfMaps(bundle.get("schedules")).stream()
            .filter(schedule -> allowedIds.contains(text(schedule.get("branch_no"))))
            .toList();
        Set<String> retainedMovieIds = schedules.stream()
            .map(schedule -> text(schedule.get("movie_no")))
            .filter(value -> !value.isBlank())
            .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        List<Map<String, Object>> movies = listOfMaps(bundle.get("movies")).stream()
            .filter(movie -> retainedMovieIds.contains(text(movie.get("movie_no")))
                || retainedMovieIds.contains(text(movie.get("representative_movie_no"))))
            .toList();
        List<Map<String, Object>> areas = listOfMaps(bundle.get("areas")).stream()
            .filter(area -> allowedIds.contains(text(area.get("branch_no"))))
            .map(area -> enrichMegaboxArea(area, coordsById.get(text(area.get("branch_no")))))
            .toList();
        List<Map<String, Object>> seatRecords = listOfMaps(bundle.get("seat_records")).stream()
            .filter(seat -> {
                String branchNo = text(seat.get("branch_no"));
                return branchNo.isBlank() || allowedIds.contains(branchNo);
            })
            .toList();

        Map<String, Object> scopedBundle = new LinkedHashMap<>(bundle);
        scopedBundle.put("movies", movies);
        scopedBundle.put("areas", areas);
        scopedBundle.put("schedules", schedules);
        scopedBundle.put("seat_records", seatRecords);
        scopedBundle.put("movie_count", movies.size());
        scopedBundle.put("area_branch_count", areas.size());
        scopedBundle.put("schedule_count", schedules.size());
        scopedBundle.put("seat_count", seatRecords.size());
        return scopedBundle;
    }

    private static Map<String, Object> enrichMegaboxArea(
        Map<String, Object> area,
        NearbyTheaterTargetResolver.TheaterMapEntry theaterEntry
    ) {
        if (theaterEntry == null) {
            return area;
        }
        Map<String, Object> enriched = new LinkedHashMap<>(area);
        enriched.put("latitude", theaterEntry.latitude());
        enriched.put("longitude", theaterEntry.longitude());
        if (text(enriched.get("branch_name")).isBlank()) {
            enriched.put("branch_name", theaterEntry.name());
        }
        return enriched;
    }

    private boolean isStale(LocalDate showDate, LocalDateTime lastCollectedAt) {
        if (lastCollectedAt == null) {
            return true;
        }
        LocalDate today = LocalDate.now(clock);
        long dayOffset = ChronoUnit.DAYS.between(today, showDate);
        Duration ttl = dayOffset <= 0
            ? Duration.ofMinutes(properties.getShowtimes().getNearbyRefreshTodayTtlMinutes())
            : dayOffset == 1
                ? Duration.ofMinutes(properties.getShowtimes().getNearbyRefreshNextDayTtlMinutes())
                : Duration.ofMinutes(properties.getShowtimes().getNearbyRefreshFutureTtlMinutes());
        return lastCollectedAt.isBefore(LocalDateTime.now(clock).minus(ttl));
    }

    private boolean acquire(String key) {
        return inFlightKeys.add(key);
    }

    private void release(String key) {
        inFlightKeys.remove(key);
    }

    private void releaseForTheaters(CollectorProvider provider, LocalDate showDate, Collection<String> externalTheaterIds) {
        for (String externalTheaterId : externalTheaterIds) {
            release(inFlightKey(provider, externalTheaterId, showDate));
        }
    }

    private static String inFlightKey(CollectorProvider provider, String externalTheaterId, LocalDate showDate) {
        return provider.name() + ":" + externalTheaterId + ":" + showDate;
    }

    private static String inFlightAreaKey(CollectorProvider provider, String areaCode, LocalDate showDate) {
        return provider.name() + ":AREA:" + areaCode + ":" + showDate;
    }

    private static String refreshRequestKey(LocalDate showDate, NearbyTheaterTargetResolver.Resolution resolution) {
        List<String> lotteIds = resolution.lotteEntries().stream()
            .map(NearbyTheaterTargetResolver.TheaterMapEntry::externalTheaterId)
            .distinct()
            .sorted()
            .toList();
        List<String> megaboxIds = resolution.megaboxEntries().stream()
            .map(NearbyTheaterTargetResolver.TheaterMapEntry::externalTheaterId)
            .distinct()
            .sorted()
            .toList();
        return showDate + "|LOTTE:" + String.join(",", lotteIds) + "|MEGA:" + String.join(",", megaboxIds);
    }

    private static String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
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

    private record RefreshExecution(
        CompletableFuture<Void> future,
        boolean skipped
    ) {
        private static RefreshExecution skippedExecution() {
            return new RefreshExecution(CompletableFuture.completedFuture(null), true);
        }

        private static RefreshExecution activeExecution(CompletableFuture<Void> future) {
            return new RefreshExecution(future, false);
        }
    }

    public enum RefreshWaitOutcome {
        SKIPPED,
        COMPLETED,
        TIMED_OUT,
        FAILED
    }
}
