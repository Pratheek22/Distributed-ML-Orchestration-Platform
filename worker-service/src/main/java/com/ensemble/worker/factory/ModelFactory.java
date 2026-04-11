package com.ensemble.worker.factory;

import com.ensemble.common.strategy.MLModelStrategy;
import com.ensemble.worker.strategy.*;
import org.springframework.stereotype.Component;

@Component
public class ModelFactory {

    public MLModelStrategy create(String modelType) {
        return switch (modelType.toLowerCase()) {
            case "decision_tree" -> new DecisionTreeStrategy();
            case "random_forest" -> new RandomForestStrategy();
            case "logistic_regression" -> new LogisticRegressionStrategy();
            default -> throw new IllegalArgumentException("Unknown model type: " + modelType);
        };
    }
}
