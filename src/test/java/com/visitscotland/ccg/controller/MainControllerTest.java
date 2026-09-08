package com.visitscotland.ccg.controller;

import com.visitscotland.ccg.client.BregClient;
import com.visitscotland.ccg.client.RecaptchaClient;
import com.visitscotland.ccg.client.TraceApiClient;
import com.visitscotland.ccg.exception.TraceApiException;
import com.visitscotland.ccg.exception.VsException;
import com.visitscotland.ccg.model.RegisterResponse;
import com.visitscotland.ccg.notification.EmailNotificationService;
import com.visitscotland.ccg.testutil.TestData;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MainControllerTest {

    @Mock
    private BregClient bregService;

    @Mock
    private RecaptchaClient recaptchaService;

    @Mock
    private TraceApiClient traceAPIService;

    @Mock
    private HttpServletRequest request;

    @Mock
    private EmailNotificationService emailNotificationService;

    @InjectMocks
    private MainController controller;

    @Test
    @DisplayName("Should return the application health status")
    void shouldReturnHealthStatus() {

        assertEquals("Status OK!", controller.health());
    }

    @Test
    @DisplayName("Should reject the request when the reCAPTCHA validation fails")
    void shouldRejectInvalidRecaptcha() {

        when(recaptchaService.isValidRecaptcha(any(), any())).thenReturn(false);

        ResponseEntity<RegisterResponse> response =
                controller.register(TestData.simpleObjectNode(), request);

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());

        verifyNoInteractions(traceAPIService);
        verifyNoInteractions(bregService);
    }

    @Test
    @DisplayName("Should submit to Trace API and BREG when the reCAPTCHA is valid")
    void shouldSubmitToBothServices() {

        when(recaptchaService.isValidRecaptcha(any(), any())).thenReturn(true);
        when(traceAPIService.register(any(), anyString()))
                .thenReturn(ResponseEntity.ok(TestData.simpleObjectNode()));
        when(bregService.sendRequest(any(), anyString(), anyBoolean()))
                .thenReturn(ResponseEntity.ok("OK"));

        ResponseEntity<RegisterResponse> response =
                controller.register(TestData.simpleObjectNode(), request);

        assertEquals(HttpStatus.OK, response.getStatusCode());

        verify(traceAPIService).register(any(), anyString());
        verify(bregService).sendRequest(any(), anyString(), eq(false));
    }

    @Test
    @DisplayName("Should continue with BREG when Trace API submission fails")
    void shouldContinueWhenTraceApiFails() {

        when(recaptchaService.isValidRecaptcha(any(), any())).thenReturn(true);
        when(traceAPIService.register(any(), anyString()))
                .thenThrow(new TraceApiException("Failure"));
        when(bregService.sendRequest(any(), anyString(), anyBoolean()))
                .thenReturn(ResponseEntity.ok("OK"));

        ResponseEntity<RegisterResponse> response =
                controller.register(TestData.simpleObjectNode(), request);

        assertEquals(HttpStatus.ACCEPTED, response.getStatusCode());

        verify(bregService).sendRequest(any(), anyString(), eq(true));
    }

    @Test
    @DisplayName("Should continue when BREG submission fails")
    void shouldContinueWhenBregFails() {

        when(recaptchaService.isValidRecaptcha(any(), any())).thenReturn(true);
        when(traceAPIService.register(any(), anyString()))
                .thenReturn(ResponseEntity.ok(TestData.simpleObjectNode()));
        when(bregService.sendRequest(any(), anyString(), anyBoolean()))
                .thenThrow(new VsException("Failure"));

        assertDoesNotThrow(() ->
                controller.register(TestData.simpleObjectNode(), request));

        verify(traceAPIService).register(any(), anyString());
    }

    @Test
    @DisplayName("Should pass the Trace API failure flag to BREG")
    void shouldPassTraceApiFailureFlagToBreg() {

        when(recaptchaService.isValidRecaptcha(any(), any())).thenReturn(true);
        when(traceAPIService.register(any(), anyString()))
                .thenThrow(new TraceApiException("Failure"));
        when(bregService.sendRequest(any(), anyString(), anyBoolean()))
                .thenReturn(ResponseEntity.ok("OK"));

        controller.register(TestData.simpleObjectNode(), request);

        verify(bregService)
                .sendRequest(any(), anyString(), eq(true));
    }

    @Test
    @DisplayName("Should return an internal server error when both services fail")
    void shouldReturnInternalServerErrorWhenBothServicesFail() {

        when(recaptchaService.isValidRecaptcha(any(), any())).thenReturn(true);
        when(traceAPIService.register(any(), anyString()))
                .thenThrow(new TraceApiException("Failure"));
        when(bregService.sendRequest(any(), anyString(), anyBoolean()))
                .thenThrow(new VsException("Failure"));

        ResponseEntity<RegisterResponse> response =
                controller.register(TestData.simpleObjectNode(), request);

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
    }

    @Test
    @DisplayName("Should return success when both downstream services succeed")
    void shouldReturnSuccessWhenBothServicesSucceed() {

        when(recaptchaService.isValidRecaptcha(any(), any())).thenReturn(true);
        when(traceAPIService.register(any(), anyString()))
                .thenReturn(ResponseEntity.ok(TestData.simpleObjectNode()));
        when(bregService.sendRequest(any(), anyString(), anyBoolean()))
                .thenReturn(ResponseEntity.ok("OK"));

        ResponseEntity<RegisterResponse> response =
                controller.register(TestData.simpleObjectNode(), request);

        assertEquals(HttpStatus.OK, response.getStatusCode());
    }
}