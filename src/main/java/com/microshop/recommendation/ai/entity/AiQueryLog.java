package com.microshop.recommendation.ai.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * Registro de auditoría de cada consulta hecha a Gemini.
 * Permite monitorear uso, detectar abusos y calcular costos.
 */
@Entity
@Table(name = "rec_ai_query_log",
        indexes = {
            @Index(name = "idx_ai_log_user_id", columnList = "user_id"),
            @Index(name = "idx_ai_log_created_at", columnList = "created_at")
        })
public class AiQueryLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Puede ser null si el usuario no está autenticado */
    @Column(name = "user_id")
    private Long userId;

    @Column(name = "prompt", nullable = false, length = 500)
    private String prompt;

    /** JSON de los filtros que Gemini extrajo */
    @Column(name = "filters_json", columnDefinition = "TEXT")
    private String filtersJson;

    @Column(name = "results_count")
    private Integer resultsCount;

    @Column(name = "response_time_ms")
    private Long responseTimeMs;

    /** "SUCCESS", "FALLBACK", "ERROR" */
    @Column(name = "status", length = 20)
    private String status;

    @Column(name = "error_message", length = 500)
    private String errorMessage;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    public AiQueryLog() {}

    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getPrompt() { return prompt; }
    public void setPrompt(String prompt) { this.prompt = prompt; }
    public String getFiltersJson() { return filtersJson; }
    public void setFiltersJson(String filtersJson) { this.filtersJson = filtersJson; }
    public Integer getResultsCount() { return resultsCount; }
    public void setResultsCount(Integer resultsCount) { this.resultsCount = resultsCount; }
    public Long getResponseTimeMs() { return responseTimeMs; }
    public void setResponseTimeMs(Long responseTimeMs) { this.responseTimeMs = responseTimeMs; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
