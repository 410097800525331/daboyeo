package kr.daboyeo.backend.sync.seat;

import java.util.Locale;
import java.util.Map;
import kr.daboyeo.backend.sync.bridge.CollectorProvider;
import org.springframework.stereotype.Component;

@Component
public class SeatSnapshotStatusNormalizer {

    public String normalize(CollectorProvider provider, Map<String, Object> seat) {
        return switch (provider) {
            case CGV -> normalizeCgv(seat);
            case LOTTE_CINEMA -> normalizeLotte(seat);
            case MEGABOX -> normalizeMegabox(seat);
        };
    }

    private static String normalizeCgv(Map<String, Object> seat) {
        String statusCode = text(seat.get("seat_status_code"));
        String statusName = text(seat.get("seat_status_name"));
        String sale = text(seat.get("seat_sale_yn"));
        if ("Y".equalsIgnoreCase(sale)) {
            return "available";
        }
        if (containsAny(statusCode, statusName, "SOLD", "BOOK", "예매", "판매완료")) {
            return "sold";
        }
        if (containsAny(statusCode, statusName, "LOCK", "BLOCK", "장애", "불가")) {
            return "unavailable";
        }
        return null;
    }

    private static String normalizeLotte(Map<String, Object> seat) {
        String statusCode = text(seat.get("seat_status_code"));
        String blockCode = text(seat.get("logical_block_code")) + " " + text(seat.get("physical_block_code"));
        if (containsAny(statusCode, blockCode, "S", "BOOK", "SALE", "예매")) {
            return "sold";
        }
        if (containsAny(statusCode, blockCode, "X", "BLOCK", "DISABLE", "불가")) {
            return "unavailable";
        }
        return "available";
    }

    private static String normalizeMegabox(Map<String, Object> seat) {
        String statusCode = text(seat.get("seat_status_code"));
        String rowStatus = text(seat.get("row_status_code"));
        if (containsAny(statusCode, rowStatus, "SALE", "BOOK", "SOLD", "RESERVE")) {
            return "sold";
        }
        if (containsAny(statusCode, rowStatus, "BLOCK", "DISABLE", "HIDE", "X")) {
            return "unavailable";
        }
        return "available";
    }

    public int countSpecialSeats(ListLike<Map<String, Object>> seats) {
        int count = 0;
        for (Map<String, Object> seat : seats) {
            String seatType = text(firstNonNull(seat, "seat_kind_name", "seat_type_code", "seat_class_code", "seat_sell_ty_cd"));
            if (!seatType.isBlank() && !containsAny(seatType, "", "STANDARD", "일반")) {
                count++;
            }
        }
        return count;
    }

    private static boolean containsAny(String first, String second, String... needles) {
        String combined = (first + " " + second).toUpperCase(Locale.ROOT);
        for (String needle : needles) {
            if (combined.contains(needle.toUpperCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    private static Object firstNonNull(Map<String, Object> values, String... keys) {
        for (String key : keys) {
            Object value = values.get(key);
            if (value != null && !String.valueOf(value).isBlank()) {
                return value;
            }
        }
        return "";
    }

    private static String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    @FunctionalInterface
    public interface ListLike<T> extends Iterable<T> {
    }
}
