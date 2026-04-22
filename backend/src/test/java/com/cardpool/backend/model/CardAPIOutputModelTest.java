package com.cardpool.backend.model;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.io.File;
import java.nio.charset.StandardCharsets;

import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.Test;

import com.cardpool.backend.model.externalApi.CardAPIOutput;
import com.fasterxml.jackson.databind.ObjectMapper;

class CardAPIOutputModelTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void shouldDeserializeCardFromJson() throws Exception {
        String json = FileUtils.readFileToString(
                new File(
                        "./src/test/resources/java/com/cardpool/model/CardAPIOutput/oneItemWithEchoEffect.json"),
                StandardCharsets.UTF_8);
        ;

        CardAPIOutput card = objectMapper.readValue(json, CardAPIOutput.class);

        assertNotNull(card);
    }
}