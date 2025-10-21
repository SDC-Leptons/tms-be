package com.example.transformer_app.model;

import com.example.transformer_app.dto.Detection;
import java.util.List;
import java.util.Map;

public class Inspection {
    private String iid;
    private String transformerNumber;
    private String inspectionNumber;
    private String inspectionDate;
    private String maintainanceDate;
    private String status;
    private String inspector;
    private String refImage; // URL to image in Supabase Storage
    private List<Detection> anomalies; // List of detected anomalies
    private List<Map<String, Object>> anomaliesLog; // Log of anomaly detections with metadata

    // Getters & Setters
    public String getIid() { return iid; }
    public void setId(String iid) { this.iid = iid; }

    public String getTransformerNumber() { return transformerNumber; }
    public void setTransformerNumber(String transformerNumber) { this.transformerNumber = transformerNumber; }

    public String getInspectionNumber() { return inspectionNumber; }
    public void setInspectionNumber(String inspectionNumber) { this.inspectionNumber = inspectionNumber; }

    public String getInspectionDate() { return inspectionDate; }
    public void setInspectionDate(String inspectionDate) { this.inspectionDate = inspectionDate; }

    public String getMaintainanceDate() { return maintainanceDate; }
    public void setMaintainanceDate(String maintainanceDate) { this.maintainanceDate = maintainanceDate; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getInspector() { return inspector; }
    public void setInspector(String inspector) { this.inspector = inspector; }

    public String getRefImage() { return refImage; }
    public void setRefImage(String refImage) { this.refImage = refImage; }

    public List<Detection> getAnomalies() { return anomalies; }
    public void setAnomalies(List<Detection> anomalies) { this.anomalies = anomalies; }

    public List<Map<String, Object>> getAnomaliesLog() { return anomaliesLog; }
    public void setAnomaliesLog(List<Map<String, Object>> anomaliesLog) { this.anomaliesLog = anomaliesLog; }
}