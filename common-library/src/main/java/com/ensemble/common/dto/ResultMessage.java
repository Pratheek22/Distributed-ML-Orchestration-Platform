package com.ensemble.common.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ResultMessage implements Serializable {

    private static final long serialVersionUID = 1L;

    private String jobId;
    private String workerId;
    private int partitionIndex;
    private List<Double> predictions;

    /**
     * Ground truth labels extracted from the partition's target column.
     * Used by AggregatorService to compute accuracy/MSE against real labels.
     */
    private List<Double> groundTruthLabels;

    // Convenience constructor for backward compatibility
    public ResultMessage(String jobId, String workerId, int partitionIndex, List<Double> predictions) {
        this.jobId = jobId;
        this.workerId = workerId;
        this.partitionIndex = partitionIndex;
        this.predictions = predictions;
    }
}
