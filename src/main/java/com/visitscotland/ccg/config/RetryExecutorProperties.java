package com.visitscotland.ccg.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "retry")
public class RetryExecutorProperties {

    private Integer maxAttempts;
    private Long maxDelay;

    public RetryExecutorProperties() {

    }

    public RetryExecutorProperties(Integer maxAttempts, Long maxDelay) {
        this.maxAttempts = maxAttempts;
        this.maxDelay = maxDelay;
    }

    public Integer getMaxAttempts() {
        return maxAttempts;
    }

    public void setMaxAttempts(Integer maxAttempts) {
        this.maxAttempts = maxAttempts;
    }

    public Long getMaxDelay() {
        return maxDelay;
    }

    public void setMaxDelay(Long maxDelay) {
        this.maxDelay = maxDelay;
    }
}
