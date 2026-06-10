package com.microshop.recommendation.ai.controller;

import com.microshop.recommendation.ai.dto.AiSearchRequest;
import com.microshop.recommendation.ai.dto.AiSearchResponse;
import com.microshop.recommendation.ai.entity.AiQueryLog;
import com.microshop.recommendation.ai.repository.AiQueryLogRepository;
import com.microshop.recommendation.ai.service.AiRecommendationService;
import jakarta.validation.Valid;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/recommendations/ai")
public class AiRecommendationController {

    private final AiRecommendationService aiService;
    private final AiQueryLogRepository logRepo;

    public AiRecommendationController(AiRecommendationService aiService,
                                       AiQueryLogRepository logRepo) {
        this.aiService = aiService;
        this.logRepo = logRepo;
    }

    /**
     * POST /api/recommendations/ai/search
     *
     * Búsqueda en lenguaje natural con Gemini.
     * El frontend envía el texto libre del usuario y recibe productos.
     *
     * Body: { "userId": 5, "prompt": "quiero audífonos para gaming", "limit": 10 }
     */
    @PostMapping("/search")
    public ResponseEntity<AiSearchResponse> search(
            @Valid @RequestBody AiSearchRequest request) {
        return ResponseEntity.ok(aiService.search(request));
    }

    /**
     * GET /api/recommendations/ai/history/{userId}?limit=20
     *
     * Devuelve el historial de consultas IA de un usuario.
     * Útil para mostrar "Búsquedas recientes" en el frontend.
     */
    @GetMapping("/history/{userId}")
    public ResponseEntity<List<AiQueryLog>> getHistory(
            @PathVariable Long userId,
            @RequestParam(defaultValue = "10") int limit) {
        return ResponseEntity.ok(
            logRepo.findByUserIdOrderByCreatedAtDesc(userId, PageRequest.of(0, Math.min(limit, 50)))
        );
    }

    /**
     * GET /api/recommendations/ai/health
     * Endpoint de verificación rápida para el frontend.
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        return ResponseEntity.ok(Map.of("status", "AI recommendations active"));
    }
}
