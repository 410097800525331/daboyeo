package kr.daboyeo.backend.crawler.megabox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class MegaboxEventApiClient {
    private static final Logger logger = LoggerFactory.getLogger(MegaboxEventApiClient.class);

    private static final String MOBILE_EVENT_PAGE_URL = "https://m.megabox.co.kr/event/megabox";
    private static final String MOBILE_EVENT_LIST_API_URL = "https://m.megabox.co.kr/on/oh/ohe/Event/eventMngDiv.do";
    private static final String MOBILE_ROBOTS_URL = "https://m.megabox.co.kr/robots.txt";
    private static final String DESKTOP_ROBOTS_URL = "https://www.megabox.co.kr/robots.txt";

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    public MegaboxEventApiClient(RestTemplate restTemplate, ObjectMapper objectMapper) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
    }

    public String fetchPublicEventPage() {
        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.USER_AGENT, defaultUserAgent());
        headers.setAccept(List.of(MediaType.TEXT_HTML, MediaType.ALL));

        ResponseEntity<byte[]> response = restTemplate.exchange(
                MOBILE_EVENT_PAGE_URL,
                HttpMethod.GET,
                new HttpEntity<>(headers),
                byte[].class
        );
        return convertBytesToText(response);
    }

    public String fetchEventListFragment(String eventDivCd) {
        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.USER_AGENT, defaultUserAgent());
        headers.set(HttpHeaders.REFERER, MOBILE_EVENT_PAGE_URL);
        headers.set("X-Requested-With", "XMLHttpRequest");
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(List.of(MediaType.TEXT_HTML, MediaType.ALL));

        String payload = buildPayload(eventDivCd);
        HttpEntity<String> request = new HttpEntity<>(payload, headers);
        ResponseEntity<byte[]> response = restTemplate.exchange(
                MOBILE_EVENT_LIST_API_URL,
                HttpMethod.POST,
                request,
                byte[].class
        );
        return convertBytesToText(response);
    }

    public String fetchRobotsTxt(boolean mobile) {
        String target = mobile ? MOBILE_ROBOTS_URL : DESKTOP_ROBOTS_URL;
        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.USER_AGENT, defaultUserAgent());
        headers.setAccept(List.of(MediaType.TEXT_PLAIN, MediaType.ALL));

        ResponseEntity<byte[]> response = restTemplate.exchange(
                target,
                HttpMethod.GET,
                new HttpEntity<>(headers),
                byte[].class
        );
        return convertBytesToText(response);
    }

    private String buildPayload(String eventDivCd) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("totCnt", 1);
        payload.put("currentPage", "1");
        payload.put("recordCountPerPage", "10");
        payload.put("eventTitle", "");
        payload.put("eventDivCd", eventDivCd);
        payload.put("eventTyCd", "");
        payload.put("eventStatCd", "ONG");
        payload.put("orderReqCd", "ONGlist");

        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to build Megabox request payload", e);
        }
    }

    private String convertBytesToText(ResponseEntity<byte[]> response) {
        byte[] bytes = response.getBody();
        if (bytes == null) {
            return "";
        }

        Charset headerCharset = response.getHeaders().getContentType() != null
                ? response.getHeaders().getContentType().getCharset()
                : null;

        List<Charset> candidates = headerCharset == null
                ? List.of(StandardCharsets.UTF_8, Charset.forName("EUC-KR"), Charset.forName("CP949"))
                : List.of(headerCharset, StandardCharsets.UTF_8, Charset.forName("EUC-KR"), Charset.forName("CP949"));

        String bestText = new String(bytes, StandardCharsets.UTF_8);
        int bestScore = Integer.MIN_VALUE;

        for (Charset charset : candidates) {
            String candidate = new String(bytes, charset);
            int score = readabilityScore(candidate);
            if (score > bestScore) {
                bestScore = score;
                bestText = candidate;
            }
        }

        return bestText;
    }

    private int readabilityScore(String text) {
        if (text == null || text.isBlank()) {
            return Integer.MIN_VALUE;
        }

        int score = 0;
        for (char ch : text.toCharArray()) {
            if (ch == '\uFFFD') {
                score -= 20;
            } else if ((ch >= '가' && ch <= '힣') || Character.isAlphabetic(ch)) {
                score += 1;
            }
        }
        return score;
    }

    private String defaultUserAgent() {
        return "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
                + "(KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";
    }
}
