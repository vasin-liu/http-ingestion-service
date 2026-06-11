package com.pcitech.http.ingestion.core.webhook;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

public final class EventInfoNormalizer {

    private EventInfoNormalizer() {
    }

    public static ObjectNode normalize(ObjectMapper objectMapper, JsonNode body) {
        if (body == null || !body.isObject()) {
            throw new IllegalArgumentException("EventInfo body must be a JSON object");
        }
        ObjectNode normalized = (ObjectNode) body.deepCopy();
        if (!normalized.hasNonNull("ImgUrl") && normalized.hasNonNull("ImageUrl")) {
            normalized.set("ImgUrl", normalized.get("ImageUrl"));
        }
        return normalized;
    }
}
