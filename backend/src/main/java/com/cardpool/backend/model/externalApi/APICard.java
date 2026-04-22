package com.cardpool.backend.model.externalApi;

import java.util.List;
import java.util.Map;

import com.cardpool.backend.model.card_elements.CardSet;
import com.cardpool.backend.model.card_elements.Faction;
import com.cardpool.backend.model.card_elements.LocalizedNamedEntity;
import com.cardpool.backend.model.card_elements.NamedEntity;
import com.cardpool.backend.service.DeserializerService;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

import lombok.Getter;

@JsonIgnoreProperties(ignoreUnknown = true)
@Getter
public class APICard {
    @JsonProperty("id")
    private String id;

    @JsonProperty("reference")
    private String reference;

    @JsonProperty("name")
    private Map<String, String> name;

    @JsonProperty("cardType")
    private LocalizedNamedEntity cardType;

    @JsonProperty("set")
    private CardSet set;

    @JsonProperty("cardSubTypes")
    private List<LocalizedNamedEntity> cardSubTypes;

    @JsonProperty("faction")
    private Faction faction;

    @JsonProperty("rarity")
    private NamedEntity rarity;

    @JsonProperty("mainCost")
    private int mainCost;

    @JsonProperty("recallCost")
    private int recallCost;

    @JsonProperty("oceanPower")
    private int oceanPower;

    @JsonProperty("mountainPower")
    private int mountainPower;

    @JsonProperty("forestPower")
    private int forestPower;

    @JsonProperty("mainEffect")
    @JsonDeserialize(using = DeserializerService.class)
    private Map<String, String> mainEffect;

    @JsonProperty("echoEffect")
    @JsonDeserialize(using = DeserializerService.class)
    private Map<String, String> echoEffect;

    @JsonProperty("imagePath")
    private Map<String, String> imagePath;

    @JsonProperty("isBanned")
    private boolean banned;

    @JsonProperty("isSuspended")
    private boolean suspended;

    @JsonProperty("isExclusive")
    private boolean exclusive;
}
