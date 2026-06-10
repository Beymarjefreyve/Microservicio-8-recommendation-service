package com.microshop.recommendation.ai.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.microshop.recommendation.ai.config.GeminiConfig;
import com.microshop.recommendation.ai.dto.GeminiFilters;
import com.microshop.recommendation.ai.exception.GeminiUnavailableException;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Servicio que se comunica directamente con la API de Google Gemini.
 * Construye el prompt, llama al endpoint y parsea la respuesta JSON.
 */
@Service
public class GeminiService {

    private static final Logger log = LoggerFactory.getLogger(GeminiService.class);

    private final GeminiConfig config;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final Bucket rateLimiter;

    public GeminiService(GeminiConfig config,
                         @Qualifier("geminiRestTemplate") RestTemplate restTemplate,
                         ObjectMapper objectMapper) {
        this.config = config;
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;

        // Rate limiter: máx N requests por minuto (configurable) — Bucket4j 8.x API
        Bandwidth limit = Bandwidth.builder()
            .capacity(config.getRateLimitRpm())
            .refillGreedy(config.getRateLimitRpm(), Duration.ofMinutes(1))
            .build();
        this.rateLimiter = Bucket.builder().addLimit(limit).build();
    }

    /**
     * Envía el prompt a Gemini y devuelve los filtros extraídos.
     * Si Gemini no responde o el JSON es inválido, lanza GeminiUnavailableException.
     */
    public GeminiFilters extractFilters(String userPrompt,
                                         List<String> availableCategories,
                                         List<String> userPurchasedCategories,
                                         List<String> userViewedCategories,
                                         List<String> sampleProductNames) {

        // Rate limiting
        if (!rateLimiter.tryConsume(1)) {
            throw new GeminiUnavailableException(
                "Demasiadas consultas de IA al mismo tiempo. Intenta en unos segundos.");
        }

        String fullPrompt = buildPrompt(userPrompt, availableCategories,
                                         userPurchasedCategories, userViewedCategories,
                                         sampleProductNames);
        String rawResponse = callGeminiApi(fullPrompt);
        return parseResponse(rawResponse, availableCategories);
    }

    // ─────────────────────────────────────────────────────────────
    // CONSTRUCCIÓN DEL PROMPT
    // ─────────────────────────────────────────────────────────────

    private String buildPrompt(String userPrompt,
                                List<String> availableCategories,
                                List<String> purchasedCategories,
                                List<String> viewedCategories,
                                List<String> sampleProductNames) {

        StringBuilder sb = new StringBuilder();

        sb.append("Eres un asistente de recomendación de productos para MicroShop, una tienda en línea. ");
        sb.append("Respond ONLY with a valid JSON object. No explanation, no markdown, no code blocks. Just the raw JSON.\n\n");

        sb.append("AVAILABLE CATEGORIES (copy-paste names exactly as written): ")
          .append(String.join(", ", availableCategories))
          .append("\n\n");

        if (sampleProductNames != null && !sampleProductNames.isEmpty()) {
            sb.append("REAL PRODUCT NAMES IN THE STORE (use these as reference to pick keywords that will match):\n");
            sb.append(String.join(", ", sampleProductNames)).append("\n");
            sb.append("Keywords must be words that actually appear in the product names above. ");
            sb.append("Do not invent keywords that don't match any product name.\n\n");
        }

        if (!purchasedCategories.isEmpty()) {
            sb.append("USER CONTEXT - Previously purchased in: ")
              .append(String.join(", ", purchasedCategories))
              .append("\n");
        }
        if (!viewedCategories.isEmpty()) {
            sb.append("USER CONTEXT - Recently viewed categories: ")
              .append(String.join(", ", viewedCategories))
              .append("\n");
        }

        sb.append("\nUSER QUERY: \"").append(userPrompt).append("\"\n\n");

        sb.append("RULES FOR FILLING THE JSON:\n");
        sb.append("- categories: ONLY use exact names from AVAILABLE CATEGORIES list above. ");
        sb.append("Copy-paste them exactly. If unsure, use fewer or return empty array []. ");
        sb.append("NEVER invent or modify category names.\n");
        sb.append("- keywords: generate between 4 and 8 keywords. Include synonyms, related terms, ");
        sb.append("and broader terms that could match product names or descriptions. ");
        sb.append("Example: for 'arte para decorar cuarto' use [Póster, Cuadro, Lienzo, Decoración, Pintura, Arte, Marco, Ilustración]. ");
        sb.append("If the user mentioned a specific brand or product name, include it. ");
        sb.append("Prefer words that appear in the REAL PRODUCT NAMES list above.\n");
        sb.append("- priceMin / priceMax: ONLY set if the user explicitly mentioned a price or budget. ");
        sb.append("Otherwise both must be null.\n");
        sb.append("- attributes: ONLY include attributes the user explicitly mentioned. ");
        sb.append("Do NOT infer or assume any attribute.\n");
        sb.append("- explanation: friendly Spanish phrase, max 80 characters.\n\n");

        sb.append("Respond with this JSON structure and nothing else:\n");
        sb.append("{\n");
        sb.append("  \"categories\": [],\n");
        sb.append("  \"keywords\": [],\n");
        sb.append("  \"priceMin\": null,\n");
        sb.append("  \"priceMax\": null,\n");
        sb.append("  \"attributes\": [],\n");
        sb.append("  \"explanation\": \"\"\n");
        sb.append("}");

        return sb.toString();
    }

    // ─────────────────────────────────────────────────────────────
    // LLAMADA HTTP A GEMINI
    // ─────────────────────────────────────────────────────────────

    private String callGeminiApi(String prompt) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            // Payload para Gemini REST API v1beta
            Map<String, Object> body = Map.of(
                "contents", List.of(
                    Map.of("parts", List.of(Map.of("text", prompt)))
                ),
                "generationConfig", Map.of(
                    "maxOutputTokens", 2048,
                    "temperature", 0.3,
                    "responseMimeType", "application/json"  // fuerza JSON limpio sin markdown
                )
            );

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);

            ResponseEntity<String> response = restTemplate.exchange(
                config.getEndpointUrl(),
                HttpMethod.POST,
                request,
                String.class
            );

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                return response.getBody();
            }

            throw new GeminiUnavailableException("Gemini respondió con status " + response.getStatusCode());

        } catch (GeminiUnavailableException e) {
            throw e;
        } catch (Exception e) {
            log.error("Error llamando a Gemini API: {}", e.getMessage());
            throw new GeminiUnavailableException("No se pudo contactar a Gemini API", e);
        }
    }

    // ─────────────────────────────────────────────────────────────
    // PARSEO DE RESPUESTA
    // ─────────────────────────────────────────────────────────────

    private GeminiFilters parseResponse(String rawResponse, List<String> availableCategories) {
        try {
            JsonNode root = objectMapper.readTree(rawResponse);

            // Extraer el texto de la respuesta: candidates[0].content.parts[0].text
            String text = root
                .path("candidates").get(0)
                .path("content")
                .path("parts").get(0)
                .path("text")
                .asText();

            // Limpiar posible markdown que Gemini a veces añade (```json ... ```)
            text = cleanJsonText(text);

            GeminiFilters filters = objectMapper.readValue(text, GeminiFilters.class);

            // Validate categories: keep only names that exactly match the available list (case-insensitive)
            if (filters.getCategories() != null && !filters.getCategories().isEmpty()) {
                List<String> validCategories = filters.getCategories().stream()
                    .filter(cat -> availableCategories.stream()
                        .anyMatch(ac -> ac.equalsIgnoreCase(cat)))
                    .collect(Collectors.toList());
                filters.setCategories(validCategories);
                if (validCategories.size() < filters.getCategories().size()) {
                    log.debug("Removed {} invalid categories from Gemini response",
                        filters.getCategories().size() - validCategories.size());
                }
            }

            return filters;

        } catch (JsonProcessingException e) {
            log.error("No se pudo parsear la respuesta de Gemini: {}", e.getMessage());
            return buildFallbackFilters();
        } catch (Exception e) {
            log.error("Error inesperado parseando Gemini: {}", e.getMessage());
            return buildFallbackFilters();
        }
    }

    private String cleanJsonText(String text) {
        // Eliminar bloques markdown ```json ... ``` o ``` ... ```
        text = text.replaceAll("```json", "").replaceAll("```", "").trim();
        // Extraer solo el objeto JSON entre el primer { y el último }
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return text.substring(start, end + 1);
        }
        return text.trim();
    }

    private GeminiFilters buildFallbackFilters() {
        GeminiFilters f = new GeminiFilters();
        f.setKeywords(List.of());
        f.setCategories(List.of());
        f.setAttributes(List.of());
        f.setExplanation("Mostrando productos populares de la tienda");
        return f;
    }
}
