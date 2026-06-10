package com.microshop.recommendation.service;

import com.microshop.recommendation.client.CatalogClient;
import com.microshop.recommendation.dto.CatalogProductDTO;
import com.microshop.recommendation.dto.PurchaseRequest;
import com.microshop.recommendation.dto.RecommendedProductDTO;
import com.microshop.recommendation.entity.ProductPopularity;
import com.microshop.recommendation.entity.ProductView;
import com.microshop.recommendation.entity.PurchaseHistory;
import com.microshop.recommendation.repository.ProductPopularityRepository;
import com.microshop.recommendation.repository.ProductViewRepository;
import com.microshop.recommendation.repository.PurchaseHistoryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class RecommendationService {

    private static final Logger log = LoggerFactory.getLogger(RecommendationService.class);

    // Pesos del sistema de puntuación
    private static final double WEIGHT_PURCHASED_CATEGORY  = 5.0;  // categoría ya comprada
    private static final double WEIGHT_VIEWED_CATEGORY     = 2.0;  // categoría solo vista
    private static final double WEIGHT_VIEW_FREQUENCY      = 0.5;  // por cada vista adicional
    private static final double WEIGHT_POPULARITY          = 1.0;  // score de popularidad global

    private final ProductViewRepository viewRepo;
    private final PurchaseHistoryRepository purchaseRepo;
    private final ProductPopularityRepository popularityRepo;
    private final CatalogClient catalogClient;

    public RecommendationService(ProductViewRepository viewRepo,
                                  PurchaseHistoryRepository purchaseRepo,
                                  ProductPopularityRepository popularityRepo,
                                  CatalogClient catalogClient) {
        this.viewRepo = viewRepo;
        this.purchaseRepo = purchaseRepo;
        this.popularityRepo = popularityRepo;
        this.catalogClient = catalogClient;
    }

    // ─────────────────────────────────────────────────────────────
    // REGISTRO DE EVENTOS
    // ─────────────────────────────────────────────────────────────

    /**
     * Registra que un usuario visualizó un producto.
     * Actualiza el contador de popularidad correspondiente.
     */
    @Transactional
    public void registerView(Long userId, Long productId, Long categoryIdHint) {
        Long categoryId = categoryIdHint;
        if (categoryId == null) {
            CatalogProductDTO product = fetchProduct(productId);
            categoryId = (product != null) ? product.getCategory() : -1L;
        }
        viewRepo.save(new ProductView(userId, productId, categoryId));
        updatePopularityOnView(productId, categoryId);
    }

    @Transactional
    public void registerPurchase(Long userId, Long orderId, List<PurchaseRequest.PurchaseItem> items) {
        for (PurchaseRequest.PurchaseItem item : items) {
            if (purchaseRepo.existsByOrderIdAndProductId(orderId, item.getProductId())) {
                log.debug("Purchase already registered for order={} product={}", orderId, item.getProductId());
                continue;
            }

            Long categoryId = item.getCategoryId();
            if (categoryId == null) {
                CatalogProductDTO product = fetchProduct(item.getProductId());
                categoryId = (product != null) ? product.getCategory() : -1L;
            }

            purchaseRepo.save(new PurchaseHistory(
                userId, item.getProductId(), categoryId, orderId, item.getQuantity()
            ));
            updatePopularityOnPurchase(item.getProductId(), categoryId, item.getQuantity());
        }
    }

    // ─────────────────────────────────────────────────────────────
    // RECOMENDACIONES
    // ─────────────────────────────────────────────────────────────

    /**
     * Genera hasta {@code limit} recomendaciones personalizadas para un usuario.
     *
     * Sistema de puntuación por candidato:
     *  +5 × si la categoría del producto coincide con categorías compradas
     *  +2 × si la categoría coincide con categorías vistas (pero no compradas)
     *  +0.5 × frecuencia_de_vistas_del_usuario a ese producto
     *  +1 × (score_popularidad_global / 100) — normalizado para no dominar
     *
     * Se excluyen los productos ya comprados por el usuario.
     */
    public List<RecommendedProductDTO> getRecommendations(Long userId, int limit) {
        // 1. Datos del usuario
        Set<Long> purchasedIds   = purchaseRepo.findPurchasedProductIdsByUserId(userId);
        List<Long> purchasedCats = purchaseRepo.findPurchasedCategoryIdsByUserId(userId);
        List<Long> viewedCats    = viewRepo.findDistinctCategoryIdsByUserId(userId);

        Set<Long> purchasedCatSet = new HashSet<>(purchasedCats);
        // Categorías vistas pero no compradas
        Set<Long> viewedOnlyCats = viewedCats.stream()
                .filter(c -> !purchasedCatSet.contains(c))
                .collect(Collectors.toSet());

        // 2. Candidatos: top populares excluyendo ya comprados
        Set<Long> excluded = purchasedIds.isEmpty() ? Set.of(-1L) : purchasedIds;
        List<ProductPopularity> candidates = popularityRepo.findTopExcluding(
                excluded, PageRequest.of(0, limit * 5)); // sobredimensionamos para ordenar

        if (candidates.isEmpty()) {
            // Sin historial: devolver simplemente los más populares
            return getPopularProducts(limit);
        }

        // 3. Frecuencia de vistas del usuario por producto
        Map<Long, Long> viewFrequency = new HashMap<>();
        viewRepo.findProductIdsByUserId(userId).forEach(pid ->
            viewFrequency.merge(pid, 1L, Long::sum));

        // 4. Calcular score personalizado para cada candidato
        List<RecommendedProductDTO> scored = candidates.stream().map(pop -> {
            double score = 0.0;
            String reason = "Popular en MicroShop";

            if (purchasedCatSet.contains(pop.getCategoryId())) {
                score += WEIGHT_PURCHASED_CATEGORY;
                reason = "Basado en tus compras";
            } else if (viewedOnlyCats.contains(pop.getCategoryId())) {
                score += WEIGHT_VIEWED_CATEGORY;
                reason = "Basado en tu historial de visitas";
            }

            long freq = viewFrequency.getOrDefault(pop.getProductId(), 0L);
            score += freq * WEIGHT_VIEW_FREQUENCY;
            score += (pop.getScore() / 100.0) * WEIGHT_POPULARITY;

            return new RecommendedProductDTO(pop.getProductId(), score, reason);
        }).collect(Collectors.toList());

        // 5. Ordenar por score descendente, tomar top N
        scored.sort(Comparator.comparingDouble(RecommendedProductDTO::getScore).reversed());
        List<RecommendedProductDTO> topN = scored.stream().limit(limit).collect(Collectors.toList());

        // 6. Enriquecer con datos del catálogo
        enrichWithCatalogData(topN);
        return topN;
    }

    /**
     * Devuelve productos similares al dado (misma categoría, ordenados por popularidad).
     */
    public List<RecommendedProductDTO> getSimilarProducts(Long productId, int limit) {
        CatalogProductDTO product = fetchProduct(productId);
        if (product == null) return List.of();

        Long categoryId = product.getCategory();
        List<ProductPopularity> similar = popularityRepo.findSimilarByCategory(
                categoryId, productId, PageRequest.of(0, limit));

        List<RecommendedProductDTO> result = similar.stream()
                .map(p -> new RecommendedProductDTO(p.getProductId(), p.getScore(), "Similar a este producto"))
                .collect(Collectors.toList());

        enrichWithCatalogData(result);
        return result;
    }

    /**
     * Devuelve los productos más populares de forma global.
     */
    public List<RecommendedProductDTO> getPopularProducts(int limit) {
        List<ProductPopularity> top = popularityRepo.findTopByScore(PageRequest.of(0, limit));
        List<RecommendedProductDTO> result = top.stream()
                .map(p -> new RecommendedProductDTO(p.getProductId(), p.getScore(), "Más vendido"))
                .collect(Collectors.toList());
        enrichWithCatalogData(result);
        return result;
    }

    /**
     * Elimina del registro de popularidad los productos que ya no existen en el catálogo.
     * Evita WARNs repetidos de "Producto no encontrado en catalog-service".
     * @return cantidad de entradas eliminadas
     */
    @Transactional
    public int cleanupStaleProducts() {
        List<ProductPopularity> all = popularityRepo.findAll();
        List<Long> toDelete = new ArrayList<>();
        for (ProductPopularity p : all) {
            try {
                CatalogProductDTO product = catalogClient.getProductById(p.getProductId());
                if (product == null || product.getName() == null) {
                    toDelete.add(p.getProductId());
                }
            } catch (Exception e) {
                // 404 or any error → product no longer exists
                toDelete.add(p.getProductId());
            }
        }
        if (!toDelete.isEmpty()) {
            log.info("Cleaning up {} stale product(s) from popularity: {}", toDelete.size(), toDelete);
            popularityRepo.deleteAllById(toDelete);
        }
        return toDelete.size();
    }

    private void updatePopularityOnView(Long productId, Long categoryId) {
        ProductPopularity pop = popularityRepo.findById(productId)
                .orElse(new ProductPopularity(productId, categoryId));
        pop.incrementViews();
        popularityRepo.save(pop);
    }

    private void updatePopularityOnPurchase(Long productId, Long categoryId, int qty) {
        ProductPopularity pop = popularityRepo.findById(productId)
                .orElse(new ProductPopularity(productId, categoryId));
        pop.incrementPurchases(qty);
        popularityRepo.save(pop);
    }

    /**
     * Enriquece los DTOs con nombre, precio, imagen y categoría desde el catalog-service.
     * Si Feign falla para un producto, se omite silenciosamente.
     */
    private void enrichWithCatalogData(List<RecommendedProductDTO> dtos) {
        Iterator<RecommendedProductDTO> iterator = dtos.iterator();
        while (iterator.hasNext()) {
            RecommendedProductDTO dto = iterator.next();
            try {
                CatalogProductDTO product = catalogClient.getProductById(dto.getProductId());
                if (product == null || product.getName() == null || product.getName().trim().isEmpty()) {
                    iterator.remove();
                    continue;
                }
                dto.setName(product.getName());
                dto.setDescription(product.getDescription());
                dto.setPrice(product.getPrice());
                dto.setImage(product.getImage());
                dto.setCategoryId(product.getCategory());
                dto.setCategoryName(product.getCategoryName());
                dto.setStock(product.getStock());
                dto.setAverageRating(product.getAverageRating());
            } catch (Exception e) {
                log.warn("No se pudo enriquecer producto {} desde catalog-service: {}",
                        dto.getProductId(), e.getMessage());
                iterator.remove();
            }
        }
    }

    private CatalogProductDTO fetchProduct(Long productId) {
        try {
            return catalogClient.getProductById(productId);
        } catch (Exception e) {
            log.warn("No se pudo obtener producto {} del catalog-service: {}", productId, e.getMessage());
            return null;
        }
    }
}
