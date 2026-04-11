package com.ensemble.master.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "datasets")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DatasetEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "file_name", nullable = false, length = 255)
    private String fileName;

    @Column(name = "columns_json", nullable = false, columnDefinition = "TEXT")
    private String columnsJson;

    @Column(name = "types_json", nullable = false, columnDefinition = "TEXT")
    private String typesJson;

    @Column(name = "target_column", length = 100)
    private String targetColumn;

    @Column(name = "task_type", length = 20)
    private String taskType;

    @Column(name = "dataframe_json", columnDefinition = "LONGTEXT")
    private String dataframeJson;

    @CreationTimestamp
    @Column(name = "uploaded_at", updatable = false)
    private LocalDateTime uploadedAt;
}
