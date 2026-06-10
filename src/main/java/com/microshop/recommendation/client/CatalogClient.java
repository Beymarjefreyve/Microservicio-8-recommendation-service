package com.microshop.recommendation.client;

import com.microshop.recommendation.dto.CatalogProductDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

/**
 * Feign client para consultar el catalog-service (Django REST Framework).
 * La URL se configura via CATALOG_SERVICE_URL env var.
 * Llama directamente al catalog-service (sin pasar por el gateway) — no requiere JWT.
 */
@FeignClient(name = "catalog-service", url = "${CATALOG_SERVICE_URL:http://localhost:8002}")
public interface CatalogClient {

    @GetMapping("/api/catalog/categories/")
    String getCategories();

    @GetMapping("/api/catalog/products/{id}/")
    CatalogProductDTO getProductById(@PathVariable("id") Long id);

    @GetMapping("/api/catalog/products/")
    String getProducts(
            @RequestParam(value = "category", required = false) Long categoryId,
            @RequestParam(value = "page_size", defaultValue = "20") int pageSize
    );
}
