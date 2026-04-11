package com.ensemble.common.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DatasetMeta {

    private Long id;
    private String fileName;
    private List<String> columns;
    private Map<String, String> dataTypes;
    private String targetColumn;
    private String taskType;
    private Long userId;
    private LocalDateTime uploadedAt;

    public boolean hasColumn(String name) {
        return columns != null && columns.contains(name);
    }

    public String inferTaskType() {
        if (dataTypes != null && "numeric".equals(dataTypes.get(targetColumn))) {
            return "regression";
        }
        return "classification";
    }
}
