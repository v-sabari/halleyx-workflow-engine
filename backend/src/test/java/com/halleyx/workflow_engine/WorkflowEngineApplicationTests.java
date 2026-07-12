package com.halleyx.workflow_engine;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Spring context load test.
 * Uses application-test.properties via @ActiveProfiles("test").
 * This is cleaner than @TestPropertySource inline — the profile
 * can also be activated via -Dspring.profiles.active=test at CI.
 */
@SpringBootTest
@ActiveProfiles("test")
class WorkflowEngineApplicationTests {

    @Test
    void contextLoads() {
        // Verifies the Spring context wires up without errors.
    }
}
