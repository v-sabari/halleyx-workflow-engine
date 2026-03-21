package com.halleyx.workflow_engine.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class InputSchemaValidatorServiceTest {

    private InputSchemaValidatorService validatorService;

    @BeforeEach
    void setUp() {
        validatorService = new InputSchemaValidatorService();
    }

    @Test
    void validate_shouldPassWithValidInput() {
        String schema = "{\"amount\":{\"type\":\"number\",\"required\":true}," +
                "\"country\":{\"type\":\"string\",\"required\":true}}";
        Map<String, Object> input = Map.of("amount", 150, "country", "US");

        assertDoesNotThrow(() -> validatorService.validate(schema, input));
    }

    @Test
    void validate_shouldFailWhenRequiredFieldMissing() {
        String schema = "{\"amount\":{\"type\":\"number\",\"required\":true}}";
        Map<String, Object> input = new HashMap<>();

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> validatorService.validate(schema, input));

        assertTrue(ex.getMessage().contains("amount"));
    }

    @Test
    void validate_shouldFailWhenWrongType_numberExpected() {
        String schema = "{\"amount\":{\"type\":\"number\",\"required\":true}}";
        Map<String, Object> input = Map.of("amount", "not-a-number");

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> validatorService.validate(schema, input));

        assertTrue(ex.getMessage().contains("number"));
    }

    @Test
    void validate_shouldFailWhenWrongType_stringExpected() {
        String schema = "{\"country\":{\"type\":\"string\",\"required\":true}}";
        Map<String, Object> input = Map.of("country", 123);

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> validatorService.validate(schema, input));

        assertTrue(ex.getMessage().contains("string"));
    }

    @Test
    void validate_shouldFailWhenWrongType_booleanExpected() {
        String schema = "{\"isActive\":{\"type\":\"boolean\",\"required\":true}}";
        Map<String, Object> input = Map.of("isActive", "yes");

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> validatorService.validate(schema, input));

        assertTrue(ex.getMessage().contains("boolean"));
    }

    @Test
    void validate_shouldPassWhenOptionalFieldMissing() {
        String schema = "{\"notes\":{\"type\":\"string\",\"required\":false}}";
        Map<String, Object> input = new HashMap<>();

        assertDoesNotThrow(() -> validatorService.validate(schema, input));
    }

    @Test
    void validate_shouldPassWithNullSchema() {
        assertDoesNotThrow(() -> validatorService.validate(null, Map.of()));
    }

    @Test
    void validate_shouldPassWithEmptySchema() {
        assertDoesNotThrow(() -> validatorService.validate("{}", Map.of()));
    }

    @Test
    void validate_shouldPassFullExpenseApprovalScenario() {
        String schema = "{" +
                "\"amount\":{\"type\":\"number\",\"required\":true}," +
                "\"country\":{\"type\":\"string\",\"required\":true}," +
                "\"priority\":{\"type\":\"string\",\"required\":true}" +
                "}";
        Map<String, Object> input = Map.of(
                "amount",   150,
                "country",  "US",
                "priority", "High"
        );

        assertDoesNotThrow(() -> validatorService.validate(schema, input));
    }
}