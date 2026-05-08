package kr.daboyeo.backend.api;

import kr.daboyeo.backend.domain.Category;
import kr.daboyeo.backend.domain.MovieEvent;
import kr.daboyeo.backend.service.MovieEventService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/events")
@RequiredArgsConstructor
public class MovieEventController {

    private final MovieEventService movieEventService;

    @GetMapping
    public List<MovieEvent> getEvents(
            @RequestParam(required = false) String source,
            @RequestParam(required = false) Category category
    ) {
        if (source != null && category != null) {
            return movieEventService.getBySourceAndCategory(source, category);
        }
        if (source != null) {
            return movieEventService.getBySource(source);
        }
        if (category != null) {
            return movieEventService.getByCategory(category);
        }
        return movieEventService.getAllEvents();
    }

    @PostMapping("/crawl")
    public String crawlEvents() {
        movieEventService.crawlAndSaveEvents();
        return "Crawling completed successfully";
    }

    @GetMapping("/crawl-test")
    public List<MovieEvent> crawlTest() {
        return movieEventService.crawlEventsManually();
    }
}
