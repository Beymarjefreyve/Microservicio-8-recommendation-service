package com.microshop.recommendation.ai.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

@Configuration
@ConfigurationProperties(prefix = "gemini")
public class GeminiConfig {

    private String apiKey;
    private String model = "gemini-2.5-flash-lite";
    private int maxOutputTokens = 512;
    private int timeoutMs = 8000;
    private int rateLimitRpm = 10;

    @Bean(name = "geminiRestTemplate")
    public RestTemplate geminiRestTemplate() {
        return new RestTemplate();
    }

    public String getApiKey() { return apiKey; }
    public void setApiKey(String apiKey) { this.apiKey = apiKey; }

    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }

    public int getMaxOutputTokens() { return maxOutputTokens; }
    public void setMaxOutputTokens(int maxOutputTokens) { this.maxOutputTokens = maxOutputTokens; }

    public int getTimeoutMs() { return timeoutMs; }
    public void setTimeoutMs(int timeoutMs) { this.timeoutMs = timeoutMs; }

    public int getRateLimitRpm() { return rateLimitRpm; }
    public void setRateLimitRpm(int rateLimitRpm) { this.rateLimitRpm = rateLimitRpm; }

    /** URL completa del endpoint de Gemini */
    public String getEndpointUrl() {
        return String.format(
            "https://generativelanguage.googleapis.com/v1beta/models/%s:generateContent?key=%s",
            model, apiKey
        );
    }
}
