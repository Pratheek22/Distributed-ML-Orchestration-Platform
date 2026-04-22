package com.ensemble.master.service;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.recipes.cache.CuratorCache;
import org.apache.curator.framework.recipes.cache.CuratorCacheListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
public class ZooKeeperService {

    private static final String WORKERS_PATH = "/workers";

    private final CuratorFramework client;
    private final Set<String> activeWorkers = ConcurrentHashMap.newKeySet();
    private final Set<String> inactiveWorkers = ConcurrentHashMap.newKeySet();
    private CuratorCache cache;

    // Injected lazily to avoid circular dependency
    private OrchestratorService orchestratorService;

    public ZooKeeperService(CuratorFramework client) {
        this.client = client;
    }

    @Autowired
    public void setOrchestratorService(@Lazy OrchestratorService orchestratorService) {
        this.orchestratorService = orchestratorService;
    }

    @PostConstruct
    public void watchWorkerNodes() {
        try {
            if (client.checkExists().forPath(WORKERS_PATH) == null) {
                client.create().creatingParentsIfNeeded().forPath(WORKERS_PATH);
            }

            cache = CuratorCache.build(client, WORKERS_PATH);

            CuratorCacheListener listener = CuratorCacheListener.builder()
                    .forCreates(node -> {
                        String path = node.getPath();
                        if (!path.equals(WORKERS_PATH)) {
                            String workerId = extractWorkerId(path);
                            if (!workerId.isEmpty()) {
                                activeWorkers.add(workerId);
                                inactiveWorkers.remove(workerId);
                                log.info("Worker registered: {}", workerId);
                            }
                        }
                    })
                    .forDeletes(node -> {
                        String path = node.getPath();
                        if (!path.equals(WORKERS_PATH)) {
                            String workerId = extractWorkerId(path);
                            if (!workerId.isEmpty()) {
                                activeWorkers.remove(workerId);
                                inactiveWorkers.add(workerId);
                                log.warn("Worker deregistered (failure?): {}", workerId);
                                // Trigger fault tolerance reassignment
                                if (orchestratorService != null) {
                                    orchestratorService.handleWorkerFailure(workerId);
                                }
                            }
                        }
                    })
                    .build();

            cache.listenable().addListener(listener);
            cache.start();

            // Populate initial state from existing znodes
            cache.stream()
                    .map(cd -> cd.getPath())
                    .filter(p -> !p.equals(WORKERS_PATH))
                    .map(this::extractWorkerId)
                    .filter(id -> !id.isEmpty())
                    .forEach(id -> {
                        activeWorkers.add(id);
                        log.info("Found existing worker on startup: {}", id);
                    });

        } catch (Exception e) {
            log.error("Failed to set up ZooKeeper watcher", e);
        }
    }

    @PreDestroy
    public void close() {
        if (cache != null) {
            cache.close();
        }
    }

    public List<String> getActiveWorkers() {
        return new ArrayList<>(activeWorkers);
    }

    public List<String> getInactiveWorkers() {
        return new ArrayList<>(inactiveWorkers);
    }

    public boolean isWorkerActive(String workerId) {
        return activeWorkers.contains(workerId);
    }

    private String extractWorkerId(String path) {
        if (path == null || path.isEmpty())
            return "";
        int idx = path.lastIndexOf('/');
        return idx >= 0 ? path.substring(idx + 1) : path;
    }
}
