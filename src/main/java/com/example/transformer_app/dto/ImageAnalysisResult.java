package com.example.transformer_app.dto;

import java.util.List;

public class ImageAnalysisResult {
    private String imageUrl;
    private List<Detection> detections;

    public ImageAnalysisResult() {
    }

    public ImageAnalysisResult(String imageUrl, List<Detection> detections) {
        this.imageUrl = imageUrl;
        this.detections = detections;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public List<Detection> getDetections() {
        return detections;
    }

    public void setImageUrl(String imageUrl) {
        this.imageUrl = imageUrl;
    }

    public void setDetections(List<Detection> detections) {
        this.detections = detections;
    }
}
