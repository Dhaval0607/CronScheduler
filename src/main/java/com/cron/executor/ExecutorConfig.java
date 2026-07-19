package com.cron.executor;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;

@Configuration
public class ExecutorConfig {

    /**
     * Timeouts are not optional here: without them a hung endpoint holds an
     * execution thread forever and starves every other job.
     */
    @Bean
    RestClient jobRestClient(@Value("${scheduler.http.connect-timeout-ms:5000}") long connectTimeoutMs,
                             @Value("${scheduler.http.read-timeout-ms:10000}") long readTimeoutMs) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofMillis(connectTimeoutMs));
        factory.setReadTimeout(Duration.ofMillis(readTimeoutMs));
        return RestClient.builder().requestFactory(factory).build();
    }
}
