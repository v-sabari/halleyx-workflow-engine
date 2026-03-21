package com.halleyx.workflow_engine.repository;

import com.halleyx.workflow_engine.entity.Step;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.UUID;

@Repository
public interface StepRepository extends JpaRepository<Step, UUID> {
    List<Step> findByWorkflowId(UUID workflowId);
    List<Step> findByWorkflowIdOrderBySequenceOrderAsc(UUID workflowId);
    void deleteByWorkflowId(UUID workflowId);
}