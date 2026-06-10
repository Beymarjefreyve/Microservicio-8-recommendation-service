package com.microshop.recommendation.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public class ProductViewRequest {

    @NotNull(message = "userId es requerido")
    @Positive
    private Long userId;

    @NotNull(message = "productId es requerido")
    @Positive
    private Long productId;

    /** Opcional: si se envía, se usa directamente y no se consulta el catálogo */
    private Long categoryId;

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public Long getProductId() { return productId; }
    public void setProductId(Long productId) { this.productId = productId; }
    public Long getCategoryId() { return categoryId; }
    public void setCategoryId(Long categoryId) { this.categoryId = categoryId; }
}
