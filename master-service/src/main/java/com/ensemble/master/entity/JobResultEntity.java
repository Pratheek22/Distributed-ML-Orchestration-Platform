package com.ensemble.master.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "job_results")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class JobResultEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "job_id", nullable = false)
    private Long jobId;

    @Column(name = "worker_id", nullable = false, length = 100)
    private String workerId;

    @Column(name = "partition_idx", nullable = false)
    private Integer partitionIdx;

    @Column(name = "predictions_json", nullable = false, columnDefinition = "TEXT")
    private String predictionsJson;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}
