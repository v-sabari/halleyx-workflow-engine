package com.halleyx.workflow_engine.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * HealthController
 *
 * Lightweight, unauthenticated uptime-check endpoint (e.g. for cron/uptime
 * services pinging the app to keep it warm on Render/Railway).
 *
 * SecurityConfig already permits "/health" without an API key
 * (see SecurityConfig#filterChain -> .requestMatchers("/actuator/**", "/health").permitAll()).
 * If you also want "/api/v1/health" reachable without a key, add it to that
 * same permitAll() list — "/api/v1/**" otherwise requires ROLE_API_CLIENT.
 *
 * GET /health, /api/v1/health -> 200 "workflow-engine backend is running"
 */
@RestController
@CrossOrigin(origins = "*")
public class HealthController {

    @GetMapping({"/health", "/api/v1/health"})
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("workflow-engine backend is running");
    }
}