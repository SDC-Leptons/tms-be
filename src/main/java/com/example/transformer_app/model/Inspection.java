package com.example.transformer_app.model;

public class Inspection {
    private String iid;
    private String transformerNumber;
    private String inspectionNumber;
    private String inspectionDate;
    private String maintainanceDate;
    private String status;
    private String refImage; // URL to image in Supabase Storage

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

    public String getRefImage() { return refImage; }
    public void setRefImage(String refImage) { this.refImage = refImage; }
}