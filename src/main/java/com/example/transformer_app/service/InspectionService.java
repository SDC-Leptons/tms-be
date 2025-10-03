package com.example.transformer_app.service;

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
        if (refImage != null && !refImage.isEmpty()) {
            imageUrl = uploadImage(refImage);
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

        String dbUrl = supabaseUrl + "/rest/v1/inspections";
        HttpEntity<Map<String, Object>> requestEntity = new HttpEntity<>(body, dbHeaders);

        return restTemplate.exchange(dbUrl, HttpMethod.POST, requestEntity, String.class);
    }

    public ResponseEntity<String> updateInspectionRefImage(Long iid, MultipartFile refImage) throws IOException {
        // First, get the existing inspection record
        Map<String, Object> existingInspection = getInspectionById(iid);
        if (existingInspection == null) {
            throw new RuntimeException("Inspection with IID " + iid + " not found");
        }

        // Upload the new image if provided
        String imageUrl = "";
        if (refImage != null && !refImage.isEmpty()) {
            imageUrl = uploadImage(refImage);
        }

        // Prepare the updated record with all existing fields plus new image
        HttpHeaders dbHeaders = getHeaders();
        dbHeaders.setContentType(MediaType.APPLICATION_JSON);
        dbHeaders.set("Prefer", "return=representation");

        Map<String, Object> updatedBody = new HashMap<>(existingInspection);
        updatedBody.put("refImage", imageUrl);

        // Use PUT to update the entire record
        String dbUrl = supabaseUrl + "/rest/v1/inspections?iid=eq." + iid;
        HttpEntity<Map<String, Object>> requestEntity = new HttpEntity<>(updatedBody, dbHeaders);

        return restTemplate.exchange(dbUrl, HttpMethod.PUT, requestEntity, String.class);
    }

    /**
     * Fetches a single inspection by its IID.
     * @return A map representing the inspection, or null if not found.
     */
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

    private HttpHeaders getHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("apikey", supabaseApiKey);
        headers.set("Authorization", "Bearer " + supabaseApiKey);
        return headers;
    }
}