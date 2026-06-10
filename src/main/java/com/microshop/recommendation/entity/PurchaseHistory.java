package com.microshop.recommendation.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * Registra cada producto comprado por un usuario (una fila por ítem de orden).
 * Es la fuente más relevante para generar recomendaciones personalizadas.
 */
@Entity
@Table(name = "rec_purchase_history",
        indexes = {
            @Index(name = "idx_purchase_user_id", columnList = "user_id"),
            @Index(name = "idx_purchase_product_id", columnList = "product_id")
        })
public class PurchaseHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(name = "category_id", nullable = false)
    private Long categoryId;

    @Column(name = "order_id", nullable = false)
    private Long orderId;

    @Column(name = "quantity", nullable = false)
    private Integer quantity;

    @CreationTimestamp
    @Column(name = "purchased_at", updatable = false)
    private LocalDateTime purchasedAt;

    public PurchaseHistory() {}

    public PurchaseHistory(Long userId, Long productId, Long categoryId, Long orderId, Integer quantity) {
        this.userId = userId;
        this.productId = productId;
        this.categoryId = categoryId;
        this.orderId = orderId;
        this.quantity = quantity;
    }

    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public Long getProductId() { return productId; }
    public void setProductId(Long productId) { this.productId = productId; }
    public Long getCategoryId() { return categoryId; }
    public void setCategoryId(Long categoryId) { this.categoryId = categoryId; }
    public Long getOrderId() { return orderId; }
    public void setOrderId(Long orderId) { this.orderId = orderId; }
    public Integer getQuantity() { return quantity; }
    public void setQuantity(Integer quantity) { this.quantity = quantity; }
    public LocalDateTime getPurchasedAt() { return purchasedAt; }
}
