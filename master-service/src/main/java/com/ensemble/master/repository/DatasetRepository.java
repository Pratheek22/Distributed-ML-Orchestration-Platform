package com.ensemble.master.repository;

import com.ensemble.master.entity.DatasetEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DatasetRepository extends JpaRepository<DatasetEntity, Long> {

    List<DatasetEntity> findByUserId(Long userId);
}
