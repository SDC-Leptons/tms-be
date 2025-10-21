// src/main/java/com/example/transformer_app/service/TransformerService.java
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class TransformerService {

    @Value("${supabase.url}")
    private String supabaseUrl;

    @Value("${supabase.apikey}")
    private String supabaseApiKey;

    @Value("${supabase.bucket.name}")
    private String bucketName;

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Creates a transformer. The image can be null or empty.
     */
    public ResponseEntity<String> createTransformer(
            String transformerNumber,
            String poleNumber,
            String region,
            String type,
            String locationDetails,
            Double capacity,
            MultipartFile baselineImage
    ) throws IOException {
        // If the caller didn't provide a transformer number, generate one server-side
        if (transformerNumber == null || transformerNumber.trim().isEmpty()) {
            transformerNumber = generateUniqueTransformerNumber();
        }

        String imageUrl = "";
        // Only upload if an image is actually provided
        if (baselineImage != null && !baselineImage.isEmpty()) {
            imageUrl = uploadImage(baselineImage);
        }

        HttpHeaders dbHeaders = getHeaders();
        dbHeaders.setContentType(MediaType.APPLICATION_JSON);
        dbHeaders.set("Prefer", "return=representation");

        Map<String, Object> body = new HashMap<>();
        body.put("transformerNumber", transformerNumber);
        body.put("poleNumber", poleNumber);
        body.put("region", region);
        body.put("type", type);
        body.put("locationDetails", locationDetails);

        // Convert capacity to integer if not null (database expects bigint, not decimal)
        if (capacity != null) {
            body.put("capacity", capacity.intValue());
        } else {
            body.put("capacity", null);
        }

        body.put("baselineImage", imageUrl);

        String dbUrl = supabaseUrl + "/rest/v1/transformers";
        HttpEntity<Map<String, Object>> requestEntity = new HttpEntity<>(body, dbHeaders);

        return restTemplate.exchange(dbUrl, HttpMethod.POST, requestEntity, String.class);
    }

    /**
     * Updates the baseline image for an existing transformer by its ID
     * by replacing the entire record (using PUT).
     */
    public ResponseEntity<String> updateTransformerBaselineImage(Long id, MultipartFile baselineImage) throws IOException {
        // First, get the existing transformer record
        Map<String, Object> existingTransformer = getTransformerById(id);
        if (existingTransformer == null) {
            throw new RuntimeException("Transformer with ID " + id + " not found");
        }

        // Upload the new image if provided
        String imageUrl = "";
        if (baselineImage != null && !baselineImage.isEmpty()) {
            imageUrl = uploadImage(baselineImage);
        }

        // Prepare the updated record with all existing fields plus new image
        HttpHeaders dbHeaders = getHeaders();
        dbHeaders.setContentType(MediaType.APPLICATION_JSON);
        dbHeaders.set("Prefer", "return=representation");

        Map<String, Object> updatedBody = new HashMap<>(existingTransformer);
        updatedBody.put("baselineImage", imageUrl);

        // Use PUT to update the entire record
        String dbUrl = supabaseUrl + "/rest/v1/transformers?id=eq." + id;
        HttpEntity<Map<String, Object>> requestEntity = new HttpEntity<>(updatedBody, dbHeaders);

        return restTemplate.exchange(dbUrl, HttpMethod.PUT, requestEntity, String.class);
    }

    /**
     * Fetches a single transformer by its ID.
     * @return A map representing the transformer, or null if not found.
     */
    private Map<String, Object> getTransformerById(Long id) throws IOException {
        String url = supabaseUrl + "/rest/v1/transformers?id=eq." + id + "&select=*&limit=1";
        HttpHeaders headers = getHeaders();
        HttpEntity<String> entity = new HttpEntity<>(headers);
        try {
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);
            List<Map<String, Object>> transformerList = objectMapper.readValue(response.getBody(), new TypeReference<List<Map<String, Object>>>() {});
            return transformerList.isEmpty() ? null : transformerList.get(0);
        } catch (HttpClientErrorException.NotFound e) {
            return null;
        }
    }


    /**
     * Uploads a file to a Supabase Storage bucket.
     * @return The public URL of the uploaded file.
     */
    public String uploadImage(MultipartFile file) throws IOException {
        String originalFileName = file.getOriginalFilename();
        String sanitizedFileName = (originalFileName == null) ? "file" : originalFileName.replaceAll("[^a-zA-Z0-9.\\-_]", "_");
        String fileName = UUID.randomUUID().toString() + "_" + sanitizedFileName;

        HttpHeaders storageHeaders = getHeaders();
        storageHeaders.setContentType(MediaType.parseMediaType(file.getContentType()));

        HttpEntity<byte[]> storageRequestEntity = new HttpEntity<>(file.getBytes(), storageHeaders);

        String storageUrl = UriComponentsBuilder.fromHttpUrl(supabaseUrl)
                .path("/storage/v1/object/")
                .pathSegment(bucketName, "baselineImages", fileName)
                .toUriString();

        restTemplate.exchange(storageUrl, HttpMethod.POST, storageRequestEntity, String.class);

        return supabaseUrl + "/storage/v1/object/public/" + bucketName + "/baselineImages/" + fileName;
    }

    /**
     * Creates and returns a HttpHeaders object with the required Supabase authentication headers.
     */
    private HttpHeaders getHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("apikey", supabaseApiKey);
        headers.set("Authorization", "Bearer " + supabaseApiKey);
        return headers;
    }

    /**
     * Check whether a transformerNumber already exists in the Supabase table.
     */
    private boolean existsTransformerNumber(String transformerNumber) throws IOException {
        String url = supabaseUrl + "/rest/v1/transformers?transformerNumber=eq." + transformerNumber + "&select=transformerNumber&limit=1";
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

    /**
     * Generate a unique transformer number in the format T-XXXXXX (6 digits). It will check the DB and retry
     * until a unique value is found or a safety limit is exceeded.
     */
    private String generateUniqueTransformerNumber() throws IOException {
        String candidate;
        int attempts = 0;
        do {
            int randomNumber = (int) (Math.random() * 1_000_000); // 0 .. 999999
            candidate = String.format("T-%06d", randomNumber);
            attempts++;
            if (attempts > 10000) {
                throw new RuntimeException("Unable to generate a unique transformer number after " + attempts + " attempts");
            }
        } while (existsTransformerNumber(candidate));

        return candidate;
    }
}