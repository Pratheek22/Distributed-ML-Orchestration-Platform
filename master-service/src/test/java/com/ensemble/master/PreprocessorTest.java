package com.ensemble.master;

import com.ensemble.common.model.DataFrame;
import com.ensemble.master.service.Preprocessor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class PreprocessorTest {

    private Preprocessor preprocessor;

    @BeforeEach
    void setUp() {
        preprocessor = new Preprocessor();
    }

    private DataFrame buildDf(List<String> columns, Map<String, String> types, List<Map<String, Object>> rows) {
        return new DataFrame(columns, types, rows);
    }

    @Test
    void testNumericImputationWithMean() {
        List<String> cols = List.of("age", "label");
        Map<String, String> types = Map.of("age", "numeric", "label", "numeric");
        List<Map<String, Object>> rows = new ArrayList<>();
        rows.add(new LinkedHashMap<>(Map.of("age", 10.0, "label", 0.0)));
        rows.add(new LinkedHashMap<>(Map.of("age", 20.0, "label", 1.0)));
        Map<String, Object> rowWithNull = new LinkedHashMap<>();
        rowWithNull.put("age", null);
        rowWithNull.put("label", 0.0);
        rows.add(rowWithNull);

        DataFrame df = buildDf(cols, types, rows);
        DataFrame result = preprocessor.preprocess(df, "label");

        // Mean of [10, 20] = 15; after min-max normalization: (15-10)/(20-10) = 0.5
        double imputedAge = ((Number) result.getRows().get(2).get("age")).doubleValue();
        assertEquals(0.5, imputedAge, 0.001, "Missing numeric should be imputed with column mean then normalized");
    }

    @Test
    void testCategoricalImputationWithMode() {
        List<String> cols = List.of("dept", "label");
        Map<String, String> types = Map.of("dept", "categorical", "label", "numeric");
        List<Map<String, Object>> rows = new ArrayList<>();
        rows.add(new LinkedHashMap<>(Map.of("dept", "Eng", "label", 1.0)));
        rows.add(new LinkedHashMap<>(Map.of("dept", "Eng", "label", 0.0)));
        rows.add(new LinkedHashMap<>(Map.of("dept", "HR", "label", 0.0)));
        Map<String, Object> rowWithNull = new LinkedHashMap<>();
        rowWithNull.put("dept", null);
        rowWithNull.put("label", 1.0);
        rows.add(rowWithNull);

        DataFrame df = buildDf(cols, types, rows);
        DataFrame result = preprocessor.preprocess(df, "label");

        // Mode of ["Eng","Eng","HR"] = "Eng" → encoded as some integer
        // After encoding, the imputed value should match the encoding of "Eng"
        assertNotNull(result.getRows().get(3).get("dept"), "Missing categorical should be imputed");
    }

    @Test
    void testLabelEncodingProducesNumericValues() {
        List<String> cols = List.of("color", "label");
        Map<String, String> types = Map.of("color", "categorical", "label", "numeric");
        List<Map<String, Object>> rows = new ArrayList<>();
        rows.add(new LinkedHashMap<>(Map.of("color", "red", "label", 1.0)));
        rows.add(new LinkedHashMap<>(Map.of("color", "blue", "label", 0.0)));
        rows.add(new LinkedHashMap<>(Map.of("color", "green", "label", 1.0)));

        DataFrame df = buildDf(cols, types, rows);
        DataFrame result = preprocessor.preprocess(df, "label");

        for (Map<String, Object> row : result.getRows()) {
            Object val = row.get("color");
            assertNotNull(val);
            assertTrue(val instanceof Number, "Encoded categorical should be numeric");
        }
    }

    @Test
    void testMinMaxNormalizationProducesValuesInZeroOne() {
        List<String> cols = List.of("score", "label");
        Map<String, String> types = Map.of("score", "numeric", "label", "numeric");
        List<Map<String, Object>> rows = new ArrayList<>();
        rows.add(new LinkedHashMap<>(Map.of("score", 10.0, "label", 0.0)));
        rows.add(new LinkedHashMap<>(Map.of("score", 50.0, "label", 1.0)));
        rows.add(new LinkedHashMap<>(Map.of("score", 100.0, "label", 0.0)));

        DataFrame df = buildDf(cols, types, rows);
        DataFrame result = preprocessor.preprocess(df, "label");

        for (Map<String, Object> row : result.getRows()) {
            double v = ((Number) row.get("score")).doubleValue();
            assertTrue(v >= 0.0 && v <= 1.0, "Normalized value should be in [0,1], got: " + v);
        }
    }

    @Test
    void testTargetColumnExcludedFromTransformations() {
        List<String> cols = List.of("feature", "target");
        Map<String, String> types = Map.of("feature", "numeric", "target", "categorical");
        List<Map<String, Object>> rows = new ArrayList<>();
        rows.add(new LinkedHashMap<>(Map.of("feature", 5.0, "target", "yes")));
        rows.add(new LinkedHashMap<>(Map.of("feature", 10.0, "target", "no")));

        DataFrame df = buildDf(cols, types, rows);
        DataFrame result = preprocessor.preprocess(df, "target");

        // Target column should remain unchanged (not encoded)
        assertEquals("yes", result.getRows().get(0).get("target").toString());
        assertEquals("no", result.getRows().get(1).get("target").toString());
    }

    @Test
    void propertyNoMissingValuesAfterPreprocessing() {
        // Property: for all valid DataFrames, result has no missing values
        List<String> cols = List.of("a", "b", "label");
        Map<String, String> types = Map.of("a", "numeric", "b", "categorical", "label", "numeric");
        List<Map<String, Object>> rows = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("a", i % 3 == 0 ? null : (double) i);
            row.put("b", i % 4 == 0 ? null : (i % 2 == 0 ? "cat" : "dog"));
            row.put("label", (double) (i % 2));
            rows.add(row);
        }

        DataFrame df = buildDf(cols, types, rows);
        DataFrame result = preprocessor.preprocess(df, "label");

        for (Map<String, Object> row : result.getRows()) {
            for (String col : result.getColumns()) {
                assertNotNull(row.get(col), "No null values should remain after preprocessing, col=" + col);
            }
        }
    }

    @Test
    void testEmptyDataFrameThrows() {
        List<String> cols = List.of("a", "label");
        Map<String, String> types = Map.of("a", "numeric", "label", "numeric");
        DataFrame df = buildDf(cols, types, new ArrayList<>());
        assertThrows(IllegalArgumentException.class, () -> preprocessor.preprocess(df, "label"));
    }
}
