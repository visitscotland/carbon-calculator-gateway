package com.visitscotland.ccg.payload;

import com.visitscotland.utils.Contract;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class SubmissionPayloadTransformer {

    private static final String DELIMITER = "---";
    static final String SUBMISSION_ID = "vsUID";

    public ObjectNode transform(JsonNode payload, String submissionId, String[] removeFields) {
        ObjectNode modifiedPayload = payload.deepCopy().asObject();

        for (String property : removeFields) {
            modifiedPayload.remove(property);
        }

        modifiedPayload.put(SUBMISSION_ID, submissionId);

        normalizeVariantFields(modifiedPayload);

        return modifiedPayload;
    }

    /**
     * Normalises variant fields. Fields following the convention {@code <field>-<variant>} are converted to {@code <field>}
     * If both the base field and one or more variants exist, the base field takes precedence and the variants are discarded.
     */
    public void normalizeVariantFields(ObjectNode modifiedPayload) {

        List<String> variants = modifiedPayload.propertyNames().stream()
                .filter(propertyName -> propertyName.contains(DELIMITER))
                .collect(Collectors.toList());

        for (String variant: variants) {
            JsonNode value = modifiedPayload.get(variant);
            if (!value.isNull() && !Contract.isEmpty(value.asString())) {
                modifiedPayload.putIfAbsent(variant.substring(0, variant.lastIndexOf(DELIMITER)), value);
            }
            modifiedPayload.remove(variant);
        }
    }


}
