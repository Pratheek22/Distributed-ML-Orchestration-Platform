package com.ensemble.common.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DataFrame {

    private List<String> columns;
    private Map<String, String> dataTypes;
    private List<Map<String, Object>> rows;

    public int rowCount() {
        return rows == null ? 0 : rows.size();
    }

    public DataFrame slice(int fromRow, int toRow) {
        return new DataFrame(columns, dataTypes, new ArrayList<>(rows.subList(fromRow, toRow)));
    }

    public List<Object> getColumn(String name) {
        List<Object> values = new ArrayList<>();
        if (rows != null) {
            for (Map<String, Object> row : rows) {
                values.add(row.get(name));
            }
        }
        return values;
    }
}
