package com.halleyx.workflow_engine.service;

import com.halleyx.workflow_engine.entity.Execution;
import com.halleyx.workflow_engine.entity.Execution.ExecutionStatus;
import com.halleyx.workflow_engine.entity.Step;
import com.halleyx.workflow_engine.exception.ResourceNotFoundException;
import com.halleyx.workflow_engine.repository.ExecutionRepository;
import com.halleyx.workflow_engine.repository.StepRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AsyncExecutionServiceTest {

    @Mock private ExecutionService    executionService;
    @Mock private ExecutionRepository executionRepository;
    @Mock private StepRepository      stepRepository;

    @InjectMocks
    private AsyncExecutionService asyncExecutionService;

    private Execution sampleExecution;
    private Step      sampleStep;
    private UUID      executionId;
    private UUID      stepId;

    @BeforeEach
    void setUp() {
        executionId = UUID.randomUUID();
        stepId      = UUID.randomUUID();

        sampleExecution = new Execution();
        sampleExecution.setId(executionId);
        sampleExecution.setStatus(ExecutionStatus.RUNNING);
        sampleExecution.setRetryCount(0);
        sampleExecution.setWorkflowVersion(1);

        sampleStep = new Step();
        sampleStep.setId(stepId);
        sampleStep.setStepName("First Step");
        sampleStep.setSequenceOrder(1);
        sampleStep.setStepType(Step.StepType.TASK);
    }

    // ── runAsync ──────────────────────────────────────────────────────────────

    @Test
    void runAsync_shouldDelegateToExecutionService() {
        when(executionService.runStepsFrom(any(), any(), any()))
                .thenReturn(sampleExecution);

        asyncExecutionService.runAsync(sampleExecution, sampleStep, Map.of());

        verify(executionService, times(1))
                .runStepsFrom(sampleExecution, sampleStep, Map.of());
    }

    @Test
    void runAsync_onException_shouldMarkExecutionFailed() {
        when(executionService.runStepsFrom(any(), any(), any()))
                .thenThrow(new RuntimeException("Simulated step failure"));
        when(executionRepository.findById(executionId))
                .thenReturn(Optional.of(sampleExecution));
        when(executionRepository.save(any()))
                .thenAnswer(i -> i.getArgument(0));

        // Must not propagate the exception to the caller
        assertDoesNotThrow(() ->
                asyncExecutionService.runAsync(sampleExecution, sampleStep, Map.of()));

        verify(executionRepository).save(argThat(e ->
                e.getStatus() == ExecutionStatus.FAILED));
    }

    @Test
    void runAsync_onException_shouldNotMarkFailedIfAlreadyCompleted() {
        sampleExecution.setStatus(ExecutionStatus.COMPLETED);

        when(executionService.runStepsFrom(any(), any(), any()))
                .thenThrow(new RuntimeException("Simulated failure"));
        when(executionRepository.findById(executionId))
                .thenReturn(Optional.of(sampleExecution));

        assertDoesNotThrow(() ->
                asyncExecutionService.runAsync(sampleExecution, sampleStep, Map.of()));

        // Already COMPLETED — must NOT overwrite status to FAILED
        verify(executionRepository, never()).save(any());
    }

    // ── runAsyncById ──────────────────────────────────────────────────────────

    @Test
    void runAsyncById_shouldResolveEntitiesAndDelegate() {
        when(executionRepository.findById(executionId))
                .thenReturn(Optional.of(sampleExecution));
        when(stepRepository.findById(stepId))
                .thenReturn(Optional.of(sampleStep));
        when(executionService.runStepsFrom(any(), any(), any()))
                .thenReturn(sampleExecution);

        assertDoesNotThrow(() ->
                asyncExecutionService.runAsyncById(executionId, stepId, Map.of()));

        verify(executionService, times(1))
                .runStepsFrom(sampleExecution, sampleStep, Map.of());
    }

    @Test
    void runAsyncById_unknownExecutionId_throwsResourceNotFoundException() {
        when(executionRepository.findById(executionId))
                .thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> asyncExecutionService.runAsyncById(executionId, stepId, Map.of()));
    }

    @Test
    void runAsyncById_unknownStepId_throwsResourceNotFoundException() {
        when(executionRepository.findById(executionId))
                .thenReturn(Optional.of(sampleExecution));
        when(stepRepository.findById(stepId))
                .thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> asyncExecutionService.runAsyncById(executionId, stepId, Map.of()));
    }
}
