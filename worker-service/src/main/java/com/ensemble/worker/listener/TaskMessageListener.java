package com.ensemble.worker.listener;

import com.ensemble.common.dto.ResultMessage;
import com.ensemble.common.dto.TaskMessage;
import com.ensemble.worker.config.RabbitMQConfig;
import com.ensemble.worker.service.WorkerTrainingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class TaskMessageListener {

    private final WorkerTrainingService workerTrainingService;
    private final RabbitTemplate rabbitTemplate;

    @RabbitListener(queues = RabbitMQConfig.TASK_QUEUE)
    public void onTask(TaskMessage task) {
        try {
            log.info("Received task for job {}, partition {}", task.getJobId(), task.getPartitionIndex());
            ResultMessage result = workerTrainingService.processTask(task);
            rabbitTemplate.convertAndSend(RabbitMQConfig.EXCHANGE, RabbitMQConfig.RESULTS_QUEUE, result);
            log.info("Published result for job {}, partition {}", task.getJobId(), task.getPartitionIndex());
        } catch (Exception e) {
            log.error("Failed to process task for job {}", task.getJobId(), e);
        }
    }
}
