package com.ensemble.common.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TrainingJob {

    private Long id;
    private String status;
    private String modelType;
    private Long datasetId;
    private Map<String, Object> hyperparams;
    private Double evaluationMetric;
    private String failureReason;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Long userId;

    public void transition(String newStatus) {
        this.status = newStatus;
        this.updatedAt = LocalDateTime.now();
    }
}
