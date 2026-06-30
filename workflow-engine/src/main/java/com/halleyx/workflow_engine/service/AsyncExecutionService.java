package com.halleyx.workflow_engine.service;

import com.halleyx.workflow_engine.entity.Execution;
import com.halleyx.workflow_engine.entity.Execution.ExecutionStatus;
import com.halleyx.workflow_engine.entity.Step;
import com.halleyx.workflow_engine.exception.ResourceNotFoundException;
import com.halleyx.workflow_engine.repository.ExecutionRepository;
import com.halleyx.workflow_engine.repository.StepRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.UUID;

/**
 * AsyncExecutionService
 *
 * Wraps ExecutionService.runStepsFrom() in a Spring @Async method so that
 * long-running step chains do not block the HTTP request thread.
 *
 * Flow (triggered by ExecutionController when async mode is requested):
 *
 *   1. ExecutionController saves the initial Execution row (status=RUNNING)
 *      via ExecutionService.startExecution() …
 *   2. … but instead of running steps synchronously, it calls
 *      AsyncExecutionService.runAsync(execution, firstStep, input).
 *   3. The HTTP thread returns 202 ACCEPTED with the Execution ID immediately.
 *   4. This @Async method runs on the "workflow-executor" thread pool and
 *      drives the step engine to completion in the background.
 *   5. The caller polls GET /api/v1/executions/{id} to check progress.
 *
 * Thread pool: configured in AsyncConfig (corePoolSize=4, max=20, queue=500).
 *
 * Error safety: any uncaught exception inside runAsync marks the execution
 * FAILED so the status is always consistent even if the JVM crashes mid-run
 * (the execution stays in RUNNING in that extreme case — acceptable; add a
 * watchdog/scheduler job for crash-recovery in highly critical deployments).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AsyncExecutionService {

    private final ExecutionService    executionService;
    private final ExecutionRepository executionRepository;
    private final StepRepository      stepRepository;

    /**
     * Runs the step engine asynchronously.
     *
     * @param execution  the already-persisted Execution entity (status=RUNNING)
     * @param firstStep  the first Step to execute
     * @param input      the deserialized input map
     */
    @Async("workflowExecutor")
    public void runAsync(Execution execution, Step firstStep, Map<String, Object> input) {
        log.info("[ASYNC] Starting step engine for execution id={} on thread={}",
                execution.getId(), Thread.currentThread().getName());
        try {
            executionService.runStepsFrom(execution, firstStep, input);
            log.info("[ASYNC] Step engine finished for execution id={}", execution.getId());
        } catch (Exception ex) {
            log.error("[ASYNC] Uncaught exception in step engine for execution id={}: {}",
                    execution.getId(), ex.getMessage(), ex);
            // Mark FAILED so callers never see a perpetually RUNNING execution
            executionRepository.findById(execution.getId()).ifPresent(e -> {
                if (e.getStatus() == ExecutionStatus.RUNNING) {
                    e.setStatus(ExecutionStatus.FAILED);
                    executionRepository.save(e);
                }
            });
        }
    }

    /**
     * Convenience: resolves the first step and launches async execution.
     * Called by ExecutionController when the client supplies
     * {@code "async": true} in the request body.
     *
     * @param executionId the ID of an already-saved RUNNING execution
     * @param firstStepId the step to start from
     * @param input       the execution input map
     */
    @Async("workflowExecutor")
    public void runAsyncById(UUID executionId, UUID firstStepId, Map<String, Object> input) {
        Execution execution = executionRepository.findById(executionId)
                .orElseThrow(() -> new ResourceNotFoundException("Execution", executionId));
        Step firstStep = stepRepository.findById(firstStepId)
                .orElseThrow(() -> new ResourceNotFoundException("Step", firstStepId));
        runAsync(execution, firstStep, input);
    }
}
