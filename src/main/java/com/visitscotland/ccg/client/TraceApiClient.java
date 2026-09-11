package com.visitscotland.ccg.client;

import com.visitscotland.ccg.config.TraceApiProperties;
import com.visitscotland.ccg.exception.TraceApiException;
import com.visitscotland.ccg.exception.VsException;
import com.visitscotland.ccg.payload.SubmissionPayloadTransformer;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

@Service
public class TraceApiClient {

    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(TraceApiClient.class);

    static final String REGISTER_ENDPOINT = "/visitscotland/register";
    static final String AUTH_ENDPOINT = "/auth/token";

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final TraceApiProperties properties;
    private final SubmissionPayloadTransformer transformer;

    public TraceApiClient(RestTemplate restTemplate, ObjectMapper objectMapper, TraceApiProperties properties,
                          SubmissionPayloadTransformer transformer) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
        this.properties = properties;
        this.transformer = transformer;
    }

    public ResponseEntity<ObjectNode> register(ObjectNode payload, String submissionId) throws VsException {
        if (!properties.isEnabled()) {
            throw new VsException("The submission to Trace API service is not enabled");
        }

        String token = getAuthenticationToken();
        String sanitizedPayload = sanitize(payload, submissionId);
        ResponseEntity<ObjectNode> response = submit(properties.getBaseUrl() + REGISTER_ENDPOINT, sanitizedPayload, token);
        if (!response.getStatusCode().is2xxSuccessful()) {
            logger.warn("Failed to register with Trace API: {}, submissionId: {}", response.getStatusCode(), submissionId);
        }
        return response;
    }

    /**
     * Get the authentication token from the Trace API using the provided API key and validates
     * that the service is alive.
     *
     * @return Token for the session
     *
     * @throws VsException when the authentication to their service fail.
     */
    public String getAuthenticationToken() throws TraceApiException {
        try {
            ResponseEntity<JsonNode> authentication = authenticate();
            JsonNode body = authentication.getBody();
            if (body != null && body.has("payload")) {
                ObjectNode payload = body.get("payload").asObject();
                if (payload.has("token")) {
                    return payload.get("token").asString();
                }
            }
        } catch (HttpClientErrorException e) {
            logger.error("Failed to authenticate with Trace API. Status code: {}, message: {}",
                    e.getStatusCode(), e.getMessage(), e);
            throw new TraceApiException("Failed to authenticate with Trace API. Status code: " + e.getStatusCode(), e);
        }

        throw new TraceApiException("Failed to authenticate with Trace API. The response did not contain a token");
    }

    /**
     * Makes the request to the authentication service
     */
    private ResponseEntity<JsonNode> authenticate(){
        return restTemplate.postForEntity(properties.getBaseUrl() + AUTH_ENDPOINT,
                new HttpEntity<>("", getHeaders(properties.getApiKey())), JsonNode.class);
    }

    /**
     * Remove all non-necessary properties and includes the submissionId
     * @param payload
     * @param submissionId
     * @return
     */
    private String sanitize(ObjectNode payload, String submissionId) {
        ObjectNode modifiedPayload = transformer.transform(payload, submissionId, properties.getRemoveProperties());

        return objectMapper.writeValueAsString(modifiedPayload);
    }

    private ResponseEntity<ObjectNode> submit(String url, String payload, String token) throws HttpClientErrorException {
        HttpEntity<String> entity = new HttpEntity<>(payload, getHeaders(token));

        try {
            return restTemplate.exchange(url, HttpMethod.PUT, entity, ObjectNode.class);
        } catch (HttpClientErrorException e) {
            throw new TraceApiException("Error submitting to Trace API.", e);
        }

    }

    private HttpHeaders getHeaders(String authorization){
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Authorization", authorization);

        return headers;
    }


}
