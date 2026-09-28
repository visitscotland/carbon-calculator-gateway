package com.visitscotland.ccg.client;

import com.visitscotland.ccg.config.RetryExecutorProperties;
import com.visitscotland.ccg.payload.SubmissionPayloadTransformer;
import com.visitscotland.ccg.testutil.TestData;
import com.visitscotland.ccg.config.TraceApiProperties;
import com.visitscotland.ccg.exception.TraceApiException;
import com.visitscotland.ccg.exception.VsException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.*;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TraceApiClientTest {

    private static final String BASE_URL = "https://trace-api";
    private static final String API_KEY = "my-api-key";

    @org.mockito.Mock
    private RestTemplate restTemplate;

    @Spy
    private ObjectMapper objectMapper;

    private TraceApiProperties properties;

    private TraceApiClient service;

    @Mock
    private SubmissionPayloadTransformer transformer;

    @BeforeEach
    void setUp() {

        properties = new TraceApiProperties();
        properties.setApiKey(API_KEY);
        properties.setBaseUrl(BASE_URL);
        properties.setEnabled(true);
        properties.setRemoveProperties(new String[]{"removeMe"});

        service = new TraceApiClient(restTemplate, objectMapper, properties, transformer,
                new RetryExecutor(new RetryExecutorProperties(1,0L)));
    }

    @Test
    @DisplayName("Should return the authentication token")
    void shouldReturnAuthenticationToken() {
        ObjectNode response = new TestData().add("payload", payload -> payload.add("token", "TOKEN123")).objectNode();

        when(restTemplate.postForEntity(eq(BASE_URL + TraceApiClient.AUTH_ENDPOINT), any(HttpEntity.class), eq(JsonNode.class))).thenReturn(ResponseEntity.ok(response));

        String token = service.getAuthenticationToken();

        assertEquals("TOKEN123", token);
    }

    @Test
    @DisplayName("Should throw TraceApiException when status code is not 200")
    void shouldThrowWhenPayloadIsMissing() {
        when(restTemplate.postForEntity(anyString(), any(HttpEntity.class), eq(JsonNode.class))).thenReturn(ResponseEntity.status(HttpStatus.FORBIDDEN).build());

        assertThrows(TraceApiException.class, () -> service.getAuthenticationToken());
    }

    @Test
    @DisplayName("Should throw TraceApiException when payload has no token")
    void shouldThrowWhenTokenIsMissing() {

        when(restTemplate.postForEntity(anyString(), any(HttpEntity.class), eq(JsonNode.class))).thenReturn(ResponseEntity.ok(TestData.simpleObjectNode()));

        assertThrows(TraceApiException.class, () -> service.getAuthenticationToken());
    }

    @Test
    @DisplayName("Should throw TraceApiException when authentication response has no body")
    void shouldThrowWhenBodyIsMissing() {

        when(restTemplate.postForEntity(anyString(), any(HttpEntity.class), eq(JsonNode.class))).thenReturn(ResponseEntity.ok().build());

        assertThrows(TraceApiException.class, () -> service.getAuthenticationToken());
    }

    @Test
    @DisplayName("Should wrap authentication HTTP errors in a TraceApiException")
    void shouldWrapAuthenticationHttpErrors() {

        when(restTemplate.postForEntity(anyString(), any(HttpEntity.class), eq(JsonNode.class))).thenThrow(new HttpClientErrorException(HttpStatus.UNAUTHORIZED));

        TraceApiException traceException = assertThrows(TraceApiException.class, () -> service.getAuthenticationToken());

        assertInstanceOf(HttpClientErrorException.class, traceException.getCause());
    }

    @Test
    @DisplayName("Should throw VsException when Trace API integration is disabled")
    void shouldThrowWhenTraceApiIsDisabled() {
        ObjectNode payload = TestData.simpleObjectNode();

        properties.setEnabled(false);

        VsException exception = assertThrows(VsException.class, () -> service.register(payload, "123"));

        assertNotNull(exception.getMessage());
        verifyNoInteractions(restTemplate);
    }

    @Test
    @DisplayName("Should authenticate before submitting the registration")
    void shouldAuthenticateBeforeSubmitting() {
        ObjectNode payload = TestData.simpleObjectNode();

        when(transformer.transform(payload, "submission123", properties.getRemoveProperties())).thenReturn(payload);
        when(restTemplate.postForEntity(eq(BASE_URL + TraceApiClient.AUTH_ENDPOINT), any(HttpEntity.class), eq(JsonNode.class))).thenReturn(authenticationResponse());
        when(restTemplate.exchange(eq(BASE_URL + TraceApiClient.REGISTER_ENDPOINT), eq(HttpMethod.PUT), any(HttpEntity.class), eq(ObjectNode.class))).thenReturn(ResponseEntity.ok(TestData.simpleObjectNode()));

        service.register(payload, "submission123");

        //Validate the order of events
        InOrder order = inOrder(restTemplate);
        order.verify(restTemplate).postForEntity(anyString(), any(HttpEntity.class), eq(JsonNode.class));
        order.verify(restTemplate).exchange(anyString(), eq(HttpMethod.PUT), any(HttpEntity.class), eq(ObjectNode.class));

    }

    @Test
    @DisplayName("Should send the authentication token in the Authorization header")
    void shouldSendAuthenticationToken() {
        @SuppressWarnings("rawtypes")
        ArgumentCaptor<HttpEntity> entityCaptor = ArgumentCaptor.forClass(HttpEntity.class);

        ObjectNode payload = TestData.simpleObjectNode();

        when(transformer.transform(payload, "123", properties.getRemoveProperties())).thenReturn(payload);
        when(restTemplate.postForEntity(anyString(), any(HttpEntity.class), eq(JsonNode.class)))
                .thenReturn(authenticationResponse());
        when(restTemplate.exchange(anyString(), eq(HttpMethod.PUT), entityCaptor.capture(), eq(ObjectNode.class)))
                .thenReturn(ResponseEntity.ok(TestData.simpleObjectNode()));

        service.register(payload, "123");

        assertEquals("TOKEN123", entityCaptor.getValue().getHeaders().getFirst("Authorization"));
    }

    @Test
    @DisplayName("Should send JSON content type")
    void shouldSendJsonContentType() {

        @SuppressWarnings("rawtypes")
        ArgumentCaptor<HttpEntity> entityCaptor = ArgumentCaptor.forClass(HttpEntity.class);

        ObjectNode payload = TestData.simpleObjectNode();

        when(transformer.transform(payload, "123", properties.getRemoveProperties())).thenReturn(payload);
        when(restTemplate.postForEntity(anyString(), any(HttpEntity.class), eq(JsonNode.class)))
                .thenReturn(authenticationResponse());
        when(restTemplate.exchange(anyString(), eq(HttpMethod.PUT), entityCaptor.capture(), eq(ObjectNode.class)))
                .thenReturn(ResponseEntity.ok(TestData.simpleObjectNode()));

        service.register(payload, "123");

        assertEquals(MediaType.APPLICATION_JSON, entityCaptor.getValue().getHeaders().getContentType());
    }

    @Test
    @DisplayName("Should wrap client errors returned by Trace API")
    void shouldWrapClientErrorsFromTraceApi() {
        HttpClientErrorException exception = new HttpClientErrorException(HttpStatus.BAD_REQUEST);
        ObjectNode payload = TestData.simpleObjectNode();

        when(transformer.transform(payload, "123", properties.getRemoveProperties())).thenReturn(payload);
        when(restTemplate.postForEntity(anyString(), any(HttpEntity.class), eq(JsonNode.class)))
                .thenReturn(authenticationResponse());
        when(restTemplate.exchange(anyString(), eq(HttpMethod.PUT), any(HttpEntity.class), eq(ObjectNode.class)))
                .thenThrow(exception);

        TraceApiException traceException =
                assertThrows(TraceApiException.class, () -> service.register(payload, "123"));

        assertSame(exception, traceException.getCause());
    }

    private ResponseEntity<JsonNode> authenticationResponse() {
        return ResponseEntity.ok(
                new TestData().add("payload",
                        payload -> payload.add("token", "TOKEN123"))
                        .objectNode());
    }
}
