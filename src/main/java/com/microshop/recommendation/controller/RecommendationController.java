package com.microshop.recommendation.controller;

import com.microshop.recommendation.dto.ProductViewRequest;
import com.microshop.recommendation.dto.PurchaseRequest;
import com.microshop.recommendation.dto.RecommendedProductDTO;
import com.microshop.recommendation.service.RecommendationService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/recommendations")
public class RecommendationController {

    private final RecommendationService service;

    public RecommendationController(RecommendationService service) {
        this.service = service;
    }

    /**
     * POST /api/recommendations/views
     * Registra que un usuario visualizó un producto.
     * Llamado por el frontend al abrir la página de detalle de producto.
     */
    @PostMapping("/views")
    public ResponseEntity<Map<String, String>> registerView(
            @Valid @RequestBody ProductViewRequest request) {
        service.registerView(request.getUserId(), request.getProductId(), request.getCategoryId());
        return ResponseEntity.ok(Map.of("message", "Vista registrada correctamente"));
    }

    /**
     * POST /api/recommendations/purchases
     * Registra una compra completada (llamado tras confirmar el pago).
     * Recibe todos los ítems de la orden de una vez.
     */
    @PostMapping("/purchases")
    public ResponseEntity<Map<String, String>> registerPurchase(
            @Valid @RequestBody PurchaseRequest request) {
        service.registerPurchase(request.getUserId(), request.getOrderId(), request.getItems());
        return ResponseEntity.ok(Map.of("message", "Compra registrada correctamente"));
    }

    /**
     * GET /api/recommendations/user/{userId}?limit=10
     * Recomendaciones personalizadas para un usuario basadas en:
     * - Categorías que ha comprado
     * - Categorías que ha visitado
     * - Frecuencia de vistas
     * - Popularidad global
     * Excluye productos ya comprados.
     */
    @GetMapping("/user/{userId}")
    public ResponseEntity<List<RecommendedProductDTO>> getRecommendations(
            @PathVariable Long userId,
            @RequestParam(defaultValue = "10") int limit) {
        limit = Math.min(limit, 50); // cota máxima
        return ResponseEntity.ok(service.getRecommendations(userId, limit));
    }

    /**
     * GET /api/recommendations/similar/{productId}?limit=6
     * Productos similares al dado (misma categoría, ordenados por popularidad).
     */
    @GetMapping("/similar/{productId}")
    public ResponseEntity<List<RecommendedProductDTO>> getSimilarProducts(
            @PathVariable Long productId,
            @RequestParam(defaultValue = "6") int limit) {
        return ResponseEntity.ok(service.getSimilarProducts(productId, limit));
    }

    /**
     * GET /api/recommendations/popular?limit=10
     * Productos más populares globalmente (más vistos + más comprados).
     */
    @GetMapping("/popular")
    public ResponseEntity<List<RecommendedProductDTO>> getPopularProducts(
            @RequestParam(defaultValue = "10") int limit) {
        limit = Math.min(limit, 50);
        return ResponseEntity.ok(service.getPopularProducts(limit));
    }

    /**
     * POST /api/recommendations/admin/cleanup
     * Elimina del registro de popularidad los productos que ya no existen en el catálogo.
     * Útil para limpiar datos obsoletos sin reiniciar el servicio.
     */
    @PostMapping("/admin/cleanup")
    public ResponseEntity<Map<String, Object>> cleanupStaleProducts() {
        int removed = service.cleanupStaleProducts();
        return ResponseEntity.ok(Map.of(
            "message", "Limpieza completada",
            "removed", removed
        ));
    }
}
