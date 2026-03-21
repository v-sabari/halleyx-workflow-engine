package com.halleyx.workflow_engine.service;

import com.halleyx.workflow_engine.entity.Workflow;
import com.halleyx.workflow_engine.repository.RuleRepository;
import com.halleyx.workflow_engine.repository.StepRepository;
import com.halleyx.workflow_engine.repository.WorkflowRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WorkflowServiceTest {

    @Mock private WorkflowRepository workflowRepository;
    @Mock private StepRepository     stepRepository;
    @Mock private RuleRepository     ruleRepository;

    @InjectMocks
    private WorkflowService workflowService;

    private Workflow sampleWorkflow;
    private UUID     workflowId;

    @BeforeEach
    void setUp() {
        workflowId = UUID.randomUUID();
        sampleWorkflow = new Workflow();
        sampleWorkflow.setId(workflowId);
        sampleWorkflow.setName("Expense Approval");
        sampleWorkflow.setDescription("Test workflow");
        sampleWorkflow.setVersion(1);
        sampleWorkflow.setIsActive(true);
        sampleWorkflow.setInputSchema("{}");
    }

    @Test
    void createWorkflow_shouldSetVersionOneAndActive() {
        when(workflowRepository.save(any(Workflow.class))).thenReturn(sampleWorkflow);

        Workflow result = workflowService.createWorkflow(sampleWorkflow);

        assertEquals(1, result.getVersion());
        assertTrue(result.getIsActive());
        verify(workflowRepository, times(1)).save(any(Workflow.class));
    }

    @Test
    void getWorkflowById_shouldReturnWorkflowWithSteps() {
        when(workflowRepository.findById(workflowId))
                .thenReturn(Optional.of(sampleWorkflow));
        // FIX: service calls findByWorkflowIdOrderBySequenceOrderAsc, not findByWorkflowId
        when(stepRepository.findByWorkflowIdOrderBySequenceOrderAsc(workflowId))
                .thenReturn(Collections.emptyList());

        Map<String, Object> result = workflowService.getWorkflowById(workflowId);

        assertNotNull(result);
        assertEquals(sampleWorkflow, result.get("workflow"));
        assertNotNull(result.get("steps"));
        assertEquals(0, result.get("stepCount"));
    }

    @Test
    void getWorkflowById_shouldReturnNullWhenNotFound() {
        when(workflowRepository.findById(workflowId)).thenReturn(Optional.empty());

        Map<String, Object> result = workflowService.getWorkflowById(workflowId);

        assertNull(result);
    }

    @Test
    void updateWorkflow_shouldIncrementVersion() {
        Workflow updated = new Workflow();
        updated.setName("Updated Name");
        updated.setDescription("Updated Description");
        updated.setInputSchema("{}");
        updated.setIsActive(true);

        when(workflowRepository.findById(workflowId))
                .thenReturn(Optional.of(sampleWorkflow));
        when(workflowRepository.save(any(Workflow.class)))
                .thenAnswer(i -> i.getArgument(0));

        Workflow result = workflowService.updateWorkflow(workflowId, updated);

        assertNotNull(result);
        assertEquals("Updated Name", result.getName());
        assertEquals("Updated Description", result.getDescription());
        assertEquals(2, result.getVersion());
    }

    @Test
    void updateWorkflow_shouldReturnNullWhenNotFound() {
        when(workflowRepository.findById(workflowId)).thenReturn(Optional.empty());

        Workflow result = workflowService.updateWorkflow(workflowId, sampleWorkflow);

        assertNull(result);
    }

    @Test
    void deleteWorkflow_shouldCallRepository() {
        when(stepRepository.findByWorkflowId(workflowId))
                .thenReturn(Collections.emptyList());
        doNothing().when(workflowRepository).deleteById(workflowId);

        workflowService.deleteWorkflow(workflowId);

        verify(workflowRepository, times(1)).deleteById(workflowId);
    }

    @Test
    void getAllWorkflows_withSearch_shouldCallSearchMethod() {
        Page<Workflow> mockPage = new PageImpl<>(List.of(sampleWorkflow));
        when(workflowRepository.findByNameContainingIgnoreCase(eq("Expense"), any()))
                .thenReturn(mockPage);
        // FIX: service calls findByWorkflowIdOrderBySequenceOrderAsc inside toWorkflowMap
        when(stepRepository.findByWorkflowIdOrderBySequenceOrderAsc(any()))
                .thenReturn(Collections.emptyList());

        // 3-param call — matches test contract
        Page<Map<String, Object>> result =
                workflowService.getAllWorkflows(0, 5, "Expense");

        assertNotNull(result);
        assertEquals(1, result.getTotalElements());
        verify(workflowRepository, times(1))
                .findByNameContainingIgnoreCase(eq("Expense"), any());
    }

    @Test
    void getAllWorkflows_withoutSearch_shouldCallFindAll() {
        Page<Workflow> mockPage = new PageImpl<>(List.of(sampleWorkflow));
        when(workflowRepository.findAll(any(PageRequest.class))).thenReturn(mockPage);
        // FIX: service calls findByWorkflowIdOrderBySequenceOrderAsc inside toWorkflowMap
        when(stepRepository.findByWorkflowIdOrderBySequenceOrderAsc(any()))
                .thenReturn(Collections.emptyList());

        // 3-param call — matches test contract
        Page<Map<String, Object>> result =
                workflowService.getAllWorkflows(0, 5, null);

        assertNotNull(result);
        verify(workflowRepository, times(1)).findAll(any(PageRequest.class));
    }
}