package com.halleyx.workflow_engine.security;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface ApiKeyRepository extends JpaRepository<ApiKey, UUID> {

    /** Used by the filter to validate inbound keys (looks up by SHA-256 hash). */
    Optional<ApiKey> findByKeyHashAndIsActiveTrue(String keyHash);
}
