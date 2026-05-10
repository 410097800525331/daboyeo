package kr.daboyeo.backend.crawler.lotte;

import kr.daboyeo.backend.domain.Category;
import java.util.Arrays;
import java.util.Optional;

public enum LotteCinemaEventCategory {
    HOT("10", Category.HOT, 1),
    MOVIE("20", Category.MOVIE, 2),
    PREMIERE("40", Category.PREMIERE, 3),
    DISCOUNT("50", Category.DISCOUNT, 4);

    private final String code;
    private final Category domainCategory;
    private final int priority; // Lower is higher priority

    LotteCinemaEventCategory(String code, Category domainCategory, int priority) {
        this.code = code;
        this.domainCategory = domainCategory;
        this.priority = priority;
    }

    public String getCode() {
        return code;
    }

    public Category getDomainCategory() {
        return domainCategory;
    }

    public int getPriority() {
        return priority;
    }

    public static Optional<LotteCinemaEventCategory> fromCode(String code) {
        return Arrays.stream(values())
                .filter(c -> c.code.equals(code))
                .findFirst();
    }
}
