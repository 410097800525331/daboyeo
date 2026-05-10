package kr.daboyeo.backend.service;

import kr.daboyeo.backend.domain.MovieEvent;
import kr.daboyeo.backend.repository.MovieEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class MovieEventBatchService {

    private static final Logger logger = LoggerFactory.getLogger(MovieEventBatchService.class);
    private final MovieEventRepository movieEventRepository;

    @Transactional
    public void syncEvents(String cinema, List<MovieEvent> crawledEvents) {
        logger.info("[{}] Syncing {} events to database...", cinema, crawledEvents.size());

        // 1. Get existing events for this cinema
        List<MovieEvent> existingEvents = movieEventRepository.findBySource(cinema);
        Map<String, MovieEvent> existingMap = existingEvents.stream()
                .collect(Collectors.toMap(MovieEvent::getEventId, e -> e, (e1, e2) -> e1));

        Set<String> crawledIds = crawledEvents.stream()
                .map(MovieEvent::getEventId)
                .collect(Collectors.toSet());

        // 2. Update or Insert
        for (MovieEvent crawled : crawledEvents) {
            MovieEvent existing = existingMap.get(crawled.getEventId());
            if (existing != null) {
                updateEvent(existing, crawled);
            } else {
                movieEventRepository.save(crawled);
            }
        }

        // 3. Optional: Inactive strategy (e.g. update status or delete)
        // For now, let's just log which ones are gone
        List<MovieEvent> missingEvents = existingEvents.stream()
                .filter(e -> !crawledIds.contains(e.getEventId()))
                .toList();

        if (!missingEvents.isEmpty()) {
            logger.info("[{}] {} events are no longer present in crawl result.", cinema, missingEvents.size());
            // Optional: movieEventRepository.deleteAll(missingEvents); or mark as inactive
        }
    }

    private void updateEvent(MovieEvent existing, MovieEvent crawled) {
        existing.setCategory(crawled.getCategory());
        existing.setTitle(crawled.getTitle());
        existing.setImageUrl(crawled.getImageUrl());
        existing.setStartDate(crawled.getStartDate());
        existing.setEndDate(crawled.getEndDate());
        existing.setdDay(crawled.getdDay());
        existing.setEventUrl(crawled.getEventUrl());
        // createdAt remains the same, but we might want an updatedAt field
        movieEventRepository.save(existing);
    }
}
