package com.cardpool.backend.service;

import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import com.fasterxml.jackson.core.JacksonException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;

public class DeserializerService extends JsonDeserializer<Map<String, String>> {

    @Override
    public Map<String, String> deserialize(JsonParser p, DeserializationContext ctxt)
            throws IOException, JacksonException {
        JsonNode node = p.getCodec().readTree(p);

        if (node.isArray()) {
            return Collections.emptyMap();
        }

        if (node.isObject()) {
            Map<String, String> result = new HashMap<>();
            node.properties().forEach(entry -> result.put(entry.getKey(), entry.getValue().asText()));
            return result;
        }

        return Collections.emptyMap();
    }
}
