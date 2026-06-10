package com.microshop.recommendation.dto;

/**
 * Producto recomendado enriquecido con datos del catálogo y el score calculado.
 */
public class RecommendedProductDTO {

    private Long productId;
    private String name;
    private String description;
    private Double price;
    private String image;
    private Long categoryId;
    private String categoryName;
    private Integer stock;
    private Double averageRating;
    private Double score;
    private String reason; // "Basado en tus compras", "Popular", etc.

    public RecommendedProductDTO() {}

    public RecommendedProductDTO(Long productId, Double score, String reason) {
        this.productId = productId;
        this.score = score;
        this.reason = reason;
    }

    public Long getProductId() { return productId; }
    public void setProductId(Long productId) { this.productId = productId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public Double getPrice() { return price; }
    public void setPrice(Double price) { this.price = price; }
    public String getImage() { return image; }
    public void setImage(String image) { this.image = image; }
    public Long getCategoryId() { return categoryId; }
    public void setCategoryId(Long categoryId) { this.categoryId = categoryId; }
    public String getCategoryName() { return categoryName; }
    public void setCategoryName(String categoryName) { this.categoryName = categoryName; }
    public Integer getStock() { return stock; }
    public void setStock(Integer stock) { this.stock = stock; }
    public Double getAverageRating() { return averageRating; }
    public void setAverageRating(Double averageRating) { this.averageRating = averageRating; }
    public Double getScore() { return score; }
    public void setScore(Double score) { this.score = score; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
}
