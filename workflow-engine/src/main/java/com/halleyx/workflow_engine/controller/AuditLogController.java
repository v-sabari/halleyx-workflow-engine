package com.halleyx.workflow_engine.controller;

import com.halleyx.workflow_engine.entity.ExecutionLog;
import com.halleyx.workflow_engine.repository.ExecutionLogRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.UUID;

@RestController
@RequestMapping("/audit-logs")
@CrossOrigin(origins = "http://localhost:5173")
public class AuditLogController {

    private final ExecutionLogRepository executionLogRepository;

    public AuditLogController(ExecutionLogRepository executionLogRepository) {
        this.executionLogRepository = executionLogRepository;
    }

    @GetMapping
    public Page<ExecutionLog> getAllLogs(
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false)    String status,
            @RequestParam(required = false)    String stepType,
            @RequestParam(required = false)    UUID executionId,
            @RequestParam(required = false)    String from,
            @RequestParam(required = false)    String to
    ) {
        LocalDateTime fromDate = (from != null && !from.isBlank())
                ? LocalDateTime.parse(from) : null;
        LocalDateTime toDate   = (to   != null && !to.isBlank())
                ? LocalDateTime.parse(to)   : null;

        return executionLogRepository.findWithFilters(
                status,
                stepType,
                executionId,
                fromDate,
                toDate,
                PageRequest.of(page, size)
        );
    }
}