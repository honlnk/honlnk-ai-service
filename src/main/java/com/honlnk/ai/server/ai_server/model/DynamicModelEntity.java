package com.honlnk.ai.server.ai_server.model;

import jakarta.persistence.*;

import java.util.Map;

@Entity
@Table(name = "dynamic_models")
public class DynamicModelEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String baseUrl;

    @Column(nullable = false)
    private String apiKey;

    @Convert(converter = MapToStringConverter.class)
    @Column(columnDefinition = "VARCHAR(2048)")
    private Map<String, String> headers;

    @Column(nullable = false)
    private String modelName;

    @Column(nullable = false, length = 1000)
    private String modelDescription;

    @Column(nullable = false)
    private String type;

    @Column(nullable = false, columnDefinition = "boolean default false")
    private boolean isDefault;

    @Column
    private Double temperature;

    @Column
    private Double topP;

    @Column
    private String completionsPath;

    public DynamicModelEntity() {
    }

    public DynamicModelEntity(Long id) {
        this.id = id;
    }

    // Getters and Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public String getModelName() {
        return modelName;
    }

    public void setModelName(String modelName) {
        this.modelName = modelName;
    }

    public String getModelDescription() {
        return modelDescription;
    }

    public void setModelDescription(String modelDescription) {
        this.modelDescription = modelDescription;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public Map<String, String> getHeaders() {
        return headers;
    }

    public void setHeaders(Map<String, String> headers) {
        this.headers = headers;
    }

    public Boolean getIsDefault() {
        return isDefault;
    }

    public void setIsDefault(Boolean isDefault) {
        this.isDefault = isDefault;
    }

    public Double getTemperature() {
        return temperature;
    }

    public void setTemperature(Double temperature) {
        this.temperature = temperature;
    }

    public Double getTopP() {
        return topP;
    }

    public void setTopP(Double topP) {
        this.topP = topP;
    }

    public String getCompletionsPath() {
        return completionsPath;
    }

    public void setCompletionsPath(String completionsPath) {
        this.completionsPath = completionsPath;
    }
}