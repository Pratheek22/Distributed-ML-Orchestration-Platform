package com.ensemble.master.repository;

import com.ensemble.master.entity.JobResultEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface JobResultRepository extends JpaRepository<JobResultEntity, Long> {

    List<JobResultEntity> findByJobId(Long jobId);
}
