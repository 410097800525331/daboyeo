package kr.daboyeo.backend.service;

import kr.daboyeo.backend.crawler.LotteCinemaEventCrawler;
import kr.daboyeo.backend.crawler.MegaboxEventCrawler;
import kr.daboyeo.backend.domain.Category;
import kr.daboyeo.backend.domain.MovieEvent;
import kr.daboyeo.backend.repository.MovieEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;

import java.util.ArrayList;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class MovieEventService {

    private static final Logger logger = LoggerFactory.getLogger(MovieEventService.class);

    private final LotteCinemaEventCrawler lotteCrawler;
    private final MegaboxEventCrawler megaboxCrawler;
    private final MovieEventRepository repository;

    @Transactional
    public void crawlAndSaveEvents() {
        logger.info("Starting event crawling...");
        List<MovieEvent> crawledEvents = crawlAllProviders();
        List<MovieEvent> eventsToSave = new ArrayList<>();
        Set<String> batchKeys = new HashSet<>();

        for (MovieEvent event : crawledEvents) {
            if (shouldSave(event, batchKeys)) {
                eventsToSave.add(event);
            }
        }

        repository.saveAll(eventsToSave);
        logger.info("Event crawling completed. {} new events saved.", eventsToSave.size());
    }

    // 수동 크롤링을 위한 메서드 (테스트용)
    public List<MovieEvent> crawlEventsManually() {
        return crawlAllProviders();
    }

    private List<MovieEvent> crawlAllProviders() {
        List<MovieEvent> crawledEvents = new ArrayList<>();
        crawledEvents.addAll(lotteCrawler.crawlAllEvents());
        crawledEvents.addAll(megaboxCrawler.crawlAllEvents());
        return crawledEvents;
    }

    private boolean shouldSave(MovieEvent event, Set<String> batchKeys) {
        if (event == null) {
            logger.warn("Skipping null movie event.");
            return false;
        }
        if (isBlank(event.getCinema())) {
            logger.warn("Skipping movie event without cinema. source={}, category={}, title={}",
                event.getSource(), event.getCategory(), event.getTitle());
            return false;
        }
        if (isBlank(event.getEventId())) {
            logger.warn("Skipping movie event without eventId. cinema={}, category={}, title={}",
                event.getCinema(), event.getCategory(), event.getTitle());
            return false;
        }

        String key = event.getCinema() + "::" + event.getEventId();
        if (!batchKeys.add(key)) {
            logger.debug("Event already exists in current batch: cinema={}, eventId={}, title={}",
                event.getCinema(), event.getEventId(), event.getTitle());
            return false;
        }
        if (repository.existsByEventIdAndCinema(event.getEventId(), event.getCinema())) {
            logger.debug("Event already exists in DB: cinema={}, eventId={}, title={}",
                event.getCinema(), event.getEventId(), event.getTitle());
            return false;
        }

        return true;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    public List<MovieEvent> getAllEvents() {
        return repository.findAllOrderByCreatedAtDesc();
    }

    public List<EventItem> getEvents(String source, Category category, Integer limit) {
        int boundedLimit = Math.max(1, Math.min(limit == null ? 80 : limit, 120));
        String normalizedSource = normalizeSource(source);
        LocalDate today = LocalDate.now();

        List<MovieEvent> filtered = repository.findAllOrderByCreatedAtDesc().stream()
            .filter(event -> normalizedSource.isBlank() || normalizeSource(event.getSource()).equals(normalizedSource) || normalizeSource(event.getCinema()).equals(normalizedSource))
            .filter(event -> category == null || event.getCategory() == category)
            .toList();

        List<MovieEvent> active = filtered.stream()
            .filter(event -> isActive(event, today))
            .toList();

        List<MovieEvent> sourceRows = active.isEmpty() ? filtered : active;
        return sourceRows.stream()
            .sorted(eventComparator())
            .limit(boundedLimit)
            .map(this::toEventItem)
            .toList();
    }

    public List<MovieEvent> getByCategory(Category category) {
        return repository.findByCategory(category);
    }

    public List<MovieEvent> getBySource(String source) {
        return repository.findBySource(source);
    }

    public List<MovieEvent> getBySourceAndCategory(String source, Category category) {
        return repository.findBySourceAndCategory(source, category);
    }

    private Comparator<MovieEvent> eventComparator() {
        return Comparator
            .comparing((MovieEvent event) -> event.getCategory() == Category.HOT ? 0 : 1)
            .thenComparing(event -> event.getStartDate(), Comparator.nullsLast(Comparator.reverseOrder()))
            .thenComparing(event -> event.getCreatedAt(), Comparator.nullsLast(Comparator.reverseOrder()))
            .thenComparing(event -> event.getId(), Comparator.nullsLast(Comparator.reverseOrder()));
    }

    private boolean isActive(MovieEvent event, LocalDate today) {
        if (event == null) {
            return false;
        }
        LocalDate startDate = event.getStartDate();
        LocalDate endDate = event.getEndDate();
        return (startDate == null || !startDate.isAfter(today))
            && (endDate == null || !endDate.isBefore(today));
    }

    private String normalizeSource(String source) {
        if (source == null || source.isBlank()) {
            return "";
        }
        String normalized = source.trim().toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "LOTTE_CINEMA" -> "LOTTE";
            case "MEGA" -> "MEGABOX";
            default -> normalized;
        };
    }

    private EventItem toEventItem(MovieEvent event) {
        Category eventCategory = event.getCategory();
        return new EventItem(
            event.getId(),
            safeText(event.getTitle()),
            normalizeSource(event.getSource()),
            normalizeSource(event.getCinema()),
            event.getEventId(),
            eventCategory == null ? "ALL" : eventCategory.name(),
            eventCategory == Category.HOT,
            safeText(event.getImageUrl()),
            safeText(event.getEventUrl()),
            event.getStartDate(),
            event.getEndDate(),
            safeText(event.getdDay()),
            event.getCreatedAt()
        );
    }

    private String safeText(String value) {
        return value == null ? "" : value.trim();
    }

    public record EventItem(
        Long id,
        String title,
        String source,
        String cinema,
        String eventId,
        String category,
        boolean hot,
        String imageUrl,
        String eventUrl,
        LocalDate startDate,
        LocalDate endDate,
        String dDay,
        LocalDateTime createdAt
    ) {
    }
}
