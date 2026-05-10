package kr.daboyeo.backend.api;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;
import kr.daboyeo.backend.security.PortfolioAccessGate;
import kr.daboyeo.backend.sync.bridge.PythonCollectorBridge;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(CgvSeatMapController.class)
class CgvSeatMapControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PythonCollectorBridge collectorBridge;

    @MockitoBean
    private PortfolioAccessGate accessGate;

    @Test
    void seatLayoutReturnsLiveCollectorPayload() throws Exception {
        given(collectorBridge.collectCgvSeatLayout(eq("0056"), eq("20260429"), eq("002"), eq("3"), eq("")))
            .willReturn(
                Map.of(
                    "provider", "CGV",
                    "siteNo", "0056",
                    "screeningDate", "20260429",
                    "screenNo", "002",
                    "screenSequence", "3",
                    "totalSeatCount", 1,
                    "remainingSeatCount", 1,
                    "seats", List.of(Map.of("id", "A1", "label", "A1", "x", 1, "y", 1, "w", 2, "h", 2, "status", "available"))
                )
            );

        mockMvc.perform(
                get("/api/cgv/seat-layout")
                    .queryParam("siteNo", "0056")
                    .queryParam("screeningDate", "20260429")
                    .queryParam("screenNo", "002")
                    .queryParam("screenSequence", "3")
                    .accept(MediaType.APPLICATION_JSON)
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.source").value("live-cgv-api"))
            .andExpect(jsonPath("$.request.siteNo").value("0056"))
            .andExpect(jsonPath("$.seats[0].status").value("available"))
            .andExpect(jsonPath("$.fetchedAt").isString());
    }

    @Test
    void seatLayoutRejectsInvalidBookingKey() throws Exception {
        mockMvc.perform(
                get("/api/cgv/seat-layout")
                    .queryParam("siteNo", "gangnam")
                    .queryParam("screeningDate", "20260429")
                    .queryParam("screenNo", "002")
                    .queryParam("screenSequence", "3")
                    .accept(MediaType.APPLICATION_JSON)
            )
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("BAD_REQUEST"));
    }

    @Test
    void seatLayoutReturnsOpaqueGatewayFailure() throws Exception {
        given(collectorBridge.collectCgvSeatLayout(eq("0056"), eq("20260429"), eq("002"), eq("3"), eq("")))
            .willThrow(new IllegalStateException("secret or upstream failure"));

        mockMvc.perform(
                get("/api/cgv/seat-layout")
                    .queryParam("siteNo", "0056")
                    .queryParam("screeningDate", "20260429")
                    .queryParam("screenNo", "002")
                    .queryParam("screenSequence", "3")
                    .accept(MediaType.APPLICATION_JSON)
            )
            .andExpect(status().isBadGateway())
            .andExpect(jsonPath("$.code").value("CGV_SEAT_LAYOUT_UNAVAILABLE"))
            .andExpect(jsonPath("$.details[0]").value("IllegalStateException"));
    }
}
