package com.halleyx.workflow_engine.repository;

import com.halleyx.workflow_engine.entity.Execution;
import com.halleyx.workflow_engine.entity.Execution.ExecutionStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.UUID;

@Repository
public interface ExecutionRepository extends JpaRepository<Execution, UUID> {

    List<Execution> findByWorkflowId(UUID workflowId);

    /** Original — returns all executions with a given status (no pagination). */
    List<Execution> findByStatus(ExecutionStatus status);

    /**
     * B2 / F2 FIX: paginated overload required by
     * ExecutionController.list() — the Audit Log page status filter.
     * Spring Data JPA automatically generates the query from the method name;
     * no @Query annotation needed.
     */
    Page<Execution> findByStatus(ExecutionStatus status, Pageable pageable);
}