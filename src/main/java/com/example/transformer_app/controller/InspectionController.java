package com.example.transformer_app.controller;

import com.example.transformer_app.service.InspectionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import java.util.*;
import java.io.IOException;

@RestController
@RequestMapping("/api/inspections")
@CrossOrigin(origins = "*")
public class InspectionController {

    @Value("${supabase.url}")
    private String supabaseUrl;

    @Value("${supabase.apikey}")
    private String supabaseApiKey;

    @Autowired
    private InspectionService inspectionService;

    private final RestTemplate restTemplate = new RestTemplate();

    @GetMapping
    public ResponseEntity<String> getAll() {
        HttpHeaders headers = getHeaders();
        String url = supabaseUrl + "/rest/v1/inspections?select=*";
        return restTemplate.exchange(url, HttpMethod.GET, new HttpEntity<>(headers), String.class);
    }

    @GetMapping("/{iid}")
    public ResponseEntity<?> getInspectionWithBaselineImage(@PathVariable String iid) {
        HttpHeaders headers = getHeaders();
        String inspectionUrl = supabaseUrl + "/rest/v1/inspections?iid=eq." + iid + "&select=*";
        ResponseEntity<String> inspectionResponse = restTemplate.exchange(inspectionUrl, HttpMethod.GET, new HttpEntity<>(headers), String.class);

        ObjectMapper mapper = new ObjectMapper();
        List<Map<String, Object>> inspectionList;
        try {
            inspectionList = mapper.readValue(inspectionResponse.getBody(), new TypeReference<List<Map<String, Object>>>() {});
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error parsing inspection data.");
        }
        if (inspectionList.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Inspection not found.");
        }
        Map<String, Object> inspection = inspectionList.get(0);

        String transformerNumber = (String) inspection.get("transformerNumber");
        String transformerUrl = supabaseUrl + "/rest/v1/transformers?transformerNumber=eq." + transformerNumber + "&select=baselineImage";
        ResponseEntity<String> transformerResponse = restTemplate.exchange(transformerUrl, HttpMethod.GET, new HttpEntity<>(headers), String.class);

        List<Map<String, Object>> transformerList;
        try {
            transformerList = mapper.readValue(transformerResponse.getBody(), new TypeReference<List<Map<String, Object>>>() {});
        } catch (Exception e) {
            transformerList = new ArrayList<>();
        }
        String baselineImage = transformerList.isEmpty() ? null : (String) transformerList.get(0).get("baselineImage");

        Map<String, Object> result = new HashMap<>(inspection);
        result.put("baselineImage", baselineImage);

        return ResponseEntity.ok(result);
    }

    @PostMapping(consumes = {"multipart/form-data"})
    public ResponseEntity<String> createInspection(
            @RequestParam("transformerNumber") String transformerNumber,
            @RequestParam("inspectionNumber") String inspectionNumber,
            @RequestParam("inspectionDate") String inspectionDate,
            @RequestParam("maintainanceDate") String maintainanceDate,
            @RequestParam("status") String status,
            @RequestParam(value = "refImage", required = false) MultipartFile refImage
    ) {
        try {
            return inspectionService.createInspection(transformerNumber, inspectionNumber, inspectionDate, maintainanceDate, status, refImage);
        } catch (IOException e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Failed to upload image.");
        }
    }

    @PostMapping("/{iid}/refImage")
    public ResponseEntity<String> updateRefImage(
            @PathVariable Long iid,
            @RequestParam("refImage") MultipartFile refImage,
            @RequestParam(value = "threshold", required = false) Double threshold
    ) {
        try {
            return inspectionService.updateInspectionRefImage(iid, refImage, threshold);
        } catch (RuntimeException e) {
            // Handle case where inspection is not found
            if (e.getMessage().contains("not found")) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage());
            }
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error: " + e.getMessage());
        } catch (IOException e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Failed to update image.");
        }
    }

    private HttpHeaders getHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("apikey", supabaseApiKey);
        headers.set("Authorization", "Bearer " + supabaseApiKey);
        return headers;
    }
}