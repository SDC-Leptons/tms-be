package com.example.transformer_app.controller;

import com.example.transformer_app.service.MaintenanceService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.Map;

@RestController
@RequestMapping("/api/maintenance")
@CrossOrigin(origins = "*")
public class MaintenanceController {

    private final MaintenanceService maintenanceService;

    @Autowired
    public MaintenanceController(MaintenanceService maintenanceService) {
        this.maintenanceService = maintenanceService;
    }

    /**
     * Create a new maintenance record
     * POST /api/maintenance
     * Body: {
     *   "inspectionNumber": "I-123456",
     *   "inspectorName": "John Doe",
     *   "status": "completed",
     *   "electricalReadings": {"voltage": 220, "current": 10, "power": 2200},
     *   "recommendedActions": "Replace bushings",
     *   "additionalRemarks": "Minor oil leak detected"
     * }
     */
    @PostMapping
    public ResponseEntity<String> createMaintenance(@RequestBody Map<String, Object> request) {
        try {
            String inspectionNumber = (String) request.get("inspectionNumber");
            String inspectorName = (String) request.get("inspectorName");
            String status = (String) request.get("status");
            Map<String, Object> electricalReadings = (Map<String, Object>) request.get("electricalReadings");
            String recommendedActions = (String) request.get("recommendedActions");
            String additionalRemarks = (String) request.get("additionalRemarks");

            return maintenanceService.createMaintenance(
                    inspectionNumber,
                    inspectorName,
                    status,
                    electricalReadings,
                    recommendedActions,
                    additionalRemarks
            );
        } catch (Exception e) {
            e.printStackTrace();
            System.err.println("Error creating maintenance: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("{\"error\": \"" + e.getMessage() + "\"}");
        }
    }

    /**
     * Get all maintenance records
     * GET /api/maintenance
     */
    @GetMapping
    public ResponseEntity<String> getAllMaintenance() {
        try {
            return maintenanceService.getAllMaintenance();
        } catch (Exception e) {
            e.printStackTrace();
            System.err.println("Error fetching maintenance records: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("{\"error\": \"" + e.getMessage() + "\"}");
        }
    }

    /**
     * Get maintenance record by ID
     * GET /api/maintenance/{mid}
     */
    @GetMapping("/{mid}")
    public ResponseEntity<String> getMaintenanceById(@PathVariable Long mid) {
        try {
            return maintenanceService.getMaintenanceById(mid);
        } catch (Exception e) {
            e.printStackTrace();
            System.err.println("Error fetching maintenance by ID: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("{\"error\": \"" + e.getMessage() + "\"}");
        }
    }

    /**
     * Get maintenance records by inspection number
     * GET /api/maintenance/inspection/{inspectionNumber}
     */
    @GetMapping("/inspection/{inspectionNumber}")
    public ResponseEntity<String> getMaintenanceByInspectionNumber(@PathVariable String inspectionNumber) {
        try {
            return maintenanceService.getMaintenanceByInspectionNumber(inspectionNumber);
        } catch (Exception e) {
            e.printStackTrace();
            System.err.println("Error fetching maintenance by inspection number: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("{\"error\": \"" + e.getMessage() + "\"}");
        }
    }

    /**
     * Update maintenance record
     * PATCH /api/maintenance/{mid}
     * Body: {
     *   "inspectorName": "Jane Doe",
     *   "status": "in-progress",
     *   "electricalReadings": {"voltage": 230, "current": 12},
     *   "recommendedActions": "Schedule replacement",
     *   "additionalRemarks": "Updated remarks"
     * }
     */
    @PatchMapping("/{mid}")
    public ResponseEntity<String> updateMaintenance(@PathVariable Long mid, @RequestBody Map<String, Object> request) {
        try {
            String inspectorName = (String) request.get("inspectorName");
            String status = (String) request.get("status");
            Map<String, Object> electricalReadings = (Map<String, Object>) request.get("electricalReadings");
            String recommendedActions = (String) request.get("recommendedActions");
            String additionalRemarks = (String) request.get("additionalRemarks");

            return maintenanceService.updateMaintenance(
                    mid,
                    inspectorName,
                    status,
                    electricalReadings,
                    recommendedActions,
                    additionalRemarks
            );
        } catch (Exception e) {
            e.printStackTrace();
            System.err.println("Error updating maintenance: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("{\"error\": \"" + e.getMessage() + "\"}");
        }
    }

    /**
     * Delete maintenance record
     * DELETE /api/maintenance/{mid}
     */
    @DeleteMapping("/{mid}")
    public ResponseEntity<String> deleteMaintenance(@PathVariable Long mid) {
        try {
            return maintenanceService.deleteMaintenance(mid);
        } catch (Exception e) {
            e.printStackTrace();
            System.err.println("Error deleting maintenance: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("{\"error\": \"" + e.getMessage() + "\"}");
        }
    }
}
