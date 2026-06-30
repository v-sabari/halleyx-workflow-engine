package com.halleyx.workflow_engine.idempotency;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * IdempotencyCleanupTask
 *
 * Runs every day at 02:00 (server local time) and hard-deletes
 * IdempotencyRecord rows whose {@code expires_at} is in the past.
 *
 * This keeps the {@code idempotency_records} table small even under
 * sustained load (100 req/min × 60 min × 24 h = 144 000 rows/day max,
 * all purged the next morning).
 *
 * @EnableScheduling is declared on {@link com.halleyx.workflow_engine.config.AsyncConfig},
 * which is sufficient — Spring scans all @Configuration classes in the context
 * regardless of which one carries the enabling annotation.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class IdempotencyCleanupTask {

    private final IdempotencyRepository idempotencyRepository;

    /** Cron: every day at 02:00 AM. */
    @Scheduled(cron = "0 0 2 * * *")
    public void purgeExpiredRecords() {
        int deleted = idempotencyRepository.deleteExpiredRecords(LocalDateTime.now());
        log.info("IdempotencyCleanupTask: purged {} expired record(s)", deleted);
    }
}
