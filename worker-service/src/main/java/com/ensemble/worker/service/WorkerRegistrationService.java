package com.ensemble.worker.service;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.state.ConnectionState;
import org.apache.zookeeper.CreateMode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class WorkerRegistrationService {

    private static final String WORKERS_PATH = "/workers";

    private final CuratorFramework client;
    private final String workerId;

    public WorkerRegistrationService(CuratorFramework client,
                                      @Value("${worker.id:worker-1}") String workerId) {
        this.client = client;
        this.workerId = workerId;
    }

    @PostConstruct
    public void register() {
        try {
            String path = WORKERS_PATH + "/" + workerId;
            if (client.checkExists().forPath(WORKERS_PATH) == null) {
                client.create().creatingParentsIfNeeded().forPath(WORKERS_PATH);
            }
            // Create ephemeral znode — auto-deleted on disconnect
            if (client.checkExists().forPath(path) == null) {
                client.create().withMode(CreateMode.EPHEMERAL).forPath(path, workerId.getBytes());
            }
            log.info("Worker {} registered in ZooKeeper at {}", workerId, path);

            // Re-register on reconnect
            client.getConnectionStateListenable().addListener((c, newState) -> {
                if (newState == ConnectionState.RECONNECTED) {
                    try {
                        if (client.checkExists().forPath(path) == null) {
                            client.create().withMode(CreateMode.EPHEMERAL).forPath(path, workerId.getBytes());
                            log.info("Worker {} re-registered after reconnect", workerId);
                        }
                    } catch (Exception ex) {
                        log.error("Failed to re-register worker {}", workerId, ex);
                    }
                }
            });
        } catch (Exception e) {
            log.error("Failed to register worker {} in ZooKeeper", workerId, e);
        }
    }

    @PreDestroy
    public void deregister() {
        try {
            String path = WORKERS_PATH + "/" + workerId;
            if (client.checkExists().forPath(path) != null) {
                client.delete().forPath(path);
                log.info("Worker {} deregistered from ZooKeeper", workerId);
            }
        } catch (Exception e) {
            log.warn("Failed to deregister worker {}", workerId, e);
        }
    }
}
