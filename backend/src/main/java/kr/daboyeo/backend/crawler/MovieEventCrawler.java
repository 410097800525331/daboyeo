package kr.daboyeo.backend.crawler;

import kr.daboyeo.backend.domain.MovieEvent;
import java.util.List;

/**
 * Common interface for movie theater event crawlers (Lotte, Megabox, etc.)
 */
public interface MovieEventCrawler {

    /**
     * Crawls all events from the movie theater.
     *
     * @return List of MovieEvent domain objects.
     */
    List<MovieEvent> crawlAllEvents();
}
