package kr.daboyeo.backend.domain;

public enum CinemaType {
    CGV("CGV"),
    LOTTE("LOTTE_CINEMA"),
    MEGABOX("MEGABOX");

    private final String code;

    CinemaType(String code) {
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
