package com.example.transformer_app.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.util.*;

@Service
public class MaintenanceService {

    @Value("${supabase.url}")
    private String supabaseUrl;

    @Value("${supabase.apikey}")
    private String supabaseApiKey;

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    public MaintenanceService(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    // Create a new maintenance record
    public ResponseEntity<String> createMaintenance(
            String inspectionNumber,
            String inspectorName,
            String status,
            Map<String, Object> electricalReadings,
            String recommendedActions,
            String additionalRemarks
    ) throws IOException {
        // Generate unique maintenance number
        String maintenanceNumber = generateUniqueMaintenanceNumber();

        HttpHeaders headers = getHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Prefer", "return=representation");

        Map<String, Object> body = new HashMap<>();
        body.put("maintenanceNumber", maintenanceNumber);
        body.put("inspectionNumber", inspectionNumber);
        body.put("inspectorName", inspectorName);
        body.put("status", status);
        body.put("electricalReadings", electricalReadings != null ? electricalReadings : new HashMap<>());
        body.put("recommendedActions", recommendedActions);
        body.put("additionalRemarks", additionalRemarks);
        // Don't set created_at - let Supabase handle it with DEFAULT

        String url = supabaseUrl + "/rest/v1/maintenance";
        HttpEntity<Map<String, Object>> requestEntity = new HttpEntity<>(body, headers);

        return restTemplate.exchange(url, HttpMethod.POST, requestEntity, String.class);
    }

    // Get all maintenance records
    public ResponseEntity<String> getAllMaintenance() {
        String url = supabaseUrl + "/rest/v1/maintenance?select=*&order=created_at.desc";
        HttpHeaders headers = getHeaders();
        HttpEntity<String> entity = new HttpEntity<>(headers);

        return restTemplate.exchange(url, HttpMethod.GET, entity, String.class);
    }

    // Get maintenance record by ID
    public ResponseEntity<String> getMaintenanceById(Long mid) throws IOException {
        Map<String, Object> maintenance = getMaintenanceByIdInternal(mid);
        if (maintenance == null) {
            throw new RuntimeException("Maintenance record with MID " + mid + " not found");
        }

        String json = objectMapper.writeValueAsString(maintenance);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .body(json);
    }

    // Get maintenance records by inspection number
    public ResponseEntity<String> getMaintenanceByInspectionNumber(String inspectionNumber) {
        String url = supabaseUrl + "/rest/v1/maintenance?inspectionNumber=eq." + inspectionNumber + "&select=*&order=created_at.desc";
        HttpHeaders headers = getHeaders();
        HttpEntity<String> entity = new HttpEntity<>(headers);

        return restTemplate.exchange(url, HttpMethod.GET, entity, String.class);
    }

    // Update maintenance record
    public ResponseEntity<String> updateMaintenance(
            Long mid,
            String inspectorName,
            String status,
            Map<String, Object> electricalReadings,
            String recommendedActions,
            String additionalRemarks
    ) throws IOException {
        Map<String, Object> existingMaintenance = getMaintenanceByIdInternal(mid);
        if (existingMaintenance == null) {
            throw new RuntimeException("Maintenance record with MID " + mid + " not found");
        }

        HttpHeaders headers = getHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Prefer", "return=representation");

        Map<String, Object> updateFields = new HashMap<>();
        if (inspectorName != null) updateFields.put("inspectorName", inspectorName);
        if (status != null) updateFields.put("status", status);
        if (electricalReadings != null) updateFields.put("electricalReadings", electricalReadings);
        if (recommendedActions != null) updateFields.put("recommendedActions", recommendedActions);
        if (additionalRemarks != null) updateFields.put("additionalRemarks", additionalRemarks);

        String url = supabaseUrl + "/rest/v1/maintenance?mid=eq." + mid;
        HttpEntity<Map<String, Object>> requestEntity = new HttpEntity<>(updateFields, headers);

        return restTemplate.exchange(url, HttpMethod.PATCH, requestEntity, String.class);
    }

    // Delete maintenance record
    public ResponseEntity<String> deleteMaintenance(Long mid) throws IOException {
        Map<String, Object> existingMaintenance = getMaintenanceByIdInternal(mid);
        if (existingMaintenance == null) {
            throw new RuntimeException("Maintenance record with MID " + mid + " not found");
        }

        String url = supabaseUrl + "/rest/v1/maintenance?mid=eq." + mid;
        HttpHeaders headers = getHeaders();
        HttpEntity<String> requestEntity = new HttpEntity<>(headers);

        return restTemplate.exchange(url, HttpMethod.DELETE, requestEntity, String.class);
    }

    // Helper method to get maintenance by ID internally
    private Map<String, Object> getMaintenanceByIdInternal(Long mid) throws IOException {
        String url = supabaseUrl + "/rest/v1/maintenance?mid=eq." + mid + "&select=*&limit=1";
        HttpHeaders headers = getHeaders();
        HttpEntity<String> entity = new HttpEntity<>(headers);

        try {
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);
            List<Map<String, Object>> maintenanceList = objectMapper.readValue(response.getBody(), new TypeReference<List<Map<String, Object>>>() {});
            return maintenanceList.isEmpty() ? null : maintenanceList.get(0);
        } catch (HttpClientErrorException.NotFound e) {
            return null;
        }
    }

    // Generate unique maintenance number
    private String generateUniqueMaintenanceNumber() throws IOException {
        String candidate;
        int attempts = 0;
        do {
            int randomNumber = (int) (Math.random() * 1_000_000); // 0 .. 999999
            candidate = String.format("M-%06d", randomNumber);
            attempts++;
            if (attempts > 10000) {
                throw new RuntimeException("Unable to generate a unique maintenance number after " + attempts + " attempts");
            }
        } while (existsMaintenanceNumber(candidate));

        return candidate;
    }

    // Check if maintenance number exists
    private boolean existsMaintenanceNumber(String maintenanceNumber) throws IOException {
        String url = supabaseUrl + "/rest/v1/maintenance?maintenanceNumber=eq." + maintenanceNumber + "&select=maintenanceNumber&limit=1";
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

    private HttpHeaders getHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("apikey", supabaseApiKey);
        headers.set("Authorization", "Bearer " + supabaseApiKey);
        return headers;
    }
}
