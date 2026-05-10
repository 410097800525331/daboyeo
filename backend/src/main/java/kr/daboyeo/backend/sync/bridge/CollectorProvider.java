package kr.daboyeo.backend.sync.bridge;

import java.util.Locale;

public enum CollectorProvider {
    CGV,
    LOTTE_CINEMA,
    MEGABOX;

    public static CollectorProvider fromValue(String value) {
        return switch (value == null ? "" : value.trim().toUpperCase(Locale.ROOT)) {
            case "CGV" -> CGV;
            case "LOTTE", "LOTTE_CINEMA" -> LOTTE_CINEMA;
            case "MEGA", "MEGABOX" -> MEGABOX;
            default -> throw new IllegalArgumentException("Unsupported provider: " + value);
        };
    }
}
