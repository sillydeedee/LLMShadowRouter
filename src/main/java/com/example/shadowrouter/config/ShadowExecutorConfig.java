package com.example.shadowrouter.config;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Bounded executor for background shadow evaluations.
 *
 * Caps concurrent candidate calls and pending queue depth. When both are saturated,
 * new shadow tasks are rejected (load shed) so bursts cannot grow unbounded memory
 * or starve the primary request path.
 */
@Configuration
public class ShadowExecutorConfig {

    public static final String SHADOW_EXECUTOR = "shadowExecutor";

    @Bean(name = SHADOW_EXECUTOR, destroyMethod = "shutdown")
    public ExecutorService shadowExecutor(InferenceProperties properties) {
        int concurrency = properties.shadowMaxConcurrency();
        int queueCapacity = properties.shadowQueueCapacity();

        ThreadPoolExecutor executor = new ThreadPoolExecutor(
                concurrency,
                concurrency,
                60L,
                TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(queueCapacity),
                Thread.ofVirtual().name("shadow-", 0).factory(),
                // Abort immediately when workers + queue are full (caller sheds the task).
                new ThreadPoolExecutor.AbortPolicy());

        // Allow idle virtual workers to exit so the pool shrinks after a burst.
        executor.allowCoreThreadTimeOut(true);
        return executor;
    }
}
