package kr.daboyeo.backend.service;

import kr.daboyeo.backend.crawler.LotteCinemaEventCrawler;
import kr.daboyeo.backend.domain.Category;
import kr.daboyeo.backend.domain.MovieEvent;
import kr.daboyeo.backend.repository.MovieEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class MovieEventService {

    private static final Logger logger = LoggerFactory.getLogger(MovieEventService.class);

    private final LotteCinemaEventCrawler crawler;
    private final MovieEventRepository repository;

    @Transactional
    public void crawlAndSaveEvents() {
        logger.info("Starting event crawling...");
        List<MovieEvent> crawledEvents = crawler.crawlAllEvents();
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
        return crawler.crawlAllEvents();
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

    public List<MovieEvent> getByCategory(Category category) {
        return repository.findByCategory(category);
    }

    public List<MovieEvent> getBySource(String source) {
        return repository.findBySource(source);
    }

    public List<MovieEvent> getBySourceAndCategory(String source, Category category) {
        return repository.findBySourceAndCategory(source, category);
    }
}
