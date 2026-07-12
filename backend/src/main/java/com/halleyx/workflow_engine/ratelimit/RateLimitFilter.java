package com.halleyx.workflow_engine.ratelimit;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Refill;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * RateLimitFilter — in-process Bucket4j rate limiter.
 *
 * Strategy  : token-bucket, refill 100 tokens every 60 seconds (greedy).
 * Key       : the authenticated principal name (API key description), or
 *             the client IP for unauthenticated requests.
 * Storage   : ConcurrentHashMap (JVM-local). For multi-instance deployments
 *             replace with Bucket4j's Redis or Hazelcast integration.
 * Placement : registered after ApiKeyAuthenticationFilter so the principal
 *             is already set in the SecurityContext when this runs.
 *
 * Response headers added on every request:
 *   X-RateLimit-Remaining  — tokens left in the current window
 *   X-RateLimit-Limit      — max tokens per window
 *   Retry-After            — seconds to wait (only on 429)
 */
@Slf4j
public class RateLimitFilter extends OncePerRequestFilter {

    /** Tokens allowed per window. */
    private static final long CAPACITY = 100;
    /** Window length. */
    private static final Duration WINDOW = Duration.ofMinutes(1);

    /** One bucket per principal/IP. Eviction not needed for typical key counts. */
    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();

    @Override
    protected void doFilterInternal(HttpServletRequest  request,
                                    HttpServletResponse response,
                                    FilterChain         chain)
            throws ServletException, IOException {

        String key    = resolveKey(request);
        Bucket bucket = buckets.computeIfAbsent(key, k -> newBucket());

        long remaining = bucket.getAvailableTokens();
        response.setHeader("X-RateLimit-Limit",     String.valueOf(CAPACITY));
        response.setHeader("X-RateLimit-Remaining", String.valueOf(Math.max(remaining - 1, 0)));

        if (bucket.tryConsume(1)) {
            chain.doFilter(request, response);
        } else {
            long waitSeconds = bucket.estimateAbilityToConsume(1)
                                     .getNanosToWaitForRefill() / 1_000_000_000L;
            log.warn("Rate limit exceeded for key='{}' uri={}", key, request.getRequestURI());
            response.setHeader("Retry-After", String.valueOf(waitSeconds));
            writeTooManyRequests(response, waitSeconds);
        }
    }

    /**
     * Skip rate-limiting for OPTIONS (preflight) requests.
     *
     * Note: /actuator and /health are NOT checked here because this filter is
     * registered only on the "/api/*" URL pattern (see RateLimitConfig) — the
     * servlet container never routes those paths through this filter to begin
     * with, so checking for them here would be unreachable dead code.
     */
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return "OPTIONS".equalsIgnoreCase(request.getMethod());
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private String resolveKey(HttpServletRequest request) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated()
                && !"anonymousUser".equals(auth.getPrincipal())) {
            return "key:" + auth.getPrincipal();
        }
        // Fallback: client IP (e.g. for the /api/v1/keys/issue bootstrap endpoint)
        String xff = request.getHeader("X-Forwarded-For");
        return "ip:" + (xff != null ? xff.split(",")[0].trim() : request.getRemoteAddr());
    }

    private static Bucket newBucket() {
        Bandwidth limit = Bandwidth.classic(
                CAPACITY,
                Refill.greedy(CAPACITY, WINDOW)
        );
        return Bucket.builder().addLimit(limit).build();
    }

    private void writeTooManyRequests(HttpServletResponse response, long retryAfterSeconds)
            throws IOException {
        response.setStatus(429);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write(String.format(
                "{\"error\":\"Rate limit exceeded\"," +
                "\"status\":429," +
                "\"retryAfterSeconds\":%d}", retryAfterSeconds
        ));
    }
}
