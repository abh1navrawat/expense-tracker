package com.tracker.expense.controller;

import com.tracker.expense.model.ReportJob;
import com.tracker.expense.model.User;
import com.tracker.expense.repository.ReportJobRepository;
import com.tracker.expense.security.SecurityUtils;
import com.tracker.expense.service.ReportService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/reports")
public class ReportController {

    @Autowired
    private ReportJobRepository reportJobRepository;

    @Autowired
    private ReportService reportService;

    @PostMapping("/generate")
    public ResponseEntity<?> generateReport() {
        User user = SecurityUtils.getCurrentUser();
        if (user == null) {
            return ResponseEntity.status(401).build();
        }

        UUID trackingId = UUID.randomUUID();
        ReportJob job = new ReportJob(trackingId, "PENDING", null, user);
        reportJobRepository.save(job);

        // Dispatch background async task
        reportService.generateExcelReportAsync(trackingId, user.getId());

        Map<String, Object> response = new HashMap<>();
        response.put("trackingId", trackingId);
        response.put("status", "PENDING");
        response.put("message", "Excel POI compilation successfully scheduled.");

        // Return HTTP 202 Accepted status immediately as per user workflow specifications
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
    }

    @GetMapping("/status/{trackingId}")
    public ResponseEntity<?> checkStatus(@PathVariable("trackingId") UUID trackingId) {
        Long userId = SecurityUtils.getCurrentUserId();
        if (userId == null) {
            return ResponseEntity.status(401).build();
        }

        ReportJob job = reportJobRepository.findByTrackingId(trackingId).orElse(null);
        if (job == null || !job.getUser().getId().equals(userId)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("The requested Report Job was not found.");
        }

        Map<String, String> response = new HashMap<>();
        response.put("trackingId", trackingId.toString());
        response.put("status", job.getStatus());
        
        return ResponseEntity.ok(response);
    }

    @GetMapping("/download/{trackingId}")
    public ResponseEntity<?> downloadReport(@PathVariable("trackingId") UUID trackingId) {
        Long userId = SecurityUtils.getCurrentUserId();
        if (userId == null) {
            return ResponseEntity.status(401).build();
        }

        ReportJob job = reportJobRepository.findByTrackingId(trackingId).orElse(null);
        if (job == null || !job.getUser().getId().equals(userId)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("The requested Report Job was not found.");
        }

        if (!"COMPLETED".equalsIgnoreCase(job.getStatus())) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("The report compilation has not completed yet. Current status: " + job.getStatus());
        }

        File file = new File(job.getFilePath());
        if (!file.exists()) {
            return ResponseEntity.status(HttpStatus.GONE).body("The compiled Excel file was deleted or is missing.");
        }

        Resource resource = new FileSystemResource(file);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + file.getName() + "\"")
                .body(resource);
    }
}
