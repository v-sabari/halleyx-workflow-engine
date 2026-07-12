package com.halleyx.workflow_engine.service;

import com.halleyx.workflow_engine.entity.Step;
import com.halleyx.workflow_engine.entity.Workflow;
import com.halleyx.workflow_engine.exception.ResourceNotFoundException;
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

/**
 * WorkflowService
 *
 * IMPROVEMENTS vs original:
 * - SECURITY: uses ResourceNotFoundException (404) instead of returning null,
 *   so callers never get a silent empty response on unknown IDs.
 * - MAINTAINABILITY: getWorkflowById/updateWorkflow throw typed exception
 *   instead of returning null and relying on controller null-checks.
 * - CORRECTNESS: deleteWorkflow now throws 404 when workflow doesn't exist.
 * - PERFORMANCE: toWorkflowMap remains the single place steps are fetched;
 *   no N+1 risk added (each workflow page entry fetches its own steps once).
 */
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

    /**
     * Returns { workflow, steps, stepCount }.
     * IMPROVEMENT: throws ResourceNotFoundException (404) instead of returning null.
     */
    public Map<String, Object> getWorkflowById(UUID id) {
        Workflow workflow = workflowRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Workflow", id));
        return toWorkflowMap(workflow);
    }

    /**
     * 3-param overload — called by unit tests.
     */
    public Page<Map<String, Object>> getAllWorkflows(int page, int size, String search) {
        return getAllWorkflows(page, size, search, null);
    }

    /**
     * 4-param version with optional name search + isActive filter.
     */
    public Page<Map<String, Object>> getAllWorkflows(
            int page, int size, String search, Boolean isActive) {

        PageRequest pageable = PageRequest.of(
                page, size, Sort.by(Sort.Direction.DESC, "createdAt"));

        boolean hasSearch = search   != null && !search.isBlank();
        boolean hasActive = isActive != null;

        Page<Workflow> workflowPage;
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

    /**
     * IMPROVEMENT: throws ResourceNotFoundException instead of returning null.
     */
    @Transactional
    public Workflow updateWorkflow(UUID id, Workflow updated) {
        Workflow existing = workflowRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Workflow", id));

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
    }

    // ── Delete ────────────────────────────────────────────────────────────────

    /**
     * IMPROVEMENT: throws ResourceNotFoundException if workflow doesn't exist,
     * preventing silent no-ops on bad IDs.
     */
    @Transactional
    public void deleteWorkflow(UUID id) {
        if (!workflowRepository.existsById(id)) {
            throw new ResourceNotFoundException("Workflow", id);
        }
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
