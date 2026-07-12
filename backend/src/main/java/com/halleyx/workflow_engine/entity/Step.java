package com.halleyx.workflow_engine.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Step entity.
 *
 * INDEX additions:
 *
 *   idx_steps_workflow_id          — findByWorkflowId* queries (most common)
 *   idx_steps_workflow_seq         — composite: findByWorkflowIdOrderBySequenceOrderAsc
 *                                    (the full order-by index avoids a filesort)
 *   idx_steps_step_type            — future: filter steps by type
 */
@Entity
@Table(
    name = "steps",
    indexes = {
        @Index(name = "idx_steps_workflow_id",
               columnList = "workflow_id"),
        @Index(name = "idx_steps_workflow_seq",
               columnList = "workflow_id, sequence_order")
    }
)
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Step {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "workflow_id", nullable = false)
    private UUID workflowId;

    @NotBlank(message = "Step name is required")
    @Column(name = "step_name", nullable = false)
    private String stepName;

    @NotNull(message = "Step type is required")
    @Enumerated(EnumType.STRING)
    @Column(name = "step_type", nullable = false)
    private StepType stepType;

    @NotNull(message = "Sequence order is required")
    @Column(name = "sequence_order", nullable = false)
    private Integer sequenceOrder;

    @Column(columnDefinition = "TEXT")
    private String configuration;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    public void prePersist() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    public void preUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public enum StepType {
        TASK, APPROVAL, NOTIFICATION
    }
}
