package com.microshop.recommendation.ai.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.microshop.recommendation.ai.dto.AiSearchRequest;
import com.microshop.recommendation.ai.dto.AiSearchResponse;
import com.microshop.recommendation.ai.dto.GeminiFilters;
import com.microshop.recommendation.ai.entity.AiQueryLog;
import com.microshop.recommendation.ai.repository.AiQueryLogRepository;
import com.microshop.recommendation.client.CatalogClient;
import com.microshop.recommendation.dto.CatalogProductDTO;
import com.microshop.recommendation.dto.RecommendedProductDTO;
import com.microshop.recommendation.repository.ProductPopularityRepository;
import com.microshop.recommendation.repository.PurchaseHistoryRepository;
import com.microshop.recommendation.repository.ProductViewRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Orquesta la búsqueda IA:
 * 1. Obtiene contexto del usuario (historial)
 * 2. Llama a GeminiService para extraer filtros
 * 3. Consulta el catálogo con los filtros
 * 4. Combina score IA + score historial
 * 5. Registra la consulta en el log de auditoría
 */
@Service
public class AiRecommendationService {

    private static final Logger log = LoggerFactory.getLogger(AiRecommendationService.class);

    // Pesos para la combinación IA + historial
    private static final double WEIGHT_AI_SCORE      = 0.6;
    private static final double WEIGHT_HISTORY_SCORE = 0.4;

    private final GeminiService geminiService;
    private final CatalogClient catalogClient;
    private final PurchaseHistoryRepository purchaseRepo;
    private final ProductViewRepository viewRepo;
    private final ProductPopularityRepository popularityRepo;
    private final AiQueryLogRepository logRepo;
    private final ObjectMapper objectMapper;

    public AiRecommendationService(GeminiService geminiService,
                                    CatalogClient catalogClient,
                                    PurchaseHistoryRepository purchaseRepo,
                                    ProductViewRepository viewRepo,
                                    ProductPopularityRepository popularityRepo,
                                    AiQueryLogRepository logRepo,
                                    ObjectMapper objectMapper) {
        this.geminiService = geminiService;
        this.catalogClient = catalogClient;
        this.purchaseRepo = purchaseRepo;
        this.viewRepo = viewRepo;
        this.popularityRepo = popularityRepo;
        this.logRepo = logRepo;
        this.objectMapper = objectMapper;
    }

    public AiSearchResponse search(AiSearchRequest request) {
        long startTime = System.currentTimeMillis();
        AiQueryLog queryLog = new AiQueryLog();
        queryLog.setUserId(request.getUserId());
        queryLog.setPrompt(request.getPrompt());

        try {
            // 1. Obtener contexto del usuario
            List<String> purchasedCategoryNames = getUserPurchasedCategoryNames(request.getUserId());
            List<String> viewedCategoryNames    = getUserViewedCategoryNames(request.getUserId());

            // Single catalog call — reused for both available categories and category map
            String rawCatalog = fetchRawFromCatalog(null, 20);
            List<String> availableCategories = extractCategoryNames(rawCatalog);
            if (availableCategories.isEmpty()) {
                availableCategories = List.of("Electrónica", "Ropa", "Alimentos", "Deportes",
                                              "Hogar", "Audio", "Computadores", "Accesorios");
            }
            Map<String, Long> categoryMap = buildCategoryMapFromRaw(rawCatalog);

            List<String> sampleProductNames = parseProductList(rawCatalog).stream()
                .map(p -> (String) p.get("name"))
                .filter(Objects::nonNull)
                .limit(30)
                .collect(Collectors.toList());

            log.info("=== AI SEARCH START === prompt: '{}'", request.getPrompt());
            log.info("Available categories from catalog: {}", availableCategories);

            // 2. Llamar a Gemini
            GeminiFilters filters = geminiService.extractFilters(
                request.getPrompt(),
                availableCategories,
                purchasedCategoryNames,
                viewedCategoryNames,
                sampleProductNames
            );

            // Guardar filtros en el log
            try {
                queryLog.setFiltersJson(objectMapper.writeValueAsString(filters));
            } catch (Exception ignored) {}

            log.info("Gemini selected categories: {}", filters.getCategories());
            log.info("Gemini keywords: {}", filters.getKeywords());
            log.info("Gemini price range: {} - {}", filters.getPriceMin(), filters.getPriceMax());
            log.info("Gemini explanation: {}", filters.getExplanation());
            // 3. Buscar productos en el catálogo con los filtros
            List<CatalogProductDTO> catalogProducts = searchCatalog(filters, Math.min(request.getLimit() * 2, 20), categoryMap);

            // 4. Filtrar por precio si Gemini especificó rango
            catalogProducts = applyPriceFilter(catalogProducts, filters);
            log.info("Products after price filter: {}", catalogProducts.size());

            // 5. One soft retry: if no results and filters had price or attributes, relax them
            if (catalogProducts.isEmpty()) {
                boolean hasPriceFilter = filters.getPriceMin() != null || filters.getPriceMax() != null;
                boolean hasAttributes  = filters.getAttributes() != null && !filters.getAttributes().isEmpty();

                if (hasPriceFilter || hasAttributes) {
                    log.info("=== SOFT RETRY with relaxed filters ===");
                    log.debug("0 results with strict filters — retrying with relaxed filters (no price, no attributes)");
                    GeminiFilters relaxed = new GeminiFilters();
                    relaxed.setCategories(filters.getCategories());
                    relaxed.setKeywords(filters.getKeywords());
                    relaxed.setPriceMin(null);
                    relaxed.setPriceMax(null);
                    relaxed.setAttributes(List.of());
                    relaxed.setExplanation(filters.getExplanation());

                    catalogProducts = searchCatalog(relaxed, Math.min(request.getLimit() * 2, 20), categoryMap);
                    log.info("Retry results: {} products", catalogProducts.size());
                    // No apply price filter on relaxed — we intentionally dropped it
                }
                // If still empty (or no filters to relax), catalogProducts stays empty → return empty list
            }

            // 5. Convertir a DTOs con score combinado
            List<RecommendedProductDTO> scored = rankWithCombinedScore(
                catalogProducts, request.getUserId(), filters
            );

            // 6. Tomar top N
            List<RecommendedProductDTO> result = scored.stream()
                .limit(request.getLimit())
                .collect(Collectors.toList());

            // 7. Determinar fuente
            String source = (!purchasedCategoryNames.isEmpty() || !viewedCategoryNames.isEmpty())
                ? "AI+HISTORY" : "AI";

            log.info("=== AI SEARCH END === returning {} results, source={}", result.size(), source);

            // Auditoría
            queryLog.setResultsCount(result.size());
            queryLog.setResponseTimeMs(System.currentTimeMillis() - startTime);
            queryLog.setStatus("SUCCESS");
            AiQueryLog saved = logRepo.save(queryLog);

            String explanation = filters.getExplanation() != null
                ? filters.getExplanation()
                : "Productos encontrados para tu búsqueda";

            return new AiSearchResponse(result, explanation, source, saved.getId());

        } catch (Exception e) {
            log.error("Error en AiRecommendationService.search: {}", e.getMessage());

            queryLog.setResponseTimeMs(System.currentTimeMillis() - startTime);
            queryLog.setStatus("FALLBACK");
            queryLog.setErrorMessage(e.getMessage() != null
                ? e.getMessage().substring(0, Math.min(e.getMessage().length(), 500)) : "unknown");
            AiQueryLog saved = logRepo.save(queryLog);

            // Fallback: devolver populares
            List<RecommendedProductDTO> fallback = getFallbackProducts(request.getLimit());
            return new AiSearchResponse(
                fallback,
                "Mostrando los productos más populares de la tienda",
                "FALLBACK",
                saved.getId()
            );
        }
    }

    // ─────────────────────────────────────────────────────────────
    // BÚSQUEDA EN CATÁLOGO
    // ─────────────────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private List<CatalogProductDTO> searchCatalog(GeminiFilters filters, int pageSize, Map<String, Long> categoryMap) {
        try {
            // Build search term from keywords + attributes (used for in-memory filtering)
            List<String> terms = new ArrayList<>();
            if (filters.getKeywords() != null) terms.addAll(filters.getKeywords());
            if (filters.getAttributes() != null) terms.addAll(filters.getAttributes());
            String searchTerm = String.join(" ", terms);

            log.info("Category map from catalog: {}", categoryMap);

            List<String> categoriesToSearch = (filters.getCategories() != null && !filters.getCategories().isEmpty())
                ? filters.getCategories()
                : List.of(); // empty = search all products

            List<CatalogProductDTO> allProducts = new ArrayList<>();
            Set<Long> seenIds = new HashSet<>();

            if (categoriesToSearch.isEmpty()) {
                allProducts = fetchFromCatalog(null, pageSize, categoryMap);
            } else {
                for (String cat : categoriesToSearch) {
                    Long catId = resolveCategoryId(cat, categoryMap);
                    log.info("Searching category '{}' → id={}", cat, catId);
                    if (catId == null) {
                        log.debug("Category '{}' not found in catalog map, skipping", cat);
                        continue;
                    }
                    List<CatalogProductDTO> batch = fetchFromCatalog(catId, pageSize, categoryMap);
                    for (CatalogProductDTO p : batch) {
                        if (p.getId() != null && seenIds.add(p.getId())) {
                            allProducts.add(p);
                        }
                    }
                }
            }

            // Two-phase keyword filter: prefer keyword matches, fall back to category-only results
            if (!searchTerm.isBlank()) {
                List<CatalogProductDTO> withKeywords = allProducts.stream()
                    .filter(p -> matchesSearch(p, searchTerm.toLowerCase()))
                    .collect(Collectors.toList());
                if (!withKeywords.isEmpty()) {
                    log.info("Phase 1 matched {} products with keywords", withKeywords.size());
                    allProducts = withKeywords;
                } else {
                    log.info("Phase 2: no keyword match, returning {} products by category only", allProducts.size());
                    // allProducts unchanged — category match is enough
                }
            }

            log.info("Products found after keyword filter: {} products", allProducts.size());
            if (!allProducts.isEmpty()) {
                log.info("Matched products: {}", allProducts.stream()
                    .map(p -> p.getName() + " [cat:" + p.getCategoryName() + "]")
                    .collect(Collectors.toList()));
            }

            return allProducts;

        } catch (Exception e) {
            log.warn("Error buscando en catálogo: {}", e.getMessage());
            return List.of();
        }
    }

    @SuppressWarnings("unchecked")
    private List<CatalogProductDTO> fetchFromCatalog(Long categoryId, int pageSize,
                                                      Map<String, Long> categoryMap) {
        try {
            String raw = catalogClient.getProducts(categoryId, pageSize);
            if (raw == null) return List.of();
            List<Map<String, Object>> rawList = parseProductList(raw);
            if (!rawList.isEmpty()) {
                log.info("Sample raw price from catalog: {}", rawList.get(0).get("price"));
            }
            return rawList.stream()
                .map(this::mapToCatalogProduct)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
        } catch (Exception e) {
            log.warn("Error fetching from catalog (categoryId={}): {}", categoryId, e.getMessage());
            return List.of();
        }
    }

    private boolean matchesSearch(CatalogProductDTO p, String termLower) {
        String[] words = termLower.split("\\s+");
        String name = p.getName() != null ? p.getName().toLowerCase() : "";
        String desc = p.getDescription() != null ? p.getDescription().toLowerCase() : "";
        for (String word : words) {
            if (word.length() > 2 && (name.contains(word) || desc.contains(word))) {
                return true;
            }
        }
        return false;
    }

    private List<CatalogProductDTO> applyPriceFilter(List<CatalogProductDTO> products, GeminiFilters filters) {
        return products.stream()
            .filter(p -> {
                if (p.getPrice() == null) return true;
                if (filters.getPriceMin() != null && p.getPrice() < filters.getPriceMin()) return false;
                if (filters.getPriceMax() != null && p.getPrice() > filters.getPriceMax()) return false;
                return true;
            })
            .collect(Collectors.toList());
    }

    // ─────────────────────────────────────────────────────────────
    // SCORING COMBINADO
    // ─────────────────────────────────────────────────────────────

    private List<RecommendedProductDTO> rankWithCombinedScore(
            List<CatalogProductDTO> products, Long userId, GeminiFilters filters) {

        // Score IA: basado en posición en resultados del catálogo (inversamente proporcional)
        int total = products.size();

        // Score historial por producto
        Set<Long> purchasedIds = userId != null
            ? purchaseRepo.findPurchasedProductIdsByUserId(userId)
            : Set.of();

        return products.stream()
            .filter(p -> !purchasedIds.contains(p.getId())) // excluir ya comprados
            .map(p -> {
                int index = products.indexOf(p);
                double aiScore = total > 0 ? (double)(total - index) / total * 10.0 : 5.0;

                // Score popularidad del sistema existente
                double popularityScore = popularityRepo.findById(p.getId())
                    .map(pop -> pop.getScore() / 100.0)
                    .orElse(0.0);

                // Si el usuario tiene historial, combinar; si no, solo AI
                double finalScore;
                if (userId != null && !purchasedIds.isEmpty()) {
                    finalScore = (aiScore * WEIGHT_AI_SCORE) + (popularityScore * WEIGHT_HISTORY_SCORE);
                } else {
                    finalScore = aiScore;
                }

                RecommendedProductDTO dto = new RecommendedProductDTO(p.getId(), finalScore, "Resultado de búsqueda IA");
                log.info("Before setPrice - dto.price={}, p.getPrice()={}", dto.getPrice(), p.getPrice());
                dto.setName(p.getName());
                dto.setDescription(p.getDescription());
                dto.setPrice(p.getPrice());
                dto.setImage(p.getImage());
                dto.setCategoryId(p.getCategory());
                dto.setCategoryName(p.getCategoryName());
                dto.setStock(p.getStock());
                dto.setAverageRating(p.getAverageRating());
                log.info("Product '{}' price set to: {}", dto.getName(), dto.getPrice());
                return dto;
            })
            .sorted(Comparator.comparingDouble(RecommendedProductDTO::getScore).reversed())
            .collect(Collectors.toList());
    }

    // ─────────────────────────────────────────────────────────────
    // HELPERS
    // ─────────────────────────────────────────────────────────────

    private List<String> getUserPurchasedCategoryNames(Long userId) {
        if (userId == null) return List.of();
        try {
            return purchaseRepo.findPurchasedCategoryIdsByUserId(userId).stream()
                .map(id -> "Categoría " + id)
                .collect(Collectors.toList());
        } catch (Exception e) { return List.of(); }
    }

    private List<String> getUserViewedCategoryNames(Long userId) {
        if (userId == null) return List.of();
        try {
            return viewRepo.findDistinctCategoryIdsByUserId(userId).stream()
                .map(id -> "Categoría " + id)
                .collect(Collectors.toList());
        } catch (Exception e) { return List.of(); }
    }

    private String fetchRawFromCatalog(Long categoryId, int pageSize) {
        try {
            return catalogClient.getProducts(categoryId, pageSize);
        } catch (Exception e) {
            log.warn("Could not fetch raw catalog response (categoryId={}): {}", categoryId, e.getMessage());
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Long> buildCategoryMapFromRaw(String raw) {
        try {
            List<Map<String, Object>> rawList = parseProductList(raw);
            Map<String, Long> map = new HashMap<>();
            for (Map<String, Object> product : rawList) {
                String name = (String) product.get("category_name");
                Object idObj = product.get("category");
                if (name != null && idObj instanceof Number) {
                    map.put(name.toLowerCase(), ((Number) idObj).longValue());
                }
            }
            return map;
        } catch (Exception e) {
            log.warn("Could not build category map from raw response: {}", e.getMessage());
            return Map.of();
        }
    }

    private Long resolveCategoryId(String categoryName, Map<String, Long> categoryMap) {
        if (categoryName == null) return null;
        String normalized = normalizeString(categoryName);
        return categoryMap.entrySet().stream()
            .filter(e -> normalizeString(e.getKey()).equals(normalized))
            .map(Map.Entry::getValue)
            .findFirst()
            .orElse(null);
    }

    private String normalizeString(String s) {
        return java.text.Normalizer.normalize(s.toLowerCase(), java.text.Normalizer.Form.NFD)
            .replaceAll("\\p{InCombiningDiacriticalMarks}+", "");
    }

    private List<String> extractCategoryNames(String raw) {
        return parseProductList(raw).stream()
            .map(product -> (String) product.get("category_name"))
            .filter(Objects::nonNull)
            .distinct()
            .collect(Collectors.toList());
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> parseProductList(String raw) {
        try {
            Object parsed = objectMapper.readValue(raw, Object.class);
            if (parsed instanceof List) {
                return (List<Map<String, Object>>) parsed;
            }
            Map<String, Object> map = (Map<String, Object>) parsed;
            Object results = map.get("results") != null ? map.get("results") : map.get("data");
            if (results instanceof List) {
                return (List<Map<String, Object>>) results;
            }
            return List.of();
        } catch (Exception e) {
            log.warn("Could not parse product list from catalog response");
            return List.of();
        }
    }

    @SuppressWarnings("unchecked")
    private CatalogProductDTO mapToCatalogProduct(Map<String, Object> raw) {
        try {
            CatalogProductDTO dto = new CatalogProductDTO();
            dto.setId(raw.get("id") instanceof Number ? ((Number) raw.get("id")).longValue() : null);
            dto.setName((String) raw.get("name"));
            dto.setDescription((String) raw.get("description"));
            Object priceRaw = raw.get("price");
            if (priceRaw instanceof Number) {
                dto.setPrice(((Number) priceRaw).doubleValue());
            } else if (priceRaw instanceof String) {
                try {
                    // Catalog sends plain decimal strings like "4200000.00"
                    // Also handle Colombian format "4.200.000,00" just in case
                    String s = ((String) priceRaw).replace("$", "").trim();
                    // Detect Colombian thousands-dot format: has dot but no comma, multiple dot groups
                    // e.g. "4.200.000" or "4.200.000,00" vs plain "4200000.00"
                    if (s.contains(",")) {
                        // Colombian: "4.200.000,00" → strip dots, replace comma with dot
                        s = s.replace(".", "").replace(",", ".");
                    }
                    // else: plain decimal "4200000.00" — use as-is
                    dto.setPrice(Double.parseDouble(s));
                } catch (NumberFormatException e) {
                    log.warn("Could not parse price '{}' for product '{}'", priceRaw, raw.get("name"));
                }
            }
            dto.setImage((String) raw.get("image"));
            if (raw.get("category") instanceof Number) {
                dto.setCategory(((Number) raw.get("category")).longValue());
            }
            Object ratingRaw = raw.get("average_rating");
            if (ratingRaw instanceof Number) {
                dto.setAverageRating(((Number) ratingRaw).doubleValue());
            }
            dto.setCategoryName((String) raw.get("category_name"));
            if (raw.get("stock") instanceof Number) {
                dto.setStock(((Number) raw.get("stock")).intValue());
            }
            return dto;
        } catch (Exception e) {
            return null;
        }
    }

    private List<RecommendedProductDTO> getFallbackProducts(int limit) {
        try {
            return popularityRepo.findTopByScore(org.springframework.data.domain.PageRequest.of(0, limit))
                .stream()
                .map(p -> {
                    try {
                        CatalogProductDTO product = catalogClient.getProductById(p.getProductId());
                        RecommendedProductDTO dto = new RecommendedProductDTO(p.getProductId(), p.getScore(), "Popular");
                        if (product != null) {
                            dto.setName(product.getName());
                            dto.setPrice(product.getPrice());
                            dto.setImage(product.getImage());
                            dto.setCategoryName(product.getCategoryName());
                        }
                        return dto;
                    } catch (Exception e) {
                        return new RecommendedProductDTO(p.getProductId(), p.getScore(), "Popular");
                    }
                })
                .collect(Collectors.toList());
        } catch (Exception e) {
            return List.of();
        }
    }
}
