package com.example.transformer_app.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public class Detection {
    private List<Double> box;

    @JsonProperty("class")
    private String className;

    private double confidence;

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
