package com.halleyx.workflow_engine.service;

import com.halleyx.workflow_engine.entity.Step;
import com.halleyx.workflow_engine.repository.RuleRepository;
import com.halleyx.workflow_engine.repository.StepRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class StepService {

    private final StepRepository stepRepository;
    private final RuleRepository ruleRepository;

    public StepService(StepRepository stepRepository, RuleRepository ruleRepository) {
        this.stepRepository = stepRepository;
        this.ruleRepository = ruleRepository;
    }

    public Step createStep(Step step) {
        step.setCreatedAt(LocalDateTime.now());
        step.setUpdatedAt(LocalDateTime.now());
        return stepRepository.save(step);
    }

    public List<Step> getStepsByWorkflow(UUID workflowId) {
        return stepRepository.findByWorkflowIdOrderBySequenceOrderAsc(workflowId);
    }

    public Step updateStep(UUID id, Step updatedStep) {
        Step existing = stepRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Step not found: " + id));
        // FIX: correct field names from Step.java
        // stepName (NOT name), stepType, sequenceOrder (NOT order), configuration (NOT metadata)
        existing.setStepName(updatedStep.getStepName());
        existing.setStepType(updatedStep.getStepType());
        existing.setSequenceOrder(updatedStep.getSequenceOrder());
        existing.setConfiguration(updatedStep.getConfiguration());
        existing.setUpdatedAt(LocalDateTime.now());
        return stepRepository.save(existing);
    }

    @Transactional
    public void deleteStep(UUID id) {
        // Delete all rules belonging to this step first (referential integrity)
        ruleRepository.deleteByStepId(id);
        stepRepository.deleteById(id);
    }
}