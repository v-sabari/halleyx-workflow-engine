package com.halleyx.workflow_engine.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * AsyncConfig
 *
 * Registers the "workflowExecutor" thread pool used by @Async methods in
 * AsyncExecutionService, and enables Spring's @Scheduled support for
 * IdempotencyCleanupTask.
 *
 * Pool sizing rationale:
 *   - corePoolSize  = 4   (always-on threads for steady-state throughput)
 *   - maxPoolSize   = 20  (burst capacity; tune via env var ASYNC_MAX_POOL_SIZE)
 *   - queueCapacity = 500 (bounded queue; prevents OOM under spike load)
 *   - keepAliveSeconds = 60 (idle threads above core are released after 1 min)
 *
 * Rejection policy: CallerRunsPolicy — if the queue is full, the HTTP thread
 * itself runs the task instead of throwing RejectedExecutionException.
 * This degrades gracefully under extreme load rather than failing hard.
 *
 * Thread naming: "workflow-exec-N" (visible in thread dumps / APM tools).
 */
@Configuration
@EnableAsync
@EnableScheduling
@Slf4j
public class AsyncConfig {

    @Value("${async.executor.core-pool-size:4}")
    private int corePoolSize;

    @Value("${async.executor.max-pool-size:20}")
    private int maxPoolSize;

    @Value("${async.executor.queue-capacity:500}")
    private int queueCapacity;

    @Value("${async.executor.keep-alive-seconds:60}")
    private int keepAliveSeconds;

    @Bean(name = "workflowExecutor")
    public Executor workflowExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();

        executor.setCorePoolSize(corePoolSize);
        executor.setMaxPoolSize(maxPoolSize);
        executor.setQueueCapacity(queueCapacity);
        executor.setKeepAliveSeconds(keepAliveSeconds);
        executor.setThreadNamePrefix("workflow-exec-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);  // graceful shutdown window

        // Caller-runs on saturation — degrade gracefully instead of rejecting
        executor.setRejectedExecutionHandler(new CallerRunsWithWarning());

        executor.initialize();
        log.info("WorkflowExecutor pool: core={} max={} queue={}",
                corePoolSize, maxPoolSize, queueCapacity);
        return executor;
    }

    /** RejectedExecutionHandler that logs a warning before falling back to CallerRuns. */
    private static class CallerRunsWithWarning implements RejectedExecutionHandler {
        private final ThreadPoolExecutor.CallerRunsPolicy delegate =
                new ThreadPoolExecutor.CallerRunsPolicy();

        @Override
        public void rejectedExecution(Runnable r, ThreadPoolExecutor executor) {
            log.warn("WorkflowExecutor queue is full — running task on caller thread. " +
                     "Consider increasing async.executor.queue-capacity.");
            delegate.rejectedExecution(r, executor);
        }
    }
}
