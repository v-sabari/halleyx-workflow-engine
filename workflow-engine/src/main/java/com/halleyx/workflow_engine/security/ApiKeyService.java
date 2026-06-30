package com.halleyx.workflow_engine.security;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;

/**
 * ApiKeyService — manages API key lifecycle.
 *
 * Key design:
 *  - Raw key = 32 random bytes → Base64-URL-encoded string (43 chars, URL-safe).
 *  - Stored value = SHA-256 hex of the raw key (64 chars).
 *  - The raw key is returned exactly once at creation; it is never stored.
 *
 * Rotation: revoke() marks isActive=false; issue a new key and communicate
 * it out-of-band. Old key stops working immediately.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ApiKeyService {

    private final ApiKeyRepository apiKeyRepository;

    /** Characters in a generated key (Base64 URL-safe, no padding). */
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Issues a new API key.
     *
     * @param description human label for this key
     * @return {@link IssuedKey} containing both the {@link ApiKey} entity and the
     *         one-time raw key string that must be shared with the caller.
     */
    @Transactional
    public IssuedKey issueKey(String description) {
        byte[] raw = new byte[32];
        SECURE_RANDOM.nextBytes(raw);
        String rawKey = Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
        String hash   = sha256Hex(rawKey);

        ApiKey entity = ApiKey.builder()
                .keyHash(hash)
                .description(description)
                .isActive(true)
                .build();
        apiKeyRepository.save(entity);

        log.info("Issued new API key id={} description='{}'", entity.getId(), description);
        return new IssuedKey(entity, rawKey);
    }

    /**
     * Validates an inbound raw key.
     * Updates {@code lastUsedAt} on success.
     *
     * @return the matching {@link ApiKey} if valid and active, empty otherwise.
     */
    @Transactional
    public Optional<ApiKey> validate(String rawKey) {
        if (rawKey == null || rawKey.isBlank()) return Optional.empty();
        String hash = sha256Hex(rawKey);
        Optional<ApiKey> found = apiKeyRepository.findByKeyHashAndIsActiveTrue(hash);
        found.ifPresent(k -> {
            k.setLastUsedAt(LocalDateTime.now());
            apiKeyRepository.save(k);
        });
        return found;
    }

    /**
     * Revokes (deactivates) a key by its entity ID.
     *
     * @throws IllegalArgumentException if the key does not exist.
     */
    @Transactional
    public void revoke(UUID keyId) {
        ApiKey key = apiKeyRepository.findById(keyId)
                .orElseThrow(() -> new IllegalArgumentException("API key not found: " + keyId));
        key.setIsActive(false);
        apiKeyRepository.save(key);
        log.warn("Revoked API key id={} description='{}'", keyId, key.getDescription());
    }

    // ── Hash helper ───────────────────────────────────────────────────────────

    /**
     * Returns the lowercase hex SHA-256 digest of a UTF-8 string.
     * Using {@link HexFormat} (Java 17+) avoids an external dependency.
     */
    public static String sha256Hex(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(bytes);
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 is guaranteed by the Java spec — this can never happen.
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    // ── Value object ──────────────────────────────────────────────────────────

    /** Returned once at key issuance — contains the raw key shown to the caller. */
    public record IssuedKey(ApiKey entity, String rawKey) {}
}
