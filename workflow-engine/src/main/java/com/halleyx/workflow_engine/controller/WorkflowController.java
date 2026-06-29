package com.halleyx.workflow_engine.controller;

import com.halleyx.workflow_engine.entity.Execution;
import com.halleyx.workflow_engine.entity.Workflow;
import com.halleyx.workflow_engine.service.ExecutionService;
import com.halleyx.workflow_engine.service.WorkflowService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * WorkflowController
 *
 * IMPROVEMENTS vs original:
 * - POST returns 201 CREATED instead of 200 OK.
 * - DELETE returns 204 NO_CONTENT (was already correct, kept).
 * - Null-check guards removed — service now throws ResourceNotFoundException.
 * - @CrossOrigin retained for resilience; global CorsConfig remains primary.
 */
@RestController
@RequestMapping("/api/v1/workflows")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class WorkflowController {

    private final WorkflowService  workflowService;
    private final ExecutionService executionService;

    /** POST /api/v1/workflows — 201 CREATED */
    @PostMapping
    public ResponseEntity<Workflow> create(@Valid @RequestBody Workflow workflow) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(workflowService.createWorkflow(workflow));
    }

    /** GET /api/v1/workflows?page=0&size=10&search=name&isActive=true */
    @GetMapping
    public ResponseEntity<Page<Map<String, Object>>> list(
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false)    String search,
            @RequestParam(required = false)    Boolean isActive) {
        return ResponseEntity.ok(
                workflowService.getAllWorkflows(page, size, search, isActive));
    }

    /** GET /api/v1/workflows/{id} */
    @GetMapping("/{id}")
    public ResponseEntity<Map<String, Object>> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(workflowService.getWorkflowById(id));
    }

    /** PUT /api/v1/workflows/{id} — version-bumping update */
    @PutMapping("/{id}")
    public ResponseEntity<Workflow> update(
            @PathVariable UUID id,
            @Valid @RequestBody Workflow workflow) {
        return ResponseEntity.ok(workflowService.updateWorkflow(id, workflow));
    }

    /** DELETE /api/v1/workflows/{id} — 204 NO_CONTENT */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        workflowService.deleteWorkflow(id);
        return ResponseEntity.noContent().build();
    }

    /**
     * POST /api/v1/workflows/{workflowId}/execute
     * Spec-compliant alias for /api/v1/executions/start.
     */
    @PostMapping("/{workflowId}/execute")
    public ResponseEntity<Execution> execute(
            @PathVariable UUID workflowId,
            @RequestBody(required = false) Map<String, Object> body) {

        if (body == null) body = new HashMap<>();

        @SuppressWarnings("unchecked")
        Map<String, Object> input = body.containsKey("input")
                ? (Map<String, Object>) body.get("input")
                : new HashMap<>();

        String startedBy = body.containsKey("startedBy")
                ? String.valueOf(body.get("startedBy"))
                : "system";

        return ResponseEntity.ok(
                executionService.startExecution(workflowId, input, startedBy));
    }
}
