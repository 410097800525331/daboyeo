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
                    CollectorProvider.CGV,
                    "CGV:0013:2026-04-24:02:3:20042000",
                    LocalDateTime.now().plusHours(1),
                    100,
                    35,
                    Map.of(
                        "site_no", "0013",
                        "scn_ymd", "20260424",
                        "scns_no", "02",
                        "scn_sseq", "3"
                    )
                )
            )
        );

        PythonCollectorBridge bridge = mock(PythonCollectorBridge.class);
        when(bridge.collectSeatSnapshot(any())).thenReturn(new SeatCollectionResult(Map.of("seat_count", 100), List.of(Map.of("seat_label", "A1", "seat_sale_yn", "Y"))));

        SeatSnapshotPersistenceService persistenceService = mock(SeatSnapshotPersistenceService.class);

        SeatSnapshotSyncService service = new SeatSnapshotSyncService(properties, repository, bridge, persistenceService);
        service.syncSeatSnapshots();

        verify(repository, times(1)).findUpcomingTargets(any(), any(), anyInt());
        verify(bridge, times(1)).collectSeatSnapshot(any());
        verify(persistenceService, times(1)).persist(any(), any());
    }
}
