package kr.daboyeo.backend.crawler;

import kr.daboyeo.backend.domain.MovieEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public abstract class BaseMovieEventCrawler implements MovieEventCrawler {

    protected final Logger logger = LoggerFactory.getLogger(getClass());
    protected final String cinemaName;

    protected BaseMovieEventCrawler(String cinemaName) {
        this.cinemaName = cinemaName;
    }

    @Override
    public final List<MovieEvent> crawlAllEvents() {
        CrawlingMetrics metrics = new CrawlingMetrics(cinemaName);
        logger.info("[{}] Starting event crawling...", cinemaName);

        try {
            List<MovieEvent> events = doCrawl(metrics);
            metrics.markEnd();
            logger.info("{}", metrics);
            return events;
        } catch (Exception e) {
            logger.error("[{}] Fatal error during crawling: {}", cinemaName, e.getMessage(), e);
            metrics.markEnd();
            return List.of();
        }
    }

    /**
     * Implementation-specific crawling logic.
     */
    protected abstract List<MovieEvent> doCrawl(CrawlingMetrics metrics) throws Exception;
}
