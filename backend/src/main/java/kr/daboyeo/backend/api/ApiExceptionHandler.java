package kr.daboyeo.backend.api;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import java.util.List;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.server.ResponseStatusException;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler({
        IllegalArgumentException.class,
        ConstraintViolationException.class,
        MethodArgumentNotValidException.class,
        HandlerMethodValidationException.class,
        MethodArgumentTypeMismatchException.class
    })
    public ResponseEntity<ApiErrorResponse> handleBadRequest(Exception exception, HttpServletRequest request) {
        return ResponseEntity.badRequest().body(
            ApiErrorResponse.of(
                "BAD_REQUEST",
                "요청 파라미터를 확인해.",
                List.of(exception.getMessage()),
                request.getRequestURI()
            )
        );
    }

    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<ApiErrorResponse> dataUnavailable(DataAccessException exception, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(
            ApiErrorResponse.of(
                "DATA_UNAVAILABLE",
                "데이터를 불러오지 못했어. 수집/DB 상태를 확인해줘.",
                List.of(exception.getClass().getSimpleName()),
                request.getRequestURI()
            )
        );
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ApiErrorResponse> responseStatus(ResponseStatusException exception, HttpServletRequest request) {
        HttpStatusCode status = exception.getStatusCode();
        return ResponseEntity.status(status).body(
            ApiErrorResponse.of(
                codeFor(status),
                messageFor(exception),
                List.of(exception.getClass().getSimpleName()),
                request.getRequestURI()
            )
        );
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ApiErrorResponse> serverState(IllegalStateException exception, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
            ApiErrorResponse.of(
                "INTERNAL_ERROR",
                "서버 처리 중 오류가 발생했다.",
                List.of(exception.getClass().getSimpleName()),
                request.getRequestURI()
            )
        );
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleUnexpected(Exception exception, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
            ApiErrorResponse.of(
                "INTERNAL_ERROR",
                "서버 처리 중 오류가 발생했다.",
                List.of(exception.getClass().getSimpleName()),
                request.getRequestURI()
            )
        );
    }

    private String codeFor(HttpStatusCode status) {
        if (status.value() == HttpStatus.UNAUTHORIZED.value()) {
            return "UNAUTHORIZED";
        }
        if (status.value() == HttpStatus.NOT_FOUND.value()) {
            return "NOT_FOUND";
        }
        if (status.value() == HttpStatus.SERVICE_UNAVAILABLE.value()) {
            return "SERVICE_UNAVAILABLE";
        }
        if (status.is4xxClientError()) {
            return "BAD_REQUEST";
        }
        return "INTERNAL_ERROR";
    }

    private String messageFor(ResponseStatusException exception) {
        String reason = exception.getReason();
        if (reason == null || reason.isBlank()) {
            return "Request could not be processed.";
        }
        return reason;
    }
}
