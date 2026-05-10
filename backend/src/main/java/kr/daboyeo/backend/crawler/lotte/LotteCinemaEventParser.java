package kr.daboyeo.backend.crawler.lotte;

import com.fasterxml.jackson.databind.ObjectMapper;
import kr.daboyeo.backend.domain.MovieEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import lombok.RequiredArgsConstructor;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Component
@RequiredArgsConstructor
public class LotteCinemaEventParser {

    private static final Logger logger = LoggerFactory.getLogger(LotteCinemaEventParser.class);
    private final ObjectMapper objectMapper;

    public LotteCinemaEventResponse parseResponse(String json) {
        if (json == null || json.isBlank()) return null;
        try {
            return objectMapper.readValue(json, LotteCinemaEventResponse.class);
        } catch (Exception e) {
            logger.warn("[LOTTE] Failed to parse JSON response: {}", e.getMessage());
            logger.debug("[LOTTE] Raw JSON: {}", json);
            return null;
        }
    }

    public List<MovieEvent> toMovieEvents(LotteCinemaEventResponse response, LotteCinemaEventCategory category) {
        if (response == null || response.getItems().isEmpty()) {
            return List.of();
        }

        List<MovieEvent> events = new ArrayList<>();
        for (LotteCinemaEventResponse.Item item : response.getItems()) {
            try {
                MovieEvent event = mapToDomain(item, category);
                if (event != null) {
                    events.add(event);
                }
            } catch (Exception e) {
                logger.warn("[LOTTE] Item mapping failed: category={}, id={}, error={}",
                        category.name(), item.getEventID(), e.getMessage());
            }
        }
        return events;
    }

    private MovieEvent mapToDomain(LotteCinemaEventResponse.Item item, LotteCinemaEventCategory category) {
        String title = LotteCinemaEventUtils.cleanHtml(
                item.getEventName() != null && !item.getEventName().isBlank() ? item.getEventName() : item.getImageAlt()
        );

        if (title.isBlank()) return null;

        String eventId = item.getEventID() != null ? item.getEventID() : title;
        String imageUrl = LotteCinemaEventUtils.normalizeImageUrl(item.getImageUrl());
        LocalDate startDate = LotteCinemaEventUtils.parseDate(item.getProgressStartDate());
        LocalDate endDate = LotteCinemaEventUtils.parseDate(item.getProgressEndDate());
        String dDay = LotteCinemaEventUtils.buildDDay(item);
        String eventUrl = LotteCinemaEventUtils.buildEventUrl(eventId);

        return new MovieEvent(
                "LOTTE",
                "LOTTE_CINEMA",
                eventId,
                category.getDomainCategory(),
                title,
                imageUrl,
                startDate,
                endDate,
                dDay,
                eventUrl
        );
    }
}
