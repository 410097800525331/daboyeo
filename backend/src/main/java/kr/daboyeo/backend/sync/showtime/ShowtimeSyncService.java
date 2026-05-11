package kr.daboyeo.backend.sync.showtime;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import kr.daboyeo.backend.config.CollectorSyncProperties;
import kr.daboyeo.backend.ingest.CollectorBundleIngestCommand;
import kr.daboyeo.backend.ingest.CollectorBundlePersistenceService;
import kr.daboyeo.backend.sync.bridge.CollectorProvider;
import kr.daboyeo.backend.sync.bridge.PythonCollectorBridge;
import kr.daboyeo.backend.sync.bridge.ShowtimeCollectionRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class ShowtimeSyncService {

    private static final Logger logger = LoggerFactory.getLogger(ShowtimeSyncService.class);

    private final CollectorSyncProperties properties;
    private final PythonCollectorBridge collectorBridge;
    private final CollectorBundlePersistenceService persistenceService;
    private final ShowtimeCleanupService cleanupService;
    private final AtomicBoolean running = new AtomicBoolean(false);

    public ShowtimeSyncService(
        CollectorSyncProperties properties,
        PythonCollectorBridge collectorBridge,
        CollectorBundlePersistenceService persistenceService,
        ShowtimeCleanupService cleanupService
    ) {
        this.properties = properties;
        this.collectorBridge = collectorBridge;
        this.persistenceService = persistenceService;
        this.cleanupService = cleanupService;
    }

    public ShowtimeSyncRunResult syncDailyShowtimes() {
        return syncShowtimes(
            "scheduled",
            properties.getShowtimes().getDateOffsetDays().size(),
            properties.getShowtimes().isCleanupEnabled(),
            0
        );
    }

    public ShowtimeSyncRunResult syncEntryShowtimes() {
        return syncShowtimes(
            "entry",
            Math.max(1, properties.getShowtimes().getEntryRefreshMaxDates()),
            false,
            Math.max(0, properties.getShowtimes().getEntryRefreshMaxSchedulesPerBundle())
        );
    }

    private ShowtimeSyncRunResult syncShowtimes(
        String trigger,
        int maxDateCount,
        boolean cleanupExpired,
        int maxSchedulesPerBundle
    ) {
        if (!properties.isEnabled()) {
            logger.info("Showtime sync skipped because daboyeo.sync.enabled=false.");
            return ShowtimeSyncRunResult.skipped("disabled");
        }
        if (!properties.getShowtimes().isEnabled()) {
            logger.info("Showtime sync skipped because daboyeo.sync.showtimes.enabled=false.");
            return ShowtimeSyncRunResult.skipped("disabled");
        }
        if (!running.compareAndSet(false, true)) {
            logger.info("Showtime sync is already running. Skipping overlapping trigger.");
            return ShowtimeSyncRunResult.skipped("running");
        }

        try {
            LocalDate baseDate = LocalDate.now(ZoneId.of(properties.getTimezone()));
            List<Integer> offsets = limitedDateOffsets(maxDateCount);
            logger.info(
                "Showtime sync starting trigger={} timezone={} baseDate={} offsets={} lotteTargets={} megaboxTargets={}",
                trigger,
                properties.getTimezone(),
                baseDate,
                offsets,
                validLotteTargets(),
                validMegaboxTargets()
            );
            SyncTotals totals = new SyncTotals();
            for (Integer offset : offsets) {
                LocalDate playDate = baseDate.plusDays(offset == null ? 0 : offset);
                totals.add(syncLotte(playDate, maxSchedulesPerBundle));
                totals.add(syncMegabox(playDate, maxSchedulesPerBundle));
                if (properties.getShowtimes().isAutoDiscoveryEnabled()) {
                    totals.add(syncLotteDiscovered(playDate, maxSchedulesPerBundle));
                    totals.add(syncMegaboxDiscovered(playDate, maxSchedulesPerBundle));
                }
            }
            if (cleanupExpired) {
                cleanupExpiredShowtimes(baseDate);
            }
            ShowtimeSyncRunResult result = totals.toResult("completed", offsets.size());
            logger.info(
                "Showtime sync completed trigger={} baseDate={} bundleRequests={} movies={} theaters={} screens={} showtimes={}.",
                trigger,
                baseDate,
                result.bundleRequests(),
                result.movies(),
                result.theaters(),
                result.screens(),
                result.showtimes()
            );
            return result;
        } finally {
            running.set(false);
        }
    }

    private SyncTotals syncLotte(LocalDate playDate, int maxSchedulesPerBundle) {
        SyncTotals totals = new SyncTotals();
        for (CollectorSyncProperties.LotteTarget target : properties.getShowtimes().getLotteTargets()) {
            if (isBlank(target.getCinemaSelector()) || isBlank(target.getRepresentationMovieCode())) {
                continue;
            }
            ShowtimeCollectionRequest request = new ShowtimeCollectionRequest(
                CollectorProvider.LOTTE_CINEMA,
                playDate,
                null,
                null,
                target.getCinemaSelector(),
                target.getRepresentationMovieCode(),
                null
            );
            totals.add(persistCollectedBundleWithResult(request, maxSchedulesPerBundle));
        }
        return totals;
    }

    private SyncTotals syncMegabox(LocalDate playDate, int maxSchedulesPerBundle) {
        SyncTotals totals = new SyncTotals();
        for (CollectorSyncProperties.MegaboxTarget target : properties.getShowtimes().getMegaboxTargets()) {
            if (isBlank(target.getMovieNo()) || isBlank(target.getAreaCode())) {
                continue;
            }
            ShowtimeCollectionRequest request = new ShowtimeCollectionRequest(
                CollectorProvider.MEGABOX,
                playDate,
                null,
                target.getMovieNo(),
                null,
                null,
                target.getAreaCode()
            );
            totals.add(persistCollectedBundleWithResult(request, maxSchedulesPerBundle));
        }
        return totals;
    }

    private CollectorBundleIngestCommand.IngestResult persistCollectedBundleWithResult(
        ShowtimeCollectionRequest request,
        int maxSchedulesPerBundle
    ) {
        try {
            Map<String, Object> bundle = limitSchedules(
                collectorBridge.collectShowtimeBundle(request),
                maxSchedulesPerBundle
            );
            CollectorBundleIngestCommand.IngestResult result = persistenceService.persist(request.provider().name(), bundle, false);
            logger.info(
                "Showtime sync stored provider={} date={} movies={} theaters={} screens={} showtimes={}",
                request.provider(),
                request.playDate(),
                result.movies(),
                result.theaters(),
                result.screens(),
                result.showtimes()
            );
            return result;
        } catch (Exception exception) {
            logger.error("Showtime sync failed for provider={} date={}", request.provider(), request.playDate(), exception);
            return new CollectorBundleIngestCommand.IngestResult(0, 0, 0, 0);
        }
    }

    private SyncTotals syncLotteDiscovered(LocalDate playDate, int maxSchedulesPerBundle) {
        SyncTotals totals = new SyncTotals();
        logger.info(
            "Showtime auto-discovery starting provider={} date={} movieLimit={} cinemaLimit={} perCinemaLimit={} totalTargetLimit={}",
            CollectorProvider.LOTTE_CINEMA,
            playDate,
            properties.getShowtimes().getDiscoveryMovieLimit(),
            properties.getShowtimes().getDiscoveryLotteCinemaLimit(),
            properties.getShowtimes().getDiscoveryLotteMovieTargetLimit(),
            properties.getShowtimes().getDiscoveryLotteTotalTargetLimit()
        );
        PythonCollectorBridge.ProviderDiscoveryPayload discovery;
        try {
            discovery = collectorBridge.collectShowtimeDiscovery(CollectorProvider.LOTTE_CINEMA, playDate);
        } catch (Exception exception) {
            logger.error("Showtime auto-discovery failed for provider={} date={}", CollectorProvider.LOTTE_CINEMA, playDate, exception);
            return totals;
        }
        if (discovery.targets().isEmpty()) {
            logger.info("Showtime auto-discovery found no working LOTTE_CINEMA targets for date={}.", playDate);
            return totals;
        }
        logger.info(
            "Showtime auto-discovery found {} LOTTE_CINEMA targets for date={}.",
            discovery.targets().size(),
            playDate
        );
        for (Map<String, Object> target : discovery.targets()) {
            ShowtimeCollectionRequest request = new ShowtimeCollectionRequest(
                CollectorProvider.LOTTE_CINEMA,
                playDate,
                null,
                null,
                text(target.get("cinema_selector")),
                text(target.get("representation_movie_code")),
                null
            );
            totals.add(persistCollectedBundleWithResult(request, maxSchedulesPerBundle));
        }
        return totals;
    }

    private SyncTotals syncMegaboxDiscovered(LocalDate playDate, int maxSchedulesPerBundle) {
        SyncTotals totals = new SyncTotals();
        logger.info(
            "Showtime auto-discovery starting provider={} date={} movieLimit={} bundleLimit={}",
            CollectorProvider.MEGABOX,
            playDate,
            properties.getShowtimes().getDiscoveryMovieLimit(),
            properties.getShowtimes().getDiscoveryMegaboxBundleLimit()
        );
        PythonCollectorBridge.ProviderDiscoveryPayload discovery;
        try {
            discovery = collectorBridge.collectShowtimeDiscovery(CollectorProvider.MEGABOX, playDate);
        } catch (Exception exception) {
            logger.error("Showtime auto-discovery failed for provider={} date={}", CollectorProvider.MEGABOX, playDate, exception);
            return totals;
        }
        if (discovery.targets().isEmpty()) {
            logger.info("Showtime auto-discovery found no working MEGABOX targets for date={}.", playDate);
            return totals;
        }
        logger.info(
            "Showtime auto-discovery found {} MEGABOX targets for date={}.",
            discovery.targets().size(),
            playDate
        );
        for (Map<String, Object> target : discovery.targets()) {
            ShowtimeCollectionRequest request = new ShowtimeCollectionRequest(
                CollectorProvider.MEGABOX,
                playDate,
                null,
                text(target.get("movie_no")),
                null,
                null,
                text(target.get("area_code"))
            );
            totals.add(persistCollectedBundleWithResult(request, maxSchedulesPerBundle));
        }
        return totals;
    }

    private static Map<String, Object> limitSchedules(Map<String, Object> bundle, int maxSchedules) {
        if (maxSchedules <= 0) {
            return bundle;
        }
        Object schedules = bundle.get("schedules");
        if (!(schedules instanceof List<?> scheduleList) || scheduleList.size() <= maxSchedules) {
            return bundle;
        }

        Map<String, Object> limited = new LinkedHashMap<>(bundle);
        limited.put("schedules", new ArrayList<>(scheduleList.subList(0, maxSchedules)));
        limited.put("schedule_count", maxSchedules);
        return limited;
    }

    private static String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private void cleanupExpiredShowtimes(LocalDate baseDate) {
        if (!properties.getShowtimes().isCleanupEnabled()) {
            return;
        }
        ShowtimeCleanupRepository.CleanupCounts counts = cleanupService.cleanupForBaseDate(baseDate);
        logger.info(
            "Showtime cleanup completed for baseDate={} deletedShowtimes={} deletedSeatSnapshots={} deletedSeatSnapshotItems={}",
            baseDate,
            counts.deletedShowtimes(),
            counts.deletedSeatSnapshots(),
            counts.deletedSeatSnapshotItems()
        );
    }

    private long validLotteTargets() {
        return properties.getShowtimes().getLotteTargets().stream()
            .filter(target -> !isBlank(target.getCinemaSelector()) && !isBlank(target.getRepresentationMovieCode()))
            .count();
    }

    private long validMegaboxTargets() {
        return properties.getShowtimes().getMegaboxTargets().stream()
            .filter(target -> !isBlank(target.getMovieNo()) && !isBlank(target.getAreaCode()))
            .count();
    }

    private List<Integer> limitedDateOffsets(int maxDateCount) {
        List<Integer> offsets = properties.getShowtimes().getDateOffsetDays();
        if (offsets == null || offsets.isEmpty()) {
            return List.of(0);
        }
        int limit = Math.min(Math.max(1, maxDateCount), offsets.size());
        return new ArrayList<>(offsets.subList(0, limit));
    }

    public record ShowtimeSyncRunResult(
        String status,
        int dateCount,
        int bundleRequests,
        int movies,
        int theaters,
        int screens,
        int showtimes
    ) {
        static ShowtimeSyncRunResult skipped(String status) {
            return new ShowtimeSyncRunResult(status, 0, 0, 0, 0, 0, 0);
        }
    }

    private static final class SyncTotals {
        private int bundleRequests;
        private int movies;
        private int theaters;
        private int screens;
        private int showtimes;

        void add(CollectorBundleIngestCommand.IngestResult result) {
            bundleRequests += 1;
            if (result == null) {
                return;
            }
            movies += result.movies();
            theaters += result.theaters();
            screens += result.screens();
            showtimes += result.showtimes();
        }

        void add(SyncTotals other) {
            if (other == null) {
                return;
            }
            bundleRequests += other.bundleRequests;
            movies += other.movies;
            theaters += other.theaters;
            screens += other.screens;
            showtimes += other.showtimes;
        }

        ShowtimeSyncRunResult toResult(String status, int dateCount) {
            return new ShowtimeSyncRunResult(status, dateCount, bundleRequests, movies, theaters, screens, showtimes);
        }
    }
}
