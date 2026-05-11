package kr.daboyeo.backend.ingest;

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
import java.util.Objects;
import kr.daboyeo.backend.config.RootDotenvLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

@Component
public class TheaterLocationEnricher {

    private static final Logger logger = LoggerFactory.getLogger(TheaterLocationEnricher.class);
    private static final TypeReference<List<Map<String, Object>>> LOCATION_ROWS = new TypeReference<>() {
    };

    private final Map<String, TheaterLocation> locationsByProviderAndCode;
    private final Map<String, TheaterLocation> locationsByProviderAndName;

    @Autowired
    public TheaterLocationEnricher(ObjectMapper objectMapper) {
        this(loadLocations(objectMapper));
    }

    TheaterLocationEnricher(List<TheaterLocation> locations) {
        Map<String, TheaterLocation> byCode = new LinkedHashMap<>();
        Map<String, TheaterLocation> byName = new LinkedHashMap<>();
        for (TheaterLocation location : locations) {
            if (!location.code().isBlank() && !"9999".equals(location.code())) {
                byCode.put(providerKey(location.providerCode(), location.code()), location);
            }
            if (!location.name().isBlank()) {
                byName.putIfAbsent(providerKey(location.providerCode(), normalizeName(location.name())), location);
            }
        }
        this.locationsByProviderAndCode = Map.copyOf(byCode);
        this.locationsByProviderAndName = Map.copyOf(byName);
    }

    CollectorBundleIngestCommand.NormalizedBundle enrich(CollectorBundleIngestCommand.NormalizedBundle bundle) {
        List<CollectorBundleIngestCommand.TheaterRow> theaters = bundle.theaters().stream()
            .map(this::enrich)
            .toList();
        return new CollectorBundleIngestCommand.NormalizedBundle(
            bundle.movies(),
            theaters,
            bundle.screens(),
            bundle.showtimes()
        );
    }

    private CollectorBundleIngestCommand.TheaterRow enrich(CollectorBundleIngestCommand.TheaterRow theater) {
        TheaterLocation location = lookup(theater.providerCode(), theater.externalTheaterId(), theater.name());
        String resolvedAddress = firstNonBlank(theater.address(), location == null ? null : location.address());
        String resolvedRegionName = firstNonBlank(theater.regionName(), inferRegionName(resolvedAddress));
        BigDecimal resolvedLatitude = theater.latitude() == null && location != null ? location.latitude() : theater.latitude();
        BigDecimal resolvedLongitude = theater.longitude() == null && location != null ? location.longitude() : theater.longitude();

        if (Objects.equals(blankToNull(theater.address()), resolvedAddress)
            && Objects.equals(blankToNull(theater.regionName()), resolvedRegionName)
            && Objects.equals(theater.latitude(), resolvedLatitude)
            && Objects.equals(theater.longitude(), resolvedLongitude)) {
            return theater;
        }

        return new CollectorBundleIngestCommand.TheaterRow(
            theater.providerCode(),
            theater.externalTheaterId(),
            theater.name(),
            theater.regionCode(),
            resolvedRegionName,
            resolvedAddress,
            resolvedLatitude,
            resolvedLongitude,
            theater.raw()
        );
    }

    private TheaterLocation lookup(String providerCode, String externalTheaterId, String name) {
        TheaterLocation byCode = locationsByProviderAndCode.get(providerKey(providerCode, externalTheaterId));
        if (byCode != null) {
            return byCode;
        }
        return locationsByProviderAndName.get(providerKey(providerCode, normalizeName(name)));
    }

    private static List<TheaterLocation> loadLocations(ObjectMapper objectMapper) {
        Path theaterMapPath = resolveWorkspaceTheaterMapPath();
        if (theaterMapPath != null && Files.isRegularFile(theaterMapPath)) {
            try {
                List<TheaterLocation> locations = objectMapper.readValue(theaterMapPath.toFile(), LOCATION_ROWS).stream()
                    .map(TheaterLocationEnricher::toLocation)
                    .filter(location -> location != null)
                    .toList();
                logger.info("Loaded {} theater map rows for collector location enrichment from {}.", locations.size(), theaterMapPath);
                return locations;
            } catch (IOException exception) {
                logger.warn("Failed to load theater map for collector location enrichment from {}.", theaterMapPath, exception);
            }
        }

        try {
            ClassPathResource resource = new ClassPathResource("static/src/map/theaters.json");
            if (!resource.exists()) {
                logger.warn("Theater map file was not found, so collector theater location enrichment is disabled.");
                return List.of();
            }
            List<TheaterLocation> locations = objectMapper.readValue(resource.getInputStream(), LOCATION_ROWS).stream()
                .map(TheaterLocationEnricher::toLocation)
                .filter(location -> location != null)
                .toList();
            logger.info("Loaded {} bundled theater map rows for collector location enrichment.", locations.size());
            return locations;
        } catch (IOException exception) {
            logger.warn("Failed to load bundled theater map for collector location enrichment.", exception);
            return List.of();
        }
    }

    private static TheaterLocation toLocation(Map<String, Object> row) {
        String providerCode = normalizeProviderCode(text(row.get("provider")));
        String code = text(row.get("code"));
        String name = text(row.get("name"));
        BigDecimal latitude = decimalOrNull(row.get("lat"));
        BigDecimal longitude = decimalOrNull(row.get("lng"));
        String address = blankToNull(text(row.get("address")));
        if (providerCode.isBlank() || (code.isBlank() && name.isBlank()) || latitude == null || longitude == null) {
            return null;
        }
        return new TheaterLocation(providerCode, code, name, latitude, longitude, address);
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

    private static String normalizeProviderCode(String value) {
        return switch (blankToEmpty(value).toUpperCase(Locale.ROOT)) {
            case "LOTTE", "LOTTE_CINEMA" -> "LOTTE_CINEMA";
            case "MEGA", "MEGABOX" -> "MEGABOX";
            default -> "";
        };
    }

    private static String inferRegionName(String address) {
        String normalized = blankToNull(address);
        if (normalized == null) {
            return null;
        }

        return switch (normalized.split("\\s+", 2)[0]) {
            case "서울특별시", "서울시", "서울" -> "서울";
            case "경기도", "경기" -> "경기";
            case "인천광역시", "인천" -> "인천";
            case "부산광역시", "부산" -> "부산";
            case "대구광역시", "대구" -> "대구";
            case "대전광역시", "대전" -> "대전";
            case "광주광역시", "광주" -> "광주";
            case "울산광역시", "울산" -> "울산";
            case "세종특별자치시", "세종" -> "세종";
            case "강원특별자치도", "강원도", "강원" -> "강원";
            case "충청북도", "충북" -> "충북";
            case "충청남도", "충남" -> "충남";
            case "전북특별자치도", "전라북도", "전북" -> "전북";
            case "전라남도", "전남" -> "전남";
            case "경상북도", "경북" -> "경북";
            case "경상남도", "경남" -> "경남";
            case "제주특별자치도", "제주도", "제주" -> "제주";
            default -> null;
        };
    }

    private static String firstNonBlank(String first, String second) {
        String firstValue = blankToNull(first);
        return firstValue != null ? firstValue : blankToNull(second);
    }

    private static String providerKey(String providerCode, String value) {
        return normalizeProviderCode(providerCode) + "::" + blankToEmpty(value);
    }

    private static String normalizeName(String value) {
        return blankToEmpty(value)
            .replace("메가박스", "")
            .replace("롯데시네마", "")
            .replaceAll("\\s+", "")
            .replaceAll("[()\\-_/]", "")
            .toUpperCase(Locale.ROOT);
    }

    private static String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private static String blankToEmpty(String value) {
        return value == null ? "" : value.trim();
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
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

    record TheaterLocation(
        String providerCode,
        String code,
        String name,
        BigDecimal latitude,
        BigDecimal longitude,
        String address
    ) {
    }
}
