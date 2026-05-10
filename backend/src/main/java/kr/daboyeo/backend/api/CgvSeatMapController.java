package kr.daboyeo.backend.api;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.Pattern;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import kr.daboyeo.backend.security.PortfolioAccessGate;
import kr.daboyeo.backend.sync.bridge.PythonCollectorBridge;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/cgv")
public class CgvSeatMapController {

    private static final Logger logger = LoggerFactory.getLogger(CgvSeatMapController.class);

    private final PythonCollectorBridge collectorBridge;
    private final PortfolioAccessGate accessGate;

    public CgvSeatMapController(PythonCollectorBridge collectorBridge, PortfolioAccessGate accessGate) {
        this.collectorBridge = collectorBridge;
        this.accessGate = accessGate;
    }

    @GetMapping("/seat-layout")
    public ResponseEntity<?> seatLayout(
        @RequestParam(name = "siteNo")
        @Pattern(regexp = "\\d{4}", message = "siteNo must be 4 digits.")
        String siteNo,
        @RequestParam(name = "screeningDate")
        @Pattern(regexp = "\\d{8}", message = "screeningDate must be yyyyMMdd.")
        String screeningDate,
        @RequestParam(name = "screenNo")
        @Pattern(regexp = "\\d{1,4}", message = "screenNo must be numeric.")
        String screenNo,
        @RequestParam(name = "screenSequence")
        @Pattern(regexp = "\\d{1,4}", message = "screenSequence must be numeric.")
        String screenSequence,
        @RequestParam(name = "seatAreaNo", required = false)
        @Pattern(regexp = "\\d{0,12}", message = "seatAreaNo must be numeric.")
        String seatAreaNo,
        @RequestHeader(name = PortfolioAccessGate.ADMIN_TOKEN_HEADER, required = false) String token,
        HttpServletRequest request
    ) {
        accessGate.requireSeatLayoutAccess(token);
        String effectiveSeatAreaNo = seatAreaNo == null ? "" : seatAreaNo;
        try {
            Map<String, Object> layout = collectorBridge.collectCgvSeatLayout(
                siteNo,
                screeningDate,
                screenNo,
                screenSequence,
                effectiveSeatAreaNo
            );
            Map<String, Object> response = new LinkedHashMap<>(layout);
            response.put("source", "live-cgv-api");
            response.put("fetchedAt", OffsetDateTime.now().toString());
            response.put(
                "request",
                Map.of(
                    "siteNo", siteNo,
                    "screeningDate", screeningDate,
                    "screenNo", screenNo,
                    "screenSequence", screenSequence,
                    "seatAreaNo", effectiveSeatAreaNo
                )
            );
            return ResponseEntity.ok(response);
        } catch (IllegalStateException exception) {
            logger.warn(
                "CGV seat layout fetch failed siteNo={} screeningDate={} screenNo={} screenSequence={}: {}",
                siteNo,
                screeningDate,
                screenNo,
                screenSequence,
                exception.getClass().getSimpleName()
            );
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(
                ApiErrorResponse.of(
                    "CGV_SEAT_LAYOUT_UNAVAILABLE",
                    "CGV seat layout fetch failed. Check CGV_API_SECRET and booking keys.",
                    List.of(exception.getClass().getSimpleName()),
                    request.getRequestURI()
                )
            );
        }
    }
}
