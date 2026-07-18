package com.cron.scheduler;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.concurrent.ThreadPoolExecutor;

@Configuration
public class SchedulerConfig {

    /**
     * Timeouts are not optional here: without them a hung endpoint holds a
     * scheduling thread forever and starves every other job.
     */
    /**
     * Jobs run here rather than on the polling thread, so one slow job delays
     * neither the rest of its batch nor the next poll.
     *
     * <p>The queue is deliberately bounded: an unbounded one would let a backlog
     * of slow jobs grow until the heap gives out. CallerRuns is the backpressure
     * valve — once the queue is full the polling thread executes the job itself,
     * which stalls polling on purpose until the pool drains.
     */
    @Bean
    ThreadPoolTaskExecutor jobExecutionPool(
            @Value("${scheduler.execution.core-pool-size:5}") int corePoolSize,
            @Value("${scheduler.execution.max-pool-size:10}") int maxPoolSize,
            @Value("${scheduler.execution.queue-capacity:50}") int queueCapacity,
            @Value("${scheduler.execution.shutdown-await-seconds:30}") int awaitSeconds) {

        ThreadPoolTaskExecutor pool = new ThreadPoolTaskExecutor();
        pool.setCorePoolSize(corePoolSize);
        pool.setMaxPoolSize(maxPoolSize);
        pool.setQueueCapacity(queueCapacity);
        pool.setThreadNamePrefix("job-exec-");
        pool.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());

        // A claimed job has already had its nextRunAt advanced, so dropping it on
        // shutdown means it is skipped entirely rather than retried. Drain first.
        pool.setWaitForTasksToCompleteOnShutdown(true);
        pool.setAwaitTerminationSeconds(awaitSeconds);

        pool.initialize();
        return pool;
    }

    @Bean
    RestClient jobRestClient(@Value("${scheduler.http.connect-timeout-ms:5000}") long connectTimeoutMs,
                             @Value("${scheduler.http.read-timeout-ms:10000}") long readTimeoutMs) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofMillis(connectTimeoutMs));
        factory.setReadTimeout(Duration.ofMillis(readTimeoutMs));
        return RestClient.builder().requestFactory(factory).build();
    }
}
