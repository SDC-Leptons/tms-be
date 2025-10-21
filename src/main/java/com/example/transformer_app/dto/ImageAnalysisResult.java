package com.example.transformer_app.dto;

import java.util.List;
import java.util.Map;

public class ImageAnalysisResult {
    private String imageUrl;
    private List<Detection> detections;
    private List<Map<String, Object>> anomaliesLog;

    public ImageAnalysisResult() {
    }

    public ImageAnalysisResult(String imageUrl, List<Detection> detections) {
        this.imageUrl = imageUrl;
        this.detections = detections;
    }

    public ImageAnalysisResult(String imageUrl, List<Detection> detections, List<Map<String, Object>> anomaliesLog) {
        this.imageUrl = imageUrl;
        this.detections = detections;
        this.anomaliesLog = anomaliesLog;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public List<Detection> getDetections() {
        return detections;
    }

    public List<Map<String, Object>> getAnomaliesLog() {
        return anomaliesLog;
    }

    public void setImageUrl(String imageUrl) {
        this.imageUrl = imageUrl;
    }

    public void setDetections(List<Detection> detections) {
        this.detections = detections;
    }

    public void setAnomaliesLog(List<Map<String, Object>> anomaliesLog) {
        this.anomaliesLog = anomaliesLog;
    }
}
