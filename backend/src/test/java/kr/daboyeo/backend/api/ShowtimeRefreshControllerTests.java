package kr.daboyeo.backend.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import kr.daboyeo.backend.security.PortfolioAccessGate;
import kr.daboyeo.backend.sync.showtime.EntryShowtimeRefreshService;
import kr.daboyeo.backend.sync.showtime.EntryShowtimeRefreshService.EntryShowtimeRefreshResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;

@WebMvcTest(ShowtimeRefreshController.class)
class ShowtimeRefreshControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private EntryShowtimeRefreshService refreshService;

    @MockitoBean
    private PortfolioAccessGate accessGate;

    @Test
    void refreshIsBlockedBeforeServiceWorkWhenGateRejects() throws Exception {
        doThrow(new ResponseStatusException(HttpStatus.NOT_FOUND))
            .when(accessGate).requireCollectionAccess(isNull());

        mockMvc.perform(
                post("/api/showtimes/refresh")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{}")
            )
            .andExpect(status().isNotFound());

        verify(refreshService, never()).requestRefresh(any());
    }

    @Test
    void refreshUsesAdminTokenHeaderWhenProvided() throws Exception {
        given(refreshService.requestRefresh(any()))
            .willReturn(new EntryShowtimeRefreshResponse(
                "running",
                "entry",
                "job-1",
                List.of("LOTTE_CINEMA", "MEGABOX"),
                true,
                null,
                null,
                null,
                null,
                null,
                null,
                "Lotte/Megabox refresh is running."
            ));

        mockMvc.perform(
                post("/api/showtimes/refresh")
                    .header(PortfolioAccessGate.ADMIN_TOKEN_HEADER, "admin-token")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"reason\":\"ai-entry\"}")
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("running"));

        verify(accessGate).requireCollectionAccess(eq("admin-token"));
        verify(refreshService).requestRefresh(any());
    }
}
