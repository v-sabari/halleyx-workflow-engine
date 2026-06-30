package com.halleyx.workflow_engine.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Notification entity.
 *
 * INDEX additions:
 *
 *   idx_notifications_execution_id — future: "all notifications for execution X"
 *   idx_notifications_is_read      — findByIsReadFalse… (unread inbox query)
 *   idx_notifications_created_at   — ORDER BY created_at DESC on list endpoints
 */
@Entity
@Table(
    name = "notifications",
    indexes = {
        @Index(name = "idx_notifications_execution_id", columnList = "execution_id"),
        @Index(name = "idx_notifications_is_read",      columnList = "is_read"),
        @Index(name = "idx_notifications_created_at",   columnList = "created_at")
    }
)
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "execution_id")
    private UUID executionId;

    @Column(name = "step_name")
    private String stepName;

    @Column(columnDefinition = "TEXT")
    private String message;

    private String channel;

    @Builder.Default
    @Column(name = "is_read", nullable = false)
    private Boolean isRead = false;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        createdAt = LocalDateTime.now();
        if (isRead == null) isRead = false;
    }
}
