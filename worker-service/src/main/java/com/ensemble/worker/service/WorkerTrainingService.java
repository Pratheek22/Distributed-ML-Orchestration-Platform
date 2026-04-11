package com.ensemble.worker.service;

import com.ensemble.common.dto.ResultMessage;
import com.ensemble.common.dto.TaskMessage;
import com.ensemble.common.model.DataFrame;
import com.ensemble.common.strategy.MLModelStrategy;
import com.ensemble.worker.factory.ModelFactory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class WorkerTrainingService {

    private final ModelFactory modelFactory;

    @Value("${worker.id:worker-1}")
    private String workerId;

    public ResultMessage processTask(TaskMessage task) {
        log.info("Worker {} processing task for job {}, partition {}",
                workerId, task.getJobId(), task.getPartitionIndex());

        MLModelStrategy strategy = modelFactory.create(task.getModelType());

        // Pass targetColumn via hyperparams so strategies know which column to predict
        Map<String, Object> hyperparams = task.getHyperparams() != null
                ? task.getHyperparams() : Map.of();

        // Train on the partition and get predictions
        List<Double> predictions = strategy.train(task.getPartition(), hyperparams);

        // Extract ground truth labels from the partition's target column
        List<Double> groundTruth = extractLabels(task.getPartition(), hyperparams);

        ResultMessage result = new ResultMessage(
                task.getJobId(), workerId, task.getPartitionIndex(), predictions, groundTruth);

        log.info("Worker {} completed task for job {}, partition {} — {} predictions",
                workerId, task.getJobId(), task.getPartitionIndex(), predictions.size());
        return result;
    }

    /**
     * Extract ground truth labels from the target column of the partition.
     */
    private List<Double> extractLabels(DataFrame partition, Map<String, Object> hyperparams) {
        if (partition == null || partition.getRows() == null) return List.of();

        // Determine target column: from hyperparams or last column
        String targetColumn = hyperparams.containsKey("targetColumn")
                ? hyperparams.get("targetColumn").toString()
                : partition.getColumns().get(partition.getColumns().size() - 1);

        List<Double> labels = new ArrayList<>();
        for (var row : partition.getRows()) {
            Object val = row.get(targetColumn);
            if (val == null) {
                labels.add(0.0);
            } else {
                try {
                    labels.add(Double.parseDouble(val.toString()));
                } catch (NumberFormatException e) {
                    labels.add(0.0);
                }
            }
        }
        return labels;
    }
}
