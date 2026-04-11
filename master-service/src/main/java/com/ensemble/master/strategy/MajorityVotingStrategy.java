package com.ensemble.master.strategy;

import com.ensemble.common.strategy.EnsembleStrategy;

import java.util.*;

public class MajorityVotingStrategy implements EnsembleStrategy {

    @Override
    public List<Double> aggregate(List<List<Double>> predictions) {
        if (predictions == null || predictions.isEmpty()) return Collections.emptyList();
        int size = predictions.get(0).size();
        List<Double> result = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            Map<Double, Integer> votes = new HashMap<>();
            for (List<Double> preds : predictions) {
                if (i < preds.size()) {
                    double val = preds.get(i);
                    votes.merge(val, 1, Integer::sum);
                }
            }
            double majority = votes.entrySet().stream()
                    .max(Map.Entry.comparingByValue())
                    .map(Map.Entry::getKey)
                    .orElse(0.0);
            result.add(majority);
        }
        return result;
    }
}
