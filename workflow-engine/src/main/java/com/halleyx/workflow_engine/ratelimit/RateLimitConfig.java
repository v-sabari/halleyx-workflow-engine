package com.halleyx.workflow_engine.ratelimit;

import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

/**
 * RateLimitConfig
 *
 * Registers {@link RateLimitFilter} as a servlet filter.
 *
 * Order: LOWEST_PRECEDENCE - 10 so it runs after the Spring Security filter
 * chain (which sets the authentication principal used as the rate-limit key).
 *
 * Why FilterRegistrationBean instead of @Component?
 *   @Component on a OncePerRequestFilter auto-registers it outside the Spring
 *   Security chain at a fixed order. Using FilterRegistrationBean gives precise
 *   control over the order and URL pattern.
 */
@Configuration
public class RateLimitConfig {

    @Bean
    public FilterRegistrationBean<RateLimitFilter> rateLimitFilter() {
        FilterRegistrationBean<RateLimitFilter> bean = new FilterRegistrationBean<>();
        bean.setFilter(new RateLimitFilter());
        bean.addUrlPatterns("/api/*");          // only rate-limit API paths
        bean.setOrder(Ordered.LOWEST_PRECEDENCE - 10);
        bean.setName("rateLimitFilter");
        return bean;
    }
}
