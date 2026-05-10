package kr.daboyeo.backend.sync.showtime;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.List;
import kr.daboyeo.backend.config.CollectorSyncProperties;
import org.junit.jupiter.api.Test;

class ShowtimeCleanupServiceTests {

    @Test
    void cleanupUsesConfiguredThreeDayWindow() {
        CollectorSyncProperties properties = new CollectorSyncProperties();
        properties.getShowtimes().setDateOffsetDays(List.of(0, 1, 2));
        properties.getShowtimes().setRetentionDays(3);

        ShowtimeCleanupRepository repository = mock(ShowtimeCleanupRepository.class);
        when(repository.cleanupOutsideWindow(LocalDate.of(2026, 4, 30), LocalDate.of(2026, 5, 2)))
            .thenReturn(new ShowtimeCleanupRepository.CleanupCounts(3, 2, 1));

        ShowtimeCleanupService service = new ShowtimeCleanupService(properties, repository);
        service.cleanupForBaseDate(LocalDate.of(2026, 4, 30));

        verify(repository, times(1)).cleanupOutsideWindow(LocalDate.of(2026, 4, 30), LocalDate.of(2026, 5, 2));
    }

    @Test
    void cleanupHonorsWiderOffsetsWhenTheyExceedRetentionDays() {
        CollectorSyncProperties properties = new CollectorSyncProperties();
        properties.getShowtimes().setDateOffsetDays(List.of(0, 1, 2, 3));
        properties.getShowtimes().setRetentionDays(3);

        ShowtimeCleanupRepository repository = mock(ShowtimeCleanupRepository.class);
        when(repository.cleanupOutsideWindow(LocalDate.of(2026, 4, 30), LocalDate.of(2026, 5, 3)))
            .thenReturn(new ShowtimeCleanupRepository.CleanupCounts(0, 0, 0));

        ShowtimeCleanupService service = new ShowtimeCleanupService(properties, repository);
        service.cleanupForBaseDate(LocalDate.of(2026, 4, 30));

        verify(repository, times(1)).cleanupOutsideWindow(LocalDate.of(2026, 4, 30), LocalDate.of(2026, 5, 3));
    }
}
