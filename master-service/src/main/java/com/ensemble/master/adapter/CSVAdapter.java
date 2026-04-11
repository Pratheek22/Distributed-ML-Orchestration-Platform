package com.ensemble.master.adapter;

import com.ensemble.common.model.DataFrame;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.csv.*;
import org.springframework.stereotype.Component;

import java.io.*;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.*;

@Slf4j
@Component
public class CSVAdapter {

    public DataFrame parse(byte[] csvBytes, char delimiter) {
        try (Reader reader = new InputStreamReader(new ByteArrayInputStream(csvBytes), StandardCharsets.UTF_8)) {
            CSVFormat format = CSVFormat.DEFAULT.builder()
                    .setHeader()
                    .setSkipHeaderRecord(true)
                    .setDelimiter(delimiter)
                    .setIgnoreEmptyLines(true)
                    .setTrim(true)
                    .build();
            CSVParser parser = CSVParser.parse(reader, format);
            List<String> columns = new ArrayList<>(parser.getHeaderNames());
            List<Map<String, Object>> rows = new ArrayList<>();
            for (CSVRecord record : parser) {
                Map<String, Object> row = new LinkedHashMap<>();
                for (String col : columns) {
                    String val = record.get(col);
                    row.put(col, (val == null || val.isBlank()) ? null : val);
                }
                rows.add(row);
            }
            Map<String, String> dataTypes = inferTypes(columns, rows);
            // Convert numeric strings to Double
            for (Map<String, Object> row : rows) {
                for (String col : columns) {
                    if ("numeric".equals(dataTypes.get(col)) && row.get(col) != null) {
                        try { row.put(col, Double.parseDouble(row.get(col).toString())); }
                        catch (NumberFormatException ignored) { row.put(col, null); }
                    }
                }
            }
            return new DataFrame(columns, dataTypes, rows);
        } catch (IOException e) {
            throw new IllegalArgumentException("Failed to parse CSV: " + e.getMessage(), e);
        }
    }

    public DataFrame parseFromUrl(String url) {
        try {
            byte[] bytes = new URL(url).openStream().readAllBytes();
            return parseAutoDetect(bytes);
        } catch (IOException e) {
            throw new IllegalArgumentException("Failed to fetch CSV from URL: " + e.getMessage(), e);
        }
    }

    public DataFrame parseAutoDetect(byte[] csvBytes) {
        for (char delim : new char[]{',', ';', '\t'}) {
            try { return parse(csvBytes, delim); } catch (Exception ignored) {}
        }
        throw new IllegalArgumentException("Could not parse CSV with any supported delimiter");
    }

    public String serialize(DataFrame df) {
        StringBuilder sb = new StringBuilder();
        sb.append(String.join(",", df.getColumns())).append("\n");
        for (Map<String, Object> row : df.getRows()) {
            List<String> vals = new ArrayList<>();
            for (String col : df.getColumns()) {
                Object v = row.get(col);
                vals.add(v == null ? "" : v.toString());
            }
            sb.append(String.join(",", vals)).append("\n");
        }
        return sb.toString();
    }

    private Map<String, String> inferTypes(List<String> columns, List<Map<String, Object>> rows) {
        Map<String, String> types = new LinkedHashMap<>();
        for (String col : columns) {
            boolean isNumeric = rows.stream()
                    .map(r -> r.get(col))
                    .filter(v -> v != null && !v.toString().isBlank())
                    .allMatch(v -> { try { Double.parseDouble(v.toString()); return true; } catch (Exception e) { return false; } });
            types.put(col, isNumeric ? "numeric" : "categorical");
        }
        return types;
    }
}
