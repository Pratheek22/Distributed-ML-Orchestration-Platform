package com.ensemble.worker.strategy;

import com.ensemble.common.strategy.EnsembleStrategy;
import java.util.*;
import java.util.stream.Collectors;

public class MajorityVotingStrategy implements EnsembleStrategy {
    @Override
    public List<Double> aggregate(List<List<Double>> predictions) {
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
    }
}
