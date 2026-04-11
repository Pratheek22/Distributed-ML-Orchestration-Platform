package com.ensemble.master.observer;

import com.ensemble.common.model.TrainingJob;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
public class DashboardObserver implements JobStatusObserver {

    private final ConcurrentHashMap<Long, String> latestStatusByJobId = new ConcurrentHashMap<>();

    @Override
    public void onStatusChange(TrainingJob job, String previousStatus) {
        log.info("Job {} transitioned: {} -> {}", job.getId(), previousStatus, job.getStatus());
        latestStatusByJobId.put(job.getId(), job.getStatus());
    }

    public String getLatestStatus(Long jobId) {
        return latestStatusByJobId.get(jobId);
    }
}
