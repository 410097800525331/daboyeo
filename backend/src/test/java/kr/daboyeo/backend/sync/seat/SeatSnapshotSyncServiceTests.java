package kr.daboyeo.backend.sync.seat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import kr.daboyeo.backend.config.CollectorSyncProperties;
import kr.daboyeo.backend.sync.bridge.CollectorProvider;
import kr.daboyeo.backend.sync.bridge.PythonCollectorBridge;
import kr.daboyeo.backend.sync.bridge.SeatCollectionResult;
import org.junit.jupiter.api.Test;

class SeatSnapshotSyncServiceTests {

    @Test
    void syncsUpcomingSeatTargets() {
        CollectorSyncProperties properties = new CollectorSyncProperties();
        properties.setEnabled(true);
        properties.getSeats().setEnabled(true);
        properties.getSeats().setLimit(10);
        properties.getSeats().setLookaheadHours(6);

        SeatSnapshotRepository repository = mock(SeatSnapshotRepository.class);
        when(repository.findUpcomingTargets(any(), any(), anyInt())).thenReturn(
            List.of(
                new SeatSnapshotTarget(
                    1L,
                    CollectorProvider.LOTTE_CINEMA,
                    "LOTTE_CINEMA:1003:2026-04-24:02:3:20042000",
                    LocalDateTime.now().plusHours(1),
                    100,
                    35,
                    Map.of(
                        "cinema_id", "1003",
                        "play_date", "2026-04-24",
                        "screen_id", "02",
                        "play_sequence", "3"
                    )
                )
            )
        );

        PythonCollectorBridge bridge = mock(PythonCollectorBridge.class);
        when(bridge.collectSeatSnapshot(any())).thenReturn(new SeatCollectionResult(Map.of("seat_count", 100), List.of(Map.of("seat_label", "A1", "seat_status_code", "OK"))));

        SeatSnapshotPersistenceService persistenceService = mock(SeatSnapshotPersistenceService.class);

        SeatSnapshotSyncService service = new SeatSnapshotSyncService(properties, repository, bridge, persistenceService);
        service.syncSeatSnapshots();

        verify(repository, times(1)).findUpcomingTargets(any(), any(), anyInt());
        verify(bridge, times(1)).collectSeatSnapshot(any());
        verify(persistenceService, times(1)).persist(any(), any());
    }
}
