package com.halleyx.workflow_engine.service;

import com.halleyx.workflow_engine.entity.Rule;
import com.halleyx.workflow_engine.repository.RuleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RuleService {

    private final RuleRepository ruleRepository;

    public Rule createRule(Rule rule) {
        rule.setCreatedAt(LocalDateTime.now());
        rule.setUpdatedAt(LocalDateTime.now());
        return ruleRepository.save(rule);
    }

    public List<Rule> getRulesByStep(UUID stepId) {
        return ruleRepository.findByStepIdOrderByPriorityAsc(stepId);
    }

    public Rule updateRule(UUID ruleId, Rule updated) {
        Rule existing = ruleRepository.findById(ruleId)
                .orElseThrow(() -> new RuntimeException("Rule not found: " + ruleId));
        // FIX: Rule.java field name is "condition" (maps to DB column "rule_condition")
        existing.setCondition(updated.getCondition());
        existing.setPriority(updated.getPriority());
        existing.setNextStepId(updated.getNextStepId());
        existing.setUpdatedAt(LocalDateTime.now());
        return ruleRepository.save(existing);
    }

    public void deleteRule(UUID ruleId) {
        ruleRepository.deleteById(ruleId);
    }
}