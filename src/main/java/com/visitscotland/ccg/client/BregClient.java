package com.visitscotland.ccg.client;

import com.visitscotland.ccg.config.BregProperties;
import com.visitscotland.ccg.exception.VsException;
import com.visitscotland.ccg.payload.SubmissionPayloadTransformer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

@Service
public class BregClient {

    private static final Logger logger = LoggerFactory.getLogger(BregClient.class);
    
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final SubmissionPayloadTransformer payloadTransformer;
    private final BregProperties properties;

    public BregClient(RestTemplate restTemplate, ObjectMapper objectMapper, SubmissionPayloadTransformer payloadTransformer, BregProperties properties) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
        this.payloadTransformer = payloadTransformer;
        this.properties = properties;
    }

    public void healthCheck() {

        try {
            // Send POST request
            restTemplate.getForEntity(properties.getHealthUrl(), String.class);
        } catch (Exception e) {
            throw new VsException("Failed to send request to BREG service", e);
        }
    }

    public ResponseEntity<String> sendRequest(JsonNode payload, String submissionId, boolean traceApiFailure) throws VsException {

        if (!properties.isEnabled()) {
            throw new VsException("The submission to BREG service is not enabled");
        }

        try {
            logger.info("Sending request to BREG service at: {}", properties.getServiceUrl());

            HttpEntity<String> requestEntity = new HttpEntity<>(
                sanitize(payload, submissionId, traceApiFailure),
                getHeaders()
            );
            
            // Send POST request
            ResponseEntity<String> response = restTemplate.postForEntity(
                properties.getServiceUrl(),
                requestEntity, 
                String.class
            );
            
            logger.info("BREG service response status: {}", response.getStatusCode());
            return response;
            
        } catch (Exception e) {
            logger.error("Error sending request to BREG service: {}", e.getMessage(), e);
            throw new VsException("Failed to send request to BREG service", e);
        }
    }

    /**
     * Remove all non-necessary properties and includes the submissionId
     * @param payload
     * @param submissionId
     * @return
     */
    private String sanitize(JsonNode payload, String submissionId, boolean traceApiFailure) {
        ObjectNode modifiedPayload = payloadTransformer.transform(payload, submissionId, properties.getRemoveProperties());

        if (traceApiFailure) {
            modifiedPayload.put("traceApiFailure", traceApiFailure);
        }

        return objectMapper.writeValueAsString(modifiedPayload);
    }

    private HttpHeaders getHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        return headers;
    }
}