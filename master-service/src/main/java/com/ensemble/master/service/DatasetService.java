package com.ensemble.master.service;

import com.ensemble.common.model.DataFrame;
import com.ensemble.common.model.DatasetMeta;
import com.ensemble.master.entity.DatasetEntity;
import com.ensemble.master.repository.DatasetRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
@RequiredArgsConstructor
public class DatasetService {

    private final DatasetRepository datasetRepository;
    private final ObjectMapper objectMapper;

    public DatasetMeta save(DatasetMeta meta) {
        return save(meta, null);
    }

    public DatasetMeta save(DatasetMeta meta, DataFrame dataFrame) {
        try {
            String dataframeJson = dataFrame != null ? objectMapper.writeValueAsString(dataFrame) : null;
            DatasetEntity entity = DatasetEntity.builder()
                    .userId(meta.getUserId())
                    .fileName(meta.getFileName())
                    .columnsJson(objectMapper.writeValueAsString(meta.getColumns()))
                    .typesJson(objectMapper.writeValueAsString(meta.getDataTypes()))
                    .targetColumn(meta.getTargetColumn())
                    .taskType(meta.getTaskType())
                    .dataframeJson(dataframeJson)
                    .build();
            DatasetEntity saved = datasetRepository.save(entity);
            meta.setId(saved.getId());
            return meta;
        } catch (Exception e) {
            throw new RuntimeException("Failed to save dataset metadata", e);
        }
    }

    public DatasetMeta findById(Long id) {
        DatasetEntity entity = datasetRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Dataset not found: " + id));
        return toMeta(entity);
    }

    public DatasetMeta setTargetColumn(Long id, String column) {
        DatasetMeta meta = findById(id);
        if (!meta.hasColumn(column)) {
            throw new IllegalArgumentException("Column not found in dataset: " + column);
        }
        DatasetEntity entity = datasetRepository.findById(id).get();
        entity.setTargetColumn(column);
        meta.setTargetColumn(column);
        String taskType = meta.inferTaskType();
        entity.setTaskType(taskType);
        meta.setTaskType(taskType);
        datasetRepository.save(entity);
        return meta;
    }

    public DataFrame getDataFrame(Long id) {
        DatasetEntity entity = datasetRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Dataset not found: " + id));
        if (entity.getDataframeJson() == null || entity.getDataframeJson().isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(entity.getDataframeJson(), DataFrame.class);
        } catch (Exception e) {
            throw new RuntimeException("Failed to deserialize DataFrame for dataset " + id, e);
        }
    }

    private DatasetMeta toMeta(DatasetEntity entity) {
        try {
            List<String> columns = objectMapper.readValue(entity.getColumnsJson(), new TypeReference<>() {});
            Map<String, String> types = objectMapper.readValue(entity.getTypesJson(), new TypeReference<>() {});
            DatasetMeta meta = new DatasetMeta();
            meta.setId(entity.getId());
            meta.setUserId(entity.getUserId());
            meta.setFileName(entity.getFileName());
            meta.setColumns(columns);
            meta.setDataTypes(types);
            meta.setTargetColumn(entity.getTargetColumn());
            meta.setTaskType(entity.getTaskType());
            meta.setUploadedAt(entity.getUploadedAt());
            return meta;
        } catch (Exception e) {
            throw new RuntimeException("Failed to deserialize dataset metadata", e);
        }
    }
}
