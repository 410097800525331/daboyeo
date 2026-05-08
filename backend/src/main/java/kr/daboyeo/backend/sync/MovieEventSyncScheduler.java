package kr.daboyeo.backend.sync;

import kr.daboyeo.backend.crawler.MovieEventCrawler;
import kr.daboyeo.backend.domain.MovieEvent;
import kr.daboyeo.backend.service.MovieEventBatchService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import lombok.RequiredArgsConstructor;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

@Component
@RequiredArgsConstructor
public class MovieEventSyncScheduler {

    private static final Logger logger = LoggerFactory.getLogger(MovieEventSyncScheduler.class);
    private final List<MovieEventCrawler> crawlers;
    private final MovieEventBatchService batchService;
    private final AtomicBoolean isRunning = new AtomicBoolean(false);

    @Scheduled(cron = "0 0 1,13 * * *") // Twice a day at 1AM and 1PM
    public void syncAllEvents() {
        if (!isRunning.compareAndSet(false, true)) {
            logger.warn("[SCHEDULER] Already running. Skipping this turn.");
            return;
        }

        try {
            logger.info("[SCHEDULER] Starting global event synchronization...");
            for (MovieEventCrawler crawler : crawlers) {
                try {
                    List<MovieEvent> events = crawler.crawlAllEvents();
                    if (!events.isEmpty()) {
                        batchService.syncEvents(events.get(0).getCinema(), events);
                    }
                } catch (Exception e) {
                    logger.error("[SCHEDULER] Failed to sync for a crawler: {}", e.getMessage(), e);
                }
            }
            logger.info("[SCHEDULER] Global event synchronization finished.");
        } finally {
            isRunning.set(false);
        }
    }
}
