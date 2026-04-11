package com.ensemble.master.facade;

import com.ensemble.common.model.DataFrame;
import com.ensemble.common.model.DatasetMeta;
import com.ensemble.common.model.TrainingJob;
import com.ensemble.master.entity.TrainingJobEntity;
import com.ensemble.master.repository.JobRepository;
import com.ensemble.master.service.DatasetService;
import com.ensemble.master.service.OrchestratorService;
import com.ensemble.master.service.Preprocessor;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class TrainingFacade {

    private final JobRepository jobRepository;
    private final DatasetService datasetService;
    private final Preprocessor preprocessor;
    private final OrchestratorService orchestratorService;
    private final ObjectMapper objectMapper;

    // In-memory DataFrame store keyed by datasetId (populated at upload time)
    private final Map<Long, DataFrame> dataFrameStore = new ConcurrentHashMap<>();

    public void storeDataFrame(Long datasetId, DataFrame df) {
        dataFrameStore.put(datasetId, df);
    }

    /**
     * Validates that all prerequisites for training are available before starting async execution.
     * This prevents the async method from failing after the HTTP response is sent.
     */
    public void validateTrainingPrerequisites(Long jobId) {
        log.info("Validating training prerequisites for job {}", jobId);
        TrainingJobEntity entity = jobRepository.findById(jobId)
                .orElseThrow(() -> new IllegalArgumentException("Job not found: " + jobId));

        log.info("Checking DataFrame for dataset {}", entity.getDatasetId());
        // Check if DataFrame is available in memory or database
        DataFrame df = dataFrameStore.get(entity.getDatasetId());
        log.info("DataFrame in memory: {}", df != null);
        if (df == null) {
            df = datasetService.getDataFrame(entity.getDatasetId());
            log.info("DataFrame from database: {}", df != null);
        }
        if (df == null) {
            throw new IllegalStateException("DataFrame not found for dataset: " + entity.getDatasetId());
        }

        // Verify dataset metadata exists
        datasetService.findById(entity.getDatasetId());
        log.info("Training prerequisites validated successfully for job {}", jobId);
    }

    @Async("trainingExecutor")
    public void startTraining(Long jobId) {
        TrainingJobEntity entity = jobRepository.findById(jobId)
                .orElseThrow(() -> new IllegalArgumentException("Job not found: " + jobId));

        TrainingJob job = entityToJob(entity);
        String prevStatus = job.getStatus();
        job.transition("RUNNING");
        entity.setStatus("RUNNING");
        jobRepository.save(entity);
        orchestratorService.notifyObservers(job, prevStatus);

        try {
            DatasetMeta meta = datasetService.findById(entity.getDatasetId());
            DataFrame df = dataFrameStore.get(entity.getDatasetId());
            if (df == null) {
                // Fall back to DB-stored DataFrame
                df = datasetService.getDataFrame(entity.getDatasetId());
            }
            if (df == null) {
                throw new IllegalStateException("DataFrame not found for dataset: " + entity.getDatasetId());
            }

            Map<String, Object> hyperparams = entity.getHyperparamsJson() != null
                    ? objectMapper.readValue(entity.getHyperparamsJson(), new TypeReference<>() {})
                    : Map.of();
            job.setHyperparams(hyperparams);

            DataFrame processed = preprocessor.preprocess(df, meta.getTargetColumn());
            orchestratorService.distributePartitions(job, processed);

        } catch (Exception e) {
            log.error("Training failed for job {}", jobId, e);
            entity.setStatus("FAILED");
            entity.setFailureReason(e.getMessage());
            jobRepository.save(entity);
            job.transition("FAILED");
            job.setFailureReason(e.getMessage());
            orchestratorService.notifyObservers(job, "RUNNING");
        }
    }

    private TrainingJob entityToJob(TrainingJobEntity e) {
        TrainingJob job = new TrainingJob();
        job.setId(e.getId());
        job.setStatus(e.getStatus());
        job.setModelType(e.getModelType());
        job.setDatasetId(e.getDatasetId());
        job.setUserId(e.getUserId());
        job.setCreatedAt(e.getCreatedAt());
        job.setUpdatedAt(e.getUpdatedAt());
        return job;
    }
}
