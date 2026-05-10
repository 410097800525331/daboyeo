package kr.daboyeo.backend.sync.seat;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class SeatSnapshotScheduler {

    private final SeatSnapshotSyncService service;

    public SeatSnapshotScheduler(SeatSnapshotSyncService service) {
        this.service = service;
    }

    @Scheduled(cron = "${daboyeo.sync.seats.cron:0 0/30 * * * *}", zone = "${daboyeo.sync.timezone:Asia/Seoul}")
    public void run() {
        service.syncSeatSnapshots();
    }
}
