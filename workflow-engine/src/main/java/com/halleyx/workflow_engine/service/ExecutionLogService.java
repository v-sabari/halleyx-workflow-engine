package com.halleyx.workflow_engine.service;

import com.halleyx.workflow_engine.entity.ExecutionLog;
import com.halleyx.workflow_engine.repository.ExecutionLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

/**
 * ExecutionLogService
 *
 * FIX: Replaced manual constructor with @RequiredArgsConstructor (Lombok),
 * matching the convention used by every other service in this codebase
 * (WorkflowService, StepService, RuleService, ExecutionService, etc.).
 * Purely a consistency fix — behavior is unchanged.
 */
@Service
@RequiredArgsConstructor
public class ExecutionLogService {

    private final ExecutionLogRepository executionLogRepository;

    public ExecutionLog saveLog(ExecutionLog executionLog) {
        return executionLogRepository.save(executionLog);
    }

    public List<ExecutionLog> getLogsByExecutionId(UUID executionId) {
        return executionLogRepository.findByExecutionIdOrderByStartedAtAsc(executionId);
    }
}
