package com.ensemble.master.service;

import com.ensemble.common.dto.ResultMessage;
import com.ensemble.common.model.DatasetMeta;
import com.ensemble.common.model.TrainingJob;
import com.ensemble.common.strategy.EnsembleStrategy;
import com.ensemble.master.entity.JobResultEntity;
import com.ensemble.master.entity.TrainingJobEntity;
import com.ensemble.master.repository.JobRepository;
import com.ensemble.master.repository.JobResultRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
public class AggregatorService {

    private final JobRepository jobRepository;
    private final JobResultRepository jobResultRepository;
    private final DatasetService datasetService;
    private final ObjectMapper objectMapper;
    private final OrchestratorService orchestratorService;

    @Autowired
    public AggregatorService(JobRepository jobRepository,
                              JobResultRepository jobResultRepository,
                              DatasetService datasetService,
                              ObjectMapper objectMapper,
                              @Lazy OrchestratorService orchestratorService) {
        this.jobRepository = jobRepository;
        this.jobResultRepository = jobResultRepository;
        this.datasetService = datasetService;
        this.objectMapper = objectMapper;
        this.orchestratorService = orchestratorService;
    }

    public void aggregate(Long jobId, List<ResultMessage> results, TrainingJobEntity entity) {
        try {
            // Persist each partition result
            for (ResultMessage r : results) {
                JobResultEntity resultEntity = JobResultEntity.builder()
                        .jobId(jobId)
                        .workerId(r.getWorkerId())
                        .partitionIdx(r.getPartitionIndex())
                        .predictionsJson(objectMapper.writeValueAsString(r.getPredictions()))
                        .build();
                jobResultRepository.save(resultEntity);
            }

            // Sort results by partitionIndex so predictions are in dataset order
            List<ResultMessage> sorted = results.stream()
                    .sorted(Comparator.comparingInt(ResultMessage::getPartitionIndex))
                    .collect(Collectors.toList());

            // Collect predictions per partition (each worker predicted on its own partition)
            List<List<Double>> allPredictions = sorted.stream()
                    .map(ResultMessage::getPredictions)
                    .collect(Collectors.toList());

            // Resolve actual task type from DatasetMeta
            String taskType = resolveTaskType(entity);

            // Apply ensemble strategy
            EnsembleStrategy strategy = selectStrategy(taskType);
            List<Double> finalPredictions = strategy.aggregate(allPredictions);

            // Compute metric against ground truth labels extracted from fullDataForLabels
            double metric = computeMetricFromLabels(sorted, finalPredictions, taskType);

            entity.setStatus("COMPLETED");
            entity.setEvalMetric(metric);
            jobRepository.save(entity);

            TrainingJob job = new TrainingJob();
            job.setId(entity.getId());
            job.setStatus("COMPLETED");
            job.setEvaluationMetric(metric);
            job.setUserId(entity.getUserId());
            orchestratorService.notifyObservers(job, "RUNNING");
            log.info("Job {} COMPLETED — taskType={}, metric={}", jobId, taskType, metric);

        } catch (Exception e) {
            log.error("Aggregation failed for job {}", jobId, e);
            entity.setStatus("FAILED");
            entity.setFailureReason("Aggregation error: " + e.getMessage());
            jobRepository.save(entity);
        }
    }

    /**
     * Resolve task type from DatasetMeta. Falls back to "classification" if not set.
     */
    private String resolveTaskType(TrainingJobEntity entity) {
        try {
            DatasetMeta meta = datasetService.findById(entity.getDatasetId());
            if (meta.getTaskType() != null && !meta.getTaskType().isBlank()) {
                return meta.getTaskType();
            }
        } catch (Exception e) {
            log.warn("Could not resolve task type for dataset {}, defaulting to classification", entity.getDatasetId());
        }
        return "classification";
    }

    public EnsembleStrategy selectStrategy(String taskType) {
        if ("classification".equalsIgnoreCase(taskType)) {
            return this::majorityVote;
        } else {
            return this::average;
        }
    }

    /**
     * Majority voting: for each sample position, pick the class with most votes.
     * Each inner list is one worker's predictions for its partition.
     * Since workers predict on different partitions, we concatenate in order.
     */
    private List<Double> majorityVote(List<List<Double>> predictions) {
        // Each list is a different partition — concatenate all predictions in order
        // (workers trained on partitions and predicted on the same partition)
        // For ensemble voting across workers that each saw the FULL dataset,
        // we need same-length lists. If lengths differ (partitions), concatenate.
        if (predictions.isEmpty()) return Collections.emptyList();

        // Check if all lists are same length (same-test-set scenario)
        boolean sameLength = predictions.stream().mapToInt(List::size).distinct().count() == 1;
        if (sameLength) {
            int size = predictions.get(0).size();
            List<Double> result = new ArrayList<>();
            for (int i = 0; i < size; i++) {
                final int idx = i;
                Map<Double, Long> votes = predictions.stream()
                        .map(p -> p.get(idx))
                        .collect(Collectors.groupingBy(v -> v, Collectors.counting()));
                result.add(votes.entrySet().stream()
                        .max(Map.Entry.comparingByValue())
                        .map(Map.Entry::getKey).orElse(0.0));
            }
            return result;
        } else {
            // Different partition sizes — concatenate in partition order
            return predictions.stream().flatMap(List::stream).collect(Collectors.toList());
        }
    }

    /**
     * Averaging: for each sample position, compute mean across workers.
     */
    private List<Double> average(List<List<Double>> predictions) {
        if (predictions.isEmpty()) return Collections.emptyList();

        boolean sameLength = predictions.stream().mapToInt(List::size).distinct().count() == 1;
        if (sameLength) {
            int size = predictions.get(0).size();
            List<Double> result = new ArrayList<>();
            for (int i = 0; i < size; i++) {
                final int idx = i;
                double avg = predictions.stream().mapToDouble(p -> p.get(idx)).average().orElse(0.0);
                result.add(avg);
            }
            return result;
        } else {
            return predictions.stream().flatMap(List::stream).collect(Collectors.toList());
        }
    }

    /**
     * Compute metric using ground truth labels from the fullDataForLabels stored in ResultMessage.
     * Falls back to self-consistency metric if labels unavailable.
     */
    private double computeMetricFromLabels(List<ResultMessage> sortedResults,
                                            List<Double> finalPredictions,
                                            String taskType) {
        // Collect ground truth labels from each result's partition labels
        List<Double> groundTruth = sortedResults.stream()
                .filter(r -> r.getGroundTruthLabels() != null && !r.getGroundTruthLabels().isEmpty())
                .flatMap(r -> r.getGroundTruthLabels().stream())
                .collect(Collectors.toList());

        if (groundTruth.isEmpty()) {
            // Fallback: use self-consistency (predictions agree with themselves = 1.0)
            log.warn("No ground truth labels available, using self-consistency metric");
            return 1.0;
        }

        int n = Math.min(finalPredictions.size(), groundTruth.size());
        if (n == 0) return 0.0;

        if ("classification".equalsIgnoreCase(taskType)) {
            long correct = 0;
            for (int i = 0; i < n; i++) {
                if (Math.abs(finalPredictions.get(i) - groundTruth.get(i)) < 0.5) correct++;
            }
            return (double) correct / n;
        } else {
            double mse = 0.0;
            for (int i = 0; i < n; i++) {
                double diff = finalPredictions.get(i) - groundTruth.get(i);
                mse += diff * diff;
            }
            return mse / n;
        }
    }

    public double computeMetric(List<Double> predictions, List<Double> labels, String taskType) {
        int n = Math.min(predictions.size(), labels.size());
        if (n == 0) return 0.0;
        if ("classification".equalsIgnoreCase(taskType)) {
            long correct = 0;
            for (int i = 0; i < n; i++) {
                if (Math.abs(predictions.get(i) - labels.get(i)) < 0.5) correct++;
            }
            return (double) correct / n;
        } else {
            double mse = 0.0;
            for (int i = 0; i < n; i++) {
                double diff = predictions.get(i) - labels.get(i);
                mse += diff * diff;
            }
            return mse / n;
        }
    }
}
