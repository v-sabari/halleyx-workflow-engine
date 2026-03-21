package com.halleyx.workflow_engine.service;

import com.halleyx.workflow_engine.entity.Rule;
import com.halleyx.workflow_engine.repository.RuleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RuleServiceTest {

    @Mock
    private RuleRepository ruleRepository;

    @InjectMocks
    private RuleService ruleService;

    private Rule sampleRule;
    private UUID ruleId;
    private UUID stepId;

    @BeforeEach
    void setUp() {
        ruleId = UUID.randomUUID();
        stepId = UUID.randomUUID();

        sampleRule = new Rule();
        sampleRule.setId(ruleId);
        sampleRule.setStepId(stepId);
        sampleRule.setCondition("amount > 100");
        sampleRule.setPriority(1);
        sampleRule.setNextStepId(UUID.randomUUID());
    }

    @Test
    void createRule_shouldSaveAndReturnRule() {
        when(ruleRepository.save(any(Rule.class))).thenReturn(sampleRule);

        Rule result = ruleService.createRule(sampleRule);

        assertNotNull(result);
        assertEquals(sampleRule.getCondition(), result.getCondition());
        verify(ruleRepository, times(1)).save(any(Rule.class));
    }

    @Test
    void createRule_shouldSetTimestamps() {
        when(ruleRepository.save(any(Rule.class))).thenAnswer(i -> i.getArgument(0));

        Rule result = ruleService.createRule(sampleRule);

        assertNotNull(result.getCreatedAt());
        assertNotNull(result.getUpdatedAt());
    }

    @Test
    void getRulesByStep_shouldReturnSortedByPriority() {
        Rule rule1 = new Rule(); rule1.setPriority(2);
        Rule rule2 = new Rule(); rule2.setPriority(1);
        when(ruleRepository.findByStepIdOrderByPriorityAsc(stepId))
                .thenReturn(List.of(rule2, rule1));

        List<Rule> result = ruleService.getRulesByStep(stepId);

        assertEquals(2, result.size());
        assertEquals(1, result.get(0).getPriority());
    }

    @Test
    void updateRule_shouldUpdateFieldsAndReturnUpdated() {
        Rule updatedRule = new Rule();
        updatedRule.setCondition("amount > 500");
        updatedRule.setPriority(2);
        updatedRule.setNextStepId(UUID.randomUUID());

        when(ruleRepository.findById(ruleId)).thenReturn(Optional.of(sampleRule));
        when(ruleRepository.save(any(Rule.class))).thenAnswer(i -> i.getArgument(0));

        Rule result = ruleService.updateRule(ruleId, updatedRule);

        assertEquals("amount > 500", result.getCondition());
        assertEquals(2, result.getPriority());
        assertNotNull(result.getUpdatedAt());
    }

    @Test
    void updateRule_shouldThrowWhenRuleNotFound() {
        when(ruleRepository.findById(ruleId)).thenReturn(Optional.empty());

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> ruleService.updateRule(ruleId, sampleRule));

        assertTrue(ex.getMessage().contains("Rule not found"));
    }

    @Test
    void deleteRule_shouldCallRepository() {
        doNothing().when(ruleRepository).deleteById(ruleId);

        ruleService.deleteRule(ruleId);

        verify(ruleRepository, times(1)).deleteById(ruleId);
    }
}