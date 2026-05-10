package kr.daboyeo.backend.sync.showtime;

import java.time.LocalDate;
import java.util.List;
import kr.daboyeo.backend.config.CollectorSyncProperties;
import org.springframework.stereotype.Service;

@Service
public class ShowtimeCleanupService {

    private final CollectorSyncProperties properties;
    private final ShowtimeCleanupRepository repository;

    public ShowtimeCleanupService(CollectorSyncProperties properties, ShowtimeCleanupRepository repository) {
        this.properties = properties;
        this.repository = repository;
    }

    public ShowtimeCleanupRepository.CleanupCounts cleanupForBaseDate(LocalDate baseDate) {
        CollectorSyncProperties.ShowtimeProperties showtimeProperties = properties.getShowtimes();
        List<Integer> offsets = showtimeProperties.getDateOffsetDays();
        int configuredRetentionDays = Math.max(showtimeProperties.getRetentionDays(), 1);
        int minOffset = offsets.stream().filter(offset -> offset != null).min(Integer::compareTo).orElse(0);
        int maxOffset = offsets.stream().filter(offset -> offset != null).max(Integer::compareTo).orElse(configuredRetentionDays - 1);

        LocalDate windowStart = baseDate.plusDays(minOffset);
        LocalDate offsetWindowEnd = baseDate.plusDays(maxOffset);
        LocalDate retentionWindowEnd = baseDate.plusDays(configuredRetentionDays - 1L);
        LocalDate windowEnd = offsetWindowEnd.isAfter(retentionWindowEnd) ? offsetWindowEnd : retentionWindowEnd;

        return repository.cleanupOutsideWindow(windowStart, windowEnd);
    }
}
