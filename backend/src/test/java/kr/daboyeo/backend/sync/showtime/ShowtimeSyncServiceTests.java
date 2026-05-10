package kr.daboyeo.backend.sync.showtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import kr.daboyeo.backend.config.CollectorSyncProperties;
import kr.daboyeo.backend.ingest.CollectorBundleIngestCommand;
import kr.daboyeo.backend.ingest.CollectorBundlePersistenceService;
import kr.daboyeo.backend.sync.bridge.CollectorProvider;
import kr.daboyeo.backend.sync.bridge.PythonCollectorBridge;
import kr.daboyeo.backend.sync.bridge.ShowtimeCollectionRequest;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class ShowtimeSyncServiceTests {

    @Test
    void syncsEveryConfiguredProviderAndDateOffset() {
        CollectorSyncProperties properties = new CollectorSyncProperties();
        properties.setEnabled(true);
        properties.setTimezone("Asia/Seoul");
        properties.getShowtimes().setEnabled(true);
        properties.getShowtimes().setIncludeCgv(true);
        properties.getShowtimes().setAutoDiscoveryEnabled(false);
        properties.getShowtimes().setDateOffsetDays(List.of(0, 1));

        CollectorSyncProperties.CgvTarget cgvTarget = new CollectorSyncProperties.CgvTarget();
        cgvTarget.setSiteNo("0013");
        cgvTarget.setMovieNo("20042000");
        properties.getShowtimes().setCgvTargets(List.of(cgvTarget));

        CollectorSyncProperties.LotteTarget lotteTarget = new CollectorSyncProperties.LotteTarget();
        lotteTarget.setCinemaSelector("1|101|0001");
        lotteTarget.setRepresentationMovieCode("L100");
        properties.getShowtimes().setLotteTargets(List.of(lotteTarget));

        CollectorSyncProperties.MegaboxTarget megaboxTarget = new CollectorSyncProperties.MegaboxTarget();
        megaboxTarget.setMovieNo("240001");
        megaboxTarget.setAreaCode("11");
        properties.getShowtimes().setMegaboxTargets(List.of(megaboxTarget));

        PythonCollectorBridge bridge = mock(PythonCollectorBridge.class);
        when(bridge.collectShowtimeBundle(any())).thenReturn(Map.of());

        CollectorBundlePersistenceService persistenceService = mock(CollectorBundlePersistenceService.class);
        when(persistenceService.persist(any(), any(), eq(false))).thenReturn(new CollectorBundleIngestCommand.IngestResult(1, 1, 1, 1));

        ShowtimeCleanupService cleanupService = mock(ShowtimeCleanupService.class);
        when(cleanupService.cleanupForBaseDate(any()))
            .thenReturn(new ShowtimeCleanupRepository.CleanupCounts(0, 0, 0));

        ShowtimeSyncService service = new ShowtimeSyncService(properties, bridge, persistenceService, cleanupService);
        service.syncDailyShowtimes();

        ArgumentCaptor<ShowtimeCollectionRequest> requestCaptor = ArgumentCaptor.forClass(ShowtimeCollectionRequest.class);
        verify(bridge, times(6)).collectShowtimeBundle(requestCaptor.capture());
        verify(persistenceService, times(6)).persist(any(), any(), eq(false));
        verify(cleanupService, times(1)).cleanupForBaseDate(any());

        List<ShowtimeCollectionRequest> requests = new ArrayList<>(requestCaptor.getAllValues());
        assertThat(requests).extracting(ShowtimeCollectionRequest::provider)
            .containsExactlyInAnyOrder(
                CollectorProvider.CGV,
                CollectorProvider.CGV,
                CollectorProvider.LOTTE_CINEMA,
                CollectorProvider.LOTTE_CINEMA,
                CollectorProvider.MEGABOX,
                CollectorProvider.MEGABOX
            );
        assertThat(requests).extracting(ShowtimeCollectionRequest::playDate).hasSize(6).allSatisfy(date -> assertThat(date).isAfterOrEqualTo(LocalDate.now().minusDays(1)));
    }

    @Test
    void dailySyncExcludesCgvByDefault() {
        CollectorSyncProperties properties = new CollectorSyncProperties();
        properties.setEnabled(true);
        properties.setTimezone("Asia/Seoul");
        properties.getShowtimes().setEnabled(true);
        properties.getShowtimes().setAutoDiscoveryEnabled(false);
        properties.getShowtimes().setDateOffsetDays(List.of(0));

        CollectorSyncProperties.CgvTarget cgvTarget = new CollectorSyncProperties.CgvTarget();
        cgvTarget.setSiteNo("0013");
        cgvTarget.setMovieNo("20042000");
        properties.getShowtimes().setCgvTargets(List.of(cgvTarget));

        CollectorSyncProperties.LotteTarget lotteTarget = new CollectorSyncProperties.LotteTarget();
        lotteTarget.setCinemaSelector("1|101|0001");
        lotteTarget.setRepresentationMovieCode("L100");
        properties.getShowtimes().setLotteTargets(List.of(lotteTarget));

        PythonCollectorBridge bridge = mock(PythonCollectorBridge.class);
        when(bridge.collectShowtimeBundle(any())).thenReturn(Map.of());

        CollectorBundlePersistenceService persistenceService = mock(CollectorBundlePersistenceService.class);
        when(persistenceService.persist(any(), any(), eq(false))).thenReturn(new CollectorBundleIngestCommand.IngestResult(1, 1, 1, 1));

        ShowtimeCleanupService cleanupService = mock(ShowtimeCleanupService.class);
        when(cleanupService.cleanupForBaseDate(any()))
            .thenReturn(new ShowtimeCleanupRepository.CleanupCounts(0, 0, 0));

        ShowtimeSyncService service = new ShowtimeSyncService(properties, bridge, persistenceService, cleanupService);
        service.syncDailyShowtimes();

        ArgumentCaptor<ShowtimeCollectionRequest> requestCaptor = ArgumentCaptor.forClass(ShowtimeCollectionRequest.class);
        verify(bridge, times(1)).collectShowtimeBundle(requestCaptor.capture());
        assertThat(requestCaptor.getAllValues()).extracting(ShowtimeCollectionRequest::provider)
            .containsExactly(CollectorProvider.LOTTE_CINEMA);
        verify(cleanupService, times(1)).cleanupForBaseDate(any());
    }

    @Test
    void autoDiscoverySyncsWorkingLotteAndMegaboxTargets() {
        CollectorSyncProperties properties = new CollectorSyncProperties();
        properties.setEnabled(true);
        properties.setTimezone("Asia/Seoul");
        properties.getShowtimes().setEnabled(true);
        properties.getShowtimes().setAutoDiscoveryEnabled(true);
        properties.getShowtimes().setDateOffsetDays(List.of(0));
        properties.getShowtimes().setDiscoveryMovieLimit(2);
        properties.getShowtimes().setDiscoveryLotteCinemaLimit(1);
        properties.getShowtimes().setDiscoveryLotteMovieTargetLimit(2);
        properties.getShowtimes().setDiscoveryMegaboxBundleLimit(1);
        properties.getShowtimes().setLottePreferredCinemaNames(List.of("위례"));
        properties.getShowtimes().setMegaboxAreaCodes(List.of("30"));

        PythonCollectorBridge bridge = mock(PythonCollectorBridge.class);
        when(bridge.collectShowtimeDiscovery(eq(CollectorProvider.LOTTE_CINEMA), any()))
            .thenReturn(new PythonCollectorBridge.ProviderDiscoveryPayload(
                List.of(
                    Map.of("cinema_selector", "1|0002|3037", "representation_movie_code", "24136"),
                    Map.of("cinema_selector", "1|0002|3037", "representation_movie_code", "24140")
                )
            ));
        when(bridge.collectShowtimeDiscovery(eq(CollectorProvider.MEGABOX), any()))
            .thenReturn(new PythonCollectorBridge.ProviderDiscoveryPayload(
                List.of(Map.of("movie_no", "26022300", "area_code", "30"))
            ));
        when(bridge.collectShowtimeBundle(any())).thenReturn(Map.of());

        CollectorBundlePersistenceService persistenceService = mock(CollectorBundlePersistenceService.class);
        when(persistenceService.persist(any(), any(), eq(false)))
            .thenReturn(new CollectorBundleIngestCommand.IngestResult(1, 1, 1, 1));

        ShowtimeCleanupService cleanupService = mock(ShowtimeCleanupService.class);
        when(cleanupService.cleanupForBaseDate(any()))
            .thenReturn(new ShowtimeCleanupRepository.CleanupCounts(0, 0, 0));

        ShowtimeSyncService service = new ShowtimeSyncService(properties, bridge, persistenceService, cleanupService);
        service.syncDailyShowtimes();

        ArgumentCaptor<ShowtimeCollectionRequest> requestCaptor = ArgumentCaptor.forClass(ShowtimeCollectionRequest.class);
        verify(bridge, times(3)).collectShowtimeBundle(requestCaptor.capture());
        List<ShowtimeCollectionRequest> requests = requestCaptor.getAllValues();
        assertThat(requests).extracting(ShowtimeCollectionRequest::provider)
            .containsExactlyInAnyOrder(CollectorProvider.LOTTE_CINEMA, CollectorProvider.LOTTE_CINEMA, CollectorProvider.MEGABOX);
        assertThat(requests).extracting(ShowtimeCollectionRequest::representationMovieCode).contains("24136", "24140");
        assertThat(requests).extracting(ShowtimeCollectionRequest::areaCode).contains("30");
        verify(cleanupService, times(1)).cleanupForBaseDate(any());
    }

    @Test
    void entrySyncExcludesCgvAndSkipsCleanup() {
        CollectorSyncProperties properties = new CollectorSyncProperties();
        properties.setEnabled(true);
        properties.setTimezone("Asia/Seoul");
        properties.getShowtimes().setEnabled(true);
        properties.getShowtimes().setAutoDiscoveryEnabled(false);
        properties.getShowtimes().setDateOffsetDays(List.of(0, 1, 2));
        properties.getShowtimes().setEntryRefreshMaxDates(2);

        CollectorSyncProperties.CgvTarget cgvTarget = new CollectorSyncProperties.CgvTarget();
        cgvTarget.setSiteNo("0013");
        cgvTarget.setMovieNo("20042000");
        properties.getShowtimes().setCgvTargets(List.of(cgvTarget));

        CollectorSyncProperties.LotteTarget lotteTarget = new CollectorSyncProperties.LotteTarget();
        lotteTarget.setCinemaSelector("1|101|0001");
        lotteTarget.setRepresentationMovieCode("L100");
        properties.getShowtimes().setLotteTargets(List.of(lotteTarget));

        CollectorSyncProperties.MegaboxTarget megaboxTarget = new CollectorSyncProperties.MegaboxTarget();
        megaboxTarget.setMovieNo("240001");
        megaboxTarget.setAreaCode("11");
        properties.getShowtimes().setMegaboxTargets(List.of(megaboxTarget));

        PythonCollectorBridge bridge = mock(PythonCollectorBridge.class);
        when(bridge.collectShowtimeBundle(any())).thenReturn(Map.of());

        CollectorBundlePersistenceService persistenceService = mock(CollectorBundlePersistenceService.class);
        when(persistenceService.persist(any(), any(), eq(false))).thenReturn(new CollectorBundleIngestCommand.IngestResult(1, 1, 1, 1));

        ShowtimeCleanupService cleanupService = mock(ShowtimeCleanupService.class);

        ShowtimeSyncService service = new ShowtimeSyncService(properties, bridge, persistenceService, cleanupService);
        ShowtimeSyncService.ShowtimeSyncRunResult result = service.syncEntryShowtimes();

        ArgumentCaptor<ShowtimeCollectionRequest> requestCaptor = ArgumentCaptor.forClass(ShowtimeCollectionRequest.class);
        verify(bridge, times(4)).collectShowtimeBundle(requestCaptor.capture());
        verify(cleanupService, times(0)).cleanupForBaseDate(any());

        assertThat(result.status()).isEqualTo("completed");
        assertThat(result.dateCount()).isEqualTo(2);
        assertThat(requestCaptor.getAllValues()).extracting(ShowtimeCollectionRequest::provider)
            .containsExactlyInAnyOrder(
                CollectorProvider.LOTTE_CINEMA,
                CollectorProvider.LOTTE_CINEMA,
                CollectorProvider.MEGABOX,
                CollectorProvider.MEGABOX
            );
    }

    @Test
    void discoveryFailureDoesNotPreventCleanupOrOtherProviders() {
        CollectorSyncProperties properties = new CollectorSyncProperties();
        properties.setEnabled(true);
        properties.setTimezone("Asia/Seoul");
        properties.getShowtimes().setEnabled(true);
        properties.getShowtimes().setAutoDiscoveryEnabled(true);
        properties.getShowtimes().setDateOffsetDays(List.of(0));

        PythonCollectorBridge bridge = mock(PythonCollectorBridge.class);
        when(bridge.collectShowtimeDiscovery(eq(CollectorProvider.LOTTE_CINEMA), any()))
            .thenThrow(new IllegalStateException("IncompleteRead"));
        when(bridge.collectShowtimeDiscovery(eq(CollectorProvider.MEGABOX), any()))
            .thenReturn(new PythonCollectorBridge.ProviderDiscoveryPayload(
                List.of(Map.of("movie_no", "26022300", "area_code", "30"))
            ));
        when(bridge.collectShowtimeBundle(any())).thenReturn(Map.of());

        CollectorBundlePersistenceService persistenceService = mock(CollectorBundlePersistenceService.class);
        when(persistenceService.persist(any(), any(), eq(false)))
            .thenReturn(new CollectorBundleIngestCommand.IngestResult(1, 1, 1, 1));

        ShowtimeCleanupService cleanupService = mock(ShowtimeCleanupService.class);
        when(cleanupService.cleanupForBaseDate(any()))
            .thenReturn(new ShowtimeCleanupRepository.CleanupCounts(0, 0, 0));

        ShowtimeSyncService service = new ShowtimeSyncService(properties, bridge, persistenceService, cleanupService);
        service.syncDailyShowtimes();

        verify(bridge, times(1)).collectShowtimeDiscovery(eq(CollectorProvider.LOTTE_CINEMA), any());
        verify(bridge, times(1)).collectShowtimeDiscovery(eq(CollectorProvider.MEGABOX), any());
        verify(bridge, times(1)).collectShowtimeBundle(any());
        verify(cleanupService, times(1)).cleanupForBaseDate(any());
    }
}
