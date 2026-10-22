package com.example.weather;

import com.example.weather.tool.WeatherTools;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class WeatherMcpApplication {
    public static void main(String[] args) {
        SpringApplication.run(WeatherMcpApplication.class, args);
    }

    @Bean
    ToolCallbackProvider weatherToolCallbackProvider(WeatherTools weatherTools) {
        return MethodToolCallbackProvider.builder().toolObjects(weatherTools).build();
    }
}
