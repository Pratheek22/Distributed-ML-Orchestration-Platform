package com.ensemble.master.service;

import com.ensemble.common.dto.ResultMessage;
import com.ensemble.common.dto.TaskMessage;
import com.ensemble.common.model.DataFrame;
import com.ensemble.common.model.DatasetMeta;
import com.ensemble.common.model.TrainingJob;
import com.ensemble.master.builder.TrainingJobBuilder;
import com.ensemble.master.config.RabbitMQConfig;
import com.ensemble.master.entity.TrainingJobEntity;
import com.ensemble.master.observer.JobStatusObserver;
import com.ensemble.master.repository.JobRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
public class OrchestratorService {

    private final JobRepository jobRepository;
    private final RabbitTemplate rabbitTemplate;
    private final ZooKeeperService zooKeeperService;
    private final AggregatorService aggregatorService;
    private final DatasetService datasetService;
    private final ObjectMapper objectMapper;
    private final List<JobStatusObserver> observers;

    // jobId -> list of received results
    private final Map<Long, List<ResultMessage>> pendingResults = new ConcurrentHashMap<>();
    // jobId -> expected partition count
    private final Map<Long, Integer> expectedCounts = new ConcurrentHashMap<>();
    // jobId -> pending task messages (for reassignment on worker failure)
    private final Map<Long, Map<Integer, TaskMessage>> pendingTasks = new ConcurrentHashMap<>();

    @Autowired
    public OrchestratorService(JobRepository jobRepository,
                                RabbitTemplate rabbitTemplate,
                                ZooKeeperService zooKeeperService,
                                @Lazy AggregatorService aggregatorService,
                                DatasetService datasetService,
                                ObjectMapper objectMapper,
                                List<JobStatusObserver> observers) {
        this.jobRepository = jobRepository;
        this.rabbitTemplate = rabbitTemplate;
        this.zooKeeperService = zooKeeperService;
        this.aggregatorService = aggregatorService;
        this.datasetService = datasetService;
        this.objectMapper = objectMapper;
        this.observers = observers;
    }

    public TrainingJob createJob(TrainingJobBuilder builder) {
        TrainingJob job = builder.build();
        try {
            TrainingJobEntity entity = TrainingJobEntity.builder()
                    .userId(job.getUserId())
                    .datasetId(job.getDatasetId())
                    .status(job.getStatus())
                    .modelType(job.getModelType())
                    .hyperparamsJson(job.getHyperparams() != null
                            ? objectMapper.writeValueAsString(job.getHyperparams()) : null)
                    .build();
            TrainingJobEntity saved = jobRepository.save(entity);
            job.setId(saved.getId());
            return job;
        } catch (Exception e) {
            throw new RuntimeException("Failed to persist training job", e);
        }
    }

    public void distributePartitions(TrainingJob job, DataFrame df) {
        List<String> activeWorkers = zooKeeperService.getActiveWorkers();
        if (activeWorkers.isEmpty()) {
            job.setFailureReason("No active workers available");
            transitionJob(job, "FAILED");
            persistJobState(job);
            return;
        }

        // Resolve target column from DatasetMeta to pass to workers
        String targetColumn = resolveTargetColumn(job.getDatasetId());

        int totalRows = df.rowCount();
        int partitionSize = (int) Math.ceil((double) totalRows / 3);
        int partitionCount = 0;
        Map<Integer, TaskMessage> taskMap = new ConcurrentHashMap<>();

        for (int i = 0; i < 3; i++) {
            int from = i * partitionSize;
            int to = Math.min(from + partitionSize, totalRows);
            if (from >= totalRows) break;

            DataFrame partition = df.slice(from, to);

            // Merge targetColumn into hyperparams so workers can extract labels
            Map<String, Object> hyperparams = new HashMap<>(
                    job.getHyperparams() != null ? job.getHyperparams() : Map.of());
            if (targetColumn != null) hyperparams.put("targetColumn", targetColumn);

            TaskMessage task = new TaskMessage(
                    String.valueOf(job.getId()), i, job.getModelType(),
                    hyperparams, partition, df);

            taskMap.put(i, task);
            rabbitTemplate.convertAndSend(RabbitMQConfig.EXCHANGE, RabbitMQConfig.TASK_QUEUE, task);
            log.info("Published partition {} for job {} ({} rows)", i, job.getId(), partition.rowCount());
            partitionCount++;
        }

        pendingResults.put(job.getId(), Collections.synchronizedList(new ArrayList<>()));
        expectedCounts.put(job.getId(), partitionCount);
        pendingTasks.put(job.getId(), taskMap);
    }

    @RabbitListener(queues = RabbitMQConfig.RESULTS_QUEUE)
    public void handleResult(ResultMessage result) {
        Long jobId = Long.parseLong(result.getJobId());
        List<ResultMessage> results = pendingResults.computeIfAbsent(
                jobId, k -> Collections.synchronizedList(new ArrayList<>()));
        results.add(result);

        // Remove from pending tasks (partition received)
        Map<Integer, TaskMessage> tasks = pendingTasks.get(jobId);
        if (tasks != null) tasks.remove(result.getPartitionIndex());

        log.info("Received result from worker {} for job {}, partition {} ({} predictions)",
                result.getWorkerId(), jobId, result.getPartitionIndex(),
                result.getPredictions() != null ? result.getPredictions().size() : 0);

        int expected = expectedCounts.getOrDefault(jobId, 3);
        if (results.size() >= expected) {
            TrainingJobEntity entity = jobRepository.findById(jobId).orElse(null);
            if (entity == null) return;
            aggregatorService.aggregate(jobId, new ArrayList<>(results), entity);
            pendingResults.remove(jobId);
            expectedCounts.remove(jobId);
            pendingTasks.remove(jobId);
        }
    }

    /**
     * Called by ZooKeeperService when a worker goes down.
     * Reassigns any pending tasks that were assigned to the failed worker.
     */
    public void handleWorkerFailure(String failedWorkerId) {
        log.warn("Worker {} failed — checking for pending task reassignment", failedWorkerId);
        List<String> activeWorkers = zooKeeperService.getActiveWorkers();

        for (Map.Entry<Long, Map<Integer, TaskMessage>> entry : pendingTasks.entrySet()) {
            Long jobId = entry.getKey();
            Map<Integer, TaskMessage> tasks = entry.getValue();

            if (tasks.isEmpty()) continue;

            if (activeWorkers.isEmpty()) {
                log.error("No active workers for reassignment — failing job {}", jobId);
                jobRepository.findById(jobId).ifPresent(entity -> {
                    entity.setStatus("FAILED");
                    entity.setFailureReason("All workers failed during training");
                    jobRepository.save(entity);
                });
                pendingTasks.remove(jobId);
                pendingResults.remove(jobId);
                expectedCounts.remove(jobId);
                continue;
            }

            // Republish all remaining tasks
            for (TaskMessage task : tasks.values()) {
                log.info("Reassigning partition {} of job {} to available workers", task.getPartitionIndex(), jobId);
                rabbitTemplate.convertAndSend(RabbitMQConfig.EXCHANGE, RabbitMQConfig.TASK_QUEUE, task);
            }
        }
    }

    public void transitionJob(TrainingJob job, String newStatus) {
        String prev = job.getStatus();
        job.transition(newStatus);
        notifyObservers(job, prev);
    }

    public void transitionJob(Long jobId, String newStatus, String failureReason) {
        TrainingJobEntity entity = jobRepository.findById(jobId)
                .orElseThrow(() -> new NoSuchElementException("Job not found: " + jobId));
        String previousStatus = entity.getStatus();
        entity.setStatus(newStatus);
        if (failureReason != null) entity.setFailureReason(failureReason);
        jobRepository.save(entity);
        notifyObservers(entityToJob(entity), previousStatus);
    }

    public void notifyObservers(TrainingJob job, String previousStatus) {
        for (JobStatusObserver observer : observers) {
            try {
                observer.onStatusChange(job, previousStatus);
            } catch (Exception e) {
                log.error("Observer {} threw exception", observer.getClass().getSimpleName(), e);
            }
        }
    }

    public void persistJobState(TrainingJob job) {
        jobRepository.findById(job.getId()).ifPresent(entity -> {
            entity.setStatus(job.getStatus());
            entity.setEvalMetric(job.getEvaluationMetric());
            entity.setFailureReason(job.getFailureReason());
            jobRepository.save(entity);
        });
    }

    private String resolveTargetColumn(Long datasetId) {
        try {
            DatasetMeta meta = datasetService.findById(datasetId);
            return meta.getTargetColumn();
        } catch (Exception e) {
            log.warn("Could not resolve target column for dataset {}", datasetId);
            return null;
        }
    }

    private TrainingJob entityToJob(TrainingJobEntity e) {
        TrainingJob job = new TrainingJob();
        job.setId(e.getId());
        job.setStatus(e.getStatus());
        job.setModelType(e.getModelType());
        job.setDatasetId(e.getDatasetId());
        job.setUserId(e.getUserId());
        job.setEvaluationMetric(e.getEvalMetric());
        job.setFailureReason(e.getFailureReason());
        job.setCreatedAt(e.getCreatedAt());
        job.setUpdatedAt(e.getUpdatedAt());
        return job;
    }
}
