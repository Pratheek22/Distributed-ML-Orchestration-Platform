package com.ensemble.master.controller;

import com.ensemble.common.model.DataFrame;
import com.ensemble.common.model.DatasetMeta;
import com.ensemble.master.adapter.CSVAdapter;
import com.ensemble.master.facade.TrainingFacade;
import com.ensemble.master.service.DatasetService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.*;

@RestController
@RequestMapping("/api/datasets")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class DatasetController {

    private static final long MAX_BYTES = 500L * 1024 * 1024; // 500 MB

    private final CSVAdapter csvAdapter;
    private final DatasetService datasetService;
    private final TrainingFacade trainingFacade;

    @PostMapping("/upload")
    public ResponseEntity<?> upload(
            @RequestParam(value = "file", required = false) MultipartFile file,
            @RequestParam(value = "url", required = false) String url,
            HttpServletRequest request) {
        try {
            Long userId = (Long) request.getAttribute("userId");
            DataFrame df;
            String fileName;

            if (file != null && !file.isEmpty()) {
                if (file.getSize() > MAX_BYTES) {
                    return ResponseEntity.status(413).body(Map.of("error", "File exceeds 500 MB limit"));
                }
                df = csvAdapter.parseAutoDetect(file.getBytes());
                fileName = file.getOriginalFilename();
            } else if (url != null && !url.isBlank()) {
                df = csvAdapter.parseFromUrl(url);
                fileName = url.substring(url.lastIndexOf('/') + 1);
            } else {
                return ResponseEntity.badRequest().body(Map.of("error", "Provide file or url parameter"));
            }

            DatasetMeta meta = new DatasetMeta();
            meta.setUserId(userId);
            meta.setFileName(fileName);
            meta.setColumns(df.getColumns());
            meta.setDataTypes(df.getDataTypes());
            meta.setUploadedAt(LocalDateTime.now());

            // Persist both metadata AND the DataFrame (for fault tolerance across restarts)
            DatasetMeta saved = datasetService.save(meta, df);

            // Also cache in-memory for fast access during the same session
            trainingFacade.storeDataFrame(saved.getId(), df);

            return ResponseEntity.ok(saved);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(422).body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/{id}/schema")
    public ResponseEntity<?> getSchema(@PathVariable Long id) {
        try {
            return ResponseEntity.ok(datasetService.findById(id));
        } catch (NoSuchElementException e) {
            return ResponseEntity.status(404).body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/{id}/target")
    public ResponseEntity<?> setTarget(@PathVariable Long id, @RequestBody Map<String, String> body) {
        try {
            String column = body.get("targetColumn");
            if (column == null || column.isBlank()) {
                return ResponseEntity.status(400).body(Map.of("error", "targetColumn is required"));
            }
            return ResponseEntity.ok(datasetService.setTargetColumn(id, column));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(400).body(Map.of("error", e.getMessage()));
        } catch (NoSuchElementException e) {
            return ResponseEntity.status(404).body(Map.of("error", e.getMessage()));
        }
    }
}
