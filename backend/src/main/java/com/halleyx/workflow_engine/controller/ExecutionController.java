package com.halleyx.workflow_engine.controller;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.halleyx.workflow_engine.entity.Execution;
import com.halleyx.workflow_engine.entity.Execution.ExecutionStatus;
import com.halleyx.workflow_engine.entity.ExecutionLog;
import com.halleyx.workflow_engine.entity.Step;
import com.halleyx.workflow_engine.entity.Workflow;
import com.halleyx.workflow_engine.exception.BusinessException;
import com.halleyx.workflow_engine.exception.ResourceNotFoundException;
import com.halleyx.workflow_engine.idempotency.IdempotencyRecord;
import com.halleyx.workflow_engine.idempotency.IdempotencyService;
import com.halleyx.workflow_engine.repository.ExecutionLogRepository;
import com.halleyx.workflow_engine.repository.ExecutionRepository;
import com.halleyx.workflow_engine.repository.StepRepository;
import com.halleyx.workflow_engine.repository.WorkflowRepository;
import com.halleyx.workflow_engine.service.AsyncExecutionService;
import com.halleyx.workflow_engine.service.ExecutionService;
import com.halleyx.workflow_engine.service.InputSchemaValidatorService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * ExecutionController
 *
 * All five improvements are wired here:
 *
 * 1. AUTHENTICATION  — Every endpoint is protected by the API key filter
 *    (configured in SecurityConfig). No code changes needed in this class
 *    beyond injecting the current principal where useful (startedBy field).
 *
 * 2. RATE LIMITING   — Enforced by RateLimitFilter upstream of this controller.
 *    Headers X-RateLimit-Limit and X-RateLimit-Remaining are set by the filter.
 *
 * 3. IDEMPOTENCY     — POST /start reads the optional "Idempotency-Key" header
 *    and passes it to ExecutionService, which handles lookup / replay / cache.
 *    Duplicate requests within 24 h return the same Execution without re-running.
 *
 * 4. ASYNC           — POST /start accepts "async": true in the request body.
 *    Sync (default): runs the step engine on the HTTP thread, returns 200 + full Execution.
 *    Async: saves the Execution, fires AsyncExecutionService.runAsync(), returns
 *           202 ACCEPTED + { "executionId": "...", "status": "RUNNING", "async": true }.
 *    The caller polls GET /{id} to track progress.
 *
 * Idempotency-Key header format: any unique string ≤ 64 chars (UUID recommended).
 */
@RestController
@RequestMapping("/api/v1/executions")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
@Slf4j
public class ExecutionController {

    private static final String IDEMPOTENCY_HEADER = "Idempotency-Key";

    private final ExecutionService       executionService;
    private final AsyncExecutionService  asyncExecutionService;
    private final ExecutionRepository    executionRepository;
    private final ExecutionLogRepository executionLogRepository;
    private final WorkflowRepository     workflowRepository;
    private final StepRepository         stepRepository;
    private final IdempotencyService     idempotencyService;
    private final InputSchemaValidatorService validatorService;
    private final ObjectMapper           objectMapper;

    // ── Start ─────────────────────────────────────────────────────────────────

    /**
     * POST /api/v1/executions/start
     *
     * Request body:
     * {
     *   "workflowId": "uuid",          // required
     *   "input":      { ... },          // optional — validated against workflow schema
     *   "startedBy":  "alice@corp.com", // optional — defaults to authenticated key name
     *   "async":      false             // optional — true for non-blocking execution
     * }
     *
     * Headers:
     *   X-API-Key: <key>               // required (enforced by SecurityConfig)
     *   Idempotency-Key: <uuid>        // optional — prevents duplicate executions
     *
     * Responses:
     *   200 OK  — synchronous execution completed (or replayed idempotent response)
     *   202 Accepted — async execution started; poll GET /{id} for status
     *   400 Bad Request — missing workflowId, inactive workflow, schema violation
     *   404 Not Found — workflowId unknown
     *   409 Conflict  — idempotency key is already being processed concurrently
     *   429 Too Many Requests — rate limit exceeded (set by RateLimitFilter)
     */
    @PostMapping("/start")
    public ResponseEntity<?> start(
            @RequestHeader(value = IDEMPOTENCY_HEADER, required = false) String idempotencyKey,
            @RequestBody Map<String, Object> body,
            Authentication authentication) {

        // ── Parse request body ────────────────────────────────────────────────
        String workflowIdStr = (String) body.get("workflowId");
        if (workflowIdStr == null || workflowIdStr.isBlank()) {
            throw new IllegalArgumentException("workflowId is required");
        }
        UUID workflowId = UUID.fromString(workflowIdStr);

        @SuppressWarnings("unchecked")
        Map<String, Object> input =
                body.containsKey("input") && body.get("input") instanceof Map
                        ? (Map<String, Object>) body.get("input")
                        : new HashMap<>();

        // Principal name from the authenticated API key is the default startedBy
        String startedBy = body.containsKey("startedBy")
                ? String.valueOf(body.get("startedBy"))
                : (authentication != null ? String.valueOf(authentication.getPrincipal()) : "system");

        boolean async = Boolean.TRUE.equals(body.get("async"));

        // ── Truncate idempotency key to max 64 chars (prevent oversized inserts) ──
        if (idempotencyKey != null && idempotencyKey.length() > 64) {
            idempotencyKey = idempotencyKey.substring(0, 64);
        }

        // ── Async path ────────────────────────────────────────────────────────
        if (async) {
            return startAsync(workflowId, input, startedBy, idempotencyKey);
        }

        // ── Synchronous path ──────────────────────────────────────────────────
        Execution result = executionService.startExecution(
                workflowId, input, startedBy, idempotencyKey);
        return ResponseEntity.ok(result);
    }

    // ── List ──────────────────────────────────────────────────────────────────

    /**
     * GET /api/v1/executions?page=0&size=10&status=FAILED
     */
    @GetMapping
    public ResponseEntity<Page<Execution>> list(
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false)    String status) {

        PageRequest pr = PageRequest.of(
                page, Math.min(size, 100),
                Sort.by(Sort.Direction.DESC, "startedAt"));

        Page<Execution> result;
        if (status != null && !status.isBlank()) {
            ExecutionStatus es = ExecutionStatus.valueOf(status.toUpperCase());
            result = executionRepository.findByStatus(es, pr);
        } else {
            result = executionRepository.findAll(pr);
        }
        return ResponseEntity.ok(result);
    }

    // ── Get by ID ─────────────────────────────────────────────────────────────

    /** GET /api/v1/executions/{id} */
    @GetMapping("/{id}")
    public ResponseEntity<Execution> status(@PathVariable UUID id) {
        return ResponseEntity.ok(executionService.getExecutionStatus(id));
    }

    // ── Logs ──────────────────────────────────────────────────────────────────

    /** GET /api/v1/executions/{id}/logs */
    @GetMapping("/{id}/logs")
    public ResponseEntity<List<ExecutionLog>> logs(@PathVariable UUID id) {
        return ResponseEntity.ok(
                executionLogRepository.findByExecutionIdOrderByStartedAtAsc(id));
    }

    // ── Cancel ────────────────────────────────────────────────────────────────

    /** POST /api/v1/executions/{id}/cancel */
    @PostMapping("/{id}/cancel")
    public ResponseEntity<Execution> cancel(@PathVariable UUID id) {
        return ResponseEntity.ok(executionService.cancelExecution(id));
    }

    // ── Retry ─────────────────────────────────────────────────────────────────

    /** POST /api/v1/executions/{id}/retry */
    @PostMapping("/{id}/retry")
    public ResponseEntity<Execution> retry(@PathVariable UUID id) {
        return ResponseEntity.ok(executionService.retryFailedStep(id));
    }

    // ── Approve ───────────────────────────────────────────────────────────────

    /** POST /api/v1/executions/{id}/approve  Body: { "approverEmail": "..." } */
    @PostMapping("/{id}/approve")
    public ResponseEntity<Execution> approve(
            @PathVariable UUID id,
            @RequestBody(required = false) Map<String, String> body) {
        String approverEmail = (body != null) ? body.get("approverEmail") : null;
        return ResponseEntity.ok(executionService.approveStep(id, approverEmail));
    }

    // ── Reject ────────────────────────────────────────────────────────────────

    /** POST /api/v1/executions/{id}/reject  Body: { "reason": "..." } */
    @PostMapping("/{id}/reject")
    public ResponseEntity<Execution> reject(
            @PathVariable UUID id,
            @RequestBody(required = false) Map<String, String> body) {
        String reason = (body != null) ? body.get("reason") : "Rejected";
        return ResponseEntity.ok(executionService.rejectStep(id, reason));
    }

    // ── Private: async start path ─────────────────────────────────────────────

    private ResponseEntity<?> startAsync(UUID workflowId,
                                         Map<String, Object> input,
                                         String startedBy,
                                         String idempotencyKey) {

        boolean hasKey = idempotencyKey != null && !idempotencyKey.isBlank();
        final String path = "POST /api/v1/executions/start";

        // Idempotency check for async path
        if (hasKey) {
            Optional<IdempotencyRecord> existing =
                    idempotencyService.findExisting(idempotencyKey, path);
            if (existing.isPresent()) {
                // Replay: return the cached execution (even for async, replay is sync)
                Execution cached = idempotencyService.replayOrReject(
                        existing.get(), Execution.class);
                return ResponseEntity.ok(cached);
            }
            idempotencyService.markProcessing(idempotencyKey, path);
        }

        // Validate & create initial Execution row synchronously
        Workflow workflow = workflowRepository.findById(workflowId)
                .orElseThrow(() -> new ResourceNotFoundException("Workflow", workflowId));

        if (!Boolean.TRUE.equals(workflow.getIsActive())) {
            throw new BusinessException("Workflow is not active: " + workflowId);
        }

        validatorService.validate(workflow.getInputSchema(), input);

        List<Step> allSteps =
                stepRepository.findByWorkflowIdOrderBySequenceOrderAsc(workflowId);
        if (allSteps.isEmpty()) {
            throw new BusinessException("Workflow has no steps defined: " + workflowId);
        }

        Step firstStep = (workflow.getFirstStepId() != null)
                ? stepRepository.findById(workflow.getFirstStepId()).orElse(allSteps.get(0))
                : allSteps.get(0);

        Execution execution = new Execution();
        execution.setWorkflowId(workflowId);
        execution.setWorkflowVersion(workflow.getVersion());
        execution.setStatus(ExecutionStatus.RUNNING);
        execution.setStartedBy(startedBy);
        execution.setRetryCount(0);
        execution.setCurrentStepId(firstStep.getId());
        execution.setInputData(serialize(input));
        execution = executionRepository.save(execution);

        log.info("[ASYNC] Execution id={} created for workflow={}, dispatching to thread pool",
                execution.getId(), workflowId);

        // Fire and forget — HTTP thread returns immediately
        asyncExecutionService.runAsync(execution, firstStep, input);

        // Cache idempotency record pointing to this execution
        if (hasKey) {
            idempotencyService.markCompleted(idempotencyKey, execution, 202);
        }

        Map<String, Object> accepted = new HashMap<>();
        accepted.put("executionId", execution.getId());
        accepted.put("status",      execution.getStatus());
        accepted.put("async",       true);
        accepted.put("pollUrl",     "/api/v1/executions/" + execution.getId());
        accepted.put("message",     "Execution started. Poll the pollUrl for status updates.");

        return ResponseEntity.status(HttpStatus.ACCEPTED).body(accepted);
    }

    // ── Helper ────────────────────────────────────────────────────────────────

    private String serialize(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            throw new BusinessException("Input serialization failed: " + e.getMessage());
        }
    }
}
