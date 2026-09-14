package com.visitscotland.ccg.client;

import com.visitscotland.ccg.config.BregProperties;
import com.visitscotland.ccg.config.RetryExecutorProperties;
import com.visitscotland.ccg.exception.VsException;
import com.visitscotland.ccg.payload.SubmissionPayloadTransformer;
import com.visitscotland.ccg.testutil.TestData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpEntity;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BregClientTest {

    private  static final String URL = "http://localhost/breg";

    @Mock
    private RestTemplate restTemplate;

    @Mock
    private SubmissionPayloadTransformer transformer;

    @Spy
    private ObjectMapper objectMapper;

    private BregProperties properties;

    private final ObjectMapper json = new ObjectMapper();

    private BregClient service;

    @BeforeEach
    void setUp() {
        //Default values for this unit test
        properties = new BregProperties();
        properties.setRemoveProperties(new String[]{"removeMe", "removeMeToo"});
        properties.setEnabled(true);
        properties.setServiceUrl(URL);

        service = new BregClient(restTemplate, objectMapper, transformer, properties,
                new RetryExecutor(new RetryExecutorProperties(1,0L)));
    }

    @Test
    @DisplayName("Should send a request to the configured BREG endpoint and return the response from BREG")
    void shouldSendRequestSuccessfully() {

        ResponseEntity<String> bregResponse = ResponseEntity.ok("SUCCESS");
        ObjectNode payload = json.createObjectNode();
        payload.put("name", "Jose");

        when(restTemplate.postForEntity(
                eq(URL),
                any(HttpEntity.class),
                eq(String.class)))
                .thenReturn(bregResponse);

        ResponseEntity<String> response = service.sendRequest(payload, "ID", false);

        assertSame(bregResponse, response);
    }

    @Test
    @DisplayName("Should add the configured headers")
    void shouldSendJsonContentType() {
        @SuppressWarnings("rawtypes")
        ArgumentCaptor<HttpEntity> requestCaptor = ArgumentCaptor.forClass(HttpEntity.class);

        when(restTemplate.postForEntity(eq(URL), any(), eq(String.class)))
                .thenReturn(ResponseEntity.ok("OK"));

        service.sendRequest(json.createObjectNode(), "123", false);

        verify(restTemplate).postForEntity(
                anyString(),
                requestCaptor.capture(),
                eq(String.class));

        assertEquals(
                MediaType.APPLICATION_JSON,
                requestCaptor.getValue().getHeaders().getContentType());
    }

    @Test
    @DisplayName("Should include the traceApiFailure flag when requested")
    void shouldIncludeTraceApiFailureFlag() {
        ObjectNode payload = TestData.simpleObjectNode();
        ArgumentCaptor<ObjectNode> nodeCaptor = ArgumentCaptor.forClass(ObjectNode.class);

        when(transformer.transform(payload, "123", properties.getRemoveProperties())).thenReturn(payload);
        when(restTemplate.postForEntity(eq(URL), any(), eq(String.class))).thenReturn(ResponseEntity.ok("OK"));

        service.sendRequest(payload, "123", true);

        verify(restTemplate).postForEntity(eq(URL), any(), eq(String.class));
        verify(objectMapper).writeValueAsString(nodeCaptor.capture());
        assertTrue(nodeCaptor.getValue().path("traceApiFailure").asBoolean());
    }

    @Test
    @DisplayName("Should omit the traceApiFailure flag when not required")
    void shouldNotIncludeTraceApiFailureFlag() {
        ArgumentCaptor<ObjectNode> nodeCaptor = ArgumentCaptor.forClass(ObjectNode.class);
        ObjectNode payload = TestData.simpleObjectNode();

        when(transformer.transform(payload, "123", properties.getRemoveProperties())).thenReturn(payload);
        when(restTemplate.postForEntity(eq(URL), any(), eq(String.class)))
                .thenReturn(ResponseEntity.ok("OK"));

        service.sendRequest(payload, "123", false);

        verify(objectMapper).writeValueAsString(nodeCaptor.capture());
        assertFalse(nodeCaptor.getValue().has("traceApiFailure"));
    }

    @Test
    @DisplayName("Should wrap RestTemplate exceptions in a VsException")
    void shouldWrapRestTemplateExceptions() {
        ObjectNode payload = json.createObjectNode();

        when(restTemplate.postForEntity(eq(URL), any(), eq(String.class)))
                .thenThrow(new RuntimeException("Oops!"));

        VsException exception = assertThrows(
                VsException.class,() -> service.sendRequest(payload, "123", false));

        assertNotNull(exception.getMessage());
        assertNotNull(exception.getCause());
        assertEquals("Oops!", exception.getCause().getMessage());
        assertNotEquals(exception.getMessage(), exception.getCause().getMessage());
    }
}