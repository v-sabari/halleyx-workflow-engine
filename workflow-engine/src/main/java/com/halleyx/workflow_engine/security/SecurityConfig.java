package com.halleyx.workflow_engine.security;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.config.Customizer;
/**
 * SecurityConfig — production-grade stateless security for the workflow engine.
 *
 * Design decisions:
 *  - STATELESS session — no cookies, no CSRF surface (all auth is per-request via header).
 *  - CSRF disabled — correct for a token-authenticated REST API with no browser session.
 *  - Every API endpoint requires ROLE_API_CLIENT (set by ApiKeyAuthenticationFilter).
 *  - POST /api/v1/keys/issue is the bootstrap endpoint; it is permitted without auth
 *    so the very first key can be created. In production this endpoint should be
 *    restricted at the network/gateway layer (VPN only, firewall rule, etc.).
 *  - OPTIONS (preflight) requests are always permitted so the browser CORS flow works.
 *  - X-Frame-Options, X-Content-Type-Options, and HSTS headers added via Spring Security's
 *    default headers (enabled by default when using the DSL).
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final ApiKeyService apiKeyService;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {

        http
            // ── No session, no CSRF ───────────────────────────────────────────
            .sessionManagement(s ->
                    s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .csrf(AbstractHttpConfigurer::disable)
            .cors(Customizer.withDefaults())
            // ── Authorisation rules ──────────────────────────────────────────
            .authorizeHttpRequests(auth -> auth
                // Preflight — always pass through
                .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                // Bootstrap key-issuance endpoint — no key needed yet
                // IMPORTANT: restrict at network layer in production
                .requestMatchers(HttpMethod.POST, "/api/v1/keys/issue").permitAll()
                // Health / actuator — accessible for load-balancer probes
                .requestMatchers("/actuator/**", "/health").permitAll()
                // Everything else requires a valid API key
                .anyRequest().hasRole("API_CLIENT")
            )

            // ── Register our custom filter ───────────────────────────────────
            .addFilterBefore(
                new ApiKeyAuthenticationFilter(apiKeyService),
                UsernamePasswordAuthenticationFilter.class
            )

            // ── Disable form-login and HTTP-Basic — not needed ───────────────
            .formLogin(AbstractHttpConfigurer::disable)
            .httpBasic(AbstractHttpConfigurer::disable);

        return http.build();
    }
}
