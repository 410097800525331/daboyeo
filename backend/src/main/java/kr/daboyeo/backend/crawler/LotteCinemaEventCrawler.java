package kr.daboyeo.backend.crawler;

import kr.daboyeo.backend.crawler.lotte.*;
import kr.daboyeo.backend.domain.MovieEvent;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;

@Component
public class LotteCinemaEventCrawler extends BaseMovieEventCrawler {

    private final LotteCinemaApiClient apiClient;
    private final LotteCinemaEventParser parser;
    private final Executor executor;

    public LotteCinemaEventCrawler(
            LotteCinemaApiClient apiClient,
            LotteCinemaEventParser parser,
            @Qualifier("movieEventCrawlerExecutor") Executor executor) {
        super("LOTTE");
        this.apiClient = apiClient;
        this.parser = parser;
        this.executor = executor;
    }

    @Override
    protected List<MovieEvent> doCrawl(CrawlingMetrics metrics) throws Exception {
        Map<String, EventWithPriority> dedupMap = Collections.synchronizedMap(new LinkedHashMap<>());

        List<CompletableFuture<Void>> futures = Arrays.stream(LotteCinemaEventCategory.values())
                .map(category -> CompletableFuture.runAsync(() -> {
                    long start = System.currentTimeMillis();
                    try {
                        List<MovieEvent> events = crawlCategory(category);
                        metrics.addFound(events.size());
                        for (MovieEvent event : events) {
                            processDedup(dedupMap, event, category, metrics);
                        }
                    } catch (Exception e) {
                        metrics.addFailed(1);
                        logger.error("[LOTTE] Error crawling category {}: {}", category.name(), e.getMessage());
                    } finally {
                        metrics.recordCategoryTime(category.name(), System.currentTimeMillis() - start);
                    }
                }, executor))
                .toList();

        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

        return dedupMap.values().stream()
                .map(EventWithPriority::event)
                .sorted(Comparator.comparing(this::isExpired)
                        .thenComparing(MovieEvent::getStartDate, Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(MovieEvent::getTitle))
                .collect(Collectors.toList());
    }

    private List<MovieEvent> crawlCategory(LotteCinemaEventCategory category) {
        List<MovieEvent> allCategoryEvents = new ArrayList<>();
        int pageNo = 1;
        int totalCount = Integer.MAX_VALUE;

        while (allCategoryEvents.size() < totalCount) {
            String json = apiClient.fetchEventPage(category.getCode(), pageNo);
            LotteCinemaEventResponse response = parser.parseResponse(json);

            if (response == null || response.getItems().isEmpty()) {
                break;
            }

            totalCount = (response.getTotalCount() != null && response.getTotalCount() > 0)
                    ? response.getTotalCount() : Integer.MAX_VALUE;

            List<MovieEvent> pageEvents = parser.toMovieEvents(response, category);
            allCategoryEvents.addAll(pageEvents);

            if (pageEvents.size() < 100 || allCategoryEvents.size() >= totalCount) {
                break;
            }
            pageNo++;
        }
        return allCategoryEvents;
    }

    private void processDedup(Map<String, EventWithPriority> dedupMap, MovieEvent event, LotteCinemaEventCategory category, CrawlingMetrics metrics) {
        String id = event.getEventId();

        dedupMap.compute(id, (key, existing) -> {
            if (existing == null) {
                return new EventWithPriority(event, category.getPriority());
            }
            if (category.getPriority() < existing.priority()) {
                metrics.addDeduped(1);
                return new EventWithPriority(event, category.getPriority());
            }
            metrics.addDeduped(1);
            return existing;
        });
    }

    private boolean isExpired(MovieEvent event) {
        if (event.getEndDate() == null) return false;
        return event.getEndDate().isBefore(java.time.LocalDate.now());
    }

    private record EventWithPriority(MovieEvent event, int priority) {}
}
