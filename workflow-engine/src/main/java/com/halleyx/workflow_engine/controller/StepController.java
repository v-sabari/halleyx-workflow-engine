package com.halleyx.workflow_engine.controller;

import com.halleyx.workflow_engine.entity.Step;
import com.halleyx.workflow_engine.service.StepService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
@CrossOrigin(origins = "http://localhost:5173")
public class StepController {

    private final StepService stepService;

    public StepController(StepService stepService) {
        this.stepService = stepService;
    }

    @PostMapping("/workflows/{workflowId}/steps")
    public ResponseEntity<Step> createStep(
            @PathVariable UUID workflowId,
            @RequestBody Step step) {
        step.setWorkflowId(workflowId);
        return ResponseEntity.ok(stepService.createStep(step));
    }

    @GetMapping("/workflows/{workflowId}/steps")
    public ResponseEntity<List<Step>> getSteps(@PathVariable UUID workflowId) {
        return ResponseEntity.ok(stepService.getStepsByWorkflow(workflowId));
    }

    @PutMapping("/steps/{id}")
    public ResponseEntity<Step> updateStep(
            @PathVariable UUID id,
            @RequestBody Step step) {
        return ResponseEntity.ok(stepService.updateStep(id, step));
    }

    @DeleteMapping("/steps/{id}")
    public ResponseEntity<Void> deleteStep(@PathVariable UUID id) {
        stepService.deleteStep(id);
        return ResponseEntity.noContent().build();
    }
}