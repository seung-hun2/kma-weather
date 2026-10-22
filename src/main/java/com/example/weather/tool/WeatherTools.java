package com.example.weather.tool;

import com.example.weather.client.KmaWeatherClient;
import java.util.Map;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Service;

@Service
public class WeatherTools {
    private final KmaWeatherClient client;

    public WeatherTools(KmaWeatherClient client) { this.client = client; }

    @Tool(name = "get_current_weather", description = "기상청 초단기실황으로 한국 지역의 최근 관측 기온, 습도, 강수량, 풍속을 조회합니다. 기상청 격자좌표 nx, ny가 필요합니다. 서울(60,127), 수원(60,121), 부산(98,76).")
    public Map<String, Object> getCurrentWeather(
            @ToolParam(description = "기상청 동네예보 격자 X 좌표. 서울=60, 수원=60, 부산=98") int nx,
            @ToolParam(description = "기상청 동네예보 격자 Y 좌표. 서울=127, 수원=121, 부산=76") int ny) {
        return client.current(nx, ny);
    }

    @Tool(name = "get_short_term_forecast", description = "기상청 단기예보를 통해 한국 지역의 시간별 예보 기온, 습도, 강수확률, 하늘상태를 조회합니다. 현재 날씨 관측값이 아니라 예보값입니다.")
    public Map<String, Object> getShortTermForecast(
            @ToolParam(description = "기상청 동네예보 격자 X 좌표. 서울=60, 수원=60, 부산=98") int nx,
            @ToolParam(description = "기상청 동네예보 격자 Y 좌표. 서울=127, 수원=121, 부산=76") int ny) {
        return client.forecast(nx, ny);
    }
}
