package com.halleyx.workflow_engine.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * ExecutionLog entity — one row per step execution.
 *
 * INDEX additions:
 *
 *   idx_exec_logs_execution_id   — GET /executions/{id}/logs (primary lookup)
 *   idx_exec_logs_step_id        — used by findTopByExecutionIdAndStepIdAndStatus…
 *   idx_exec_logs_status         — AuditLogController filter on status
 *   idx_exec_logs_step_type      — AuditLogController filter on stepType
 *   idx_exec_logs_started_at     — AuditLogController date-range filter + sort
 *
 * Composite index on (execution_id, step_id, status) supports the approval log
 * lookup: findTopByExecutionIdAndStepIdAndStatusOrderByStartedAtDesc.
 */
@Entity
@Table(
    name = "execution_logs",
    indexes = {
        @Index(name = "idx_exec_logs_execution_id",
               columnList = "execution_id"),
        @Index(name = "idx_exec_logs_step_id",
               columnList = "step_id"),
        @Index(name = "idx_exec_logs_status",
               columnList = "status"),
        @Index(name = "idx_exec_logs_step_type",
               columnList = "step_type"),
        @Index(name = "idx_exec_logs_started_at",
               columnList = "started_at"),
        // Composite: drives findTopByExecutionIdAndStepIdAndStatus…
        @Index(name = "idx_exec_logs_exec_step_status",
               columnList = "execution_id, step_id, status")
    }
)
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ExecutionLog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "execution_id")
    private UUID executionId;

    @Column(name = "step_id")
    private UUID stepId;

    @Column(name = "step_name")
    private String stepName;

    @Column(name = "step_type")
    private String stepType;

    @Column(name = "evaluated_rules", columnDefinition = "TEXT")
    private String evaluatedRules;

    @Column(name = "selected_next_step_id")
    private UUID selectedNextStepId;

    private String status;

    @Column(name = "approver_id")
    private String approverId;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "started_at")
    private LocalDateTime startedAt;

    @Column(name = "ended_at")
    private LocalDateTime endedAt;
}
