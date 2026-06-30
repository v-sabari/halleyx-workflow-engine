package com.halleyx.workflow_engine.security;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

/**
 * ApiKeyManagementController
 *
 * Endpoints:
 *   POST /api/v1/keys/issue   — create a new API key (no auth required, see SecurityConfig)
 *   DELETE /api/v1/keys/{id}  — revoke an existing key (requires a valid key)
 *
 * The POST endpoint is intentionally left open so that the first key can be
 * bootstrapped. Harden this in production:
 *   - Restrict the route at the gateway/firewall to your internal network.
 *   - Or add an ADMIN_SECRET env-var check inside the handler.
 *   - Or remove this endpoint entirely once keys are seeded via DB migration.
 */
@RestController
@RequestMapping("/api/v1/keys")
@RequiredArgsConstructor
public class ApiKeyManagementController {

    private final ApiKeyService apiKeyService;

    /**
     * POST /api/v1/keys/issue
     * Body: { "description": "ci-pipeline" }
     *
     * Response: { "id": "...", "rawKey": "...", "description": "...", "warning": "..." }
     *
     * The rawKey is shown ONCE. It is not stored in the database.
     * Losing it means the key must be revoked and a new one issued.
     */
    @PostMapping("/issue")
    public ResponseEntity<Map<String, Object>> issue(@RequestBody Map<String, String> body) {
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
