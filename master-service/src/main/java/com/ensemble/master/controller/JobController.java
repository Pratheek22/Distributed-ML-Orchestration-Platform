package com.ensemble.master.controller;

import com.ensemble.common.model.TrainingJob;
import com.ensemble.master.builder.TrainingJobBuilder;
import com.ensemble.master.entity.TrainingJobEntity;
import com.ensemble.master.facade.TrainingFacade;
import com.ensemble.master.repository.JobRepository;
import com.ensemble.master.service.OrchestratorService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.NoSuchElementException;

@RestController
@RequestMapping("/api/jobs")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class JobController {

    private final OrchestratorService orchestratorService;
    private final TrainingFacade trainingFacade;
    private final JobRepository jobRepository;

    @PostMapping("/configure")
    public ResponseEntity<?> configure(@RequestBody Map<String, Object> body, HttpServletRequest request) {
        try {
            Long userId = (Long) request.getAttribute("userId");
            Long datasetId = Long.parseLong(body.get("datasetId").toString());
            String modelType = body.get("modelType").toString();
            @SuppressWarnings("unchecked")
            Map<String, Object> hyperparams = body.containsKey("hyperparams")
                    ? (Map<String, Object>) body.get("hyperparams") : Map.of();

            TrainingJobBuilder builder = new TrainingJobBuilder()
                    .datasetId(datasetId)
                    .modelType(modelType)
                    .hyperparams(hyperparams)
                    .userId(userId);

            TrainingJob job = orchestratorService.createJob(builder);
            return ResponseEntity.ok(Map.of("jobId", job.getId(), "status", job.getStatus()));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(400).body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/{id}/start")
    public ResponseEntity<?> start(@PathVariable Long id) {
        try {
            TrainingJobEntity entity = jobRepository.findById(id)
                    .orElseThrow(() -> new NoSuchElementException("Job not found: " + id));
            if (!"CREATED".equals(entity.getStatus())) {
                return ResponseEntity.status(409).body(Map.of("error", "Job is not in CREATED status"));
            }
            
            // Validate DataFrame availability before starting async training
            trainingFacade.validateTrainingPrerequisites(id);
            
            trainingFacade.startTraining(id);
            return ResponseEntity.ok(Map.of("jobId", id, "status", "RUNNING"));
        } catch (NoSuchElementException e) {
            return ResponseEntity.status(404).body(Map.of("error", e.getMessage()));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/{id}/status")
    public ResponseEntity<?> status(@PathVariable Long id) {
        try {
            TrainingJobEntity entity = jobRepository.findById(id)
                    .orElseThrow(() -> new NoSuchElementException("Job not found: " + id));
            return ResponseEntity.ok(Map.of(
                    "jobId", entity.getId(),
                    "status", entity.getStatus(),
                    "modelType", entity.getModelType(),
                    "evaluationMetric", entity.getEvalMetric() != null ? entity.getEvalMetric() : "",
                    "evalMetric", entity.getEvalMetric() != null ? entity.getEvalMetric() : "",
                    "failureReason", entity.getFailureReason() != null ? entity.getFailureReason() : "",
                    "createdAt", entity.getCreatedAt() != null ? entity.getCreatedAt().toString() : ""
            ));
        } catch (NoSuchElementException e) {
            return ResponseEntity.status(404).body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/{id}/download")
    public ResponseEntity<?> download(@PathVariable Long id) {
        try {
            TrainingJobEntity entity = jobRepository.findById(id)
                    .orElseThrow(() -> new NoSuchElementException("Job not found: " + id));
            if (!"COMPLETED".equals(entity.getStatus())) {
                return ResponseEntity.status(409).body(Map.of("error", "Job is not COMPLETED"));
            }
            // Return a simple JSON model artifact
            String artifact = "{\"jobId\":" + id + ",\"modelType\":\"" + entity.getModelType()
                    + "\",\"evalMetric\":" + entity.getEvalMetric() + "}";
            return ResponseEntity.ok()
                    .header("Content-Disposition", "attachment; filename=model-" + id + ".json")
                    .header("Content-Type", "application/json")
                    .body(artifact);
        } catch (NoSuchElementException e) {
            return ResponseEntity.status(404).body(Map.of("error", e.getMessage()));
        }
    }
}
