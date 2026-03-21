package com.halleyx.workflow_engine.service;

import com.halleyx.workflow_engine.entity.ExecutionLog;
import com.halleyx.workflow_engine.repository.ExecutionLogRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class ExecutionLogService {

    private final ExecutionLogRepository executionLogRepository;

    public ExecutionLogService(ExecutionLogRepository executionLogRepository) {
        this.executionLogRepository = executionLogRepository;
    }

    public ExecutionLog saveLog(ExecutionLog executionLog) {
        return executionLogRepository.save(executionLog);
    }

    public List<ExecutionLog> getLogsByExecutionId(UUID executionId) {
        return executionLogRepository.findByExecutionIdOrderByStartedAtAsc(executionId);
    }
}