package com.ensemble.worker.strategy;

import com.ensemble.common.model.DataFrame;
import com.ensemble.common.strategy.MLModelStrategy;
import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
public class DecisionTreeStrategy implements MLModelStrategy {

    private TreeNode root;
    private String targetColumn;
    private List<String> featureColumns;
    private static final int MAX_DEPTH = 5;
    private static final int MIN_SAMPLES = 2;

    @Override
    public List<Double> train(DataFrame partition, Map<String, Object> hyperparams) {
        this.targetColumn = getTargetColumn(partition, hyperparams);
        this.featureColumns = partition.getColumns().stream()
                .filter(c -> !c.equals(targetColumn))
                .collect(Collectors.toList());

        List<double[]> features = extractFeatures(partition);
        List<Double> labels = extractLabels(partition);

        this.root = buildTree(features, labels, 0);
        return predict(partition);
    }

    @Override
    public List<Double> predict(DataFrame data) {
        List<double[]> features = extractFeatures(data);
        return features.stream().map(f -> predictSingle(root, f)).collect(Collectors.toList());
    }

    private TreeNode buildTree(List<double[]> features, List<Double> labels, int depth) {
        if (depth >= MAX_DEPTH || features.size() < MIN_SAMPLES || allSameLabel(labels)) {
            return new TreeNode(majorityLabel(labels));
        }
        Split best = findBestSplit(features, labels);
        if (best == null) return new TreeNode(majorityLabel(labels));

        List<double[]> leftF = new ArrayList<>(), rightF = new ArrayList<>();
        List<Double> leftL = new ArrayList<>(), rightL = new ArrayList<>();
        for (int i = 0; i < features.size(); i++) {
            if (features.get(i)[best.featureIdx] <= best.threshold) {
                leftF.add(features.get(i)); leftL.add(labels.get(i));
            } else {
                rightF.add(features.get(i)); rightL.add(labels.get(i));
            }
        }
        if (leftF.isEmpty() || rightF.isEmpty()) return new TreeNode(majorityLabel(labels));

        TreeNode node = new TreeNode(best.featureIdx, best.threshold);
        node.left = buildTree(leftF, leftL, depth + 1);
        node.right = buildTree(rightF, rightL, depth + 1);
        return node;
    }

    private Split findBestSplit(List<double[]> features, List<Double> labels) {
        double bestGain = -1;
        Split best = null;
        int nFeatures = features.get(0).length;
        for (int f = 0; f < nFeatures; f++) {
            Set<Double> thresholds = new HashSet<>();
            for (double[] row : features) thresholds.add(row[f]);
            for (double t : thresholds) {
                List<Double> left = new ArrayList<>(), right = new ArrayList<>();
                for (int i = 0; i < features.size(); i++) {
                    if (features.get(i)[f] <= t) left.add(labels.get(i));
                    else right.add(labels.get(i));
                }
                if (left.isEmpty() || right.isEmpty()) continue;
                double gain = gini(labels) - (left.size() * gini(left) + right.size() * gini(right)) / labels.size();
                if (gain > bestGain) { bestGain = gain; best = new Split(f, t); }
            }
        }
        return best;
    }

    private double gini(List<Double> labels) {
        Map<Double, Long> counts = labels.stream().collect(Collectors.groupingBy(v -> v, Collectors.counting()));
        double impurity = 1.0;
        for (long c : counts.values()) impurity -= Math.pow((double) c / labels.size(), 2);
        return impurity;
    }

    private double predictSingle(TreeNode node, double[] features) {
        if (node.isLeaf) return node.value;
        return features[node.featureIdx] <= node.threshold
                ? predictSingle(node.left, features) : predictSingle(node.right, features);
    }

    private List<double[]> extractFeatures(DataFrame df) {
        List<double[]> result = new ArrayList<>();
        List<String> cols = featureColumns != null ? featureColumns :
                df.getColumns().stream().filter(c -> !c.equals(targetColumn)).collect(Collectors.toList());
        for (Map<String, Object> row : df.getRows()) {
            double[] f = new double[cols.size()];
            for (int i = 0; i < cols.size(); i++) {
                Object v = row.get(cols.get(i));
                f[i] = v == null ? 0.0 : ((Number) v).doubleValue();
            }
            result.add(f);
        }
        return result;
    }

    private List<Double> extractLabels(DataFrame df) {
        // Check if target column is numeric or categorical
        String targetType = df.getDataTypes().get(targetColumn);
        boolean isNumeric = "numeric".equals(targetType);
        
        if (isNumeric) {
            // Numeric target - cast to Number
            return df.getRows().stream().map(r -> {
                Object v = r.get(targetColumn);
                return v == null ? 0.0 : ((Number) v).doubleValue();
            }).collect(Collectors.toList());
        } else {
            // Categorical target - encode as numeric labels
            List<Object> uniqueLabels = df.getRows().stream()
                    .map(r -> r.get(targetColumn))
                    .distinct()
                    .collect(Collectors.toList());
            
            Map<Object, Double> labelMap = new HashMap<>();
            for (int i = 0; i < uniqueLabels.size(); i++) {
                labelMap.put(uniqueLabels.get(i), (double) i);
            }
            
            return df.getRows().stream().map(r -> {
                Object v = r.get(targetColumn);
                return v == null ? 0.0 : labelMap.getOrDefault(v, 0.0);
            }).collect(Collectors.toList());
        }
    }

    private String getTargetColumn(DataFrame df, Map<String, Object> hyperparams) {
        if (hyperparams != null && hyperparams.containsKey("targetColumn"))
            return hyperparams.get("targetColumn").toString();
        return df.getColumns().get(df.getColumns().size() - 1);
    }

    private boolean allSameLabel(List<Double> labels) {
        return labels.stream().distinct().count() <= 1;
    }

    private double majorityLabel(List<Double> labels) {
        return labels.stream().collect(Collectors.groupingBy(v -> v, Collectors.counting()))
                .entrySet().stream().max(Map.Entry.comparingByValue()).map(Map.Entry::getKey).orElse(0.0);
    }

    private static class TreeNode {
        boolean isLeaf;
        double value;
        int featureIdx;
        double threshold;
        TreeNode left, right;
        TreeNode(double value) { this.isLeaf = true; this.value = value; }
        TreeNode(int featureIdx, double threshold) { this.featureIdx = featureIdx; this.threshold = threshold; }
    }

    private static class Split {
        int featureIdx; double threshold;
        Split(int f, double t) { featureIdx = f; threshold = t; }
    }
}
