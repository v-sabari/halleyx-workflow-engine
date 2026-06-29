package com.halleyx.workflow_engine.controller;

import com.halleyx.workflow_engine.entity.Rule;
import com.halleyx.workflow_engine.service.RuleService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * RuleController
 *
 * IMPROVEMENTS vs original:
 * - @Valid added to create/update endpoints.
 * - POST returns 201 CREATED.
 * - @RequiredArgsConstructor replaces manual constructor.
 */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class RuleController {

    private final RuleService ruleService;

    /** POST /api/v1/steps/{stepId}/rules — 201 CREATED */
    @PostMapping("/steps/{stepId}/rules")
    public ResponseEntity<Rule> createRule(
            @PathVariable UUID stepId,
            @Valid @RequestBody Rule rule) {
        rule.setStepId(stepId);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ruleService.createRule(rule));
    }

    /** GET /api/v1/steps/{stepId}/rules */
    @GetMapping("/steps/{stepId}/rules")
    public ResponseEntity<List<Rule>> getRulesByStep(@PathVariable UUID stepId) {
        return ResponseEntity.ok(ruleService.getRulesByStep(stepId));
    }

    /** PUT /api/v1/rules/{id} */
    @PutMapping("/rules/{id}")
    public ResponseEntity<Rule> updateRule(
            @PathVariable UUID id,
            @Valid @RequestBody Rule rule) {
        return ResponseEntity.ok(ruleService.updateRule(id, rule));
    }

    /** DELETE /api/v1/rules/{id} — 204 NO_CONTENT */
    @DeleteMapping("/rules/{id}")
    public ResponseEntity<Void> deleteRule(@PathVariable UUID id) {
        ruleService.deleteRule(id);
        return ResponseEntity.noContent().build();
    }
}
