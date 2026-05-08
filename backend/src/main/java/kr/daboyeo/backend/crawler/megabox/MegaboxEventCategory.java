package kr.daboyeo.backend.crawler.megabox;

import kr.daboyeo.backend.domain.Category;

public enum MegaboxEventCategory {
    MEGA_PICK("CED03", Category.HOT, 1),
    MOVIE("CED01", Category.MOVIE, 2),
    PREMIERE("CED04", Category.PREMIERE, 3),
    DISCOUNT("CED05", Category.DISCOUNT, 4);

    private final String eventDivCd;
    private final Category domainCategory;
    private final int priority;

    MegaboxEventCategory(String eventDivCd, Category domainCategory, int priority) {
        this.eventDivCd = eventDivCd;
        this.domainCategory = domainCategory;
        this.priority = priority;
    }

    public String getEventDivCd() {
        return eventDivCd;
    }

    public Category getDomainCategory() {
        return domainCategory;
    }

    public int getPriority() {
        return priority;
    }
}
