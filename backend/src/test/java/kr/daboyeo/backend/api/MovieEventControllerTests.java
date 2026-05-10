package kr.daboyeo.backend.api;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import kr.daboyeo.backend.domain.Category;
import kr.daboyeo.backend.security.PortfolioAccessGate;
import kr.daboyeo.backend.service.MovieEventService;
import kr.daboyeo.backend.service.MovieEventService.EventItem;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(MovieEventController.class)
class MovieEventControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private MovieEventService movieEventService;

    @MockitoBean
    private PortfolioAccessGate accessGate;

    @Test
    void eventsReturnsDbBackedDtoContract() throws Exception {
        given(movieEventService.getEvents("MEGA", Category.HOT, 4))
            .willReturn(
                List.of(
                    new EventItem(
                        10L,
                        "시사회 이벤트",
                        "MEGABOX",
                        "MEGABOX",
                        "event-10",
                        "HOT",
                        true,
                        "https://cdn.example/event.jpg",
                        "https://event.example/detail",
                        LocalDate.of(2026, 5, 1),
                        LocalDate.of(2026, 5, 31),
                        "D-21",
                        LocalDateTime.of(2026, 5, 10, 10, 0)
                    )
                )
            );

        mockMvc.perform(
                get("/api/events")
                    .queryParam("source", "MEGA")
                    .queryParam("category", "HOT")
                    .queryParam("limit", "4")
                    .accept(MediaType.APPLICATION_JSON)
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].id").value(10))
            .andExpect(jsonPath("$[0].title").value("시사회 이벤트"))
            .andExpect(jsonPath("$[0].source").value("MEGABOX"))
            .andExpect(jsonPath("$[0].category").value("HOT"))
            .andExpect(jsonPath("$[0].hot").value(true))
            .andExpect(jsonPath("$[0].imageUrl").value("https://cdn.example/event.jpg"))
            .andExpect(jsonPath("$[0].eventUrl").value("https://event.example/detail"));

        verify(movieEventService).getEvents("MEGA", Category.HOT, 4);
    }
}
