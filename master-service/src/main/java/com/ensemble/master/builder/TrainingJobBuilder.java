package com.ensemble.master.builder;

import com.ensemble.common.model.TrainingJob;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class TrainingJobBuilder {

    private Long datasetId;
    private String modelType;
    private Map<String, Object> hyperparams;
    private Long userId;

    public TrainingJobBuilder datasetId(Long datasetId) {
        this.datasetId = datasetId;
        return this;
    }

    public TrainingJobBuilder modelType(String modelType) {
        this.modelType = modelType;
        return this;
    }

    public TrainingJobBuilder hyperparams(Map<String, Object> hyperparams) {
        this.hyperparams = hyperparams;
        return this;
    }

    public TrainingJobBuilder userId(Long userId) {
        this.userId = userId;
        return this;
    }

    public TrainingJob build() {
        List<String> missing = new ArrayList<>();
        if (datasetId == null) missing.add("datasetId");
        if (modelType == null) missing.add("modelType");
        if (userId == null) missing.add("userId");

        if (!missing.isEmpty()) {
            throw new IllegalStateException("Missing required fields: " + String.join(", ", missing));
        }

        LocalDateTime now = LocalDateTime.now();
        TrainingJob job = new TrainingJob();
        job.setDatasetId(datasetId);
        job.setModelType(modelType);
        job.setHyperparams(hyperparams);
        job.setUserId(userId);
        job.setStatus("CREATED");
        job.setCreatedAt(now);
        job.setUpdatedAt(now);
        return job;
    }
}
