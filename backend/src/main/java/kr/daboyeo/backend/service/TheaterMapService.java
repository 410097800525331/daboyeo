package kr.daboyeo.backend.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import kr.daboyeo.backend.config.RootDotenvLoader;
import kr.daboyeo.backend.repository.TheaterMapRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

@Service
public class TheaterMapService {

    private static final Logger logger = LoggerFactory.getLogger(TheaterMapService.class);
    private static final TypeReference<List<Map<String, Object>>> LOCATION_ROWS = new TypeReference<>() {
    };

    private final TheaterMapRepository theaterMapRepository;
    private final List<TheaterMapReference> fallbackReferences;

    @Autowired
    public TheaterMapService(TheaterMapRepository theaterMapRepository, ObjectMapper objectMapper) {
        this(theaterMapRepository, loadFallbackReferences(objectMapper));
    }

    TheaterMapService(TheaterMapRepository theaterMapRepository, List<TheaterMapReference> fallbackReferences) {
        this.theaterMapRepository = theaterMapRepository;
        this.fallbackReferences = fallbackReferences == null ? List.of() : List.copyOf(fallbackReferences);
    }

    public List<TheaterMapItem> findAll() {
        return mergedRows().values().stream()
            .map(TheaterMapService::toItem)
            .toList();
    }

    public List<TheaterSyncSource> findAllSyncSources() {
        return mergedRows().values().stream()
            .map(TheaterMapService::toSyncSource)
            .toList();
    }

    private Map<String, TheaterMapRowView> mergedRows() {
        Map<String, TheaterMapRowView> merged = new LinkedHashMap<>();
        for (TheaterMapReference reference : fallbackReferences) {
            TheaterMapRowView view = toView(reference);
            if (view == null) {
                continue;
            }
            merged.put(rowKey(view.providerCode(), view.externalTheaterId()), view);
        }
        for (TheaterMapRepository.TheaterMapRow row : theaterMapRepository.findAllWithCoordinates()) {
            TheaterMapRowView view = toView(row);
            if (view == null) {
                continue;
            }
            merged.put(rowKey(view.providerCode(), view.externalTheaterId()), view);
        }
        return merged;
    }

    private static TheaterMapItem toItem(TheaterMapRowView row) {
        String providerCode = normalizeProviderCode(row.providerCode());
        return new TheaterMapItem(
            normalizeProviderLabel(providerCode),
            providerCode,
            text(row.externalTheaterId()),
            text(row.name()),
            row.latitude(),
            row.longitude(),
            text(row.address())
        );
    }

    private static TheaterSyncSource toSyncSource(TheaterMapRowView row) {
        return new TheaterSyncSource(
            normalizeProviderCode(row.providerCode()),
            text(row.externalTheaterId()),
            text(row.name()),
            row.latitude(),
            row.longitude(),
            text(row.address())
        );
    }

    private static String normalizeProviderLabel(String providerCode) {
        return switch (providerCode) {
            case "LOTTE_CINEMA" -> "LOTTE";
            case "MEGABOX" -> "MEGA";
            default -> providerCode;
        };
    }

    private static String normalizeProviderCode(String providerCode) {
        if (providerCode == null) {
            return "";
        }
        return switch (providerCode.trim().toUpperCase(Locale.ROOT)) {
            case "LOTTE", "LOTTE_CINEMA" -> "LOTTE_CINEMA";
            case "MEGA", "MEGABOX" -> "MEGABOX";
            default -> "";
        };
    }

    private static TheaterMapRowView toView(TheaterMapRepository.TheaterMapRow row) {
        String providerCode = normalizeProviderCode(row.providerCode());
        String externalTheaterId = text(row.externalTheaterId());
        if (providerCode.isBlank() || externalTheaterId.isBlank() || row.latitude() == null || row.longitude() == null) {
            return null;
        }
        return new TheaterMapRowView(
            providerCode,
            externalTheaterId,
            text(row.name()),
            row.latitude(),
            row.longitude(),
            text(row.address())
        );
    }

    private static TheaterMapRowView toView(TheaterMapReference row) {
        String providerCode = normalizeProviderCode(row.providerCode());
        String externalTheaterId = text(row.externalTheaterId());
        if (providerCode.isBlank() || externalTheaterId.isBlank() || row.latitude() == null || row.longitude() == null) {
            return null;
        }
        return new TheaterMapRowView(
            providerCode,
            externalTheaterId,
            text(row.name()),
            row.latitude(),
            row.longitude(),
            text(row.address())
        );
    }

    private static String rowKey(String providerCode, String externalTheaterId) {
        return normalizeProviderCode(providerCode) + "::" + text(externalTheaterId);
    }

    private static List<TheaterMapReference> loadFallbackReferences(ObjectMapper objectMapper) {
        Path workspacePath = resolveWorkspaceTheaterMapPath();
        if (workspacePath != null && Files.isRegularFile(workspacePath)) {
            try {
                List<TheaterMapReference> locations = objectMapper.readValue(workspacePath.toFile(), LOCATION_ROWS).stream()
                    .map(TheaterMapService::toReference)
                    .filter(item -> item != null)
                    .toList();
                logger.info("Loaded {} theater map fallback rows from {}.", locations.size(), workspacePath);
                return locations;
            } catch (IOException exception) {
                logger.warn("Failed to load theater map fallback rows from {}.", workspacePath, exception);
            }
        }

        try {
            ClassPathResource resource = new ClassPathResource("static/src/map/theaters.json");
            if (!resource.exists()) {
                logger.warn("Bundled theater map fallback resource was not found.");
                return List.of();
            }
            List<TheaterMapReference> locations = objectMapper.readValue(resource.getInputStream(), LOCATION_ROWS).stream()
                .map(TheaterMapService::toReference)
                .filter(item -> item != null)
                .toList();
            logger.info("Loaded {} theater map fallback rows from bundled resource.", locations.size());
            return locations;
        } catch (IOException exception) {
            logger.warn("Failed to load bundled theater map fallback rows.", exception);
            return List.of();
        }
    }

    private static TheaterMapReference toReference(Map<String, Object> row) {
        String providerCode = normalizeProviderCode(text(row.get("provider")));
        String externalTheaterId = text(row.get("code"));
        String name = text(row.get("name"));
        BigDecimal latitude = decimalOrNull(row.get("lat"));
        BigDecimal longitude = decimalOrNull(row.get("lng"));
        String address = text(row.get("address"));
        if (providerCode.isBlank() || externalTheaterId.isBlank() || latitude == null || longitude == null) {
            return null;
        }
        return new TheaterMapReference(providerCode, externalTheaterId, name, latitude, longitude, address);
    }

    private static Path resolveWorkspaceTheaterMapPath() {
        Path start = Path.of(System.getProperty("user.dir", "."));
        Path dotenv = RootDotenvLoader.findDotenvPath(start);
        Path workspaceRoot = dotenv != null && dotenv.getParent() != null ? dotenv.getParent() : start.toAbsolutePath();
        Path frontendPath = workspaceRoot.resolve("frontend").resolve("src").resolve("map").resolve("theaters.json");
        if (Files.isRegularFile(frontendPath)) {
            return frontendPath;
        }
        return workspaceRoot.resolve("backend").resolve("src").resolve("main").resolve("resources").resolve("static").resolve("src").resolve("map").resolve("theaters.json");
    }

    private static BigDecimal decimalOrNull(Object value) {
        String text = text(value);
        if (text.isBlank()) {
            return null;
        }
        try {
            return new BigDecimal(text);
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private static String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    public record TheaterMapItem(
        String provider,
        String providerCode,
        String code,
        String name,
        BigDecimal lat,
        BigDecimal lng,
        String address
    ) {
    }

    public record TheaterSyncSource(
        String providerCode,
        String externalTheaterId,
        String name,
        BigDecimal latitude,
        BigDecimal longitude,
        String address
    ) {
    }

    record TheaterMapReference(
        String providerCode,
        String externalTheaterId,
        String name,
        BigDecimal latitude,
        BigDecimal longitude,
        String address
    ) {
    }

    record TheaterMapRowView(
        String providerCode,
        String externalTheaterId,
        String name,
        BigDecimal latitude,
        BigDecimal longitude,
        String address
    ) {
    }
}
