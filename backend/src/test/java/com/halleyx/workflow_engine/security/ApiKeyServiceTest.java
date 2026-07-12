package com.halleyx.workflow_engine.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ApiKeyServiceTest {

    @Mock
    private ApiKeyRepository apiKeyRepository;

    @InjectMocks
    private ApiKeyService apiKeyService;

    // ── issueKey ──────────────────────────────────────────────────────────────

    @Test
    void issueKey_shouldReturnRawKeyAndEntity() {
        when(apiKeyRepository.save(any(ApiKey.class)))
                .thenAnswer(i -> i.getArgument(0));

        ApiKeyService.IssuedKey issued = apiKeyService.issueKey("test-key");

        assertNotNull(issued.rawKey(), "Raw key must not be null");
        assertFalse(issued.rawKey().isBlank(), "Raw key must not be blank");
        assertNotNull(issued.entity(), "Entity must not be null");
        assertEquals("test-key", issued.entity().getDescription());
        assertTrue(issued.entity().getIsActive());
    }

    @Test
    void issueKey_shouldStoreHashNotRawKey() {
        when(apiKeyRepository.save(any(ApiKey.class)))
                .thenAnswer(i -> i.getArgument(0));

        ApiKeyService.IssuedKey issued = apiKeyService.issueKey("ci-pipeline");

        // The stored keyHash must be the SHA-256 of the rawKey, not the rawKey itself
        String expectedHash = ApiKeyService.sha256Hex(issued.rawKey());
        assertEquals(expectedHash, issued.entity().getKeyHash());
        assertNotEquals(issued.rawKey(), issued.entity().getKeyHash(),
                "Raw key must never be stored in the DB");
    }

    @Test
    void issueKey_twoCallsShouldProduceDifferentRawKeys() {
        when(apiKeyRepository.save(any(ApiKey.class)))
                .thenAnswer(i -> i.getArgument(0));

        ApiKeyService.IssuedKey first  = apiKeyService.issueKey("key-a");
        ApiKeyService.IssuedKey second = apiKeyService.issueKey("key-b");

        assertNotEquals(first.rawKey(), second.rawKey(),
                "Each issued raw key must be unique");
    }

    // ── validate ──────────────────────────────────────────────────────────────

    @Test
    void validate_shouldReturnActiveKeyOnValidRawKey() {
        when(apiKeyRepository.save(any(ApiKey.class)))
                .thenAnswer(i -> i.getArgument(0));

        ApiKeyService.IssuedKey issued = apiKeyService.issueKey("frontend");
        String hash = issued.entity().getKeyHash();

        when(apiKeyRepository.findByKeyHashAndIsActiveTrue(hash))
                .thenReturn(Optional.of(issued.entity()));

        Optional<ApiKey> result = apiKeyService.validate(issued.rawKey());

        assertTrue(result.isPresent());
        assertEquals("frontend", result.get().getDescription());
    }

    @Test
    void validate_shouldReturnEmptyForUnknownKey() {
        when(apiKeyRepository.findByKeyHashAndIsActiveTrue(any()))
                .thenReturn(Optional.empty());

        Optional<ApiKey> result = apiKeyService.validate("unknown-raw-key");

        assertTrue(result.isEmpty());
    }

    @Test
    void validate_shouldReturnEmptyForNullKey() {
        Optional<ApiKey> result = apiKeyService.validate(null);
        assertTrue(result.isEmpty());
        verifyNoInteractions(apiKeyRepository);
    }

    @Test
    void validate_shouldReturnEmptyForBlankKey() {
        Optional<ApiKey> result = apiKeyService.validate("   ");
        assertTrue(result.isEmpty());
        verifyNoInteractions(apiKeyRepository);
    }

    @Test
    void validate_shouldUpdateLastUsedAt() {
        when(apiKeyRepository.save(any(ApiKey.class)))
                .thenAnswer(i -> i.getArgument(0));

        ApiKeyService.IssuedKey issued = apiKeyService.issueKey("monitor");
        String hash = issued.entity().getKeyHash();

        when(apiKeyRepository.findByKeyHashAndIsActiveTrue(hash))
                .thenReturn(Optional.of(issued.entity()));

        apiKeyService.validate(issued.rawKey());

        // Verify save was called a second time (first was issueKey, second is validate)
        verify(apiKeyRepository, times(2)).save(argThat(k ->
                "monitor".equals(k.getDescription())));
    }

    // ── revoke ────────────────────────────────────────────────────────────────

    @Test
    void revoke_shouldDeactivateKey() {
        UUID keyId = UUID.randomUUID();
        ApiKey key = ApiKey.builder()
                .id(keyId)
                .description("old-key")
                .keyHash("hash")
                .isActive(true)
                .build();

        when(apiKeyRepository.findById(keyId)).thenReturn(Optional.of(key));
        when(apiKeyRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        apiKeyService.revoke(keyId);

        assertFalse(key.getIsActive(), "Key must be deactivated after revoke");
        verify(apiKeyRepository).save(key);
    }

    @Test
    void revoke_shouldThrowWhenKeyNotFound() {
        UUID keyId = UUID.randomUUID();
        when(apiKeyRepository.findById(keyId)).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class,
                () -> apiKeyService.revoke(keyId));
    }

    // ── sha256Hex ─────────────────────────────────────────────────────────────

    @Test
    void sha256Hex_shouldProduceDeterministicHash() {
        String input = "hello-world";
        assertEquals(ApiKeyService.sha256Hex(input), ApiKeyService.sha256Hex(input));
    }

    @Test
    void sha256Hex_shouldProduce64HexChars() {
        String hash = ApiKeyService.sha256Hex("any-input");
        assertEquals(64, hash.length());
        assertTrue(hash.matches("[0-9a-f]{64}"),
                "Hash must be lowercase hex");
    }

    @Test
    void sha256Hex_differentInputsShouldProduceDifferentHashes() {
        assertNotEquals(
                ApiKeyService.sha256Hex("abc"),
                ApiKeyService.sha256Hex("abd")
        );
    }
}
