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
 * IMPROVEMENTS vs original:
 * - start() validates workflowId is non-null before calling service.
 * - status filter sanitised (IllegalArgumentException → 400 via handler).
 * - @RequiredArgsConstructor used throughout.
 */
@RestController
@RequestMapping("/api/v1/executions")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
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
        String workflowIdStr = (String) body.get("workflowId");
        if (workflowIdStr == null || workflowIdStr.isBlank()) {
            throw new IllegalArgumentException("workflowId is required");
        }
        UUID workflowId = UUID.fromString(workflowIdStr);

        @SuppressWarnings("unchecked")
        Map<String, Object> input =
                (Map<String, Object>) body.getOrDefault("input", Map.of());
        String startedBy = (String) body.getOrDefault("startedBy", "system");

        return ResponseEntity.ok(
                executionService.startExecution(workflowId, input, startedBy));
    }

    /**
     * GET /api/v1/executions?page=0&size=10&status=FAILED
     */
    @GetMapping
    public ResponseEntity<Page<Execution>> list(
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false)    String status) {

        PageRequest pr = PageRequest.of(page, size,
                Sort.by(Sort.Direction.DESC, "startedAt"));

        Page<Execution> result;
        if (status != null && !status.isBlank()) {
            // Throws IllegalArgumentException on bad enum value → 400 via handler
            ExecutionStatus es = ExecutionStatus.valueOf(status.toUpperCase());
            result = executionRepository.findByStatus(es, pr);
        } else {
            result = executionRepository.findAll(pr);
        }
        return ResponseEntity.ok(result);
    }

    /** GET /api/v1/executions/{id} */
    @GetMapping("/{id}")
    public ResponseEntity<Execution> status(@PathVariable UUID id) {
        return ResponseEntity.ok(executionService.getExecutionStatus(id));
    }

    /** GET /api/v1/executions/{id}/logs */
    @GetMapping("/{id}/logs")
    public ResponseEntity<List<ExecutionLog>> logs(@PathVariable UUID id) {
        return ResponseEntity.ok(
                executionLogRepository.findByExecutionIdOrderByStartedAtAsc(id));
    }

    /** POST /api/v1/executions/{id}/cancel */
    @PostMapping("/{id}/cancel")
    public ResponseEntity<Execution> cancel(@PathVariable UUID id) {
        return ResponseEntity.ok(executionService.cancelExecution(id));
    }

    /** POST /api/v1/executions/{id}/retry */
    @PostMapping("/{id}/retry")
    public ResponseEntity<Execution> retry(@PathVariable UUID id) {
        return ResponseEntity.ok(executionService.retryFailedStep(id));
    }

    /** POST /api/v1/executions/{id}/approve */
    @PostMapping("/{id}/approve")
    public ResponseEntity<Execution> approve(
            @PathVariable UUID id,
            @RequestBody(required = false) Map<String, String> body) {
        String approverEmail = (body != null) ? body.get("approverEmail") : null;
        return ResponseEntity.ok(executionService.approveStep(id, approverEmail));
    }

    /** POST /api/v1/executions/{id}/reject */
    @PostMapping("/{id}/reject")
    public ResponseEntity<Execution> reject(
            @PathVariable UUID id,
            @RequestBody(required = false) Map<String, String> body) {
        String reason = (body != null) ? body.get("reason") : "Rejected";
        return ResponseEntity.ok(executionService.rejectStep(id, reason));
    }
}
