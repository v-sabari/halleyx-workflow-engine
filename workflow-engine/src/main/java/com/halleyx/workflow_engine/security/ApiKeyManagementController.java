package com.halleyx.workflow_engine.security;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Map;
import java.util.UUID;

/**
 * ApiKeyManagementController
 *
 * Endpoints:
 *   POST /api/v1/keys/issue   — create a new API key (requires X-Bootstrap-Secret header)
 *   DELETE /api/v1/keys/{id}  — revoke an existing key (requires a valid key)
 *
 * SECURITY FIX (was CRITICAL): POST /issue was previously permitAll() with no
 * check inside the handler, so ANY anonymous caller on the public internet
 * could mint a fully-privileged ROLE_API_CLIENT key and use it against every
 * other endpoint in the system. This is a complete authentication bypass.
 *
 * Fix: the handler now requires a caller-supplied X-Bootstrap-Secret header
 * that must match the APP_BOOTSTRAP_SECRET environment variable (set only on
 * the server, never shipped to any frontend). Comparison is constant-time to
 * avoid timing side-channels. If APP_BOOTSTRAP_SECRET is not configured, the
 * endpoint FAILS CLOSED (always rejects) rather than silently allowing open
 * access — misconfiguration can never re-open this hole.
 *
 * This check is enforced in the handler itself (not just in SecurityConfig's
 * permitAll route matcher), so it holds even if the Security filter chain is
 * ever misconfigured.
 */
@RestController
@RequestMapping("/api/v1/keys")
@RequiredArgsConstructor
@Slf4j
public class ApiKeyManagementController {

    private final ApiKeyService apiKeyService;

    @Value("${app.bootstrap-secret:}")
    private String bootstrapSecret;

    /**
     * POST /api/v1/keys/issue
     * Header: X-Bootstrap-Secret: <APP_BOOTSTRAP_SECRET>
     * Body:   { "description": "ci-pipeline" }
     *
     * Response: { "id": "...", "rawKey": "...", "description": "...", "warning": "..." }
     *
     * The rawKey is shown ONCE. It is not stored in the database.
     * Losing it means the key must be revoked and a new one issued.
     */
    @PostMapping("/issue")
    public ResponseEntity<Map<String, Object>> issue(
            @RequestBody Map<String, String> body,
            @RequestHeader(value = "X-Bootstrap-Secret", required = false) String suppliedSecret) {

        if (!isAuthorizedBootstrapCaller(suppliedSecret)) {
            log.warn("Rejected unauthorized key-issuance attempt (missing/invalid X-Bootstrap-Secret)");
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of(
                    "error",  "Key issuance requires a valid X-Bootstrap-Secret header.",
                    "status", HttpStatus.FORBIDDEN.value()
            ));
        }

        String description = body.getOrDefault("description", "unnamed");
        ApiKeyService.IssuedKey issued = apiKeyService.issueKey(description);

        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                "id",          issued.entity().getId(),
                "description", issued.entity().getDescription(),
                "rawKey",      issued.rawKey(),
                "warning",     "Store this key securely. It will not be shown again."
        ));
    }

    /**
     * Constant-time comparison against the configured bootstrap secret.
     * Fails closed: if the secret is not configured (blank), every request
     * is rejected — there is no "default open" state.
     */
    private boolean isAuthorizedBootstrapCaller(String suppliedSecret) {
        if (bootstrapSecret == null || bootstrapSecret.isBlank()) return false;
        if (suppliedSecret == null || suppliedSecret.isBlank())   return false;

        byte[] expected = bootstrapSecret.getBytes(StandardCharsets.UTF_8);
        byte[] supplied = suppliedSecret.getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(expected, supplied);
    }

    /**
     * DELETE /api/v1/keys/{id}
     * Immediately deactivates the key with the given ID.
     * Requires a valid API key in X-API-Key (you need one key to revoke another).
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> revoke(@PathVariable UUID id) {
        apiKeyService.revoke(id);
        return ResponseEntity.noContent().build();
    }
}
