package kr.daboyeo.backend.crawler.lotte;

import org.jsoup.Jsoup;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;

public class LotteCinemaEventUtils {

    private static final Logger logger = LoggerFactory.getLogger(LotteCinemaEventUtils.class);
    private static final String BASE_URL = "https://www.lottecinema.co.kr";

    private static final List<DateTimeFormatter> DATE_FORMATTERS = List.of(
            DateTimeFormatter.ofPattern("yyyy.MM.dd"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd"),
            DateTimeFormatter.ofPattern("yyyyMMdd")
    );

    public static LocalDate parseDate(String dateText) {
        if (dateText == null || dateText.isBlank()) {
            return null;
        }
        String trimmed = dateText.trim();
        for (DateTimeFormatter formatter : DATE_FORMATTERS) {
            try {
                return LocalDate.parse(trimmed, formatter);
            } catch (DateTimeParseException ignored) {
            }
        }
        logger.warn("[LOTTE] Failed to parse date: {}", dateText);
        return null;
    }

    public static String cleanHtml(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        // Jsoup parse and get text
        String text = Jsoup.parse(value).text();
        // Remove newlines and multiple spaces
        return text.replaceAll("\\r?\\n", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    public static String buildDDay(LotteCinemaEventResponse.Item item) {
        if ("1".equals(String.valueOf(item.getCloseNearYN()))) {
            return "마감임박";
        }

        LocalDate now = LocalDate.now();
        LocalDate startDate = parseDate(item.getProgressStartDate());
        LocalDate endDate = parseDate(item.getProgressEndDate());

        if (startDate != null && startDate.isAfter(now)) {
            return "오픈예정";
        }
        if (startDate != null && startDate.isEqual(now)) {
            return "오늘오픈";
        }
        if (endDate != null && endDate.isBefore(now)) {
            return "종료";
        }
        if (endDate != null && endDate.isEqual(now)) {
            return "오늘종료";
        }

        Integer remains = item.getRemainsDayCount();
        if (remains != null) {
            if (remains == 0) return "오늘종료";
            if (remains < 0) return "종료";
            return "D-" + remains;
        }

        return null;
    }

    public static String normalizeImageUrl(String imageUrl) {
        if (imageUrl == null || imageUrl.isBlank()) {
            return null;
        }
        String trimmed = imageUrl.trim();
        if (trimmed.startsWith("http")) {
            return trimmed;
        }
        return BASE_URL + (trimmed.startsWith("/") ? "" : "/") + trimmed;
    }

    public static String buildEventUrl(String eventId) {
        if (eventId == null || eventId.isBlank()) {
            return null;
        }
        return BASE_URL + "/NLCHS/Event/EventTemplateMov?eventId=" + eventId;
    }
}
