package com.halleyx.workflow_engine.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Rule entity.
 *
 * INDEX additions:
 *
 *   idx_rules_step_id              — findByStepId* queries (most common path)
 *   idx_rules_step_priority        — composite: findByStepIdOrderByPriorityAsc
 *                                    eliminates filesort on rule evaluation
 */
@Entity
@Table(
    name = "rules",
    indexes = {
        @Index(name = "idx_rules_step_id",
               columnList = "step_id"),
        @Index(name = "idx_rules_step_priority",
               columnList = "step_id, priority")
    }
)
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Rule {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "step_id", nullable = false)
    private UUID stepId;

    @NotBlank(message = "Rule condition is required")
    @Column(name = "rule_condition", columnDefinition = "TEXT", nullable = false)
    private String condition;

    @Column(name = "next_step_id")
    private UUID nextStepId;

    @NotNull(message = "Priority is required")
    @Min(value = 1, message = "Priority must be at least 1")
    @Column(nullable = false)
    private Integer priority;

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
}
