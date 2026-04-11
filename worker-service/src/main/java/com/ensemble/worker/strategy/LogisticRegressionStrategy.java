package com.ensemble.worker.strategy;

import com.ensemble.common.model.DataFrame;
import com.ensemble.common.strategy.MLModelStrategy;

import java.util.*;
import java.util.stream.Collectors;

public class LogisticRegressionStrategy implements MLModelStrategy {

    private double[] weights;
    private double bias;
    private String targetColumn;
    private List<String> featureColumns;
    private static final int ITERATIONS = 100;
    private static final double LR = 0.1;

    @Override
    public List<Double> train(DataFrame partition, Map<String, Object> hyperparams) {
        this.targetColumn = hyperparams != null && hyperparams.containsKey("targetColumn")
                ? hyperparams.get("targetColumn").toString()
                : partition.getColumns().get(partition.getColumns().size() - 1);
        this.featureColumns = partition.getColumns().stream()
                .filter(c -> !c.equals(targetColumn)).collect(Collectors.toList());

        int n = partition.rowCount();
        int m = featureColumns.size();
        weights = new double[m];
        bias = 0.0;

        double[][] X = new double[n][m];
        double[] y = new double[n];
        List<Map<String, Object>> rows = partition.getRows();
        
        // Check if target column is numeric or categorical
        String targetType = partition.getDataTypes().get(targetColumn);
        boolean isNumeric = "numeric".equals(targetType);
        
        // Build label mapping for categorical targets
        Map<Object, Double> labelMap = new HashMap<>();
        if (!isNumeric) {
            List<Object> uniqueLabels = rows.stream()
                    .map(r -> r.get(targetColumn))
                    .distinct()
                    .collect(Collectors.toList());
            for (int i = 0; i < uniqueLabels.size(); i++) {
                labelMap.put(uniqueLabels.get(i), (double) i);
            }
        }
        
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < m; j++) {
                Object v = rows.get(i).get(featureColumns.get(j));
                X[i][j] = v == null ? 0.0 : ((Number) v).doubleValue();
            }
            Object lv = rows.get(i).get(targetColumn);
            if (isNumeric) {
                y[i] = lv == null ? 0.0 : ((Number) lv).doubleValue();
            } else {
                y[i] = lv == null ? 0.0 : labelMap.getOrDefault(lv, 0.0);
            }
        }

        for (int iter = 0; iter < ITERATIONS; iter++) {
            double[] dw = new double[m];
            double db = 0.0;
            for (int i = 0; i < n; i++) {
                double z = bias;
                for (int j = 0; j < m; j++) z += weights[j] * X[i][j];
                double pred = sigmoid(z);
                double err = pred - y[i];
                for (int j = 0; j < m; j++) dw[j] += err * X[i][j];
                db += err;
            }
            for (int j = 0; j < m; j++) weights[j] -= LR * dw[j] / n;
            bias -= LR * db / n;
        }
        return predict(partition);
    }

    @Override
    public List<Double> predict(DataFrame data) {
        return data.getRows().stream().map(row -> {
            double z = bias;
            for (int j = 0; j < featureColumns.size(); j++) {
                Object v = row.get(featureColumns.get(j));
                z += weights[j] * (v == null ? 0.0 : ((Number) v).doubleValue());
            }
            return sigmoid(z) >= 0.5 ? 1.0 : 0.0;
        }).collect(Collectors.toList());
    }

    private double sigmoid(double z) { return 1.0 / (1.0 + Math.exp(-z)); }
}
