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
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * WorkflowController
 *
 * All endpoints are protected by the API key filter (SecurityConfig).
 * The /{workflowId}/execute alias passes the Idempotency-Key header through
 * to ExecutionService so callers can also use idempotency on this route.
 */
@RestController
@RequestMapping("/api/v1/workflows")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class WorkflowController {

    private static final String IDEMPOTENCY_HEADER = "Idempotency-Key";

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

    /** PUT /api/v1/workflows/{id} */
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
     * Convenience alias — also supports Idempotency-Key and authenticated principal.
     */
    @PostMapping("/{workflowId}/execute")
    public ResponseEntity<Execution> execute(
            @PathVariable UUID workflowId,
            @RequestHeader(value = IDEMPOTENCY_HEADER, required = false) String idempotencyKey,
            @RequestBody(required = false) Map<String, Object> body,
            Authentication authentication) {

        if (body == null) body = new HashMap<>();

        @SuppressWarnings("unchecked")
        Map<String, Object> input = body.containsKey("input") && body.get("input") instanceof Map
                ? (Map<String, Object>) body.get("input")
                : new HashMap<>();

        String startedBy = body.containsKey("startedBy")
                ? String.valueOf(body.get("startedBy"))
                : (authentication != null
                        ? String.valueOf(authentication.getPrincipal())
                        : "system");

        return ResponseEntity.ok(
                executionService.startExecution(workflowId, input, startedBy, idempotencyKey));
    }
}
