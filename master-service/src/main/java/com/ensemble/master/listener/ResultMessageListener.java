package com.ensemble.master.listener;

import com.ensemble.common.dto.ResultMessage;
import com.ensemble.master.service.OrchestratorService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ResultMessageListener {

    private final OrchestratorService orchestratorService;

    // Result handling is done directly in OrchestratorService via @RabbitListener
    public void onResult(ResultMessage result) {
        orchestratorService.handleResult(result);
    }
}
