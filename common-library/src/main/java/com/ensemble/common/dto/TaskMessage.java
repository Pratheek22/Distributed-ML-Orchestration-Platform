package com.ensemble.common.dto;

import com.ensemble.common.model.DataFrame;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TaskMessage implements Serializable {

    private static final long serialVersionUID = 1L;

    private String jobId;
    private int partitionIndex;
    private String modelType;
    private Map<String, Object> hyperparams;
    private DataFrame partition;
    private DataFrame fullDataForLabels;
}
