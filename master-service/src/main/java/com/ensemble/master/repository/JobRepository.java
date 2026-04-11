package com.ensemble.master.repository;

import com.ensemble.master.entity.TrainingJobEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface JobRepository extends JpaRepository<TrainingJobEntity, Long> {

    List<TrainingJobEntity> findByUserId(Long userId);

    List<TrainingJobEntity> findByStatus(String status);
}
