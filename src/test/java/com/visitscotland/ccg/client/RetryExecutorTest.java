package com.visitscotland.ccg.client;

import com.visitscotland.ccg.config.RetryExecutorProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.ResourceAccessException;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RetryExecutorTest {

    private RetryExecutorProperties properties;
    private RetryExecutor retryExecutor;

    @BeforeEach
    void setUp() {
        properties = new RetryExecutorProperties();
        properties.setMaxAttempts(3);
        properties.setMaxDelay(0L);

        retryExecutor = new RetryExecutor(properties);
    }

    @Test
    @DisplayName("Should return result when operation succeeds")
    void shouldReturnResultWhenOperationSucceeds() {
        Supplier<String> operation = mock(Supplier.class);
        when(operation.get()).thenReturn("success");

        String result = retryExecutor.execute(operation);

        assertEquals("success", result);
    }

    @ParameterizedTest
    @ValueSource(classes = {SocketTimeoutException.class, SocketException.class})
    @DisplayName("Should retry when operation fails with a retryable exception and then succeeds")
    void shouldRetryWhenSocketTimeoutAndThenSucceed(Class<? extends IOException> exceptionClass) throws NoSuchMethodException, InvocationTargetException, InstantiationException, IllegalAccessException {
        Supplier<String> operation = mock(Supplier.class);

        ResourceAccessException exception = new ResourceAccessException(
                "Retryable", exceptionClass.getDeclaredConstructor().newInstance());

        when(operation.get())
                .thenThrow(exception)
                .thenReturn("success");

        String result = retryExecutor.execute(operation);

        assertEquals("success", result);
        verify(operation, times(2)).get();
    }

    @Test
    @DisplayName("Should throw exception when all retry attempts fail")
    void shouldThrowExceptionWhenAllAttemptsFail() {
        Supplier<String> operation = mock(Supplier.class);

        ResourceAccessException exception = new ResourceAccessException(
                "Read timed out", new SocketTimeoutException("Read timed out")
        );

        when(operation.get()).thenThrow(exception);

        ResourceAccessException thrown = assertThrows(
                ResourceAccessException.class,
                () -> retryExecutor.execute(operation)
        );

        assertSame(exception, thrown);
        verify(operation, times(3)).get();
    }

    @Test
    @DisplayName("Should not retry when exception is not retryable")
    void shouldNotRetryWhenExceptionIsNotRetryable() {
        Supplier<String> operation = mock(Supplier.class);

        ResourceAccessException exception = new ResourceAccessException(
                "Request failed", new IOException()
        );

        when(operation.get()).thenThrow(exception);

        ResourceAccessException thrown = assertThrows(
                ResourceAccessException.class,
                () -> retryExecutor.execute(operation)
        );

        assertSame(exception, thrown);
        verify(operation, times(1)).get();
    }

    @Test
    @DisplayName("Should not retry when maximum attempts is one")
    void shouldNotRetryWhenMaximumAttemptsIsOne() {
        properties.setMaxAttempts(1);

        Supplier<String> operation = mock(Supplier.class);

        ResourceAccessException exception = new ResourceAccessException(
                "Connection reset",
                new SocketException("Connection reset")
        );

        when(operation.get()).thenThrow(exception);

        ResourceAccessException thrown = assertThrows(
                ResourceAccessException.class,
                () -> retryExecutor.execute(operation)
        );

        assertSame(exception, thrown);
        verify(operation, times(1)).get();
    }

    @Test

    @DisplayName("Should throw interrupted exception when retry sleep is interrupted")
    void shouldThrowIllegalStateExceptionWhenRetrySleepIsInterrupted() {
        properties.setMaxDelay(1000L);

        Supplier<String> operation = mock(Supplier.class);

        ResourceAccessException exception = new ResourceAccessException(
                "Connection reset",
                new SocketException("Connection reset")
        );

        when(operation.get()).thenThrow(exception);

        Thread.currentThread().interrupt();

        try {
            IllegalStateException thrown = assertThrows(
                    IllegalStateException.class,
                    () -> retryExecutor.execute(operation)
            );

            assertEquals("Retry interrupted", thrown.getMessage());
            assertInstanceOf(InterruptedException.class, thrown.getCause());
            verify(operation, times(1)).get();
        } finally {
            // Clear interrupt status so it does not affect other tests.
            Thread.interrupted();
        }
    }
}