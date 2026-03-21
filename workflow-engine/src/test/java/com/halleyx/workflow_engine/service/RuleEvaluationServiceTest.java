package com.halleyx.workflow_engine.service;

import com.halleyx.workflow_engine.entity.Rule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class RuleEvaluationServiceTest {

    private RuleEvaluationService ruleEvaluationService;

    @BeforeEach
    void setUp() {
        ruleEvaluationService = new RuleEvaluationService();
    }

    private Rule makeRule(String condition, int priority) {
        Rule rule = new Rule();
        rule.setId(UUID.randomUUID());
        rule.setCondition(condition);
        rule.setPriority(priority);
        rule.setNextStepId(UUID.randomUUID());
        return rule;
    }

    @Test
    void evaluate_equalsOperator_shouldMatchCorrectly() {
        Rule rule = makeRule("country == 'US'", 1);
        Map<String, Object> input = Map.of("country", "US");

        Rule result = ruleEvaluationService.evaluateRules(List.of(rule), input);

        assertNotNull(result);
        assertEquals(rule.getId(), result.getId());
    }

    @Test
    void evaluate_equalsOperator_shouldNotMatchWrongValue() {
        Rule rule = makeRule("country == 'US'", 1);
        Map<String, Object> input = Map.of("country", "UK");

        Rule result = ruleEvaluationService.evaluateRules(List.of(rule), input);

        assertNull(result);
    }

    @Test
    void evaluate_notEqualsOperator_shouldMatch() {
        Rule rule = makeRule("country != 'US'", 1);
        Map<String, Object> input = Map.of("country", "UK");

        Rule result = ruleEvaluationService.evaluateRules(List.of(rule), input);

        assertNotNull(result);
    }

    @Test
    void evaluate_greaterThan_shouldMatch() {
        Rule rule = makeRule("amount > 100", 1);
        Map<String, Object> input = Map.of("amount", 150);

        Rule result = ruleEvaluationService.evaluateRules(List.of(rule), input);

        assertNotNull(result);
    }

    @Test
    void evaluate_greaterThan_shouldNotMatchEqual() {
        Rule rule = makeRule("amount > 100", 1);
        Map<String, Object> input = Map.of("amount", 100);

        Rule result = ruleEvaluationService.evaluateRules(List.of(rule), input);

        assertNull(result);
    }

    @Test
    void evaluate_greaterThanOrEqual_shouldMatchEqual() {
        Rule rule = makeRule("amount >= 100", 1);
        Map<String, Object> input = Map.of("amount", 100);

        Rule result = ruleEvaluationService.evaluateRules(List.of(rule), input);

        assertNotNull(result);
    }

    @Test
    void evaluate_lessThan_shouldMatch() {
        Rule rule = makeRule("amount < 100", 1);
        Map<String, Object> input = Map.of("amount", 50);

        Rule result = ruleEvaluationService.evaluateRules(List.of(rule), input);

        assertNotNull(result);
    }

    @Test
    void evaluate_lessThanOrEqual_shouldMatchEqual() {
        Rule rule = makeRule("amount <= 100", 1);
        Map<String, Object> input = Map.of("amount", 100);

        Rule result = ruleEvaluationService.evaluateRules(List.of(rule), input);

        assertNotNull(result);
    }

    @Test
    void evaluate_andOperator_shouldMatchWhenBothTrue() {
        Rule rule = makeRule("amount > 100 && country == 'US'", 1);
        Map<String, Object> input = Map.of("amount", 150, "country", "US");

        Rule result = ruleEvaluationService.evaluateRules(List.of(rule), input);

        assertNotNull(result);
    }

    @Test
    void evaluate_andOperator_shouldNotMatchWhenOneFalse() {
        Rule rule = makeRule("amount > 100 && country == 'US'", 1);
        Map<String, Object> input = Map.of("amount", 50, "country", "US");

        Rule result = ruleEvaluationService.evaluateRules(List.of(rule), input);

        assertNull(result);
    }

    @Test
    void evaluate_orOperator_shouldMatchWhenOneTrue() {
        Rule rule = makeRule("amount > 100 || country == 'US'", 1);
        Map<String, Object> input = Map.of("amount", 50, "country", "US");

        Rule result = ruleEvaluationService.evaluateRules(List.of(rule), input);

        assertNotNull(result);
    }

    @Test
    void evaluate_containsFunction_shouldMatch() {
        Rule rule = makeRule("contains(name, 'John')", 1);
        Map<String, Object> input = Map.of("name", "John Doe");

        Rule result = ruleEvaluationService.evaluateRules(List.of(rule), input);

        assertNotNull(result);
    }

    @Test
    void evaluate_startsWithFunction_shouldMatch() {
        Rule rule = makeRule("startsWith(name, 'John')", 1);
        Map<String, Object> input = Map.of("name", "John Doe");

        Rule result = ruleEvaluationService.evaluateRules(List.of(rule), input);

        assertNotNull(result);
    }

    @Test
    void evaluate_endsWithFunction_shouldMatch() {
        Rule rule = makeRule("endsWith(name, 'Doe')", 1);
        Map<String, Object> input = Map.of("name", "John Doe");

        Rule result = ruleEvaluationService.evaluateRules(List.of(rule), input);

        assertNotNull(result);
    }

    @Test
    void evaluate_defaultRule_shouldBeUsedAsFallback() {
        Rule specificRule = makeRule("amount > 1000", 1);
        Rule defaultRule  = makeRule("DEFAULT", 2);
        Map<String, Object> input = Map.of("amount", 50);

        Rule result = ruleEvaluationService.evaluateRules(
                List.of(specificRule, defaultRule), input);

        assertNotNull(result);
        assertEquals(defaultRule.getId(), result.getId());
    }

    @Test
    void evaluate_priorityOrder_shouldPickHigherPriorityFirst() {
        Rule lowPriority  = makeRule("amount > 50",  2);
        Rule highPriority = makeRule("amount > 100", 1);
        Map<String, Object> input = Map.of("amount", 150);

        Rule result = ruleEvaluationService.evaluateRules(
                List.of(lowPriority, highPriority), input);

        assertNotNull(result);
        assertEquals(highPriority.getId(), result.getId());
    }

    @Test
    void evaluate_emptyRules_shouldReturnNull() {
        Map<String, Object> input = Map.of("amount", 100);

        Rule result = ruleEvaluationService.evaluateRules(List.of(), input);

        assertNull(result);
    }

    @Test
    void evaluate_complexCondition_expenseApprovalScenario() {
        Rule rule1      = makeRule("amount > 100 && country == 'US' && priority == 'High'", 1);
        Rule rule2      = makeRule("amount <= 100", 2);
        Rule defaultRule = makeRule("DEFAULT", 4);

        Map<String, Object> input = Map.of(
                "amount",   150,
                "country",  "US",
                "priority", "High"
        );

        Rule result = ruleEvaluationService.evaluateRules(
                List.of(rule1, rule2, defaultRule), input);

        assertNotNull(result);
        assertEquals(rule1.getId(), result.getId());
    }
}