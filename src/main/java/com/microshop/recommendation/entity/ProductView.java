package com.microshop.recommendation.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * Registra cada vez que un usuario visualiza un producto.
 * Se usa para calcular recomendaciones por frecuencia e interés de categoría.
 */
@Entity
@Table(name = "rec_product_views",
        indexes = {
            @Index(name = "idx_view_user_id", columnList = "user_id"),
            @Index(name = "idx_view_product_id", columnList = "product_id")
        })
public class ProductView {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(name = "category_id", nullable = false)
    private Long categoryId;

    @CreationTimestamp
    @Column(name = "viewed_at", updatable = false)
    private LocalDateTime viewedAt;

    public ProductView() {}

    public ProductView(Long userId, Long productId, Long categoryId) {
        this.userId = userId;
        this.productId = productId;
        this.categoryId = categoryId;
    }

    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public Long getProductId() { return productId; }
    public void setProductId(Long productId) { this.productId = productId; }
    public Long getCategoryId() { return categoryId; }
    public void setCategoryId(Long categoryId) { this.categoryId = categoryId; }
    public LocalDateTime getViewedAt() { return viewedAt; }
}
