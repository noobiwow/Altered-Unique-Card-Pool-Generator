package com.cardpool.backend.model.card_elements;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Getter;

@JsonIgnoreProperties(ignoreUnknown = true)
@Getter
public class LocalizedNamedEntity {
    @JsonProperty("id")
    private String id;

    @JsonProperty("reference")
    private String reference;

    @JsonProperty("name")
    private LocalizedElement name;
}