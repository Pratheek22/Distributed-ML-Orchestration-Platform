package com.ensemble.master.service;

import com.ensemble.common.model.DataFrame;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

@Component
public class Preprocessor {

    public DataFrame preprocess(DataFrame df, String targetColumn) {
        if (df.rowCount() == 0) throw new IllegalArgumentException("DataFrame has zero rows");
        DataFrame result = imputeMissing(df);
        result = encodeCategorical(result, targetColumn);
        result = normalizeNumeric(result, targetColumn);
        if (result.rowCount() == 0) throw new IllegalArgumentException("DataFrame has zero rows after preprocessing");
        return result;
    }

    private DataFrame imputeMissing(DataFrame df) {
        List<Map<String, Object>> rows = deepCopy(df.getRows());
        for (String col : df.getColumns()) {
            String type = df.getDataTypes().get(col);
            if ("numeric".equals(type)) {
                double mean = rows.stream()
                        .map(r -> r.get(col))
                        .filter(v -> v != null)
                        .mapToDouble(v -> ((Number) v).doubleValue())
                        .average().orElse(0.0);
                rows.forEach(r -> { if (r.get(col) == null) r.put(col, mean); });
            } else {
                Map<Object, Long> freq = rows.stream()
                        .map(r -> r.get(col))
                        .filter(v -> v != null)
                        .collect(Collectors.groupingBy(v -> v, Collectors.counting()));
                Object mode = freq.entrySet().stream()
                        .max(Map.Entry.comparingByValue())
                        .map(Map.Entry::getKey).orElse("");
                rows.forEach(r -> { if (r.get(col) == null) r.put(col, mode); });
            }
        }
        return new DataFrame(df.getColumns(), df.getDataTypes(), rows);
    }

    private DataFrame encodeCategorical(DataFrame df, String targetColumn) {
        List<Map<String, Object>> rows = deepCopy(df.getRows());
        Map<String, String> types = new LinkedHashMap<>(df.getDataTypes());
        for (String col : df.getColumns()) {
            if ("categorical".equals(df.getDataTypes().get(col)) && !col.equals(targetColumn)) {
                List<String> uniqueVals = rows.stream()
                        .map(r -> r.get(col) == null ? "" : r.get(col).toString())
                        .distinct().sorted().collect(Collectors.toList());
                Map<String, Integer> labelMap = new LinkedHashMap<>();
                for (int i = 0; i < uniqueVals.size(); i++) labelMap.put(uniqueVals.get(i), i);
                rows.forEach(r -> r.put(col, (double) labelMap.getOrDefault(
                        r.get(col) == null ? "" : r.get(col).toString(), 0)));
                types.put(col, "numeric");
            }
        }
        return new DataFrame(df.getColumns(), types, rows);
    }

    private DataFrame normalizeNumeric(DataFrame df, String targetColumn) {
        List<Map<String, Object>> rows = deepCopy(df.getRows());
        for (String col : df.getColumns()) {
            if ("numeric".equals(df.getDataTypes().get(col)) && !col.equals(targetColumn)) {
                double min = rows.stream().mapToDouble(r -> ((Number) r.get(col)).doubleValue()).min().orElse(0.0);
                double max = rows.stream().mapToDouble(r -> ((Number) r.get(col)).doubleValue()).max().orElse(0.0);
                double range = max - min;
                rows.forEach(r -> {
                    double v = ((Number) r.get(col)).doubleValue();
                    r.put(col, range == 0 ? 0.0 : (v - min) / range);
                });
            }
        }
        return new DataFrame(df.getColumns(), df.getDataTypes(), rows);
    }

    private List<Map<String, Object>> deepCopy(List<Map<String, Object>> rows) {
        List<Map<String, Object>> copy = new ArrayList<>();
        for (Map<String, Object> row : rows) copy.add(new LinkedHashMap<>(row));
        return copy;
    }
}
