package kr.daboyeo.backend.sync.showtime;

import kr.daboyeo.backend.config.CollectorSyncProperties;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component
public class ShowtimeSyncScheduler {

    private static final Logger logger = LoggerFactory.getLogger(ShowtimeSyncScheduler.class);

    private final CollectorSyncProperties properties;
    private final ShowtimeSyncService service;

    public ShowtimeSyncScheduler(CollectorSyncProperties properties, ShowtimeSyncService service) {
        this.properties = properties;
        this.service = service;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void runOnStartup() {
        if (!properties.getShowtimes().isStartupEnabled()) {
            logger.info("Skipping startup showtime sync because daboyeo.sync.showtimes.startup-enabled=false.");
            return;
        }
        runSync("startup");
    }

    @Scheduled(cron = "${daboyeo.sync.showtimes.cron:0 0 * * * *}", zone = "${daboyeo.sync.timezone:Asia/Seoul}")
    public void runOnSchedule() {
        runSync("scheduled");
    }

    private void runSync(String trigger) {
        try {
            logger.info("Triggering showtime sync via {} path.", trigger);
            service.syncDailyShowtimes();
        } catch (Exception e) {
            logger.error("Showtime sync failed via {} path.", trigger, e);
        }
    }
}
