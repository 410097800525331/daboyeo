package kr.daboyeo.backend.api;

import kr.daboyeo.backend.sync.showtime.EntryShowtimeRefreshService;
import kr.daboyeo.backend.sync.showtime.EntryShowtimeRefreshService.EntryShowtimeRefreshRequest;
import kr.daboyeo.backend.sync.showtime.EntryShowtimeRefreshService.EntryShowtimeRefreshResponse;
import kr.daboyeo.backend.security.PortfolioAccessGate;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class ShowtimeRefreshController {

    private final EntryShowtimeRefreshService refreshService;
    private final PortfolioAccessGate accessGate;

    public ShowtimeRefreshController(EntryShowtimeRefreshService refreshService, PortfolioAccessGate accessGate) {
        this.refreshService = refreshService;
        this.accessGate = accessGate;
    }

    @PostMapping("/showtimes/refresh")
    public EntryShowtimeRefreshResponse refreshShowtimes(
        @RequestHeader(name = PortfolioAccessGate.ADMIN_TOKEN_HEADER, required = false) String token,
        @RequestBody(required = false) EntryShowtimeRefreshRequest request
    ) {
        accessGate.requireCollectionAccess(token);
        return refreshService.requestRefresh(request);
    }
}
