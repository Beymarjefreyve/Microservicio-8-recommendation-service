package com.microshop.recommendation.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.util.List;

public class PurchaseRequest {

    @NotNull(message = "userId es requerido")
    @Positive
    private Long userId;

    @NotNull(message = "orderId es requerido")
    @Positive
    private Long orderId;

    @NotEmpty(message = "items no puede estar vacío")
    @Valid
    private List<PurchaseItem> items;

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public Long getOrderId() { return orderId; }
    public void setOrderId(Long orderId) { this.orderId = orderId; }
    public List<PurchaseItem> getItems() { return items; }
    public void setItems(List<PurchaseItem> items) { this.items = items; }

    public static class PurchaseItem {
        @NotNull @Positive
        private Long productId;

        @NotNull @Positive
        private Integer quantity;

        /** Opcional: si se envía, se usa directamente y no se consulta el catálogo */
        private Long categoryId;

        public Long getProductId() { return productId; }
        public void setProductId(Long productId) { this.productId = productId; }
        public Integer getQuantity() { return quantity; }
        public void setQuantity(Integer quantity) { this.quantity = quantity; }
        public Long getCategoryId() { return categoryId; }
        public void setCategoryId(Long categoryId) { this.categoryId = categoryId; }
    }
}
