package com.example.transformer_app.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public class Detection {
    private String id; // Unique identifier for this detection
    private List<Double> box;

    @JsonProperty("class")
    private String className;

    private double confidence;

    private String madeBy; // "AI" or "User" - tracks how this anomaly was created

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getMadeBy() {
        return madeBy;
    }

    public void setMadeBy(String madeBy) {
        this.madeBy = madeBy;
    }

    public List<Double> getBox() {
        return box;
    }

    public void setBox(List<Double> box) {
        this.box = box;
    }

    public String getClassName() {
        return className;
    }

    public void setClassName(String className) {
        this.className = className;
    }

    public double getConfidence() {
        return confidence;
    }

    public void setConfidence(double confidence) {
        this.confidence = confidence;
    }
}
