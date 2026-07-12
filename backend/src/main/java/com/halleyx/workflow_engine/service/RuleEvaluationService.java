package com.halleyx.workflow_engine.service;

import com.halleyx.workflow_engine.entity.Rule;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Evaluates a prioritised list of Rule conditions against a runtime input map
 * and returns the first matching Rule.
 *
 * FIX: Null-safety — missing input keys return false instead of NPE.
 * FIX: OR split before AND for correct operator precedence.
 * FIX: DEFAULT collected separately, returned only when no other rule matches.
 * FIX: Regex patterns compiled once as static constants (performance).
 */
@Service
@Slf4j
public class RuleEvaluationService {

    private static final Pattern CONTAINS_PATTERN = Pattern.compile(
            "contains\\(\\s*(\\w+)\\s*,\\s*'([^']*)'\\s*\\)",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern STARTS_WITH_PATTERN = Pattern.compile(
            "startsWith\\(\\s*(\\w+)\\s*,\\s*'([^']*)'\\s*\\)",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern ENDS_WITH_PATTERN = Pattern.compile(
            "endsWith\\(\\s*(\\w+)\\s*,\\s*'([^']*)'\\s*\\)",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern COMPARISON_PATTERN = Pattern.compile(
            "(\\w+)\\s*(==|!=|>=|<=|>|<)\\s*'?([^'\\s&|]+)'?");

    // ── Public API ────────────────────────────────────────────────────────────

    public Rule evaluateRules(List<Rule> rules, Map<String, Object> input) {
        if (rules == null || rules.isEmpty()) return null;

        List<Rule> sorted = rules.stream()
                .sorted(Comparator.comparingInt(Rule::getPriority))
                .toList();

        Rule defaultRule = null;

        for (Rule rule : sorted) {
            String condition = rule.getCondition();
            if (condition == null || condition.isBlank()) continue;
            condition = condition.trim();

            if ("DEFAULT".equalsIgnoreCase(condition)) {
                if (defaultRule == null) defaultRule = rule;
                continue;
            }

            try {
                if (evaluateCondition(condition, input)) {
                    log.debug("Rule matched: [{}]", condition);
                    return rule;
                }
            } catch (Exception e) {
                log.warn("Rule evaluation error for condition '{}': {}", condition, e.getMessage());
            }
        }

        if (defaultRule != null) {
            log.debug("No specific rule matched — using DEFAULT rule id={}", defaultRule.getId());
        }
        return defaultRule;
    }

    // ── Condition evaluation ──────────────────────────────────────────────────

    private boolean evaluateCondition(String condition, Map<String, Object> input) {
        // OR has lowest precedence — split first
        String[] orParts = condition.split("\\|\\|");
        if (orParts.length > 1) {
            for (String part : orParts) {
                if (evaluateCondition(part.trim(), input)) return true;
            }
            return false;
        }

        // AND
        String[] andParts = condition.split("&&");
        if (andParts.length > 1) {
            for (String part : andParts) {
                if (!evaluateCondition(part.trim(), input)) return false;
            }
            return true;
        }

        return evaluateSimpleCondition(condition.trim(), input);
    }

    private boolean evaluateSimpleCondition(String condition, Map<String, Object> input) {

        // contains(field, 'value')
        Matcher m = CONTAINS_PATTERN.matcher(condition);
        if (m.matches()) {
            Object val = input.get(m.group(1));
            if (val == null) return false;                  // FIX: null-safe
            return (val instanceof String s) && s.contains(m.group(2));
        }

        // startsWith(field, 'value')
        m = STARTS_WITH_PATTERN.matcher(condition);
        if (m.matches()) {
            Object val = input.get(m.group(1));
            if (val == null) return false;
            return (val instanceof String s) && s.startsWith(m.group(2));
        }

        // endsWith(field, 'value')
        m = ENDS_WITH_PATTERN.matcher(condition);
        if (m.matches()) {
            Object val = input.get(m.group(1));
            if (val == null) return false;
            return (val instanceof String s) && s.endsWith(m.group(2));
        }

        // field OPERATOR value
        m = COMPARISON_PATTERN.matcher(condition);
        if (m.matches()) {
            String field       = m.group(1);
            String operator    = m.group(2);
            String rawExpected = m.group(3).replace("'", "").trim();
            Object inputVal    = input.get(field);
            if (inputVal == null) {                          // FIX: null-safe
                log.debug("Field '{}' not found in input — condition '{}' → false", field, condition);
                return false;
            }
            return compare(inputVal, operator, rawExpected);
        }

        log.warn("Unrecognised condition syntax: '{}'", condition);
        return false;
    }

    private boolean compare(Object inputVal, String operator, String rawExpected) {

        if (inputVal instanceof Number number) {
            double actual;
            double expected;
            try {
                actual   = number.doubleValue();
                expected = Double.parseDouble(rawExpected);
            } catch (NumberFormatException e) {
                log.warn("Cannot parse expected numeric value '{}'", rawExpected);
                return false;
            }
            return switch (operator) {
                case "==" -> actual == expected;
                case "!=" -> actual != expected;
                case ">"  -> actual >  expected;
                case ">=" -> actual >= expected;
                case "<"  -> actual <  expected;
                case "<=" -> actual <= expected;
                default   -> { log.warn("Unknown operator '{}' for numeric", operator); yield false; }
            };
        }

        if (inputVal instanceof String actual) {
            return switch (operator) {
                case "==" ->  actual.equals(rawExpected);
                case "!=" -> !actual.equals(rawExpected);
                default   -> { log.warn("Operator '{}' not supported for strings", operator); yield false; }
            };
        }

        if (inputVal instanceof Boolean actual) {
            boolean expected = Boolean.parseBoolean(rawExpected);
            return switch (operator) {
                case "==" -> actual == expected;
                case "!=" -> actual != expected;
                default   -> { log.warn("Operator '{}' not supported for booleans", operator); yield false; }
            };
        }

        log.warn("Unsupported input value type: {}", inputVal.getClass().getSimpleName());
        return false;
    }
}