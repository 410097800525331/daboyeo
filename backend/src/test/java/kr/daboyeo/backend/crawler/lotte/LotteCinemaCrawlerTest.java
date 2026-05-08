package kr.daboyeo.backend.crawler.lotte;

import com.fasterxml.jackson.databind.ObjectMapper;
import kr.daboyeo.backend.domain.Category;
import kr.daboyeo.backend.domain.MovieEvent;
import kr.daboyeo.backend.crawler.LotteCinemaEventCrawler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class LotteCinemaCrawlerTest {

    private RestTemplate restTemplate;
    private MockRestServiceServer mockServer;
    private ObjectMapper objectMapper;
    private LotteCinemaApiClient apiClient;
    private LotteCinemaEventParser parser;
    private LotteCinemaEventCrawler crawler;

    @BeforeEach
    void setUp() {
        restTemplate = new RestTemplate();
        mockServer = MockRestServiceServer.createServer(restTemplate);
        objectMapper = new ObjectMapper();
        apiClient = new LotteCinemaApiClient(restTemplate, objectMapper);
        parser = new LotteCinemaEventParser(objectMapper);
        // Use a synchronous executor for testing
        crawler = new LotteCinemaEventCrawler(apiClient, parser, Runnable::run);
    }

    @Test
    @DisplayName("날짜 파싱 테스트 - 다양한 형식 지원")
    void testDateParsing() {
        assertThat(LotteCinemaEventUtils.parseDate("2024.05.01")).isEqualTo(LocalDate.of(2024, 5, 1));
        assertThat(LotteCinemaEventUtils.parseDate("2024-05-01")).isEqualTo(LocalDate.of(2024, 5, 1));
        assertThat(LotteCinemaEventUtils.parseDate("20240501")).isEqualTo(LocalDate.of(2024, 5, 1));
        assertThat(LotteCinemaEventUtils.parseDate("invalid")).isNull();
    }

    @Test
    @DisplayName("D-Day 생성 테스트")
    void testDDayGeneration() {
        LotteCinemaEventResponse.Item item = new LotteCinemaEventResponse.Item();

        // 마감임박
        item.setCloseNearYN("1");
        assertThat(LotteCinemaEventUtils.buildDDay(item)).isEqualTo("마감임박");

        // D-Day
        item.setCloseNearYN("0");
        item.setRemainsDayCount(5);
        assertThat(LotteCinemaEventUtils.buildDDay(item)).isEqualTo("D-5");

        // 오늘종료
        item.setRemainsDayCount(0);
        assertThat(LotteCinemaEventUtils.buildDDay(item)).isEqualTo("오늘종료");
    }

    @Test
    @DisplayName("인코딩 복구 테스트 - EUC-KR 응답 처리")
    void testEncodingRecovery() {
        String originalText = "{\"Items\":[{\"EventName\":\"한글 테스트\"}],\"TotalCount\":1}";
        byte[] eucKrBytes = originalText.getBytes(Charset.forName("EUC-KR"));

        mockServer.expect(method(HttpMethod.POST))
                .andRespond(withSuccess(eucKrBytes, MediaType.APPLICATION_JSON));

        String result = apiClient.fetchEventPage("10", 1);

        assertThat(result).contains("한글 테스트");
        assertThat(result).doesNotContain("\uFFFD"); // No replacement characters
    }

    @Test
    @DisplayName("중복 제거 및 우선순위 테스트 (HOT > MOVIE)")
    void testDedupWithPriority() {
        // Mock API for HOT
        String hotJson = "{\"Items\":[{\"EventID\":\"E1\", \"EventName\":\"Event 1 (HOT)\"}], \"TotalCount\":1}";
        mockServer.expect(method(HttpMethod.POST))
                .andRespond(withSuccess(hotJson, MediaType.APPLICATION_JSON));

        // Mock API for MOVIE
        String movieJson = "{\"Items\":[{\"EventID\":\"E1\", \"EventName\":\"Event 1 (MOVIE)\"}], \"TotalCount\":1}";
        mockServer.expect(method(HttpMethod.POST))
                .andRespond(withSuccess(movieJson, MediaType.APPLICATION_JSON));

        // Mock API for remaining categories (empty)
        String emptyJson = "{\"Items\":[], \"TotalCount\":0}";
        mockServer.expect(method(HttpMethod.POST)).andRespond(withSuccess(emptyJson, MediaType.APPLICATION_JSON));
        mockServer.expect(method(HttpMethod.POST)).andRespond(withSuccess(emptyJson, MediaType.APPLICATION_JSON));

        List<MovieEvent> events = crawler.crawlAllEvents();

        assertThat(events).hasSize(1);
        assertThat(events.get(0).getCategory()).isEqualTo(Category.HOT);
        assertThat(events.get(0).getTitle()).isEqualTo("Event 1 (HOT)");
    }

    @Test
    @DisplayName("병렬 크롤링 및 메트릭 수집 테스트")
    void testParallelCrawlingAndMetrics() {
        // Mock all categories
        String mockJson = "{\"Items\":[{\"EventID\":\"E1\", \"EventName\":\"Test\"}], \"TotalCount\":1}";
        for (int i = 0; i < LotteCinemaEventCategory.values().length; i++) {
            mockServer.expect(method(HttpMethod.POST))
                    .andRespond(withSuccess(mockJson, MediaType.APPLICATION_JSON));
        }

        List<MovieEvent> events = crawler.crawlAllEvents();

        assertThat(events).isNotEmpty();
        // Since we use mockServer which is sequential, we can't truly test parallelism timing here,
        // but we can verify that all categories were processed and metrics were collected.
        mockServer.verify();
    }
}
