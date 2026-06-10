package com.microshop.recommendation.ai.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * Filtros que Gemini extrae del prompt del usuario.
 * Gemini debe devolver SIEMPRE este JSON, nunca texto libre.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class GeminiFilters {

    /** Nombres de categorías del catálogo, ej: ["Electrónica", "Audio"] */
    private List<String> categories;

    /** Palabras clave de búsqueda, ej: ["audífonos", "gaming", "inalámbrico"] */
    private List<String> keywords;

    /** Precio mínimo en COP (null si no se mencionó) */
    private Double priceMin;

    /** Precio máximo en COP (null si no se mencionó) */
    private Double priceMax;

    /** Atributos adicionales, ej: ["bluetooth", "cancelación de ruido"] */
    private List<String> attributes;

    /** Explicación amigable para mostrar al usuario */
    private String explanation;

    public List<String> getCategories() { return categories; }
    public void setCategories(List<String> categories) { this.categories = categories; }
    public List<String> getKeywords() { return keywords; }
    public void setKeywords(List<String> keywords) { this.keywords = keywords; }
    public Double getPriceMin() { return priceMin; }
    public void setPriceMin(Double priceMin) { this.priceMin = priceMin; }
    public Double getPriceMax() { return priceMax; }
    public void setPriceMax(Double priceMax) { this.priceMax = priceMax; }
    public List<String> getAttributes() { return attributes; }
    public void setAttributes(List<String> attributes) { this.attributes = attributes; }
    public String getExplanation() { return explanation; }
    public void setExplanation(String explanation) { this.explanation = explanation; }
}
