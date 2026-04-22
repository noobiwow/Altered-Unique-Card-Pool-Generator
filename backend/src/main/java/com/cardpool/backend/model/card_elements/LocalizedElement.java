package com.cardpool.backend.model.card_elements;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class LocalizedElement {
    @JsonProperty("de")
    private String de_label;
    @JsonProperty("en")
    private String en_label;
    @JsonProperty("es")
    private String es_label;
    @JsonProperty("fr")
    private String fr_label;
    @JsonProperty("it")
    private String it_label;
}
