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

    // Create transformer (image is now optional)
    @PostMapping(consumes = {"multipart/form-data"})
    public ResponseEntity<String> createWithImage(
            @RequestParam("transformerNumber") String transformerNumber,
            @RequestParam("poleNumber") String poleNumber,
            @RequestParam("region") String region,
            @RequestParam("type") String type,
            // Make the image parameter optional
            @RequestParam(value = "baselineImage", required = false) MultipartFile baselineImage
    ) {
        try {
            return transformerService.createTransformer(transformerNumber, poleNumber, region, type, baselineImage);
        } catch (IOException e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Failed to create transformer.");
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
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Failed to update image.");
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