package kr.daboyeo.backend.crawler;

import kr.daboyeo.backend.crawler.megabox.MegaboxEventApiClient;
import kr.daboyeo.backend.crawler.megabox.MegaboxEventCategory;
import kr.daboyeo.backend.domain.MovieEvent;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Component
public class MegaboxEventCrawler extends BaseMovieEventCrawler {
    private static final String MOBILE_EVENT_DETAIL_URL = "https://m.megabox.co.kr/event/detail?eventNo=";
    private static final Pattern EVENT_NO_PATTERN = Pattern.compile("fn_eventDetail\\('([0-9]+)'");
    private static final Pattern DATE_RANGE_PATTERN = Pattern.compile("(\\d{4}\\.\\d{2}\\.\\d{2})\\s*~\\s*(\\d{4}\\.\\d{2}\\.\\d{2})");
    private static final DateTimeFormatter MEGABOX_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy.MM.dd");

    private final MegaboxEventApiClient apiClient;

    public MegaboxEventCrawler(MegaboxEventApiClient apiClient) {
        super("MEGABOX");
        this.apiClient = apiClient;
    }

    @Override
    protected List<MovieEvent> doCrawl(CrawlingMetrics metrics) {
        if (!isRobotsAllowed()) {
            logger.warn("[MEGABOX] robots policy is unavailable/disallowing event crawl. stop crawling.");
            return List.of();
        }

        String publicEventPage = apiClient.fetchPublicEventPage();
        if (publicEventPage == null || publicEventPage.isBlank() || !publicEventPage.contains("eventMngDiv.do")) {
            logger.warn("[MEGABOX] public event page did not expose expected browser request contract. fail closed.");
            return List.of();
        }

        Map<String, EventWithPriority> dedup = new LinkedHashMap<>();

        for (MegaboxEventCategory category : MegaboxEventCategory.values()) {
            long start = System.currentTimeMillis();
            try {
                // Strong rate limiting: one category request at a time with delay.
                Thread.sleep(1500);
                String fragmentHtml = apiClient.fetchEventListFragment(category.getEventDivCd());
                if (fragmentHtml == null || fragmentHtml.isBlank()) {
                    throw new IllegalStateException("empty response fragment");
                }

                List<MovieEvent> events = parseEvents(fragmentHtml, category);
                metrics.addFound(events.size());
                for (MovieEvent event : events) {
                    mergeDedup(dedup, event, category.getPriority(), metrics);
                }
            } catch (Exception e) {
                metrics.addFailed(1);
                logger.warn("[MEGABOX] category crawl failed ({}). fail closed.", category.name(), e);
                return List.of();
            } finally {
                metrics.recordCategoryTime(category.name(), System.currentTimeMillis() - start);
            }
        }

        return dedup.values().stream()
                .map(EventWithPriority::event)
                .sorted(Comparator.comparing(MovieEvent::getStartDate, Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(MovieEvent::getTitle))
                .collect(Collectors.toList());
    }

    private boolean isRobotsAllowed() {
        try {
            String mobile = apiClient.fetchRobotsTxt(true);
            String desktop = apiClient.fetchRobotsTxt(false);
            return isAllowedByText(mobile) && isAllowedByText(desktop);
        } catch (Exception e) {
            logger.warn("[MEGABOX] robots check failed. fail closed.", e);
            return false;
        }
    }

    private boolean isAllowedByText(String robotsText) {
        if (robotsText == null || robotsText.isBlank()) {
            logger.warn("[MEGABOX] robots response is empty. continue with conservative crawl mode.");
            return true;
        }
        String lower = robotsText.toLowerCase(Locale.ROOT);
        // robots-like syntax is not visible. treat as temporarily unavailable page and continue conservatively.
        if (!lower.contains("user-agent:")) {
            logger.warn("[MEGABOX] robots syntax not detected. continue with conservative crawl mode.");
            return true;
        }

        boolean inWildcardBlock = false;
        for (String rawLine : robotsText.split("\\R")) {
            String line = rawLine.trim();
            if (line.isEmpty() || line.startsWith("#")) continue;
            String low = line.toLowerCase(Locale.ROOT);
            if (low.startsWith("user-agent:")) {
                String value = low.substring("user-agent:".length()).trim();
                inWildcardBlock = "*".equals(value);
                continue;
            }
            if (!inWildcardBlock) continue;
            if (low.startsWith("disallow:")) {
                String path = low.substring("disallow:".length()).trim();
                if ("/".equals(path)
                        || path.startsWith("/event")
                        || path.startsWith("/on/oh/ohe/event")) {
                    logger.warn("[MEGABOX] robots disallow matched path={}", path);
                    return false;
                }
            }
        }
        return true;
    }

    private List<MovieEvent> parseEvents(String fragmentHtml, MegaboxEventCategory category) {
        Document doc = Jsoup.parseBodyFragment(fragmentHtml);
        Elements links = doc.select(".event-list .item a, .event-main-list .item a");
        List<MovieEvent> events = new ArrayList<>();
        for (Element link : links) {
            MovieEvent event = toMovieEvent(link, category);
            if (event != null) {
                events.add(event);
            }
        }
        return events;
    }

    private MovieEvent toMovieEvent(Element link, MegaboxEventCategory category) {
        String onclick = link.attr("onclick");
        Matcher eventNoMatcher = EVENT_NO_PATTERN.matcher(onclick);
        if (!eventNoMatcher.find()) {
            return null;
        }
        String eventNo = eventNoMatcher.group(1);

        String title = clean(link.selectFirst(".info .title") != null
                ? link.selectFirst(".info .title").text()
                : link.text());
        if (title.isBlank()) {
            return null;
        }

        Element imageEl = link.selectFirst("img");
        String imageUrl = imageEl == null ? "" : clean(firstNonBlank(
                imageEl.attr("data-src"),
                imageEl.attr("src")
        ));

        String dateText = clean(link.selectFirst(".info .date") != null
                ? link.selectFirst(".info .date").text()
                : "");
        LocalDate startDate = null;
        LocalDate endDate = null;
        if (!dateText.isBlank()) {
            Matcher dateMatcher = DATE_RANGE_PATTERN.matcher(dateText);
            if (dateMatcher.find()) {
                startDate = parseDate(dateMatcher.group(1));
                endDate = parseDate(dateMatcher.group(2));
            }
        }

        return new MovieEvent(
                "MEGABOX",
                "MEGABOX",
                eventNo,
                category.getDomainCategory(),
                title,
                imageUrl,
                startDate,
                endDate,
                buildDDay(endDate),
                MOBILE_EVENT_DETAIL_URL + eventNo
        );
    }

    private void mergeDedup(Map<String, EventWithPriority> dedup, MovieEvent event, int priority, CrawlingMetrics metrics) {
        dedup.compute(event.getEventId(), (key, existing) -> {
            if (existing == null) {
                return new EventWithPriority(event, priority);
            }
            metrics.addDeduped(1);
            if (priority < existing.priority()) {
                return new EventWithPriority(event, priority);
            }
            return existing;
        });
    }

    private LocalDate parseDate(String text) {
        try {
            return LocalDate.parse(text, MEGABOX_DATE_FORMAT);
        } catch (Exception ignored) {
            return null;
        }
    }

    private String buildDDay(LocalDate endDate) {
        if (endDate == null) {
            return null;
        }
        long diff = java.time.temporal.ChronoUnit.DAYS.between(LocalDate.now(), endDate);
        if (diff < 0) return "종료";
        if (diff == 0) return "D-DAY";
        return "D-" + diff;
    }

    private String clean(String value) {
        if (value == null) return "";
        return value.replace('\u00A0', ' ').trim();
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return "";
    }

    private record EventWithPriority(MovieEvent event, int priority) {
    }
}
