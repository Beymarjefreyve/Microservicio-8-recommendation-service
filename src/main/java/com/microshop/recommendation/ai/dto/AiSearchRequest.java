package com.microshop.recommendation.ai.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public class AiSearchRequest {

    /** ID del usuario (null si no está autenticado — se ignorará el historial) */
    private Long userId;

    @NotBlank(message = "El prompt no puede estar vacío")
    @Size(min = 3, max = 500, message = "El prompt debe tener entre 3 y 500 caracteres")
    private String prompt;

    @Positive
    private int limit = 10;

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getPrompt() { return prompt; }
    public void setPrompt(String prompt) { this.prompt = prompt; }
    public int getLimit() { return Math.min(limit, 30); }
    public void setLimit(int limit) { this.limit = limit; }
}
