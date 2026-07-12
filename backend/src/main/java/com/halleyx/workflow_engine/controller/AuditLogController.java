package com.halleyx.workflow_engine.controller;

import com.halleyx.workflow_engine.entity.ExecutionLog;
import com.halleyx.workflow_engine.repository.ExecutionLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * AuditLogController
 *
 * IMPROVEMENTS vs original:
 * - @DateTimeFormat(iso = ISO.DATE_TIME) used instead of manual LocalDateTime.parse(),
 *   which gives proper 400 errors on malformed dates instead of a 500 exception.
 * - @RequiredArgsConstructor replaces manual constructor.
 * - Max page size capped at 100 to prevent accidental large queries.
 */
@RestController
@RequestMapping("/audit-logs")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class AuditLogController {

    private static final int MAX_PAGE_SIZE = 100;

    private final ExecutionLogRepository executionLogRepository;

    @GetMapping
    public Page<ExecutionLog> getAllLogs(
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false)    String status,
            @RequestParam(required = false)    String stepType,
            @RequestParam(required = false)    UUID executionId,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to
    ) {
        // Cap page size to prevent heavy queries
        int safeSize = Math.min(size, MAX_PAGE_SIZE);

        return executionLogRepository.findWithFilters(
                status,
                stepType,
                executionId,
                from,
                to,
                PageRequest.of(page, safeSize)
        );
    }
}
