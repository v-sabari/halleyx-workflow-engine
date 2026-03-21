package com.halleyx.workflow_engine.service;

import com.halleyx.workflow_engine.entity.Step;
import com.halleyx.workflow_engine.entity.Workflow;
import com.halleyx.workflow_engine.repository.RuleRepository;
import com.halleyx.workflow_engine.repository.StepRepository;
import com.halleyx.workflow_engine.repository.WorkflowRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class WorkflowService {

    private final WorkflowRepository workflowRepository;
    private final StepRepository     stepRepository;
    private final RuleRepository     ruleRepository;

    // ── Create ────────────────────────────────────────────────────────────────

    @Transactional
    public Workflow createWorkflow(Workflow workflow) {
        workflow.setVersion(1);
        if (workflow.getIsActive() == null) workflow.setIsActive(true);
        workflow.setCreatedAt(LocalDateTime.now());
        workflow.setUpdatedAt(LocalDateTime.now());
        Workflow saved = workflowRepository.save(workflow);
        log.info("Created workflow id={} name={}", saved.getId(), saved.getName());
        return saved;
    }

    // ── Read ──────────────────────────────────────────────────────────────────

    public Map<String, Object> getWorkflowById(UUID id) {
        return workflowRepository.findById(id).map(workflow -> {
            List<Step> steps = stepRepository
                    .findByWorkflowIdOrderBySequenceOrderAsc(id);
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("workflow",  workflow);
            result.put("steps",     steps);
            result.put("stepCount", steps.size());
            return result;
        }).orElse(null);
    }

    /**
     * 3-param overload — called by tests (getAllWorkflows(page, size, search)).
     * Delegates to the 4-param version with isActive=null.
     */
    public Page<Map<String, Object>> getAllWorkflows(int page, int size, String search) {
        return getAllWorkflows(page, size, search, null);
    }

    /**
     * 4-param version with optional name search + isActive filter.
     * FIX: isActive was received by controller but completely ignored before.
     */
    public Page<Map<String, Object>> getAllWorkflows(
            int page, int size, String search, Boolean isActive) {

        PageRequest pageable = PageRequest.of(
                page, size, Sort.by(Sort.Direction.DESC, "createdAt"));

        Page<Workflow> workflowPage;
        boolean hasSearch = search   != null && !search.isBlank();
        boolean hasActive = isActive != null;

        if (hasSearch && hasActive) {
            workflowPage = workflowRepository
                    .findByNameContainingIgnoreCaseAndIsActive(search, isActive, pageable);
        } else if (hasSearch) {
            workflowPage = workflowRepository
                    .findByNameContainingIgnoreCase(search, pageable);
        } else if (hasActive) {
            workflowPage = workflowRepository
                    .findByIsActive(isActive, pageable);
        } else {
            workflowPage = workflowRepository.findAll(pageable);
        }

        return workflowPage.map(this::toWorkflowMap);
    }

    // ── Update ────────────────────────────────────────────────────────────────

    @Transactional
    public Workflow updateWorkflow(UUID id, Workflow updated) {
        return workflowRepository.findById(id).map(existing -> {
            existing.setName(updated.getName());
            existing.setDescription(updated.getDescription());
            existing.setInputSchema(updated.getInputSchema());
            if (updated.getIsActive() != null) {
                existing.setIsActive(updated.getIsActive());
            }
            existing.setVersion(existing.getVersion() + 1);
            existing.setUpdatedAt(LocalDateTime.now());
            Workflow saved = workflowRepository.save(existing);
            log.info("Updated workflow id={} new version={}", saved.getId(), saved.getVersion());
            return saved;
        }).orElse(null);
    }

    // ── Delete ────────────────────────────────────────────────────────────────

    @Transactional
    public void deleteWorkflow(UUID id) {
        List<Step> steps = stepRepository.findByWorkflowId(id);
        steps.forEach(s -> ruleRepository.deleteByStepId(s.getId()));
        stepRepository.deleteByWorkflowId(id);
        workflowRepository.deleteById(id);
        log.info("Deleted workflow id={}", id);
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private Map<String, Object> toWorkflowMap(Workflow workflow) {
        List<Step> steps = stepRepository
                .findByWorkflowIdOrderBySequenceOrderAsc(workflow.getId());
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("workflow",  workflow);
        entry.put("steps",     steps);
        entry.put("stepCount", steps.size());
        return entry;
    }
}