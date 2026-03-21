package com.halleyx.workflow_engine.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * Validates a runtime input map against the workflow's declared JSON input schema.
 *
 * Schema format (stored as JSON string in Workflow.inputSchema):
 * {
 *   "fieldName": {
 *     "type": "string" | "number" | "boolean",
 *     "required": true | false,
 *     "defaultValue": "..."   (optional)
 *   }
 * }
 */
@Service
public class InputSchemaValidatorService {

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Validates {@code input} against {@code schema}.
     *
     * @param schema    JSON string — the workflow's inputSchema column value
     * @param input     runtime key-value map from the execution request
     * @throws RuntimeException if a required field is missing or has the wrong type
     */
    public void validate(String schema, Map<String, Object> input) {

        // No schema defined — nothing to validate
        if (schema == null || schema.isBlank() || schema.equals("{}") || schema.equals("null")) {
            return;
        }

        Map<String, Map<String, Object>> schemaMap;
        try {
            schemaMap = objectMapper.readValue(schema, new TypeReference<>() {});
        } catch (Exception e) {
            throw new RuntimeException("Workflow input schema is invalid JSON: " + e.getMessage());
        }

        for (Map.Entry<String, Map<String, Object>> entry : schemaMap.entrySet()) {
            String field    = entry.getKey();
            Map<String, Object> fieldDef = entry.getValue();

            boolean required     = Boolean.TRUE.equals(fieldDef.get("required"));
            String  expectedType = (String) fieldDef.get("type");
            Object  value        = input != null ? input.get(field) : null;

            // ── Required check ──────────────────────────────────────────────
            if (value == null || (value instanceof String s && s.isBlank())) {
                if (required) {
                    throw new RuntimeException(
                            "Required input field missing: '" + field + "'");
                }
                continue;   // optional + absent → skip type check
            }

            // ── Type check ──────────────────────────────────────────────────
            if (expectedType != null) {
                boolean typeMatch = switch (expectedType.toLowerCase()) {
                    case "number"  -> value instanceof Number;
                    case "string"  -> value instanceof String;
                    case "boolean" -> value instanceof Boolean;
                    default        -> true;   // unknown type → skip
                };

                if (!typeMatch) {
                    throw new RuntimeException(
                            "Field '" + field + "' expected type '" + expectedType
                                    + "' but received '" + value.getClass().getSimpleName() + "'");
                }
            }
        }
    }
}