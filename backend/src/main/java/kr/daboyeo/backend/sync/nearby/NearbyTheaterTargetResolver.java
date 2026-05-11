package kr.daboyeo.backend.sync.nearby;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import kr.daboyeo.backend.config.CollectorSyncProperties;
import kr.daboyeo.backend.domain.LiveMovieSearchCriteria;
import kr.daboyeo.backend.service.TheaterMapService;
import kr.daboyeo.backend.service.TheaterMapService.TheaterSyncSource;
import kr.daboyeo.backend.sync.bridge.CollectorProvider;
import org.springframework.stereotype.Component;

@Component
public class NearbyTheaterTargetResolver {

    private static final double EARTH_RADIUS_KM = 6371.0d;

    private final CollectorSyncProperties properties;
    private final TheaterMapService theaterMapService;

    public NearbyTheaterTargetResolver(CollectorSyncProperties properties, TheaterMapService theaterMapService) {
        this.properties = properties;
        this.theaterMapService = theaterMapService;
    }

    public Resolution resolve(LiveMovieSearchCriteria criteria) {
        Map<CollectorProvider, List<TheaterMapEntry>> grouped = new LinkedHashMap<>();
        double refreshRadiusKm = refreshRadiusKm(criteria);
        for (TheaterSyncSource source : theaterMapService.findAllSyncSources()) {
            TheaterMapEntry entry = toEntry(source);
            if (entry == null) {
                continue;
            }
            if (!supports(entry.provider())) {
                continue;
            }
            if (!matchesProviderFilter(criteria.providers(), entry.provider())) {
                continue;
            }
            double distanceKm = distanceKm(criteria.lat().doubleValue(), criteria.lng().doubleValue(), entry.latitude(), entry.longitude());
            if (distanceKm > refreshRadiusKm) {
                continue;
            }
            grouped.computeIfAbsent(entry.provider(), ignored -> new ArrayList<>())
                .add(entry.withDistanceKm(distanceKm));
        }

        int perProviderLimit = Math.max(1, properties.getShowtimes().getNearbyRefreshMaxTheatersPerProvider());
        List<TheaterMapEntry> lotteEntries = grouped.getOrDefault(CollectorProvider.LOTTE_CINEMA, List.of()).stream()
            .sorted(Comparator.comparingDouble(TheaterMapEntry::distanceKm))
            .limit(perProviderLimit)
            .toList();
        List<TheaterMapEntry> megaboxEntries = grouped.getOrDefault(CollectorProvider.MEGABOX, List.of()).stream()
            .sorted(Comparator.comparingDouble(TheaterMapEntry::distanceKm))
            .limit(perProviderLimit)
            .toList();

        return new Resolution(lotteEntries, megaboxEntries);
    }

    private static TheaterMapEntry toEntry(TheaterSyncSource row) {
        CollectorProvider provider;
        try {
            provider = CollectorProvider.fromValue(row.providerCode());
        } catch (IllegalArgumentException exception) {
            return null;
        }
        BigDecimal latitude = row.latitude();
        BigDecimal longitude = row.longitude();
        String code = text(row.externalTheaterId());
        String name = text(row.name());
        if (latitude == null || longitude == null || code.isBlank() || name.isBlank()) {
            return null;
        }
        return new TheaterMapEntry(provider, code, name, latitude.doubleValue(), longitude.doubleValue(), Double.MAX_VALUE);
    }

    private static boolean supports(CollectorProvider provider) {
        return provider == CollectorProvider.LOTTE_CINEMA || provider == CollectorProvider.MEGABOX;
    }

    private double refreshRadiusKm(LiveMovieSearchCriteria criteria) {
        BigDecimal configuredRadius = properties.getShowtimes().getNearbyRefreshRadiusKm();
        if (configuredRadius == null || configuredRadius.signum() <= 0) {
            return criteria.radiusKm().doubleValue();
        }
        return Math.min(criteria.radiusKm().doubleValue(), configuredRadius.doubleValue());
    }

    private static boolean matchesProviderFilter(List<String> requestedProviders, CollectorProvider provider) {
        if (requestedProviders == null || requestedProviders.isEmpty()) {
            return true;
        }
        return requestedProviders.stream()
            .map(value -> value == null ? "" : value.trim().toUpperCase(Locale.ROOT))
            .map(value -> switch (value) {
                case "LOTTE", "LOTTE_CINEMA" -> "LOTTE_CINEMA";
                case "MEGA", "MEGABOX" -> "MEGABOX";
                default -> value;
            })
            .anyMatch(value -> value.equals(provider.name()));
    }

    private static double distanceKm(double sourceLat, double sourceLng, double targetLat, double targetLng) {
        double dLat = Math.toRadians(targetLat - sourceLat);
        double dLng = Math.toRadians(targetLng - sourceLng);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
            + Math.cos(Math.toRadians(sourceLat)) * Math.cos(Math.toRadians(targetLat))
            * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return EARTH_RADIUS_KM * c;
    }

    private static String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    public record Resolution(
        List<TheaterMapEntry> lotteEntries,
        List<TheaterMapEntry> megaboxEntries
    ) {

        public boolean isEmpty() {
            return lotteEntries.isEmpty() && megaboxEntries.isEmpty();
        }
    }

    public record TheaterMapEntry(
        CollectorProvider provider,
        String externalTheaterId,
        String name,
        double latitude,
        double longitude,
        double distanceKm
    ) {
        TheaterMapEntry withDistanceKm(double value) {
            return new TheaterMapEntry(provider, externalTheaterId, name, latitude, longitude, value);
        }
    }
}
