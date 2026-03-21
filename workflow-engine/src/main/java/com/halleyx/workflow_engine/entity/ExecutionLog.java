package com.halleyx.workflow_engine.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * ExecutionLog entity — one row per step execution.
 *
 * Changes vs original:
 * B4 FIX — added `approverId` field.
 *   Spec step log example includes "approver_id": "user-001".
 *   This field is populated by ExecutionService.approveStep()
 *   via the overloaded writeExecutionLog(..., approverId) call.
 */
@Entity
@Table(name = "execution_logs")
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

    /**
     * B4 FIX: approver_id — set when an APPROVAL step is approved.
     * Spec step log example: "approver_id": "user-001"
     * In practice this holds the approverEmail passed to approveStep().
     */
    @Column(name = "approver_id")
    private String approverId;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "started_at")
    private LocalDateTime startedAt;

    @Column(name = "ended_at")
    private LocalDateTime endedAt;
}