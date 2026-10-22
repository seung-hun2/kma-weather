package com.example.weather.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class KmaWeatherClient {
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HHmm");
    private static final int MAX_ITEMS = 1000;
    private final RestClient restClient;
    private final ObjectMapper mapper;
    private final String serviceKey;
    private final String baseUrl;

    public KmaWeatherClient(RestClient kmaRestClient, ObjectMapper mapper,
            @Value("${kma.service-key:}") String serviceKey,
            @Value("${kma.base-url:https://apis.data.go.kr/1360000/VilageFcstInfoService_2.0}") String baseUrl) {
        this.restClient = kmaRestClient;
        this.mapper = mapper;
        this.serviceKey = serviceKey;
        this.baseUrl = baseUrl;
    }

    public Map<String, Object> current(int nx, int ny) {
        // 초단기실황은 매시 정각 기준 생산되며 발표 지연을 고려하여 45분 이전 시각 사용
        LocalDateTime now = LocalDateTime.now(KST).minusMinutes(45);
        LocalDateTime base = now.withMinute(0).withSecond(0).withNano(0);
        List<JsonNode> items = request("getUltraSrtNcst", nx, ny, base, 100);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("type", "current_observation");
        result.put("baseDate", base.format(DATE));
        result.put("baseTime", base.format(TIME));
        result.put("nx", nx);
        result.put("ny", ny);
        Map<String, String> values = new LinkedHashMap<>();
        for (JsonNode item : items) {
            values.put(item.path("category").asText(), item.path("obsrValue").asText());
        }
        result.put("temperatureC", values.get("T1H"));
        result.put("humidityPercent", values.get("REH"));
        result.put("rainfallLastHourMm", values.get("RN1"));
        result.put("windSpeedMs", values.get("WSD"));
        result.put("precipitationTypeCode", values.get("PTY"));
        result.put("rawCategories", values);
        return result;
    }

    public Map<String, Object> forecast(int nx, int ny) {
        // 단기예보 발표 시각: 02,05,08,11,14,17,20,23시. 공표 지연 20분 고려
        LocalDateTime available = LocalDateTime.now(KST).minusMinutes(20);
        LocalDateTime base = available.withMinute(0).withSecond(0).withNano(0);
        while (!List.of(2, 5, 8, 11, 14, 17, 20, 23).contains(base.getHour())) {
            base = base.minusHours(1);
        }
        List<JsonNode> items = request("getVilageFcst", nx, ny, base, MAX_ITEMS);
        Map<String, Map<String, String>> byTime = new LinkedHashMap<>();
        for (JsonNode item : items) {
            String time = item.path("fcstDate").asText() + " " + item.path("fcstTime").asText();
            byTime.computeIfAbsent(time, key -> new LinkedHashMap<>())
                  .put(item.path("category").asText(), item.path("fcstValue").asText());
        }
        List<Map<String, Object>> hourly = new ArrayList<>();
        byTime.entrySet().stream().sorted(Map.Entry.comparingByKey()).limit(48).forEach(entry -> {
            Map<String, Object> v = new LinkedHashMap<>();
            v.put("forecastTimeKst", entry.getKey());
            Map<String, String> cats = entry.getValue();
            v.put("temperatureC", cats.get("TMP"));
            v.put("humidityPercent", cats.get("REH"));
            v.put("precipitationProbabilityPercent", cats.get("POP"));
            v.put("precipitationAmount", cats.get("PCP"));
            v.put("skyCode", cats.get("SKY"));
            v.put("precipitationTypeCode", cats.get("PTY"));
            v.put("windSpeedMs", cats.get("WSD"));
            hourly.add(v);
        });
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("type", "short_term_forecast");
        result.put("baseDate", base.format(DATE));
        result.put("baseTime", base.format(TIME));
        result.put("nx", nx);
        result.put("ny", ny);
        result.put("hourly", hourly);
        result.put("note", "예보값은 관측값이 아닙니다. skyCode: 1 맑음, 3 구름많음, 4 흐림; precipitationTypeCode: 0 없음, 1 비, 2 비/눈, 3 눈, 4 소나기");
        return result;
    }

    private List<JsonNode> request(String operation, int nx, int ny, LocalDateTime base, int numOfRows) {
        if (serviceKey.isBlank()) throw new IllegalStateException("KMA_SERVICE_KEY 환경변수를 설정하세요.");
        if (nx < 1 || nx > 149 || ny < 1 || ny > 253) throw new IllegalArgumentException("유효하지 않은 기상청 격자좌표입니다.");
        // build().toUri() 이용: 이미 인코딩한 키가 아닌 Decoding 키를 전달합니다.
        String url = baseUrl + "/" + operation
                + "?serviceKey=" + URLEncoder.encode(serviceKey, StandardCharsets.UTF_8)
                + "&pageNo=1&numOfRows=" + numOfRows + "&dataType=JSON"
                + "&base_date=" + base.format(DATE) + "&base_time=" + base.format(TIME)
                + "&nx=" + nx + "&ny=" + ny;
        String json = restClient.get().uri(URI.create(url))
                .retrieve().body(String.class);
        try {
            JsonNode root = mapper.readTree(json);
            JsonNode response = root.path("response");
            String resultCode = response.path("header").path("resultCode").asText();
            if (!"00".equals(resultCode)) {
                throw new IllegalStateException("기상청 API 오류: " + resultCode + " / "
                        + response.path("header").path("resultMsg").asText());
            }
            JsonNode items = response.path("body").path("items").path("item");
            if (!items.isArray() || items.isEmpty()) {
                throw new IllegalStateException("기상청 API 응답에 데이터가 없습니다. 발표 시각이나 격자좌표를 확인하세요.");
            }
            List<JsonNode> result = new ArrayList<>();
            items.forEach(result::add);
            return result;
        } catch (com.fasterxml.jackson.core.JsonProcessingException ex) {
            throw new IllegalStateException("기상청 응답을 JSON으로 파싱할 수 없습니다. 인증키 또는 API 응답을 확인하세요.", ex);
        }
    }
}
