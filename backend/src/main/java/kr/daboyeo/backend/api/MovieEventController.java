package kr.daboyeo.backend.api;

import kr.daboyeo.backend.domain.Category;
import kr.daboyeo.backend.domain.MovieEvent;
import kr.daboyeo.backend.security.PortfolioAccessGate;
import kr.daboyeo.backend.service.MovieEventService.EventItem;
import kr.daboyeo.backend.service.MovieEventService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/events")
@RequiredArgsConstructor
public class MovieEventController {

    private final MovieEventService movieEventService;
    private final PortfolioAccessGate accessGate;

    @GetMapping
    public List<EventItem> getEvents(
            @RequestParam(required = false) String source,
            @RequestParam(required = false) Category category,
            @RequestParam(required = false) Integer limit
    ) {
        return movieEventService.getEvents(source, category, limit);
    }

    @PostMapping("/crawl")
    public String crawlEvents(@RequestHeader(name = PortfolioAccessGate.ADMIN_TOKEN_HEADER, required = false) String token) {
        accessGate.requireCollectionAccess(token);
        movieEventService.crawlAndSaveEvents();
        return "Crawling completed successfully";
    }

    @GetMapping("/crawl-test")
    public List<MovieEvent> crawlTest(@RequestHeader(name = PortfolioAccessGate.ADMIN_TOKEN_HEADER, required = false) String token) {
        accessGate.requireCollectionAccess(token);
        return movieEventService.crawlEventsManually();
    }
}
