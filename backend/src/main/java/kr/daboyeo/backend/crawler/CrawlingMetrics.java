package kr.daboyeo.backend.crawler;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class CrawlingMetrics {
    private final String cinemaName;
    private final LocalDateTime startTime;
    private LocalDateTime endTime;

    private final AtomicInteger totalFound = new AtomicInteger(0);
    private final AtomicInteger dedupedCount = new AtomicInteger(0);
    private final AtomicInteger failedCount = new AtomicInteger(0);
    private final Map<String, Long> categoryElapsedMillis = new ConcurrentHashMap<>();

    public CrawlingMetrics(String cinemaName) {
        this.cinemaName = cinemaName;
        this.startTime = LocalDateTime.now();
    }

    public void markEnd() {
        this.endTime = LocalDateTime.now();
    }

    public void addFound(int count) {
        totalFound.addAndGet(count);
    }

    public void addDeduped(int count) {
        dedupedCount.addAndGet(count);
    }

    public void addFailed(int count) {
        failedCount.addAndGet(count);
    }

    public void recordCategoryTime(String category, long millis) {
        categoryElapsedMillis.put(category, millis);
    }

    @Override
    public String toString() {
        return String.format(
            "[%s] Crawling Summary:\n" +
            "- Start: %s\n" +
            "- End: %s\n" +
            "- Total Found: %d\n" +
            "- Deduped: %d\n" +
            "- Failed: %d\n" +
            "- Category Times: %s",
            cinemaName, startTime, endTime, totalFound.get(), dedupedCount.get(), failedCount.get(), categoryElapsedMillis
        );
    }

    // Getters
    public String getCinemaName() { return cinemaName; }
    public LocalDateTime getStartTime() { return startTime; }
    public LocalDateTime getEndTime() { return endTime; }
    public int getTotalFound() { return totalFound.get(); }
    public int getDedupedCount() { return dedupedCount.get(); }
    public int getFailedCount() { return failedCount.get(); }
}
