package com.cardpool.backend.model.card_elements;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class Rarity {
    @JsonProperty("id")
    private String id;

    @JsonProperty("reference")
    private String reference;
}
