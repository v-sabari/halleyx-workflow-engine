package com.halleyx.workflow_engine.controller;

import com.halleyx.workflow_engine.entity.Rule;
import com.halleyx.workflow_engine.service.RuleService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
@CrossOrigin(origins = "http://localhost:5173")
public class RuleController {

    private final RuleService ruleService;

    public RuleController(RuleService ruleService) {
        this.ruleService = ruleService;
    }

    @PostMapping("/steps/{stepId}/rules")
    public ResponseEntity<Rule> createRule(
            @PathVariable UUID stepId,
            @RequestBody Rule rule) {
        rule.setStepId(stepId);
        return ResponseEntity.ok(ruleService.createRule(rule));
    }

    @GetMapping("/steps/{stepId}/rules")
    public ResponseEntity<List<Rule>> getRulesByStep(@PathVariable UUID stepId) {
        return ResponseEntity.ok(ruleService.getRulesByStep(stepId));
    }

    @PutMapping("/rules/{id}")
    public ResponseEntity<Rule> updateRule(
            @PathVariable UUID id,
            @RequestBody Rule rule) {
        return ResponseEntity.ok(ruleService.updateRule(id, rule));
    }

    @DeleteMapping("/rules/{id}")
    public ResponseEntity<Void> deleteRule(@PathVariable UUID id) {
        ruleService.deleteRule(id);
        return ResponseEntity.noContent().build();
    }
}