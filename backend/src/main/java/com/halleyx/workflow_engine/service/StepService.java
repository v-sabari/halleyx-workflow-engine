package com.halleyx.workflow_engine.service;

import com.halleyx.workflow_engine.entity.Step;
import com.halleyx.workflow_engine.exception.ResourceNotFoundException;
import com.halleyx.workflow_engine.repository.RuleRepository;
import com.halleyx.workflow_engine.repository.StepRepository;
import com.halleyx.workflow_engine.repository.WorkflowRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * StepService
 *
 * IMPROVEMENTS vs original:
 * - Uses @RequiredArgsConstructor (Lombok) instead of manual constructor.
 * - updateStep / deleteStep throw ResourceNotFoundException (404) on missing ID.
 * - createStep validates the parent workflow exists before saving.
 * - @Slf4j for structured logging.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class StepService {

    private final StepRepository     stepRepository;
    private final RuleRepository     ruleRepository;
    private final WorkflowRepository workflowRepository;

    public Step createStep(Step step) {
        // Validate parent workflow exists
        if (!workflowRepository.existsById(step.getWorkflowId())) {
            throw new ResourceNotFoundException("Workflow", step.getWorkflowId());
        }
        step.setCreatedAt(LocalDateTime.now());
        step.setUpdatedAt(LocalDateTime.now());
        Step saved = stepRepository.save(step);
        log.info("Created step id={} name='{}' workflow={}", saved.getId(), saved.getStepName(), saved.getWorkflowId());
        return saved;
    }

    public List<Step> getStepsByWorkflow(UUID workflowId) {
        if (!workflowRepository.existsById(workflowId)) {
            throw new ResourceNotFoundException("Workflow", workflowId);
        }
        return stepRepository.findByWorkflowIdOrderBySequenceOrderAsc(workflowId);
    }

    public Step updateStep(UUID id, Step updatedStep) {
        Step existing = stepRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Step", id));

        existing.setStepName(updatedStep.getStepName());
        existing.setStepType(updatedStep.getStepType());
        existing.setSequenceOrder(updatedStep.getSequenceOrder());
        existing.setConfiguration(updatedStep.getConfiguration());
        existing.setUpdatedAt(LocalDateTime.now());

        Step saved = stepRepository.save(existing);
        log.info("Updated step id={} name='{}'", saved.getId(), saved.getStepName());
        return saved;
    }

    @Transactional
    public void deleteStep(UUID id) {
        if (!stepRepository.existsById(id)) {
            throw new ResourceNotFoundException("Step", id);
        }
        ruleRepository.deleteByStepId(id);
        stepRepository.deleteById(id);
        log.info("Deleted step id={}", id);
    }
}
