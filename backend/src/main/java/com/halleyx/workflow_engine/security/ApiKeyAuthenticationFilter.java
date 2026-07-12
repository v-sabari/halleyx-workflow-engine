package com.halleyx.workflow_engine.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

/**
 * ApiKeyAuthenticationFilter
 *
 * Reads the raw API key from the {@code X-API-Key} header on every request.
 * On success: sets an authenticated token in the SecurityContext so that
 * Spring Security's authorisation rules (configured in SecurityConfig) pass.
 * On failure: writes a 401 JSON error immediately and does NOT continue the
 * filter chain.
 *
 * This filter runs before Spring Security's UsernamePasswordAuthenticationFilter
 * (registered via SecurityConfig.addFilterBefore).
 *
 * FIX: doFilterInternal previously called chain.doFilter(...) from inside a
 * Consumer lambda (ifPresentOrElse's first branch), which required wrapping the
 * checked IOException/ServletException in a RuntimeException just to satisfy
 * the functional interface — polluting the stack trace and obscuring the real
 * exception type from callers further up the chain. Rewritten with a plain
 * Optional + if/else so the checked exceptions declared on doFilterInternal()
 * propagate naturally without an extra wrapping layer.
 *
 * Header: X-API-Key: <raw-key>
 */
@RequiredArgsConstructor
@Slf4j
public class ApiKeyAuthenticationFilter extends OncePerRequestFilter {

    public static final String API_KEY_HEADER = "X-API-Key";

    private final ApiKeyService apiKeyService;

    @Override
    protected void doFilterInternal(HttpServletRequest  request,
                                    HttpServletResponse response,
                                    FilterChain         chain)
            throws ServletException, IOException {

        String rawKey = request.getHeader(API_KEY_HEADER);
        Optional<ApiKey> apiKey = apiKeyService.validate(rawKey);

        if (apiKey.isPresent()) {
            // Principal = key description (useful in audit logs); Authority = ROLE_API_CLIENT
            var auth = new UsernamePasswordAuthenticationToken(
                    apiKey.get().getDescription(),
                    null,
                    List.of(new SimpleGrantedAuthority("ROLE_API_CLIENT"))
            );
            SecurityContextHolder.getContext().setAuthentication(auth);
            log.debug("API key authenticated: '{}'", apiKey.get().getDescription());
            chain.doFilter(request, response);
        } else {
            log.warn("Rejected unauthenticated request: {} {}",
                    request.getMethod(), request.getRequestURI());
            writeUnauthorized(response);
        }
    }

    // ── skip filter for actuator / health (SecurityConfig handles permit) ─────
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        // These paths are permitted without auth in SecurityConfig;
        // skip the filter so they pass through without a key check.
        return path.startsWith("/actuator")
                || path.startsWith("/health")
                || path.equals("/api/v1/keys/issue");   // bootstrap endpoint (see SecurityConfig)
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private void writeUnauthorized(HttpServletResponse response) {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        try {
            response.getWriter().write(
                    "{\"error\":\"Missing or invalid API key\"," +
                    "\"status\":401," +
                    "\"hint\":\"Supply your key in the X-API-Key header\"}"
            );
        } catch (IOException e) {
            log.error("Could not write 401 response", e);
        }
    }
}
