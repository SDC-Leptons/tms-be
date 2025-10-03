package com.example.transformer_app.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "transformers")
public class Transformer {
    @Id
    private String id;

    private String transformerNumber;
    private String poleNumber;
    private String region;
    private String type;
    private String baselineImage; // Base64 string

    // Getters & Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getTransformerNumber() { return transformerNumber; }
    public void setTransformerNumber(String transformerNumber) { this.transformerNumber = transformerNumber; }

    public String getPoleNumber() { return poleNumber; }
    public void setPoleNumber(String poleNumber) { this.poleNumber = poleNumber; }

    public String getRegion() { return region; }
    public void setRegion(String region) { this.region = region; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public String getBaselineImage() { return baselineImage; }
    public void setBaselineImage(String baselineImage) { this.baselineImage = baselineImage; }
}