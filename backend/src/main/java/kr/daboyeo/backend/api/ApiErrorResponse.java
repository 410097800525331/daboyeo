package kr.daboyeo.backend.api;

import java.time.OffsetDateTime;
import java.util.List;

public record ApiErrorResponse(
    String code,
    String message,
    List<String> details,
    String path,
    String time
) {

    public static ApiErrorResponse of(String code, String message, List<String> details, String path) {
        return new ApiErrorResponse(code, message, details, path, OffsetDateTime.now().toString());
    }
}
