package com.cardpool.backend.config;

import java.util.Map;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * {@code cardpool.*} settings that are not plain values.
 *
 * @param defaultSetWeights default share of each set code in a generated pool,
 *                          proposed by the generator form
 */
@ConfigurationProperties(prefix = "cardpool")
public record CardPoolProperties(Map<String, Double> defaultSetWeights) {

    public CardPoolProperties {
        defaultSetWeights = defaultSetWeights == null ? Map.of() : Map.copyOf(defaultSetWeights);
    }
}
