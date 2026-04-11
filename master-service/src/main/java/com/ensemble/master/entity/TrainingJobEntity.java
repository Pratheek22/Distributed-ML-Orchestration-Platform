package com.ensemble.master.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "training_jobs")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TrainingJobEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "dataset_id", nullable = false)
    private Long datasetId;

    @Column(nullable = false, length = 20)
    private String status;

    @Column(name = "model_type", nullable = false, length = 50)
    private String modelType;

    @Column(name = "hyperparams_json", columnDefinition = "TEXT")
    private String hyperparamsJson;

    @Column(name = "eval_metric")
    private Double evalMetric;

    @Column(name = "failure_reason", columnDefinition = "TEXT")
    private String failureReason;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
