package com.halleyx.workflow_engine.controller;

import com.halleyx.workflow_engine.entity.Execution;
import com.halleyx.workflow_engine.entity.Execution.ExecutionStatus;
import com.halleyx.workflow_engine.entity.ExecutionLog;
import com.halleyx.workflow_engine.repository.ExecutionLogRepository;
import com.halleyx.workflow_engine.repository.ExecutionRepository;
import com.halleyx.workflow_engine.service.ExecutionService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * ExecutionController
 *
 * B3 FIX — GET /api/v1/executions/{id}/logs added.
 *   WorkflowExecution.jsx calls this to populate the step-logs table.
 *   Returns List<ExecutionLog> ordered startedAt ASC (execution order).
 *
 * B6 FIX — @CrossOrigin added.
 *   Original controller lacked it; all other controllers had it.
 *   CorsConfig bean covers all routes globally, but explicit annotation
 *   guards against future Spring Security config overriding the CORS filter.
 *
 * GET /api/v1/executions (paginated) — required by AuditLog.jsx (F2 fix).
 */
@RestController
@RequestMapping("/api/v1/executions")
@RequiredArgsConstructor
@CrossOrigin(origins = "http://localhost:5173")
public class ExecutionController {

    private final ExecutionService       executionService;
    private final ExecutionRepository    executionRepository;
    private final ExecutionLogRepository executionLogRepository;

    /**
     * POST /api/v1/executions/start
     * Body: { "workflowId": "uuid", "input": {...}, "startedBy": "name" }
     */
    @PostMapping("/start")
    public ResponseEntity<Execution> start(@RequestBody Map<String, Object> body) {
        UUID workflowId = UUID.fromString((String) body.get("workflowId"));
        @SuppressWarnings("unchecked")
        Map<String, Object> input =
                (Map<String, Object>) body.getOrDefault("input", Map.of());
        String startedBy = (String) body.getOrDefault("startedBy", "system");
        return ResponseEntity.ok(
                executionService.startExecution(workflowId, input, startedBy));
    }

    /**
     * GET /api/v1/executions?page=0&size=10&status=FAILED
     * Paginated execution list — AuditLog page primary data source.
     */
    @GetMapping
    public ResponseEntity<Page<Execution>> list(
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false)    String status) {

        PageRequest pr = PageRequest.of(
                page, size,
                Sort.by(Sort.Direction.DESC, "startedAt"));

        Page<Execution> result;
        if (status != null && !status.isBlank()) {
            try {
                result = executionRepository.findByStatus(
                        ExecutionStatus.valueOf(status.toUpperCase()), pr);
            } catch (IllegalArgumentException e) {
                result = Page.empty(pr);
            }
        } else {
            result = executionRepository.findAll(pr);
        }
        return ResponseEntity.ok(result);
    }

    /**
     * GET /api/v1/executions/{id}
     */
    @GetMapping("/{id}")
    public ResponseEntity<Execution> status(@PathVariable UUID id) {
        return ResponseEntity.ok(executionService.getExecutionStatus(id));
    }

    /**
     * GET /api/v1/executions/{id}/logs   — B3 FIX
     *
     * Returns all ExecutionLog rows for this execution, ordered startedAt ASC.
     * WorkflowExecution.jsx calls this URL for the step-logs table.
     * AuditLog.jsx drill-down also uses this for the sub-table.
     */
    @GetMapping("/{id}/logs")
    public ResponseEntity<List<ExecutionLog>> logs(@PathVariable UUID id) {
        List<ExecutionLog> logList =
                executionLogRepository.findByExecutionIdOrderByStartedAtAsc(id);
        return ResponseEntity.ok(logList);
    }

    /**
     * POST /api/v1/executions/{id}/cancel
     */
    @PostMapping("/{id}/cancel")
    public ResponseEntity<Execution> cancel(@PathVariable UUID id) {
        return ResponseEntity.ok(executionService.cancelExecution(id));
    }

    /**
     * POST /api/v1/executions/{id}/retry
     * Retries only the failed step — not the entire workflow (spec requirement).
     */
    @PostMapping("/{id}/retry")
    public ResponseEntity<Execution> retry(@PathVariable UUID id) {
        return ResponseEntity.ok(executionService.retryFailedStep(id));
    }

    /**
     * POST /api/v1/executions/{id}/approve
     * Body: { "approverEmail": "manager@example.com" }
     */
    @PostMapping("/{id}/approve")
    public ResponseEntity<Execution> approve(
            @PathVariable UUID id,
            @RequestBody(required = false) Map<String, String> body) {
        String approverEmail = (body != null) ? body.get("approverEmail") : null;
        return ResponseEntity.ok(executionService.approveStep(id, approverEmail));
    }

    /**
     * POST /api/v1/executions/{id}/reject
     * Body: { "reason": "..." }
     */
    @PostMapping("/{id}/reject")
    public ResponseEntity<Execution> reject(
            @PathVariable UUID id,
            @RequestBody(required = false) Map<String, String> body) {
        String reason = (body != null) ? body.get("reason") : "Rejected";
        return ResponseEntity.ok(executionService.rejectStep(id, reason));
    }
}