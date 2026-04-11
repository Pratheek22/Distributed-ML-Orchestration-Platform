package com.ensemble.worker.strategy;

import com.ensemble.common.model.DataFrame;
import com.ensemble.common.strategy.MLModelStrategy;

import java.util.*;
import java.util.stream.Collectors;

public class RandomForestStrategy implements MLModelStrategy {

    private final List<DecisionTreeStrategy> trees = new ArrayList<>();
    private static final int N_TREES = 10;

    @Override
    public List<Double> train(DataFrame partition, Map<String, Object> hyperparams) {
        Random rng = new Random(42);
        int n = partition.rowCount();
        for (int t = 0; t < N_TREES; t++) {
            // Bootstrap sample
            List<Map<String, Object>> sample = new ArrayList<>();
            for (int i = 0; i < n; i++) sample.add(partition.getRows().get(rng.nextInt(n)));
            DataFrame bootstrap = new DataFrame(partition.getColumns(), partition.getDataTypes(), sample);
            DecisionTreeStrategy tree = new DecisionTreeStrategy();
            tree.train(bootstrap, hyperparams);
            trees.add(tree);
        }
        return predict(partition);
    }

    @Override
    public List<Double> predict(DataFrame data) {
        List<List<Double>> allPreds = trees.stream().map(t -> t.predict(data)).collect(Collectors.toList());
        int size = allPreds.get(0).size();
        List<Double> result = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            final int idx = i;
            Map<Double, Long> votes = allPreds.stream().map(p -> p.get(idx))
                    .collect(Collectors.groupingBy(v -> v, Collectors.counting()));
            result.add(votes.entrySet().stream().max(Map.Entry.comparingByValue()).map(Map.Entry::getKey).orElse(0.0));
        }
        return result;
    }
}
