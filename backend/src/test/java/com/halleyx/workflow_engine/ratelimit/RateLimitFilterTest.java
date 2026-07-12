package com.halleyx.workflow_engine.ratelimit;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RateLimitFilterTest {

    private RateLimitFilter filter;

    @Mock
    private FilterChain filterChain;

    @BeforeEach
    void setUp() {
        filter = new RateLimitFilter();
    }

    // ── shouldNotFilter ───────────────────────────────────────────────────────

    @Test
    void shouldNotFilter_OPTIONS_returnsTrue() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("OPTIONS", "/api/v1/workflows");
        assertTrue(filter.shouldNotFilter(request));
    }

    @Test
    void shouldNotFilter_GET_returnsFalse() throws Exception {
        // /actuator and /health are excluded at the servlet URL-pattern level
        // (RateLimitConfig registers this filter only on "/api/*"), not inside
        // shouldNotFilter — so a GET to any path this filter actually receives
        // must return false here.
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/workflows");
        assertFalse(filter.shouldNotFilter(request));
    }

    @Test
    void shouldNotFilter_apiEndpoint_returnsFalse() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/executions/start");
        assertFalse(filter.shouldNotFilter(request));
    }

    // ── normal request passes through ─────────────────────────────────────────

    @Test
    void firstRequest_shouldPassThrough() throws Exception {
        MockHttpServletRequest  request  = new MockHttpServletRequest("GET", "/api/v1/workflows");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain, times(1)).doFilter(request, response);
        assertEquals(200, response.getStatus(),
                "Normal request should not trigger 429");
    }

    @Test
    void firstRequest_shouldAddRateLimitHeaders() throws Exception {
        MockHttpServletRequest  request  = new MockHttpServletRequest("GET", "/api/v1/workflows");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilterInternal(request, response, filterChain);

        assertNotNull(response.getHeader("X-RateLimit-Limit"),
                "X-RateLimit-Limit header must be set");
        assertNotNull(response.getHeader("X-RateLimit-Remaining"),
                "X-RateLimit-Remaining header must be set");
        assertEquals("100", response.getHeader("X-RateLimit-Limit"));
    }

    // ── rate limit enforcement ────────────────────────────────────────────────

    @Test
    void exhaustedBucket_shouldReturn429() throws Exception {
        // Fire 100 requests to exhaust the bucket for a unique IP
        String uniqueIp = "10.99.99.1";
        for (int i = 0; i < 100; i++) {
            MockHttpServletRequest  req  = makeRequestFromIp(uniqueIp);
            MockHttpServletResponse resp = new MockHttpServletResponse();
            filter.doFilterInternal(req, resp, filterChain);
        }

        // 101st request must be rejected
        MockHttpServletRequest  req  = makeRequestFromIp(uniqueIp);
        MockHttpServletResponse resp = new MockHttpServletResponse();
        filter.doFilterInternal(req, resp, filterChain);

        assertEquals(429, resp.getStatus(), "101st request must be rate-limited");
        assertNotNull(resp.getHeader("Retry-After"),
                "Retry-After header must be set on 429");
    }

    @Test
    void differentIps_shouldHaveIndependentBuckets() throws Exception {
        // Exhaust IP A
        String ipA = "10.1.1.1";
        for (int i = 0; i < 100; i++) {
            MockHttpServletRequest  req  = makeRequestFromIp(ipA);
            MockHttpServletResponse resp = new MockHttpServletResponse();
            filter.doFilterInternal(req, resp, filterChain);
        }

        // IP B should still have a full bucket
        MockHttpServletRequest  reqB  = makeRequestFromIp("10.1.1.2");
        MockHttpServletResponse respB = new MockHttpServletResponse();
        filter.doFilterInternal(reqB, respB, filterChain);

        assertNotEquals(429, respB.getStatus(),
                "A different IP must have its own independent bucket");
    }

    @Test
    void exhaustedBucket_shouldReturnJsonBody() throws Exception {
        String uniqueIp = "10.50.50.1";
        for (int i = 0; i < 100; i++) {
            MockHttpServletRequest  req  = makeRequestFromIp(uniqueIp);
            MockHttpServletResponse resp = new MockHttpServletResponse();
            filter.doFilterInternal(req, resp, filterChain);
        }

        MockHttpServletRequest  req  = makeRequestFromIp(uniqueIp);
        MockHttpServletResponse resp = new MockHttpServletResponse();
        filter.doFilterInternal(req, resp, filterChain);

        String body = resp.getContentAsString();
        assertTrue(body.contains("\"status\":429"),
                "429 response body must include status field");
        assertTrue(body.contains("Rate limit exceeded"),
                "429 response body must include error message");
    }

    // ── helpers ────────────────────────────────────────────────────────────────

    private MockHttpServletRequest makeRequestFromIp(String ip) {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/v1/workflows");
        req.setRemoteAddr(ip);
        return req;
    }
}
