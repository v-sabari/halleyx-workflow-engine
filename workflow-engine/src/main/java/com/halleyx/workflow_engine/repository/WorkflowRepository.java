package com.halleyx.workflow_engine.repository;

import com.halleyx.workflow_engine.entity.Workflow;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.UUID;

@Repository
public interface WorkflowRepository extends JpaRepository<Workflow, UUID> {
    Page<Workflow> findByNameContainingIgnoreCase(String name, Pageable pageable);
    Page<Workflow> findByIsActive(Boolean isActive, Pageable pageable);
    Page<Workflow> findByNameContainingIgnoreCaseAndIsActive(
            String name, Boolean isActive, Pageable pageable);
}