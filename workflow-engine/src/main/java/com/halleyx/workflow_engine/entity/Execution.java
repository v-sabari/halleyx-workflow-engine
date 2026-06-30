package com.halleyx.workflow_engine.entity;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Execution entity.
 *
 * INDEX additions (all queries that filter on these columns are now O(log n)):
 *
 *   idx_executions_workflow_id   — GET /executions?workflowId=... and
 *                                  WorkflowService.toWorkflowMap (N+1 guard)
 *   idx_executions_status        — GET /executions?status=FAILED (frequent filter)
 *   idx_executions_started_at    — default sort column on all list queries
 *   idx_executions_started_by    — future audit queries "all executions by user X"
 *
 * No field renames. @Table updated to add the indexes[] attribute.
 * spring.jpa.hibernate.ddl-auto=update will emit the ALTER TABLE … ADD INDEX
 * statements on next startup if the indexes do not yet exist.
 */
@Entity
@Table(
    name = "executions",
    indexes = {
        @Index(name = "idx_executions_workflow_id", columnList = "workflow_id"),
        @Index(name = "idx_executions_status",      columnList = "status"),
        @Index(name = "idx_executions_started_at",  columnList = "started_at"),
        @Index(name = "idx_executions_started_by",  columnList = "started_by")
    }
)
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Execution {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "workflow_id", nullable = false)
    private UUID workflowId;

    @Column(name = "workflow_version", nullable = false)
    private Integer workflowVersion;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ExecutionStatus status;

    @Column(name = "input_data", columnDefinition = "TEXT")
    private String inputData;

    @Column(name = "current_step_id")
    private UUID currentStepId;

    @Builder.Default
    @Column(name = "retry_count", nullable = false)
    @JsonProperty("retries")
    private Integer retryCount = 0;

    @Column(name = "started_by")
    private String startedBy;

    @Column(name = "started_at")
    private LocalDateTime startedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    public void prePersist() {
        startedAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (retryCount == null) retryCount = 0;
        if (status == null) status = ExecutionStatus.PENDING;
    }

    @PreUpdate
    public void preUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public enum ExecutionStatus {
        PENDING, RUNNING, WAITING_FOR_APPROVAL, FAILED, COMPLETED, CANCELLED
    }
}
