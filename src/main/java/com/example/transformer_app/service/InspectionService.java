package com.example.transformer_app.service;

import com.example.transformer_app.dto.Detection;
import com.example.transformer_app.dto.ImageAnalysisResult;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
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

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    public InspectionService(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    public ResponseEntity<String> createInspection(
            String transformerNumber,
            String inspectionNumber,
            String inspectionDate,
            String maintainanceDate,
            String status,
            String inspector,
            MultipartFile refImage
    ) throws IOException {
        // If the caller didn't provide an inspection number, generate one server-side
        // Check for null first, then check if empty after trimming
        if (inspectionNumber == null || inspectionNumber.isEmpty() || inspectionNumber.trim().isEmpty()) {
            inspectionNumber = generateUniqueInspectionNumber();
        }

        String imageUrl = "";
        List<Detection> detections = Collections.emptyList();
        List<Map<String, Object>> anomaliesLog = new ArrayList<>();

        if (refImage != null && !refImage.isEmpty()) {
            ImageAnalysisResult result = uploadImageAndAnalyze(refImage);
            imageUrl = result.getImageUrl();
            detections = result.getDetections();
            anomaliesLog = result.getAnomaliesLog(); // <-- Get anomaliesLog directly from result!

            // Debug logging
            System.out.println("=== DEBUG: Creating Inspection ===");
            System.out.println("Number of detections: " + detections.size());
            System.out.println("Number of log entries: " + anomaliesLog.size());
            System.out.println("Detections array:");
            System.out.println(objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(detections));
            System.out.println("AnomaliesLog array:");
            System.out.println(objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(anomaliesLog));
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
        body.put("inspector", inspector);
        body.put("refImage", imageUrl);
        body.put("anomalies", detections);
        body.put("anomaliesLog", anomaliesLog);

        // Debug: Print what we're sending to database
        System.out.println("=== DEBUG: Request Body to Database ===");
        System.out.println(objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(body));

        String dbUrl = supabaseUrl + "/rest/v1/inspections";
        HttpEntity<Map<String, Object>> requestEntity = new HttpEntity<>(body, dbHeaders);

        ResponseEntity<String> response = restTemplate.exchange(dbUrl, HttpMethod.POST, requestEntity, String.class);

        // Debug: Print the response from database
        System.out.println("=== DEBUG: Database Response ===");
        System.out.println(response.getBody());

        return response;
    }


    public ResponseEntity<String> updateInspectionRefImage(Long iid, MultipartFile refImage) throws IOException {
        Map<String, Object> existingInspection = getInspectionById(iid);
        if (existingInspection == null) {
            throw new RuntimeException("Inspection with IID " + iid + " not found");
        }

        String imageUrl = "";
        List<Detection> detections = Collections.emptyList();
        List<Map<String, Object>> anomaliesLog = getAnomaliesLogList(iid);

        if (refImage != null && !refImage.isEmpty()) {
            ImageAnalysisResult result = uploadImageAndAnalyze(refImage);
            imageUrl = result.getImageUrl();
            detections = result.getDetections();

            // Get the NEW anomalies log from result and ADD to existing log
            List<Map<String, Object>> newAnomaliesLog = result.getAnomaliesLog();
            if (newAnomaliesLog != null) {
                anomaliesLog.addAll(newAnomaliesLog);
            }
        }

        HttpHeaders dbHeaders = getHeaders();
        dbHeaders.setContentType(MediaType.APPLICATION_JSON);
        dbHeaders.set("Prefer", "return=representation");

        // PATCH only the fields we're updating
        Map<String, Object> updateFields = new HashMap<>();
        updateFields.put("refImage", imageUrl);
        updateFields.put("anomalies", detections);
        updateFields.put("anomaliesLog", anomaliesLog);

        String dbUrl = supabaseUrl + "/rest/v1/inspections?iid=eq." + iid;
        HttpEntity<Map<String, Object>> requestEntity = new HttpEntity<>(updateFields, dbHeaders);

        return restTemplate.exchange(dbUrl, HttpMethod.PATCH, requestEntity, String.class);
    }

    public ResponseEntity<String> updateInspectionRefImage(Long iid, MultipartFile refImage, Double threshold) throws IOException {
        Map<String, Object> existingInspection = getInspectionById(iid);
        if (existingInspection == null) {
            throw new RuntimeException("Inspection with IID " + iid + " not found");
        }

        String imageUrl = "";
        List<Detection> detections = Collections.emptyList();
        List<Map<String, Object>> anomaliesLog = getAnomaliesLogList(iid);

        System.out.println("=== DEBUG: updateInspectionRefImage called ===");
        System.out.println("Existing anomaliesLog size: " + anomaliesLog.size());

        // Validate threshold: must be between 0 and 1, else use default
        double usedThreshold = (threshold != null && threshold >= 0.0 && threshold <= 1.0) ? threshold : lambdaThreshold;

        if (refImage != null && !refImage.isEmpty()) {
            ImageAnalysisResult result = uploadImageAndAnalyze(refImage, usedThreshold);
            imageUrl = result.getImageUrl();
            detections = result.getDetections();

            // Get the NEW anomalies log from result and ADD to existing log
            List<Map<String, Object>> newAnomaliesLog = result.getAnomaliesLog();
            if (newAnomaliesLog != null) {
                anomaliesLog.addAll(newAnomaliesLog);
            }

            System.out.println("=== DEBUG: After uploadImageAndAnalyze in UPDATE ===");
            System.out.println("Detections size: " + detections.size());
            System.out.println("New anomalies log entries: " + (newAnomaliesLog != null ? newAnomaliesLog.size() : 0));
            System.out.println("Total anomaliesLog size: " + anomaliesLog.size());
            System.out.println("AnomaliesLog array:");
            System.out.println(objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(anomaliesLog));
        }

        HttpHeaders dbHeaders = getHeaders();
        dbHeaders.setContentType(MediaType.APPLICATION_JSON);
        dbHeaders.set("Prefer", "return=representation");

        // Use PATCH instead of PUT to only update the fields we're changing
        Map<String, Object> updateFields = new HashMap<>();
        updateFields.put("refImage", imageUrl);
        updateFields.put("anomalies", detections);
        updateFields.put("anomaliesLog", anomaliesLog);

        System.out.println("=== DEBUG: Update Fields to Database (UPDATE) ===");
        System.out.println(objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(updateFields));

        String dbUrl = supabaseUrl + "/rest/v1/inspections?iid=eq." + iid;
        HttpEntity<Map<String, Object>> requestEntity = new HttpEntity<>(updateFields, dbHeaders);

        ResponseEntity<String> response = restTemplate.exchange(dbUrl, HttpMethod.PATCH, requestEntity, String.class);

        System.out.println("=== DEBUG: Database Response (UPDATE) ===");
        System.out.println(response.getBody());

        return response;
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
        List<Map<String, Object>> anomaliesLog = new ArrayList<>();

        if (file == null || file.isEmpty()) {
            return new ImageAnalysisResult(imageUrl, detections, anomaliesLog);
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
                System.out.println("Lambda response: " + response.getBody());

                Map<String, Object> result = objectMapper.readValue(response.getBody(), new TypeReference<Map<String, Object>>() {});

                Object imageUrlObj = result.get("imageUrl");
                if (imageUrlObj instanceof String) {
                    imageUrl = (String) imageUrlObj;
                }

                Object detectionsObj = result.get("detections");
                if (detectionsObj != null) {
                    String detectionsJson = objectMapper.writeValueAsString(detectionsObj);
                    detections = objectMapper.readValue(detectionsJson, new TypeReference<List<Detection>>() {});

                    // Convert coordinates and assign unique IDs, mark as AI-generated, and CREATE LOG ENTRIES
                    for (Detection detection : detections) {
                        // Convert box coordinates from [x1, y1, x2, y2] to [x_center, y_center, width, height]
                        convertBoxCoordinates(detection);

                        if (detection.getId() == null || detection.getId().isEmpty()) {
                            detection.setId(UUID.randomUUID().toString());
                        }
                        detection.setMadeBy("AI");

                        // Create anomaly log entry for this detection
                        Map<String, Object> logEntry = createAnomalyLogEntry(
                            detection.getId(),
                            detection.getBox(),
                            "AI",
                            detection.getClassName(),
                            detection.getConfidence(),
                            "add"
                        );
                        anomaliesLog.add(logEntry);
                    }
                    System.out.println("Detections: " + objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(detections));
                    System.out.println("AnomaliesLog created: " + objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(anomaliesLog));
                }
            }
        } catch (Exception ex) {
            System.err.println("Error during Lambda analysis: " + ex.getMessage());
            ex.printStackTrace();
            detections = Collections.emptyList();
            anomaliesLog = new ArrayList<>();
        }

        return new ImageAnalysisResult(imageUrl, detections, anomaliesLog);
    }

    // Overloaded method to support threshold
    public ImageAnalysisResult uploadImageAndAnalyze(MultipartFile file, double threshold) throws IOException {
        String imageUrl = "";
        List<Detection> detections = Collections.emptyList();
        List<Map<String, Object>> anomaliesLog = new ArrayList<>();

        if (file == null || file.isEmpty()) {
            return new ImageAnalysisResult(imageUrl, detections, anomaliesLog);
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
                System.out.println("Lambda response: " + response.getBody());

                Map<String, Object> result = objectMapper.readValue(response.getBody(), new TypeReference<Map<String, Object>>() {});

                Object imageUrlObj = result.get("imageUrl");
                if (imageUrlObj instanceof String) {
                    imageUrl = (String) imageUrlObj;
                }

                Object detectionsObj = result.get("detections");
                if (detectionsObj != null) {
                    String detectionsJson = objectMapper.writeValueAsString(detectionsObj);
                    detections = objectMapper.readValue(detectionsJson, new TypeReference<List<Detection>>() {});

                    // Convert coordinates and assign unique IDs, mark as AI-generated, and CREATE LOG ENTRIES
                    for (Detection detection : detections) {
                        // Convert box coordinates from [x1, y1, x2, y2] to [x_center, y_center, width, height]
                        convertBoxCoordinates(detection);

                        if (detection.getId() == null || detection.getId().isEmpty()) {
                            detection.setId(UUID.randomUUID().toString());
                        }
                        detection.setMadeBy("AI");

                        // Create anomaly log entry for this detection
                        Map<String, Object> logEntry = createAnomalyLogEntry(
                            detection.getId(),
                            detection.getBox(),
                            "AI",
                            detection.getClassName(),
                            detection.getConfidence(),
                            "add"
                        );
                        anomaliesLog.add(logEntry);
                    }
                    System.out.println("Detections: " + objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(detections));
                    System.out.println("AnomaliesLog created: " + objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(anomaliesLog));
                }
            }
        } catch (Exception ex) {
            System.err.println("Error during Lambda analysis: " + ex.getMessage());
            ex.printStackTrace();
            detections = Collections.emptyList();
            anomaliesLog = new ArrayList<>();
        }
        return new ImageAnalysisResult(imageUrl, detections, anomaliesLog);
    }

    private HttpHeaders getHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("apikey", supabaseApiKey);
        headers.set("Authorization", "Bearer " + supabaseApiKey);
        return headers;
    }

    // Get anomalies from the inspections table (from the anomalies JSON column)
    public ResponseEntity<String> getAnomalies(Long iid) throws IOException {
        Map<String, Object> inspection = getInspectionById(iid);
        if (inspection == null) {
            throw new RuntimeException("Inspection with IID " + iid + " not found");
        }

        Object anomaliesObj = inspection.get("anomalies");
        String anomaliesJson = objectMapper.writeValueAsString(anomaliesObj != null ? anomaliesObj : Collections.emptyList());

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .body(anomaliesJson);
    }

    // Add a new anomaly to the anomalies list in the inspection
    public ResponseEntity<String> addAnomaly(Long iid, Map<String, Object> anomaly) throws IOException {
        Map<String, Object> inspection = getInspectionById(iid);
        if (inspection == null) {
            throw new RuntimeException("Inspection with IID " + iid + " not found");
        }

        // Get existing anomalies and log
        List<Map<String, Object>> anomalies = getAnomaliesList(iid);
        List<Map<String, Object>> anomaliesLog = getAnomaliesLogList(iid);

        // Assign a unique ID to the new anomaly if not present
        if (!anomaly.containsKey("id") || anomaly.get("id") == null) {
            anomaly.put("id", UUID.randomUUID().toString());
        }

        // Mark manually added anomalies as "User"
        if (!anomaly.containsKey("madeBy") || anomaly.get("madeBy") == null) {
            anomaly.put("madeBy", "User");
        }

        // Add the new anomaly
        anomalies.add(anomaly);

        // Log the addition
        Map<String, Object> logEntry = createAnomalyLogEntry(
            (String) anomaly.get("id"),
            anomaly.get("box"),
            "User",
            (String) anomaly.get("className"),
            anomaly.get("confidence") instanceof Number ? ((Number) anomaly.get("confidence")).doubleValue() : null,
            "add"
        );
        anomaliesLog.add(logEntry);

        // Update the inspection with the new anomalies list and log
        HttpHeaders headers = getHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Prefer", "return=representation");

        Map<String, Object> body = new HashMap<>();
        body.put("anomalies", anomalies);
        body.put("anomaliesLog", anomaliesLog);

        String url = supabaseUrl + "/rest/v1/inspections?iid=eq." + iid;
        HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);

        return restTemplate.exchange(url, HttpMethod.PATCH, request, String.class);
    }

    // Update an existing anomaly in the anomalies list by its ID
    public ResponseEntity<String> updateAnomaly(Long iid, String anomalyId, Map<String, Object> updatedAnomaly) throws IOException {
        Map<String, Object> inspection = getInspectionById(iid);
        if (inspection == null) {
            throw new RuntimeException("Inspection with IID " + iid + " not found");
        }

        // Get existing anomalies and log
        List<Map<String, Object>> anomalies = getAnomaliesList(iid);
        List<Map<String, Object>> anomaliesLog = getAnomaliesLogList(iid);

        // Find and update the anomaly with matching ID
        boolean found = false;
        String madeBy = "User";
        for (int i = 0; i < anomalies.size(); i++) {
            Map<String, Object> anomaly = anomalies.get(i);
            if (anomalyId.equals(anomaly.get("id"))) {
                // Preserve the ID and madeBy
                updatedAnomaly.put("id", anomalyId);
                // Preserve the original madeBy value - don't allow it to be changed
                if (anomaly.containsKey("madeBy")) {
                    madeBy = (String) anomaly.get("madeBy");
                    updatedAnomaly.put("madeBy", madeBy);
                }
                anomalies.set(i, updatedAnomaly);
                found = true;
                break;
            }
        }

        if (!found) {
            throw new RuntimeException("Anomaly with ID " + anomalyId + " not found in inspection " + iid);
        }

        // Log the update
        Map<String, Object> logEntry = createAnomalyLogEntry(
            anomalyId,
            updatedAnomaly.get("box"),
            madeBy,
            (String) updatedAnomaly.get("className"),
            updatedAnomaly.get("confidence") instanceof Number ? ((Number) updatedAnomaly.get("confidence")).doubleValue() : null,
            "edit"
        );
        anomaliesLog.add(logEntry);

        // Update the inspection with the modified anomalies list and log
        HttpHeaders headers = getHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Prefer", "return=representation");

        Map<String, Object> body = new HashMap<>();
        body.put("anomalies", anomalies);
        body.put("anomaliesLog", anomaliesLog);

        String url = supabaseUrl + "/rest/v1/inspections?iid=eq." + iid;
        HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);

        return restTemplate.exchange(url, HttpMethod.PATCH, request, String.class);
    }

    // Delete an anomaly from the anomalies list by its ID
    public ResponseEntity<String> deleteAnomaly(Long iid, String anomalyId) throws IOException {
        Map<String, Object> inspection = getInspectionById(iid);
        if (inspection == null) {
            throw new RuntimeException("Inspection with IID " + iid + " not found");
        }

        // Get existing anomalies and log
        List<Map<String, Object>> anomalies = getAnomaliesList(iid);
        List<Map<String, Object>> anomaliesLog = getAnomaliesLogList(iid);

        // Find the anomaly to capture its data before deletion
        Map<String, Object> deletedAnomaly = null;
        for (Map<String, Object> anomaly : anomalies) {
            if (anomalyId.equals(anomaly.get("id"))) {
                deletedAnomaly = anomaly;
                break;
            }
        }

        // Remove the anomaly with matching ID
        boolean removed = anomalies.removeIf(anomaly -> anomalyId.equals(anomaly.get("id")));

        if (!removed) {
            throw new RuntimeException("Anomaly with ID " + anomalyId + " not found in inspection " + iid);
        }

        // Log the deletion
        if (deletedAnomaly != null) {
            Map<String, Object> logEntry = createAnomalyLogEntry(
                anomalyId,
                deletedAnomaly.get("box"),
                (String) deletedAnomaly.get("madeBy"),
                (String) deletedAnomaly.get("className"),
                deletedAnomaly.get("confidence") instanceof Number ? ((Number) deletedAnomaly.get("confidence")).doubleValue() : null,
                "delete"
            );
            anomaliesLog.add(logEntry);
        }

        // Update the inspection with the modified anomalies list and log
        HttpHeaders headers = getHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Prefer", "return=representation");

        Map<String, Object> body = new HashMap<>();
        body.put("anomalies", anomalies);
        body.put("anomaliesLog", anomaliesLog);

        String url = supabaseUrl + "/rest/v1/inspections?iid=eq." + iid;
        HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);

        return restTemplate.exchange(url, HttpMethod.PATCH, request, String.class);
    }

    // Helper: return anomalies list as parsed objects from the inspection
    private List<Map<String, Object>> getAnomaliesList(Long iid) throws IOException {
        Map<String, Object> inspection = getInspectionById(iid);
        if (inspection == null) {
            return Collections.emptyList();
        }

        Object anomaliesObj = inspection.get("anomalies");
        if (anomaliesObj == null) {
            return new ArrayList<>();
        }

        // Handle different types that anomaliesObj might be
        if (anomaliesObj instanceof List) {
            // If it's already a List, convert each item to Map
            List<?> anomaliesList = (List<?>) anomaliesObj;
            List<Map<String, Object>> result = new ArrayList<>();
            for (Object item : anomaliesList) {
                if (item instanceof Map) {
                    result.add((Map<String, Object>) item);
                } else {
                    // Convert to Map via JSON serialization
                    String itemJson = objectMapper.writeValueAsString(item);
                    Map<String, Object> itemMap = objectMapper.readValue(itemJson, new TypeReference<Map<String, Object>>() {});
                    result.add(itemMap);
                }
            }
            return result;
        } else if (anomaliesObj instanceof String) {
            // If it's a String, parse it as JSON
            String anomaliesJson = (String) anomaliesObj;
            if (anomaliesJson.trim().isEmpty() || anomaliesJson.equals("[]")) {
                return new ArrayList<>();
            }
            return objectMapper.readValue(anomaliesJson, new TypeReference<List<Map<String, Object>>>() {});
        } else {
            // For any other type, try to convert via JSON serialization
            String anomaliesJson = objectMapper.writeValueAsString(anomaliesObj);
            return objectMapper.readValue(anomaliesJson, new TypeReference<List<Map<String, Object>>>() {});
        }
    }

    // Helper: return anomalies log list as parsed objects from the inspection
    private List<Map<String, Object>> getAnomaliesLogList(Long iid) throws IOException {
        Map<String, Object> inspection = getInspectionById(iid);
        if (inspection == null) {
            return Collections.emptyList();
        }

        Object anomaliesLogObj = inspection.get("anomaliesLog");
        if (anomaliesLogObj == null) {
            return new ArrayList<>();
        }

        // Handle different types that anomaliesLogObj might be
        if (anomaliesLogObj instanceof List) {
            // If it's already a List, convert each item to Map
            List<?> anomaliesLogList = (List<?>) anomaliesLogObj;
            List<Map<String, Object>> result = new ArrayList<>();
            for (Object item : anomaliesLogList) {
                if (item instanceof Map) {
                    result.add((Map<String, Object>) item);
                } else {
                    // Convert to Map via JSON serialization
                    String itemJson = objectMapper.writeValueAsString(item);
                    Map<String, Object> itemMap = objectMapper.readValue(itemJson, new TypeReference<Map<String, Object>>() {});
                    result.add(itemMap);
                }
            }
            return result;
        } else if (anomaliesLogObj instanceof String) {
            // If it's a String, parse it as JSON
            String anomaliesLogJson = (String) anomaliesLogObj;
            if (anomaliesLogJson.trim().isEmpty() || anomaliesLogJson.equals("[]")) {
                return new ArrayList<>();
            }
            return objectMapper.readValue(anomaliesLogJson, new TypeReference<List<Map<String, Object>>>() {});
        } else {
            // For any other type, try to convert via JSON serialization
            String anomaliesLogJson = objectMapper.writeValueAsString(anomaliesLogObj);
            return objectMapper.readValue(anomaliesLogJson, new TypeReference<List<Map<String, Object>>>() {});
        }
    }

    // Helper: generate a unique inspection number
    private String generateUniqueInspectionNumber() throws IOException {
        String candidate;
        int attempts = 0;
        do {
            int randomNumber = (int) (Math.random() * 1_000_000); // 0 .. 999999
            candidate = String.format("I-%06d", randomNumber);
            attempts++;
            if (attempts > 10000) {
                throw new RuntimeException("Unable to generate a unique inspection number after " + attempts + " attempts");
            }
        } while (existsInspectionNumber(candidate));

        return candidate;
    }

    /**
     * Check whether an inspectionNumber already exists in the Supabase table.
     */
    private boolean existsInspectionNumber(String inspectionNumber) throws IOException {
        String url = supabaseUrl + "/rest/v1/inspections?inspectionNumber=eq." + inspectionNumber + "&select=inspectionNumber&limit=1";
        HttpHeaders headers = getHeaders();
        HttpEntity<String> entity = new HttpEntity<>(headers);
        try {
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);
            List<Map<String, Object>> list = objectMapper.readValue(response.getBody(), new TypeReference<List<Map<String, Object>>>() {});
            return !list.isEmpty();
        } catch (HttpClientErrorException.NotFound e) {
            return false;
        }
    }

    // Helper: create a log entry for an anomaly
    private Map<String, Object> createAnomalyLogEntry(String id, Object box, String madeBy, String className, Double confidence, String action) {
        Map<String, Object> logEntry = new LinkedHashMap<>(); // Use LinkedHashMap to preserve order
        logEntry.put("id", id != null ? id : "");
        logEntry.put("box", box != null ? box : new ArrayList<>());
        logEntry.put("confidence", confidence != null ? confidence : 0.0);
        logEntry.put("class", className != null ? className : ""); // Use "class" instead of "className"
        logEntry.put("timestamp", new Date().toInstant().toString()); // Use ISO-8601 string format
        logEntry.put("madeBy", madeBy != null ? madeBy : ""); // Use "madeBy" with capital B
        logEntry.put("action", action != null ? action : ""); // Track if it was add/edit/delete

        System.out.println("=== DEBUG: Created log entry ===");
        System.out.println("Log entry: " + logEntry);

        return logEntry;
    }

    // Helper: convert box coordinates from [x1, y1, x2, y2] to [x_center, y_center, width, height]
    private void convertBoxCoordinates(Detection detection) {
        if (detection == null || detection.getBox() == null) {
            return;
        }

        List<Double> box = detection.getBox();
        if (box.size() != 4) {
            return; // Invalid box format, skipping
        }

        double x1 = box.get(0);
        double y1 = box.get(1);
        double x2 = box.get(2);
        double y2 = box.get(3);

        System.out.println("=== DEBUG: Converting box coordinates ===");
        System.out.println("Original format [x1, y1, x2, y2]: [" + x1 + ", " + y1 + ", " + x2 + ", " + y2 + "]");

        // Calculate center_x, center_y, width, height
        double centerX = (x1 + x2) / 2.0;
        double centerY = (y1 + y2) / 2.0;
        double width = Math.abs(x2 - x1);
        double height = Math.abs(y2 - y1);

        System.out.println("Converted format [x_center, y_center, width, height]: [" + centerX + ", " + centerY + ", " + width + ", " + height + "]");

        // Set the new box coordinates: [x_center, y_center, width, height]
        detection.setBox(Arrays.asList(centerX, centerY, width, height));
    }
}
