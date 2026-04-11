package com.ensemble.worker.strategy;

import com.ensemble.common.strategy.EnsembleStrategy;
import java.util.*;

public class AveragingStrategy implements EnsembleStrategy {
    @Override
    public List<Double> aggregate(List<List<Double>> predictions) {
        int size = predictions.get(0).size();
        List<Double> result = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            final int idx = i;
            double avg = predictions.stream().mapToDouble(p -> p.get(idx)).average().orElse(0.0);
            result.add(avg);
        }
        return result;
    }
}
