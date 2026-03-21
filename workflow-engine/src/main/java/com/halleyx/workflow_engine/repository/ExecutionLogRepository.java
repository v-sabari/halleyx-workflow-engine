package com.halleyx.workflow_engine.repository;

import com.halleyx.workflow_engine.entity.ExecutionLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ExecutionLogRepository extends JpaRepository<ExecutionLog, UUID> {

    List<ExecutionLog> findByExecutionId(UUID executionId);

    List<ExecutionLog> findByExecutionIdOrderByStartedAtAsc(UUID executionId);

    Optional<ExecutionLog> findTopByExecutionIdAndStepIdAndStatusOrderByStartedAtDesc(
            UUID executionId,
            UUID stepId,
            String status
    );

    @Query("""
            SELECT l FROM ExecutionLog l
            WHERE (:status   IS NULL OR l.status   = :status)
            AND   (:stepType IS NULL OR l.stepType = :stepType)
            AND   (:executionId IS NULL OR l.executionId = :executionId)
            AND   (:from IS NULL OR l.startedAt >= :from)
            AND   (:to   IS NULL OR l.startedAt <= :to)
            ORDER BY l.startedAt DESC
            """)
    Page<ExecutionLog> findWithFilters(
            @Param("status") String status,
            @Param("stepType") String stepType,
            @Param("executionId") UUID executionId,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to,
            Pageable pageable
    );
}