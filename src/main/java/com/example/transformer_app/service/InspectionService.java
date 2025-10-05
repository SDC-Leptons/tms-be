package com.example.transformer_app.service;

import com.example.transformer_app.dto.Detection;
import com.example.transformer_app.dto.ImageAnalysisResult;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;
import java.util.*;

@Service
public class InspectionService {

    @Value("${supabase.url}")
    private String supabaseUrl;

    @Value("${supabase.apikey}")
    private String supabaseApiKey;

    @Value("${supabase.bucket.name}")
    private String bucketName;

    // Lambda config
    @Value("${lambda.url:https://zbpuxumseg.execute-api.ap-southeast-1.amazonaws.com/prod/}")
    private String lambdaUrl;

    @Value("${lambda.threshold:0.1}")
    private double lambdaThreshold;

    @Value("${lambda.iouThreshold:0.2}")
    private double lambdaIouThreshold;

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    public ResponseEntity<String> createInspection(
            String transformerNumber,
            String inspectionNumber,
            String inspectionDate,
            String maintainanceDate,
            String status,
            MultipartFile refImage
    ) throws IOException {
        String imageUrl = "";
        List<Detection> detections = Collections.emptyList();

        if (refImage != null && !refImage.isEmpty()) {
            ImageAnalysisResult result = uploadImageAndAnalyze(refImage);
            imageUrl = result.getImageUrl();
            detections = result.getDetections();
        }

        HttpHeaders dbHeaders = getHeaders();
        dbHeaders.setContentType(MediaType.APPLICATION_JSON);
        dbHeaders.set("Prefer", "return=representation");

        Map<String, Object> body = new HashMap<>();
        body.put("transformerNumber", transformerNumber);
        body.put("inspectionNumber", inspectionNumber);
        body.put("inspectionDate", inspectionDate);
        body.put("maintainanceDate", maintainanceDate);
        body.put("status", status);
        body.put("refImage", imageUrl);
        body.put("anomalies", detections); // Add anomalies to the body

        String dbUrl = supabaseUrl + "/rest/v1/inspections";
        HttpEntity<Map<String, Object>> requestEntity = new HttpEntity<>(body, dbHeaders);

        return restTemplate.exchange(dbUrl, HttpMethod.POST, requestEntity, String.class);
    }


    public ResponseEntity<String> updateInspectionRefImage(Long iid, MultipartFile refImage) throws IOException {
        Map<String, Object> existingInspection = getInspectionById(iid);
        if (existingInspection == null) {
            throw new RuntimeException("Inspection with IID " + iid + " not found");
        }

        String imageUrl = "";
        List<Detection> detections = Collections.emptyList();

        if (refImage != null && !refImage.isEmpty()) {
            ImageAnalysisResult result = uploadImageAndAnalyze(refImage);
            imageUrl = result.getImageUrl();
            detections = result.getDetections();
        }

        HttpHeaders dbHeaders = getHeaders();
        dbHeaders.setContentType(MediaType.APPLICATION_JSON);
        dbHeaders.set("Prefer", "return=representation");

        Map<String, Object> updatedBody = new HashMap<>(existingInspection);
        updatedBody.put("refImage", imageUrl);
        updatedBody.put("anomalies", detections);

        String dbUrl = supabaseUrl + "/rest/v1/inspections?iid=eq." + iid;
        HttpEntity<Map<String, Object>> requestEntity = new HttpEntity<>(updatedBody, dbHeaders);

        return restTemplate.exchange(dbUrl, HttpMethod.PUT, requestEntity, String.class);
    }

    public ResponseEntity<String> updateInspectionRefImage(Long iid, MultipartFile refImage, Double threshold) throws IOException {
        Map<String, Object> existingInspection = getInspectionById(iid);
        if (existingInspection == null) {
            throw new RuntimeException("Inspection with IID " + iid + " not found");
        }

        String imageUrl = "";
        List<Detection> detections = Collections.emptyList();

        // Validate threshold: must be between 0 and 1, else use default
        double usedThreshold = (threshold != null && threshold >= 0.0 && threshold <= 1.0) ? threshold : lambdaThreshold;

        if (refImage != null && !refImage.isEmpty()) {
            ImageAnalysisResult result = uploadImageAndAnalyze(refImage, usedThreshold);
            imageUrl = result.getImageUrl();
            detections = result.getDetections();
        }

        HttpHeaders dbHeaders = getHeaders();
        dbHeaders.setContentType(MediaType.APPLICATION_JSON);
        dbHeaders.set("Prefer", "return=representation");

        Map<String, Object> updatedBody = new HashMap<>(existingInspection);
        updatedBody.put("refImage", imageUrl);
        updatedBody.put("anomalies", detections);

        String dbUrl = supabaseUrl + "/rest/v1/inspections?iid=eq." + iid;
        HttpEntity<Map<String, Object>> requestEntity = new HttpEntity<>(updatedBody, dbHeaders);

        return restTemplate.exchange(dbUrl, HttpMethod.PUT, requestEntity, String.class);
    }

    private Map<String, Object> getInspectionById(Long iid) throws IOException {
        String url = supabaseUrl + "/rest/v1/inspections?iid=eq." + iid + "&select=*&limit=1";
        HttpHeaders headers = getHeaders();
        HttpEntity<String> entity = new HttpEntity<>(headers);
        try {
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);
            List<Map<String, Object>> inspectionList = objectMapper.readValue(response.getBody(), new TypeReference<List<Map<String, Object>>>() {});
            return inspectionList.isEmpty() ? null : inspectionList.get(0);
        } catch (HttpClientErrorException.NotFound e) {
            return null;
        }
    }

    // Keep this unchanged
    public String uploadImage(MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) return "";
        String originalFileName = file.getOriginalFilename();
        String sanitizedFileName = (originalFileName == null) ? "file" : originalFileName.replaceAll("[^a-zA-Z0-9.\\-_]", "_");
        String fileName = UUID.randomUUID().toString() + "_" + sanitizedFileName;

        HttpHeaders storageHeaders = getHeaders();
        storageHeaders.setContentType(MediaType.parseMediaType(file.getContentType()));

        HttpEntity<byte[]> storageRequestEntity = new HttpEntity<>(file.getBytes(), storageHeaders);

        String storageUrl = UriComponentsBuilder.fromHttpUrl(supabaseUrl)
                .path("/storage/v1/object/")
                .pathSegment(bucketName, "refImages", fileName)
                .toUriString();

        restTemplate.exchange(storageUrl, HttpMethod.POST, storageRequestEntity, String.class);

        return supabaseUrl + "/storage/v1/object/public/" + bucketName + "/refImages/" + fileName;
    }

    // New method: uploads to Supabase, then sends Base64 image to Lambda and returns both URL and detections
    public ImageAnalysisResult uploadImageAndAnalyze(MultipartFile file) throws IOException {
        String imageUrl = "";
        List<Detection> detections = Collections.emptyList();

        if (file == null || file.isEmpty()) {
            return new ImageAnalysisResult(imageUrl, detections);
        }

        // 1) Keep existing upload flow
        imageUrl = uploadImage(file);

        // 2) Prepare Lambda payload
        String imgBase64 = Base64.getEncoder().encodeToString(file.getBytes());
        Map<String, Object> payload = new HashMap<>();
        payload.put("image", imgBase64);
        payload.put("threshold", lambdaThreshold);
        payload.put("iou_threshold", lambdaIouThreshold);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, Object>> request = new HttpEntity<>(payload, headers);

        // 3) Invoke Lambda
        try {
            ResponseEntity<String> response = restTemplate.postForEntity(lambdaUrl, request, String.class);
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                System.out.println("Lambda response: " + response.getBody()); // Print the raw response
                Map<String, Object> result = objectMapper.readValue(response.getBody(), new TypeReference<Map<String, Object>>() {});
                Object detectionsObj = result.get("detections");
                if (detectionsObj != null) {
                    String detectionsJson = objectMapper.writeValueAsString(detectionsObj);
                    detections = objectMapper.readValue(detectionsJson, new TypeReference<List<Detection>>() {});
                }
            }
        } catch (Exception ex) {
            System.err.println("Error during Lambda analysis: " + ex.getMessage());
            ex.printStackTrace();
            detections = Collections.emptyList();
        }

        return new ImageAnalysisResult(imageUrl, detections);
    }

    // Overloaded method to support threshold
    public ImageAnalysisResult uploadImageAndAnalyze(MultipartFile file, double threshold) throws IOException {
        String imageUrl = "";
        List<Detection> detections = Collections.emptyList();

        if (file == null || file.isEmpty()) {
            return new ImageAnalysisResult(imageUrl, detections);
        }

        // 1) Keep existing upload flow
        imageUrl = uploadImage(file);

        // 2) Prepare Lambda payload
        String imgBase64 = Base64.getEncoder().encodeToString(file.getBytes());
        Map<String, Object> payload = new HashMap<>();
        payload.put("image", imgBase64);
        payload.put("threshold", threshold);
        payload.put("iou_threshold", lambdaIouThreshold);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, Object>> request = new HttpEntity<>(payload, headers);

        // 3) Invoke Lambda
        try {
            ResponseEntity<String> response = restTemplate.postForEntity(lambdaUrl, request, String.class);
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                System.out.println("Lambda response: " + response.getBody()); // Print the raw response
                Map<String, Object> result = objectMapper.readValue(response.getBody(), new TypeReference<Map<String, Object>>() {});
                Object detectionsObj = result.get("detections");
                if (detectionsObj != null) {
                    String detectionsJson = objectMapper.writeValueAsString(detectionsObj);
                    detections = objectMapper.readValue(detectionsJson, new TypeReference<List<Detection>>() {});
                }
            }
        } catch (Exception ex) {
            System.err.println("Error during Lambda analysis: " + ex.getMessage());
        }
        return new ImageAnalysisResult(imageUrl, detections);
    }

    private HttpHeaders getHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("apikey", supabaseApiKey);
        headers.set("Authorization", "Bearer " + supabaseApiKey);
        return headers;
    }
}
