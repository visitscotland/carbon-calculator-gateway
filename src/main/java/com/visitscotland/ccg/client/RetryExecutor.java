package com.visitscotland.ccg.client;

import com.visitscotland.ccg.config.RetryExecutorProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.ResourceAccessException;

import java.util.function.Supplier;

@Service
public class RetryExecutor {

    private static final Logger logger = LoggerFactory.getLogger(RetryExecutor.class);

    private final RetryExecutorProperties properties;

    public RetryExecutor(RetryExecutorProperties properties) {
        this.properties = properties;
    }

    public <T> T execute(Supplier<T> operation) {
        ResourceAccessException lastException;

        int attempt = 1;
        do {
            try {
                return operation.get();
            } catch (ResourceAccessException ex) {
                lastException = ex;

                if (properties.getMaxAttempts() <= 1 || !shouldRetry(ex)){
                    throw ex;
                } else if (attempt < properties.getMaxAttempts()) {
                    //Note: If this message is registered in the logs frequently, this class should receive and log more context from the caller
                    logger.info("A connection error occurred while attempting a service. Trying again after {} milliseconds."
                            , properties.getMaxDelay());
                    sleep();
                } else {
                    logger.warn("Max attempts reached for Retry Executor");
                }
            }
        } while (attempt++ <= properties.getMaxAttempts());

        throw lastException;
    }

    private boolean shouldRetry(ResourceAccessException ex) {
        Throwable cause = ex.getCause();

        return cause instanceof java.net.SocketTimeoutException
                || cause instanceof java.net.SocketException;
    }

    private void sleep() {
        try {

            Thread.sleep(properties.getMaxDelay());
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Retry interrupted", ex);
        }
    }
}