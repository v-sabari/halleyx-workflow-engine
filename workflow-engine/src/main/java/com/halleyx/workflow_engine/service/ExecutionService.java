package com.halleyx.workflow_engine.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.halleyx.workflow_engine.entity.Execution;
import com.halleyx.workflow_engine.entity.Execution.ExecutionStatus;
import com.halleyx.workflow_engine.entity.ExecutionLog;
import com.halleyx.workflow_engine.entity.Rule;
import com.halleyx.workflow_engine.entity.Step;
import com.halleyx.workflow_engine.entity.Workflow;
import com.halleyx.workflow_engine.repository.ExecutionLogRepository;
import com.halleyx.workflow_engine.repository.ExecutionRepository;
import com.halleyx.workflow_engine.repository.RuleRepository;
import com.halleyx.workflow_engine.repository.StepRepository;
import com.halleyx.workflow_engine.repository.WorkflowRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class ExecutionService {

    private final ExecutionRepository executionRepository;
    private final WorkflowRepository workflowRepository;
    private final StepRepository stepRepository;
    private final RuleRepository ruleRepository;
    private final ExecutionLogRepository executionLogRepository;
    private final RuleEvaluationService ruleEvaluationService;
    private final InputSchemaValidatorService validatorService;
    private final NotificationService notificationService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Transactional
    public Execution startExecution(UUID workflowId,
                                    Map<String, Object> input,
                                    String startedBy) {
        Workflow workflow = workflowRepository.findById(workflowId)
                .orElseThrow(() -> new RuntimeException("Workflow not found: " + workflowId));

        if (!Boolean.TRUE.equals(workflow.getIsActive())) {
            throw new RuntimeException("Workflow is not active: " + workflowId);
        }

        validatorService.validate(workflow.getInputSchema(), input);

        List<Step> allSteps = stepRepository.findByWorkflowIdOrderBySequenceOrderAsc(workflowId);
        if (allSteps.isEmpty()) {
            throw new RuntimeException("Workflow has no steps defined: " + workflowId);
        }

        Step firstStep = (workflow.getFirstStepId() != null)
                ? stepRepository.findById(workflow.getFirstStepId()).orElse(allSteps.get(0))
                : allSteps.get(0);

        Execution execution = new Execution();
        execution.setWorkflowId(workflowId);
        execution.setWorkflowVersion(workflow.getVersion());
        execution.setStatus(ExecutionStatus.RUNNING);
        execution.setStartedBy(startedBy);
        execution.setRetryCount(0);
        execution.setCurrentStepId(firstStep.getId());
        execution.setInputData(serialize(input));

        execution = executionRepository.save(execution);

        log.info("Started execution id={} workflow={}", execution.getId(), workflowId);
        return runStepsFrom(execution, firstStep, input);
    }

    @Transactional
    public Execution approveStep(UUID executionId, String approverEmail) {
        Execution execution = requireExecution(executionId);

        if (execution.getStatus() != ExecutionStatus.WAITING_FOR_APPROVAL) {
            throw new RuntimeException("Execution is not waiting for approval: " + executionId);
        }

        Step currentStep = requireStep(execution.getCurrentStepId());
        Map<String, Object> input = deserializeInput(execution.getInputData());
        List<Rule> rules = ruleRepository.findByStepIdOrderByPriorityAsc(currentStep.getId());
        Rule matched = ruleEvaluationService.evaluateRules(rules, input);

        completeLatestWaitingApprovalLog(
                executionId,
                currentStep,
                "APPROVED",
                matched != null ? matched.getCondition() : null,
                matched != null ? matched.getNextStepId() : null,
                null,
                approverEmail
        );

        if (matched != null && matched.getNextStepId() != null) {
            execution.setStatus(ExecutionStatus.RUNNING);
            execution.setCurrentStepId(matched.getNextStepId());
            execution = executionRepository.save(execution);

            Step nextStep = requireStep(matched.getNextStepId());
            return runStepsFrom(execution, nextStep, input);
        }

        execution.setStatus(ExecutionStatus.COMPLETED);
        execution.setCompletedAt(LocalDateTime.now());
        return executionRepository.save(execution);
    }

    @Transactional
    public Execution rejectStep(UUID executionId, String reason) {
        Execution execution = requireExecution(executionId);

        if (execution.getStatus() != ExecutionStatus.WAITING_FOR_APPROVAL) {
            throw new RuntimeException("Execution is not waiting for approval: " + executionId);
        }

        Step currentStep = requireStep(execution.getCurrentStepId());

        completeLatestWaitingApprovalLog(
                executionId,
                currentStep,
                "REJECTED",
                null,
                null,
                reason != null ? reason : "Rejected",
                null
        );

        execution.setStatus(ExecutionStatus.FAILED);
        execution.setCompletedAt(LocalDateTime.now());
        Execution saved = executionRepository.save(execution);

        log.info("Execution id={} rejected at step='{}'", executionId, currentStep.getStepName());
        return saved;
    }

    @Transactional
    public Execution retryFailedStep(UUID executionId) {
        Execution execution = requireExecution(executionId);

        if (execution.getStatus() != ExecutionStatus.FAILED) {
            throw new RuntimeException("Execution is not in FAILED state: " + executionId);
        }

        Step failedStep = requireStep(execution.getCurrentStepId());
        int retryLimit = readRetryLimit(failedStep);

        if (execution.getRetryCount() >= retryLimit) {
            throw new RuntimeException(
                    "Retry limit of " + retryLimit + " exceeded for step '" + failedStep.getStepName() + "'"
            );
        }

        execution.setRetryCount(execution.getRetryCount() + 1);
        execution.setStatus(ExecutionStatus.RUNNING);
        execution = executionRepository.save(execution);

        log.info("Retrying execution id={} step='{}' attempt={}/{}",
                executionId, failedStep.getStepName(), execution.getRetryCount(), retryLimit);

        Map<String, Object> input = deserializeInput(execution.getInputData());
        return runStepsFrom(execution, failedStep, input);
    }

    @Transactional
    public Execution cancelExecution(UUID executionId) {
        Execution execution = requireExecution(executionId);

        if (execution.getStatus() == ExecutionStatus.COMPLETED
                || execution.getStatus() == ExecutionStatus.CANCELLED) {
            throw new RuntimeException("Cannot cancel a finished execution: " + executionId);
        }

        execution.setStatus(ExecutionStatus.CANCELLED);
        execution.setCompletedAt(LocalDateTime.now());

        Execution saved = executionRepository.save(execution);
        log.info("Cancelled execution id={}", executionId);
        return saved;
    }

    @Transactional(readOnly = true)
    public Execution getExecutionStatus(UUID executionId) {
        return requireExecution(executionId);
    }

    @Transactional
    public Execution runStepsFrom(Execution execution,
                                  Step startStep,
                                  Map<String, Object> input) {
        Step currentStep = startStep;

        while (currentStep != null) {
            String evaluatedRule = null;
            UUID nextStepId = null;
            String errorMessage = null;
            String stepStatus;
            LocalDateTime stepStartedAt = LocalDateTime.now();

            try {
                switch (currentStep.getStepType()) {
                    case TASK:
                        processTask(currentStep, input);
                        break;

                    case NOTIFICATION:
                        Map<String, Object> notifInput = new HashMap<>(input);
                        notifInput.put("__executionId", execution.getId());
                        notificationService.sendNotification(currentStep, notifInput);
                        break;

                    case APPROVAL:
                        writeWaitingApprovalLog(execution.getId(), currentStep, stepStartedAt);
                        execution.setStatus(ExecutionStatus.WAITING_FOR_APPROVAL);
                        execution.setCurrentStepId(currentStep.getId());
                        return executionRepository.save(execution);

                    default:
                        throw new RuntimeException("Unsupported step type: " + currentStep.getStepType());
                }

                List<Rule> rules = ruleRepository.findByStepIdOrderByPriorityAsc(currentStep.getId());
                Rule matched = ruleEvaluationService.evaluateRules(rules, input);

                if (matched != null) {
                    evaluatedRule = matched.getCondition();
                    nextStepId = matched.getNextStepId();
                }

                stepStatus = "COMPLETED";

            } catch (Exception ex) {
                log.error("Step '{}' failed: {}", currentStep.getStepName(), ex.getMessage(), ex);

                errorMessage = ex.getMessage();
                stepStatus = "FAILED";

                writeExecutionLog(
                        execution.getId(),
                        currentStep,
                        stepStatus,
                        null,
                        null,
                        errorMessage,
                        null,
                        stepStartedAt,
                        LocalDateTime.now()
                );

                execution.setStatus(ExecutionStatus.FAILED);
                execution.setCurrentStepId(currentStep.getId());
                return executionRepository.save(execution);
            }

            writeExecutionLog(
                    execution.getId(),
                    currentStep,
                    stepStatus,
                    evaluatedRule,
                    nextStepId,
                    null,
                    null,
                    stepStartedAt,
                    LocalDateTime.now()
            );

            if (nextStepId != null) {
                execution.setCurrentStepId(nextStepId);
                execution = executionRepository.save(execution);

                Optional<Step> nextOpt = stepRepository.findById(nextStepId);
                currentStep = nextOpt.orElse(null);
            } else {
                execution.setStatus(ExecutionStatus.COMPLETED);
                execution.setCompletedAt(LocalDateTime.now());
                return executionRepository.save(execution);
            }
        }

        log.warn("Execution id={} reached a null step — marking COMPLETED", execution.getId());
        execution.setStatus(ExecutionStatus.COMPLETED);
        execution.setCompletedAt(LocalDateTime.now());
        return executionRepository.save(execution);
    }

    private void processTask(Step step, Map<String, Object> input) {
        log.info("Processing TASK step: '{}'", step.getStepName());
    }

    private void writeWaitingApprovalLog(UUID executionId,
                                         Step step,
                                         LocalDateTime startedAt) {
        ExecutionLog logEntry = ExecutionLog.builder()
                .executionId(executionId)
                .stepId(step.getId())
                .stepName(step.getStepName())
                .stepType(step.getStepType().name())
                .evaluatedRules(null)
                .selectedNextStepId(null)
                .status("WAITING_FOR_APPROVAL")
                .errorMessage(null)
                .approverId(null)
                .startedAt(startedAt)
                .endedAt(null)
                .build();

        executionLogRepository.save(logEntry);
    }

    private void completeLatestWaitingApprovalLog(UUID executionId,
                                                  Step step,
                                                  String finalStatus,
                                                  String evaluatedRules,
                                                  UUID selectedNextStepId,
                                                  String errorMessage,
                                                  String approverId) {
        ExecutionLog waitingLog = executionLogRepository
                .findTopByExecutionIdAndStepIdAndStatusOrderByStartedAtDesc(
                        executionId,
                        step.getId(),
                        "WAITING_FOR_APPROVAL"
                )
                .orElseThrow(() -> new RuntimeException(
                        "Waiting approval log not found for execution=" + executionId + ", step=" + step.getId()
                ));

        waitingLog.setStatus(finalStatus);
        waitingLog.setEvaluatedRules(evaluatedRules);
        waitingLog.setSelectedNextStepId(selectedNextStepId);
        waitingLog.setErrorMessage(errorMessage);
        waitingLog.setApproverId(approverId);
        waitingLog.setEndedAt(LocalDateTime.now());

        executionLogRepository.save(waitingLog);
    }

    private void writeExecutionLog(UUID executionId,
                                   Step step,
                                   String status,
                                   String evaluatedRules,
                                   UUID selectedNextStepId,
                                   String errorMessage,
                                   String approverId,
                                   LocalDateTime startedAt,
                                   LocalDateTime endedAt) {
        ExecutionLog logEntry = ExecutionLog.builder()
                .executionId(executionId)
                .stepId(step.getId())
                .stepName(step.getStepName())
                .stepType(step.getStepType().name())
                .evaluatedRules(evaluatedRules)
                .selectedNextStepId(selectedNextStepId)
                .status(status)
                .errorMessage(errorMessage)
                .approverId(approverId)
                .startedAt(startedAt)
                .endedAt(endedAt)
                .build();

        executionLogRepository.save(logEntry);
    }

    private int readRetryLimit(Step step) {
        try {
            if (step.getConfiguration() != null && !step.getConfiguration().isBlank()) {
                Map<String, Object> config = objectMapper.readValue(
                        step.getConfiguration(),
                        new TypeReference<>() {}
                );
                Object limit = config.get("retry_limit");
                if (limit instanceof Number n) {
                    return n.intValue();
                }
            }
        } catch (Exception ignored) {
            // fall through
        }
        return 3;
    }

    private Execution requireExecution(UUID id) {
        return executionRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Execution not found: " + id));
    }

    private Step requireStep(UUID id) {
        return stepRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Step not found: " + id));
    }

    private Map<String, Object> deserializeInput(String json) {
        try {
            if (json == null || json.isBlank()) {
                return new HashMap<>();
            }
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (Exception e) {
            log.warn("Could not deserialize execution input: {}", e.getMessage());
            return new HashMap<>();
        }
    }

    private String serialize(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            throw new RuntimeException("Serialization failed", e);
        }
    }
}