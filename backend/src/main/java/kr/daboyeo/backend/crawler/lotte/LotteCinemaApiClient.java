package kr.daboyeo.backend.crawler.lotte;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import lombok.RequiredArgsConstructor;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class LotteCinemaApiClient {

    private static final Logger logger = LoggerFactory.getLogger(LotteCinemaApiClient.class);
    private static final String API_URL = "https://www.lottecinema.co.kr/LCWS/Event/EventData.aspx";
    private static final String REFERER_URL = "https://www.lottecinema.co.kr/NLCHS/Event/DetailList?code=";

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    public String fetchEventPage(String categoryCode, int pageNo) {
        int maxRetries = 3;
        int retryCount = 0;

        while (retryCount < maxRetries) {
            try {
                return executeRequest(categoryCode, pageNo);
            } catch (Exception e) {
                retryCount++;
                logger.warn("[LOTTE] API request failed (attempt {}/{}): category={}, page={}, error={}",
                        retryCount, maxRetries, categoryCode, pageNo, e.getMessage());
                if (retryCount >= maxRetries) {
                    logger.error("[LOTTE] API request failed after {} attempts", maxRetries);
                    return null;
                }
                try { Thread.sleep(1000L * retryCount); } catch (InterruptedException ignored) {}
            }
        }
        return null;
    }

    private String executeRequest(String categoryCode, int pageNo) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        headers.set(HttpHeaders.USER_AGENT, "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36");
        headers.set(HttpHeaders.REFERER, REFERER_URL + categoryCode);
        headers.setAccept(List.of(MediaType.APPLICATION_JSON, MediaType.ALL));

        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("paramList", buildParamList(categoryCode, pageNo));

        HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(body, headers);

        ResponseEntity<byte[]> response = restTemplate.exchange(API_URL, HttpMethod.POST, request, byte[].class);

        if (response.getStatusCode() != HttpStatus.OK) {
            throw new RuntimeException("Unexpected status code: " + response.getStatusCode());
        }

        String result = convertBytesToString(response);
        if (result == null || result.isBlank()) {
            throw new RuntimeException("Empty response body");
        }

        return result;
    }

    private String buildParamList(String categoryCode, int pageNo) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("MethodName", "GetEventLists");
        params.put("channelType", "HO");
        params.put("osType", "W");
        params.put("osVersion", "Mozilla/5.0");
        params.put("EventClassificationCode", categoryCode);
        params.put("SearchText", "");
        params.put("CinemaID", "");
        params.put("PageNo", pageNo);
        params.put("PageSize", 100);
        params.put("MemberNo", "0");

        try {
            return objectMapper.writeValueAsString(params);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize paramList", e);
        }
    }

    private String convertBytesToString(ResponseEntity<byte[]> response) {
        byte[] bytes = response.getBody();
        if (bytes == null) return null;

        MediaType contentType = response.getHeaders().getContentType();
        Charset responseCharset = (contentType != null && contentType.getCharset() != null)
                ? contentType.getCharset() : null;

        logger.debug("[LOTTE] Response Headers: {}", response.getHeaders());
        logger.debug("[LOTTE] Content-Type: {}, Detected Charset: {}", contentType, responseCharset);

        // Fallback Priority: Response Charset -> UTF-8 -> EUC-KR -> CP949
        List<Charset> fallbacks = List.of(
                StandardCharsets.UTF_8,
                Charset.forName("EUC-KR"),
                Charset.forName("CP949")
        );

        if (responseCharset != null) {
            String result = new String(bytes, responseCharset);
            if (!isCorrupted(result)) {
                logger.debug("[LOTTE] Used response charset: {}", responseCharset);
                return result;
            }
        }

        for (Charset charset : fallbacks) {
            String result = new String(bytes, charset);
            if (!isCorrupted(result)) {
                logger.debug("[LOTTE] Used fallback charset: {}", charset);
                return result;
            }
        }

        // Ultimate fallback
        logger.warn("[LOTTE] All charsets failed, using UTF-8 as last resort");
        return new String(bytes, StandardCharsets.UTF_8);
    }

    private boolean isCorrupted(String text) {
        // Simple heuristic: if it contains too many replacement characters or looks like garbage
        // In real world, we might check for specific patterns or just try parsing as JSON
        if (text == null || text.isBlank()) return false;

        // If it's valid JSON and contains some Korean characters (if expected)
        // For now, let's just check if it contains the replacement char ''
        return text.contains("\uFFFD");
    }
}
