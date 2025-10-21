// src/main/java/com/example/transformer_app/controller/TransformerController.java
package com.example.transformer_app.controller;

import com.example.transformer_app.service.TransformerService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/transformers")
@CrossOrigin(origins = "*")
public class TransformerController {

    @Value("${supabase.url}")
    private String supabaseUrl;

    @Value("${supabase.apikey}")
    private String supabaseApiKey;

    @Autowired
    public TransformerService transformerService;

    private final RestTemplate restTemplate = new RestTemplate();

    // Get all transformers
    @GetMapping
    public ResponseEntity<String> getAll() {
        HttpHeaders headers = getHeaders();
        String url = supabaseUrl + "/rest/v1/transformers?select=*";
        return restTemplate.exchange(url, HttpMethod.GET, new HttpEntity<>(headers), String.class);
    }

    // Create transformer - accepts both JSON and multipart/form-data
    @PostMapping
    public ResponseEntity<String> createTransformer(
            HttpServletRequest request,
            @RequestParam(value = "poleNumber", required = false) String poleNumberParam,
            @RequestParam(value = "region", required = false) String regionParam,
            @RequestParam(value = "type", required = false) String typeParam,
            @RequestParam(value = "locationDetails", required = false) String locationDetailsParam,
            @RequestParam(value = "capacity", required = false) Double capacityParam,
            @RequestParam(value = "baselineImage", required = false) MultipartFile baselineImage
    ) throws IOException {
        try {
            String poleNumber = poleNumberParam;
            String region = regionParam;
            String type = typeParam;
            String locationDetails = locationDetailsParam;
            Double capacity = capacityParam;

            // If parameters are null, try to read from JSON body
            if (poleNumber == null && region == null && type == null) {
                String contentType = request.getContentType();
                if (contentType != null && contentType.contains("application/json")) {
                    // Read JSON body
                    ObjectMapper mapper = new ObjectMapper();
                    Map<String, Object> jsonBody = mapper.readValue(request.getInputStream(), new TypeReference<Map<String, Object>>() {});
                    poleNumber = (String) jsonBody.get("poleNumber");
                    region = (String) jsonBody.get("region");
                    type = (String) jsonBody.get("type");
                    locationDetails = (String) jsonBody.get("locationDetails");

                    // Handle capacity which can be Integer or Double from JSON
                    Object capacityObj = jsonBody.get("capacity");
                    if (capacityObj instanceof Number) {
                        capacity = ((Number) capacityObj).doubleValue();
                    }
                }
            }

            if (poleNumber == null || region == null || type == null) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body("{\"error\":\"Missing required fields: poleNumber, region, and type are required\"}");
            }

            // Pass null for transformerNumber so the service will generate a unique T-XXXXXX
            return transformerService.createTransformer(null, poleNumber, region, type, locationDetails, capacity, baselineImage);
        } catch (IOException e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("{\"error\":\"Failed to create transformer: " + e.getMessage() + "\"}");
        }
    }

    // Update baseline image for an existing transformer
    @PostMapping("/{id}/baselineImage")
    public ResponseEntity<String> updateBaselineImage(
            @PathVariable Long id, // Use Long for the ID
            @RequestParam("baselineImage") MultipartFile baselineImage
    ) {
        try {
            // Delegate all logic to the service layer
            return transformerService.updateTransformerBaselineImage(id, baselineImage);
        } catch (IOException e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("{\"error\":\"Failed to update image: " + e.getMessage() + "\"}");
        }
    }


    // Get transformer by ID
    @GetMapping("/{id}")
    public ResponseEntity<Map<String, Object>> getById(@PathVariable String id) {
        HttpHeaders headers = getHeaders();

        // 1. Get transformer by ID
        String transformerUrl = supabaseUrl + "/rest/v1/transformers?id=eq." + id + "&select=*";
        ResponseEntity<String> transformerResponse = restTemplate.exchange(transformerUrl, HttpMethod.GET, new HttpEntity<>(headers), String.class);

        // Parse transformer JSON (assume single result)
        String transformerJson = transformerResponse.getBody();
        ObjectMapper mapper = new ObjectMapper();
        List<Map<String, Object>> transformerList;
        try {
            transformerList = mapper.readValue(transformerJson, new TypeReference<List<Map<String, Object>>>() {});
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(null);
        }
        if (transformerList.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(null);
        }
        Map<String, Object> transformer = transformerList.get(0);

        // 2. Get inspections by transformerNumber
        String transformerNumber = (String) transformer.get("transformerNumber");
        String inspectionsUrl = supabaseUrl + "/rest/v1/inspections?transformerNumber=eq." + transformerNumber + "&select=*";
        ResponseEntity<String> inspectionsResponse = restTemplate.exchange(inspectionsUrl, HttpMethod.GET, new HttpEntity<>(headers), String.class);

        List<Map<String, Object>> inspectionsList;
        try {
            inspectionsList = mapper.readValue(inspectionsResponse.getBody(), new TypeReference<List<Map<String, Object>>>() {});
        } catch (Exception e) {
            inspectionsList = new ArrayList<>();
        }

        // 3. Combine and return
        Map<String, Object> result = new HashMap<>(transformer);
        result.put("inspections", inspectionsList);

        return ResponseEntity.ok(result);
    }

    private HttpHeaders getHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("apikey", supabaseApiKey);
        headers.set("Authorization", "Bearer " + supabaseApiKey);
//        headers.set("Cache-Control", "no-cache, no-store, must-revalidate");
//        headers.set("Pragma", "no-cache");
//        headers.set("Expires", "0");
        return headers;
    }
}