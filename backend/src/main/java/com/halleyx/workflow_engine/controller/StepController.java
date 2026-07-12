package com.halleyx.workflow_engine.controller;

import com.halleyx.workflow_engine.entity.Step;
import com.halleyx.workflow_engine.service.StepService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * StepController
 *
 * IMPROVEMENTS vs original:
 * - @Valid added to create/update so Bean Validation constraints fire.
 * - POST returns 201 CREATED.
 * - @RequiredArgsConstructor replaces manual constructor.
 */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class StepController {

    private final StepService stepService;

    /** POST /api/v1/workflows/{workflowId}/steps — 201 CREATED */
    @PostMapping("/workflows/{workflowId}/steps")
    public ResponseEntity<Step> createStep(
            @PathVariable UUID workflowId,
            @Valid @RequestBody Step step) {
        step.setWorkflowId(workflowId);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(stepService.createStep(step));
    }

    /** GET /api/v1/workflows/{workflowId}/steps */
    @GetMapping("/workflows/{workflowId}/steps")
    public ResponseEntity<List<Step>> getSteps(@PathVariable UUID workflowId) {
        return ResponseEntity.ok(stepService.getStepsByWorkflow(workflowId));
    }

    /** PUT /api/v1/steps/{id} */
    @PutMapping("/steps/{id}")
    public ResponseEntity<Step> updateStep(
            @PathVariable UUID id,
            @Valid @RequestBody Step step) {
        return ResponseEntity.ok(stepService.updateStep(id, step));
    }

    /** DELETE /api/v1/steps/{id} — 204 NO_CONTENT */
    @DeleteMapping("/steps/{id}")
    public ResponseEntity<Void> deleteStep(@PathVariable UUID id) {
        stepService.deleteStep(id);
        return ResponseEntity.noContent().build();
    }
}
