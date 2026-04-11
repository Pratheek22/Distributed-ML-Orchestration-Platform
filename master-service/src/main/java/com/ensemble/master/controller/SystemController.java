package com.ensemble.master.controller;

import com.ensemble.master.repository.JobRepository;
import com.ensemble.master.service.ZooKeeperService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/system")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class SystemController {

    private final ZooKeeperService zooKeeperService;
    private final JobRepository jobRepository;

    /**
     * GET /api/system/status
     * Returns active/inactive workers and job counts.
     */
    @GetMapping("/status")
    public ResponseEntity<?> systemStatus() {
        long jobsRunning = jobRepository.findByStatus("RUNNING").size();
        long jobsFailed  = jobRepository.findByStatus("FAILED").size();

        Map<String, Object> status = new LinkedHashMap<>();
        status.put("active_nodes",    zooKeeperService.getActiveWorkers());
        status.put("inactive_nodes",  zooKeeperService.getInactiveWorkers());
        status.put("jobs_running",    jobsRunning);
        status.put("jobs_failed",     jobsFailed);
        // Keep legacy keys for frontend compatibility
        status.put("activeWorkers",   zooKeeperService.getActiveWorkers());
        status.put("inactiveWorkers", zooKeeperService.getInactiveWorkers());

        return ResponseEntity.ok(status);
    }
}
