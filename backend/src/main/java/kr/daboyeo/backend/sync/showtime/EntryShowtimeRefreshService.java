package kr.daboyeo.backend.sync.showtime;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;
import jakarta.annotation.PreDestroy;
import kr.daboyeo.backend.config.CollectorSyncProperties;
import kr.daboyeo.backend.sync.showtime.ShowtimeSyncService.ShowtimeSyncRunResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class EntryShowtimeRefreshService {

    private static final Logger logger = LoggerFactory.getLogger(EntryShowtimeRefreshService.class);
    private static final List<String> ENTRY_PROVIDERS = List.of("LOTTE_CINEMA", "MEGABOX");

    private final CollectorSyncProperties properties;
    private final ShowtimeSyncService showtimeSyncService;
    private final ExecutorService executorService = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "entry-showtime-refresh");
        thread.setDaemon(true);
        return thread;
    });
    private final AtomicReference<RefreshTask> activeTask = new AtomicReference<>();
    private volatile RefreshSnapshot lastCompleted;

    public EntryShowtimeRefreshService(
        CollectorSyncProperties properties,
        ShowtimeSyncService showtimeSyncService
    ) {
        this.properties = properties;
        this.showtimeSyncService = showtimeSyncService;
    }

    @PreDestroy
    public void shutdown() {
        executorService.shutdownNow();
    }

    public EntryShowtimeRefreshResponse requestRefresh(EntryShowtimeRefreshRequest request) {
        String reason = normalizeReason(request == null ? null : request.reason());
        if (!isEnabled()) {
            return response("disabled", reason, null, false, null, "Showtime refresh is disabled.");
        }

        RefreshSnapshot cached = lastCompleted;
        if (cached != null && cached.isFresh(ttl())) {
            return response("recent", reason, null, true, cached.result(), "Recent Lotte/Megabox refresh is still fresh.");
        }

        RefreshTask task = activeTask.get();
        if (task == null || task.future().isDone()) {
            RefreshTask candidate = new RefreshTask(UUID.randomUUID().toString(), Instant.now(), new CompletableFuture<>());
            if (activeTask.compareAndSet(task, candidate)) {
                task = candidate;
                submit(task);
            } else {
                task = activeTask.get();
            }
        }

        if (task == null) {
            return response("failed", reason, null, false, null, "Refresh could not be started.");
        }
        return await(task, reason);
    }

    private void submit(RefreshTask task) {
        executorService.execute(() -> {
            try {
                ShowtimeSyncRunResult result = showtimeSyncService.syncEntryShowtimes();
                if ("completed".equals(result.status())) {
                    lastCompleted = new RefreshSnapshot(Instant.now(), result);
                }
                task.future().complete(result);
            } catch (Exception exception) {
                logger.warn("Entry showtime refresh failed.", exception);
                task.future().completeExceptionally(exception);
            } finally {
                activeTask.compareAndSet(task, null);
            }
        });
    }

    private EntryShowtimeRefreshResponse await(RefreshTask task, String reason) {
        long maxWaitMillis = Math.max(0L, properties.getShowtimes().getEntryRefreshMaxWaitMillis());
        try {
            ShowtimeSyncRunResult result = maxWaitMillis == 0L
                ? task.future().getNow(null)
                : task.future().get(maxWaitMillis, TimeUnit.MILLISECONDS);
            if (result == null) {
                return response("running", reason, task.id(), false, null, "Lotte/Megabox refresh is running.");
            }
            String status = "completed".equals(result.status()) ? "completed" : result.status();
            return response(status, reason, task.id(), false, result, messageFor(result));
        } catch (TimeoutException exception) {
            return response("running", reason, task.id(), false, null, "Lotte/Megabox refresh is running.");
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return response("running", reason, task.id(), false, null, "Lotte/Megabox refresh is running.");
        } catch (ExecutionException exception) {
            logger.warn("Entry showtime refresh task failed.", exception.getCause());
            return response("failed", reason, task.id(), false, null, "Lotte/Megabox refresh failed. Check server logs.");
        }
    }

    private boolean isEnabled() {
        return properties.isEnabled()
            && properties.getShowtimes().isEnabled()
            && properties.getShowtimes().isEntryRefreshEnabled();
    }

    private Duration ttl() {
        return Duration.ofMinutes(Math.max(0, properties.getShowtimes().getEntryRefreshTtlMinutes()));
    }

    private static String normalizeReason(String reason) {
        String normalized = reason == null ? "" : reason.trim().toLowerCase();
        return switch (normalized) {
            case "ai-entry", "direct-compare", "recommend-start" -> normalized;
            default -> "entry";
        };
    }

    private static String messageFor(ShowtimeSyncRunResult result) {
        if ("completed".equals(result.status())) {
            return "Lotte/Megabox refresh completed.";
        }
        if ("running".equals(result.status())) {
            return "Another showtime refresh is already running.";
        }
        if ("disabled".equals(result.status())) {
            return "Showtime refresh is disabled.";
        }
        return "Showtime refresh did not run.";
    }

    private static EntryShowtimeRefreshResponse response(
        String status,
        String reason,
        String jobId,
        boolean cached,
        ShowtimeSyncRunResult result,
        String message
    ) {
        return new EntryShowtimeRefreshResponse(
            status,
            reason,
            jobId,
            ENTRY_PROVIDERS,
            true,
            cached,
            result == null ? null : result.dateCount(),
            result == null ? null : result.bundleRequests(),
            result == null ? null : result.movies(),
            result == null ? null : result.theaters(),
            result == null ? null : result.screens(),
            result == null ? null : result.showtimes(),
            message
        );
    }

    public record EntryShowtimeRefreshRequest(String reason) {
    }

    public record EntryShowtimeRefreshResponse(
        String status,
        String reason,
        String jobId,
        List<String> providers,
        boolean cgvDeferred,
        boolean cached,
        Integer dateCount,
        Integer bundleRequests,
        Integer movies,
        Integer theaters,
        Integer screens,
        Integer showtimes,
        String message
    ) {
    }

    private record RefreshTask(String id, Instant requestedAt, CompletableFuture<ShowtimeSyncRunResult> future) {
    }

    private record RefreshSnapshot(Instant completedAt, ShowtimeSyncRunResult result) {
        boolean isFresh(Duration ttl) {
            if (ttl.isZero() || ttl.isNegative()) {
                return false;
            }
            return completedAt.isAfter(Instant.now().minus(ttl));
        }
    }
}
