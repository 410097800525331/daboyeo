package kr.daboyeo.backend.api;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.server.ResponseStatusException;

class ApiExceptionHandlerTests {

    @Test
    void responseStatusExceptionKeepsHttpStatus() {
        ApiExceptionHandler handler = new ApiExceptionHandler();
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/internal/ai-bridge/jobs");

        var response = handler.responseStatus(
            new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid AI bridge token."),
            request
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo("UNAUTHORIZED");
        assertThat(response.getBody().path()).isEqualTo("/api/internal/ai-bridge/jobs");
    }
}
