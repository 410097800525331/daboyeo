package kr.daboyeo.backend.sync.showtime;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import kr.daboyeo.backend.config.CollectorSyncProperties;
import org.junit.jupiter.api.Test;

class ShowtimeSyncSchedulerTests {

    @Test
    void startupTriggerCallsSyncServiceWhenEnabled() {
        CollectorSyncProperties properties = new CollectorSyncProperties();
        properties.getShowtimes().setStartupEnabled(true);
        ShowtimeSyncService service = mock(ShowtimeSyncService.class);
        ShowtimeSyncScheduler scheduler = new ShowtimeSyncScheduler(properties, service);

        scheduler.runOnStartup();

        verify(service, times(1)).syncDailyShowtimes();
    }

    @Test
    void startupTriggerSkipsSyncServiceWhenDisabled() {
        CollectorSyncProperties properties = new CollectorSyncProperties();
        properties.getShowtimes().setStartupEnabled(false);
        ShowtimeSyncService service = mock(ShowtimeSyncService.class);
        ShowtimeSyncScheduler scheduler = new ShowtimeSyncScheduler(properties, service);

        scheduler.runOnStartup();

        verify(service, never()).syncDailyShowtimes();
    }

    @Test
    void scheduledTriggerCallsSyncService() {
        CollectorSyncProperties properties = new CollectorSyncProperties();
        ShowtimeSyncService service = mock(ShowtimeSyncService.class);
        ShowtimeSyncScheduler scheduler = new ShowtimeSyncScheduler(properties, service);

        scheduler.runOnSchedule();

        verify(service, times(1)).syncDailyShowtimes();
    }
}
