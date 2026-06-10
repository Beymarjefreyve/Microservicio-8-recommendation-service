package com.microshop.recommendation.entity;

import jakarta.persistence.*;

/**
 * Contadores agregados de popularidad por producto.
 * Se actualiza cada vez que se registra una vista o compra.
 * Permite consultas rápidas de productos populares sin agregar en tiempo real.
 */
@Entity
@Table(name = "rec_product_popularity")
public class ProductPopularity {

    @Id
    @Column(name = "product_id")
    private Long productId;

    @Column(name = "category_id", nullable = false)
    private Long categoryId;

    @Column(name = "view_count", nullable = false)
    private Long viewCount = 0L;

    @Column(name = "purchase_count", nullable = false)
    private Long purchaseCount = 0L;

    @Column(name = "score", nullable = false)
    private Double score = 0.0;

    public ProductPopularity() {}

    public ProductPopularity(Long productId, Long categoryId) {
        this.productId = productId;
        this.categoryId = categoryId;
    }

    /** Recalcula el score: las compras pesan 3x más que las vistas */
    public void recalculateScore() {
        this.score = (this.viewCount * 1.0) + (this.purchaseCount * 3.0);
    }

    public void incrementViews() {
        this.viewCount++;
        recalculateScore();
    }

    public void incrementPurchases(int qty) {
        this.purchaseCount += qty;
        recalculateScore();
    }

    public Long getProductId() { return productId; }
    public void setProductId(Long productId) { this.productId = productId; }
    public Long getCategoryId() { return categoryId; }
    public void setCategoryId(Long categoryId) { this.categoryId = categoryId; }
    public Long getViewCount() { return viewCount; }
    public void setViewCount(Long viewCount) { this.viewCount = viewCount; }
    public Long getPurchaseCount() { return purchaseCount; }
    public void setPurchaseCount(Long purchaseCount) { this.purchaseCount = purchaseCount; }
    public Double getScore() { return score; }
    public void setScore(Double score) { this.score = score; }
}
