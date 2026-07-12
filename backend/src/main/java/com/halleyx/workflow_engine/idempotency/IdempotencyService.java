package com.halleyx.workflow_engine.idempotency;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.halleyx.workflow_engine.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * IdempotencyService
 *
 * Used by ExecutionService.startExecution() to guarantee that submitting
 * the same idempotency key twice never creates two Execution rows.
 *
 * Typical call pattern in ExecutionService:
 *
 *   // 1. Before doing any work:
 *   Optional<IdempotencyRecord> existing =
 *       idempotencyService.findExisting(idempotencyKey, requestPath);
 *   if (existing.isPresent()) {
 *       return idempotencyService.replayOrReject(existing.get());
 *   }
 *
 *   // 2. Mark in-flight BEFORE the real work:
 *   idempotencyService.markProcessing(idempotencyKey, requestPath);
 *
 *   // 3. Do the real work (save execution, run steps …)
 *   Execution result = … ;
 *
 *   // 4. Cache the result for future replays:
 *   idempotencyService.markCompleted(idempotencyKey, result, 200);
 *   return result;
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class IdempotencyService {

    private final IdempotencyRepository idempotencyRepository;
    private final ObjectMapper          objectMapper;

    // ── Step 1 — lookup ───────────────────────────────────────────────────────

    public Optional<IdempotencyRecord> findExisting(String key, String requestPath) {
        if (key == null || key.isBlank()) return Optional.empty();
        return idempotencyRepository.findByIdempotencyKey(key)
                .filter(r -> r.getExpiresAt().isAfter(java.time.LocalDateTime.now()))
                .map(r -> {
                    // Reject reuse on a different path
                    if (!r.getRequestPath().equals(requestPath)) {
                        throw new BusinessException(
                            "Idempotency key '" + key + "' was previously used for "
                            + r.getRequestPath() + ", not " + requestPath);
                    }
                    return r;
                });
    }

    // ── Step 2 — mark processing ──────────────────────────────────────────────

    /**
     * Inserts a PROCESSING record.
     *
     * Uses REQUIRES_NEW so this commit is immediately visible to concurrent
     * threads — they will find the PROCESSING record and return 409 instead
     * of racing to create a second Execution.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markProcessing(String key, String requestPath) {
        IdempotencyRecord record = IdempotencyRecord.builder()
                .idempotencyKey(key)
                .requestPath(requestPath)
                .status(IdempotencyRecord.IdempotencyStatus.PROCESSING)
                .build();
        idempotencyRepository.save(record);
        log.debug("Idempotency key '{}' marked PROCESSING for {}", key, requestPath);
    }

    // ── Step 3 — replay or reject ─────────────────────────────────────────────

    /**
     * Called when {@link #findExisting} returned a record.
     *
     * @param record  the existing {@link IdempotencyRecord}
     * @param type    the Java type to deserialise the cached body into
     * @return the cached response body deserialised to {@code type}
     * @throws BusinessException if the previous request is still PROCESSING
     */
    public <T> T replayOrReject(IdempotencyRecord record, Class<T> type) {
        if (record.getStatus() == IdempotencyRecord.IdempotencyStatus.PROCESSING) {
            throw new BusinessException(
                "A request with this idempotency key is already being processed. " +
                "Wait for it to complete or use a different key.");
        }
        // COMPLETED — replay
        try {
            T result = objectMapper.readValue(record.getCachedResponse(), type);
            log.info("Replaying idempotent response for key='{}'", record.getIdempotencyKey());
            return result;
        } catch (JsonProcessingException e) {
            throw new BusinessException("Failed to replay cached response: " + e.getMessage());
        }
    }

    // ── Step 4 — mark completed ───────────────────────────────────────────────

    /**
     * Updates the record to COMPLETED and stores the serialised response body.
     * Must be called after the real work succeeds, inside the same transaction.
     */
    @Transactional
    public void markCompleted(String key, Object responseBody, int httpStatus) {
        idempotencyRepository.findByIdempotencyKey(key).ifPresent(record -> {
            try {
                record.setCachedResponse(objectMapper.writeValueAsString(responseBody));
                record.setResponseStatus(httpStatus);
                record.setStatus(IdempotencyRecord.IdempotencyStatus.COMPLETED);
                idempotencyRepository.save(record);
                log.debug("Idempotency key '{}' marked COMPLETED", key);
            } catch (JsonProcessingException e) {
                // Non-fatal: log and continue; the response is still returned
                // to the caller, but future duplicates won't get a clean replay.
                log.error("Could not cache idempotency response for key='{}': {}",
                        key, e.getMessage());
            }
        });
    }
}
