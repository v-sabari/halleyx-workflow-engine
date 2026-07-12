package com.halleyx.workflow_engine.service;

import com.halleyx.workflow_engine.entity.Rule;
import com.halleyx.workflow_engine.exception.ResourceNotFoundException;
import com.halleyx.workflow_engine.repository.RuleRepository;
import com.halleyx.workflow_engine.repository.StepRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * RuleService
 *
 * IMPROVEMENTS vs original:
 * - updateRule / deleteRule throw ResourceNotFoundException (404).
 * - createRule validates the parent step exists.
 * - @Slf4j for structured logging.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RuleService {

    private final RuleRepository  ruleRepository;
    private final StepRepository  stepRepository;

    public Rule createRule(Rule rule) {
        // Validate parent step exists
        if (!stepRepository.existsById(rule.getStepId())) {
            throw new ResourceNotFoundException("Step", rule.getStepId());
        }
        rule.setCreatedAt(LocalDateTime.now());
        rule.setUpdatedAt(LocalDateTime.now());
        Rule saved = ruleRepository.save(rule);
        log.info("Created rule id={} step={} priority={}", saved.getId(), saved.getStepId(), saved.getPriority());
        return saved;
    }

    public List<Rule> getRulesByStep(UUID stepId) {
        if (!stepRepository.existsById(stepId)) {
            throw new ResourceNotFoundException("Step", stepId);
        }
        return ruleRepository.findByStepIdOrderByPriorityAsc(stepId);
    }

    public Rule updateRule(UUID ruleId, Rule updated) {
        Rule existing = ruleRepository.findById(ruleId)
                .orElseThrow(() -> new ResourceNotFoundException("Rule", ruleId));

        existing.setCondition(updated.getCondition());
        existing.setPriority(updated.getPriority());
        existing.setNextStepId(updated.getNextStepId());
        existing.setUpdatedAt(LocalDateTime.now());

        Rule saved = ruleRepository.save(existing);
        log.info("Updated rule id={}", saved.getId());
        return saved;
    }

    public void deleteRule(UUID ruleId) {
        if (!ruleRepository.existsById(ruleId)) {
            throw new ResourceNotFoundException("Rule", ruleId);
        }
        ruleRepository.deleteById(ruleId);
        log.info("Deleted rule id={}", ruleId);
    }
}
