package kr.daboyeo.backend.sync.seat;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import kr.daboyeo.backend.config.CollectorSyncProperties;
import kr.daboyeo.backend.sync.bridge.PythonCollectorBridge;
import kr.daboyeo.backend.sync.bridge.SeatCollectionRequest;
import kr.daboyeo.backend.sync.bridge.SeatCollectionResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class SeatSnapshotSyncService {

    private static final Logger logger = LoggerFactory.getLogger(SeatSnapshotSyncService.class);

    private final CollectorSyncProperties properties;
    private final SeatSnapshotRepository repository;
    private final PythonCollectorBridge collectorBridge;
    private final SeatSnapshotPersistenceService persistenceService;
    private final AtomicBoolean running = new AtomicBoolean(false);

    public SeatSnapshotSyncService(
        CollectorSyncProperties properties,
        SeatSnapshotRepository repository,
        PythonCollectorBridge collectorBridge,
        SeatSnapshotPersistenceService persistenceService
    ) {
        this.properties = properties;
        this.repository = repository;
        this.collectorBridge = collectorBridge;
        this.persistenceService = persistenceService;
    }

    public void syncSeatSnapshots() {
        if (!properties.isEnabled() || !properties.getSeats().isEnabled()) {
            return;
        }
        if (!running.compareAndSet(false, true)) {
            logger.info("Seat snapshot sync is already running. Skipping overlapping trigger.");
            return;
        }

        try {
            LocalDateTime now = LocalDateTime.now(ZoneId.of(properties.getTimezone()));
            LocalDateTime until = now.plusHours(properties.getSeats().getLookaheadHours());
            List<SeatSnapshotTarget> targets = repository.findUpcomingTargets(now, until, properties.getSeats().getLimit());
            for (SeatSnapshotTarget target : targets) {
                try {
                    SeatCollectionResult result = collectorBridge.collectSeatSnapshot(
                        new SeatCollectionRequest(target.provider(), target.externalShowtimeKey(), target.bookingKey())
                    );
                    persistenceService.persist(target, result);
                    logger.info("Seat snapshot stored provider={} showtimeKey={} seatCount={}", target.provider(), target.externalShowtimeKey(), result.seats().size());
                } catch (Exception exception) {
                    logger.error("Seat snapshot sync failed for provider={} showtimeKey={}", target.provider(), target.externalShowtimeKey(), exception);
                }
            }
        } finally {
            running.set(false);
        }
    }
}
