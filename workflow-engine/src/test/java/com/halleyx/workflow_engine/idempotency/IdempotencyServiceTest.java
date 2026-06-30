package com.halleyx.workflow_engine.idempotency;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.halleyx.workflow_engine.entity.Execution;
import com.halleyx.workflow_engine.entity.Execution.ExecutionStatus;
import com.halleyx.workflow_engine.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class IdempotencyServiceTest {

    @Mock
    private IdempotencyRepository idempotencyRepository;

    // Use a real ObjectMapper (same as prod) — not a mock
    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule());

    private IdempotencyService idempotencyService;

    private static final String KEY  = "test-idem-key-" + UUID.randomUUID();
    private static final String PATH = "POST /api/v1/executions/start";

    @BeforeEach
    void setUp() {
        idempotencyService = new IdempotencyService(idempotencyRepository, objectMapper);
    }

    // ── findExisting ──────────────────────────────────────────────────────────

    @Test
    void findExisting_nullKey_returnsEmpty() {
        Optional<IdempotencyRecord> result =
                idempotencyService.findExisting(null, PATH);
        assertTrue(result.isEmpty());
        verifyNoInteractions(idempotencyRepository);
    }

    @Test
    void findExisting_blankKey_returnsEmpty() {
        Optional<IdempotencyRecord> result =
                idempotencyService.findExisting("  ", PATH);
        assertTrue(result.isEmpty());
        verifyNoInteractions(idempotencyRepository);
    }

    @Test
    void findExisting_noRecord_returnsEmpty() {
        when(idempotencyRepository.findByIdempotencyKey(KEY))
                .thenReturn(Optional.empty());

        Optional<IdempotencyRecord> result =
                idempotencyService.findExisting(KEY, PATH);
        assertTrue(result.isEmpty());
    }

    @Test
    void findExisting_expiredRecord_returnsEmpty() {
        IdempotencyRecord expired = IdempotencyRecord.builder()
                .idempotencyKey(KEY)
                .requestPath(PATH)
                .status(IdempotencyRecord.IdempotencyStatus.COMPLETED)
                .createdAt(LocalDateTime.now().minusDays(2))
                .expiresAt(LocalDateTime.now().minusHours(1))   // already expired
                .build();

        when(idempotencyRepository.findByIdempotencyKey(KEY))
                .thenReturn(Optional.of(expired));

        Optional<IdempotencyRecord> result =
                idempotencyService.findExisting(KEY, PATH);
        assertTrue(result.isEmpty(),
                "Expired records must be treated as non-existent");
    }

    @Test
    void findExisting_wrongPath_throwsBusinessException() {
        IdempotencyRecord record = IdempotencyRecord.builder()
                .idempotencyKey(KEY)
                .requestPath("POST /api/v1/other")
                .status(IdempotencyRecord.IdempotencyStatus.COMPLETED)
                .createdAt(LocalDateTime.now())
                .expiresAt(LocalDateTime.now().plusHours(23))
                .build();

        when(idempotencyRepository.findByIdempotencyKey(KEY))
                .thenReturn(Optional.of(record));

        assertThrows(BusinessException.class,
                () -> idempotencyService.findExisting(KEY, PATH),
                "Reuse on a different path must throw BusinessException");
    }

    @Test
    void findExisting_validRecord_returnsIt() {
        IdempotencyRecord record = IdempotencyRecord.builder()
                .idempotencyKey(KEY)
                .requestPath(PATH)
                .status(IdempotencyRecord.IdempotencyStatus.COMPLETED)
                .createdAt(LocalDateTime.now())
                .expiresAt(LocalDateTime.now().plusHours(23))
                .build();

        when(idempotencyRepository.findByIdempotencyKey(KEY))
                .thenReturn(Optional.of(record));

        Optional<IdempotencyRecord> result =
                idempotencyService.findExisting(KEY, PATH);
        assertTrue(result.isPresent());
    }

    // ── replayOrReject ────────────────────────────────────────────────────────

    @Test
    void replayOrReject_processingStatus_throwsBusinessException() {
        IdempotencyRecord processing = IdempotencyRecord.builder()
                .idempotencyKey(KEY)
                .requestPath(PATH)
                .status(IdempotencyRecord.IdempotencyStatus.PROCESSING)
                .cachedResponse(null)
                .build();

        assertThrows(BusinessException.class,
                () -> idempotencyService.replayOrReject(processing, Execution.class),
                "PROCESSING status must throw BusinessException (concurrent duplicate)");
    }

    @Test
    void replayOrReject_completedStatus_returnsCachedExecution() throws Exception {
        // Build a real Execution and serialise it as if markCompleted had stored it
        Execution original = new Execution();
        original.setId(UUID.randomUUID());
        original.setWorkflowId(UUID.randomUUID());
        original.setStatus(ExecutionStatus.COMPLETED);
        original.setRetryCount(0);
        original.setWorkflowVersion(1);

        String json = objectMapper.writeValueAsString(original);

        IdempotencyRecord completed = IdempotencyRecord.builder()
                .idempotencyKey(KEY)
                .requestPath(PATH)
                .status(IdempotencyRecord.IdempotencyStatus.COMPLETED)
                .cachedResponse(json)
                .responseStatus(200)
                .build();

        Execution replayed =
                idempotencyService.replayOrReject(completed, Execution.class);

        assertNotNull(replayed);
        assertEquals(original.getId(), replayed.getId());
        assertEquals(ExecutionStatus.COMPLETED, replayed.getStatus());
    }

    // ── markCompleted ─────────────────────────────────────────────────────────

    @Test
    void markCompleted_shouldSetStatusAndCacheResponse() throws Exception {
        Execution execution = new Execution();
        execution.setId(UUID.randomUUID());
        execution.setStatus(ExecutionStatus.RUNNING);
        execution.setRetryCount(0);
        execution.setWorkflowVersion(1);

        IdempotencyRecord record = IdempotencyRecord.builder()
                .idempotencyKey(KEY)
                .requestPath(PATH)
                .status(IdempotencyRecord.IdempotencyStatus.PROCESSING)
                .build();

        when(idempotencyRepository.findByIdempotencyKey(KEY))
                .thenReturn(Optional.of(record));
        when(idempotencyRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        idempotencyService.markCompleted(KEY, execution, 200);

        verify(idempotencyRepository).save(argThat(r ->
                r.getStatus() == IdempotencyRecord.IdempotencyStatus.COMPLETED
                && r.getResponseStatus() == 200
                && r.getCachedResponse() != null
                && r.getCachedResponse().contains(execution.getId().toString())
        ));
    }

    @Test
    void markCompleted_keyNotFound_noOp() {
        when(idempotencyRepository.findByIdempotencyKey(KEY))
                .thenReturn(Optional.empty());

        // Must not throw even when record is missing
        assertDoesNotThrow(() ->
                idempotencyService.markCompleted(KEY, new Object(), 200));

        verify(idempotencyRepository, never()).save(any());
    }
}
