package com.halleyx.workflow_engine.security;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * ApiKey entity — one row per issued API key.
 *
 * The raw key is only ever shown once (at creation time).
 * What is stored here is the SHA-256 hex digest so that a DB breach does not
 * expose working credentials.
 *
 * Table: api_keys
 * Columns: id, key_hash (unique), description, is_active, created_at, last_used_at
 */
@Entity
@Table(
    name = "api_keys",
    indexes = @Index(name = "idx_api_keys_hash", columnList = "key_hash", unique = true)
)
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ApiKey {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /** SHA-256 hex of the raw key — what is stored and compared. */
    @Column(name = "key_hash", nullable = false, unique = true, length = 64)
    private String keyHash;

    /** Human-readable label set at creation (e.g. "CI pipeline", "frontend-prod"). */
    @Column(nullable = false)
    private String description;

    @Builder.Default
    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "last_used_at")
    private LocalDateTime lastUsedAt;

    @PrePersist
    public void prePersist() {
        createdAt = LocalDateTime.now();
    }
}
