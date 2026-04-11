package com.ensemble.master.strategy;

import com.ensemble.common.strategy.EnsembleStrategy;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class AveragingStrategy implements EnsembleStrategy {

    @Override
    public List<Double> aggregate(List<List<Double>> predictions) {
        if (predictions == null || predictions.isEmpty()) return Collections.emptyList();
        int size = predictions.get(0).size();
        List<Double> result = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            double sum = 0.0;
            int count = 0;
            for (List<Double> preds : predictions) {
                if (i < preds.size()) {
                    sum += preds.get(i);
                    count++;
                }
            }
            result.add(count > 0 ? sum / count : 0.0);
        }
        return result;
    }
}
