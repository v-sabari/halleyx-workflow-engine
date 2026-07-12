package com.halleyx.workflow_engine.idempotency;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * IdempotencyRecord — one row per unique idempotency key seen on a mutating
 * request.
 *
 * Lifecycle:
 *  1. Request arrives with header {@code Idempotency-Key: <uuid>}.
 *  2. We look up the key. If found and COMPLETED → replay cachedResponse.
 *  3. If found and PROCESSING → return 409 (concurrent duplicate).
 *  4. If not found → insert with status=PROCESSING, run the handler, update
 *     to status=COMPLETED with the serialised response body.
 *
 * TTL: rows are valid for 24 h. A scheduled task (IdempotencyCleanupTask) purges
 * expired rows nightly.
 *
 * Table: idempotency_records
 */
@Entity
@Table(
    name = "idempotency_records",
    indexes = @Index(name = "idx_idempotency_key", columnList = "idempotency_key", unique = true)
)
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class IdempotencyRecord {

    /** The caller-supplied idempotency key (UUID string). */
    @Id
    @Column(name = "idempotency_key", nullable = false, length = 64)
    private String idempotencyKey;

    /**
     * The route this key was first used on (e.g. "POST /api/v1/executions/start").
     * We reject reuse of the same key on a different endpoint.
     */
    @Column(name = "request_path", nullable = false)
    private String requestPath;

    /** Serialised JSON of the response body cached after first execution. */
    @Column(name = "cached_response", columnDefinition = "LONGTEXT")
    private String cachedResponse;

    /** HTTP status code of the cached response (e.g. 200). */
    @Column(name = "response_status")
    private Integer responseStatus;

    /**
     * PROCESSING  — request is in-flight (used to detect concurrent duplicates).
     * COMPLETED   — response has been cached; replay on duplicate.
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private IdempotencyStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @PrePersist
    public void prePersist() {
        createdAt = LocalDateTime.now();
        expiresAt = createdAt.plusHours(24);
    }

    public enum IdempotencyStatus { PROCESSING, COMPLETED }
}
