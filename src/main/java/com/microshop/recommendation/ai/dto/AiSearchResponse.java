package com.microshop.recommendation.ai.dto;

import com.microshop.recommendation.dto.RecommendedProductDTO;

import java.util.List;

public class AiSearchResponse {

    private List<RecommendedProductDTO> products;

    /** Texto amigable generado por Gemini para mostrar al usuario */
    private String explanation;

    /** "AI" si Gemini respondió bien, "AI+HISTORY" si se combinó con historial, "FALLBACK" si Gemini falló */
    private String source;

    /** ID del log para trazabilidad */
    private Long queryId;

    public AiSearchResponse() {}

    public AiSearchResponse(List<RecommendedProductDTO> products, String explanation,
                             String source, Long queryId) {
        this.products = products;
        this.explanation = explanation;
        this.source = source;
        this.queryId = queryId;
    }

    public List<RecommendedProductDTO> getProducts() { return products; }
    public void setProducts(List<RecommendedProductDTO> products) { this.products = products; }
    public String getExplanation() { return explanation; }
    public void setExplanation(String explanation) { this.explanation = explanation; }
    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }
    public Long getQueryId() { return queryId; }
    public void setQueryId(Long queryId) { this.queryId = queryId; }
}
