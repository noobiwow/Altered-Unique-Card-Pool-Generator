package com.cardpool.backend.model;

import java.util.List;
import java.util.Map;

import com.cardpool.backend.model.card_elements.CardSet;
import com.cardpool.backend.model.card_elements.Faction;
import com.cardpool.backend.model.card_elements.LocalizedNamedEntity;
import com.cardpool.backend.model.card_elements.NamedEntity;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Getter;
import lombok.Setter;

@JsonIgnoreProperties(ignoreUnknown = true)
@Getter
@Setter
public class Card {

    @JsonIgnore
    @JsonProperty("id")
    private String id;

    @JsonProperty("reference")
    private String reference;

    @JsonProperty("name")
    private String name;

    @JsonProperty("cardType")
    private LocalizedNamedEntity cardType;

    @JsonProperty("cardSet")
    private CardSet cardSet;

    @JsonProperty("cardSubTypes")
    private List<LocalizedNamedEntity> cardSubTypes;

    @JsonProperty("mainFaction")
    private Faction mainFaction;

    @JsonProperty("rarity")
    private NamedEntity rarity;

    @JsonProperty("elements")
    private Map<String, String> elements;

    @JsonProperty("mainEffect")
    private String mainEffect;

    @JsonProperty("echoEffect")
    private String echoEffect;

    @JsonProperty("imagePath")
    private String imagePath;

    @JsonProperty("isBanned")
    private boolean banned;

    @JsonProperty("isSuspended")
    private boolean suspended;

    @JsonProperty("isExclusive")
    private boolean exclusive;

    // --- Convenience helpers ---

    @JsonIgnore
    public String getFactionName() {
        return mainFaction != null ? mainFaction.getName() : null;
    }

    @JsonIgnore
    public String getRarityName() {
        return rarity != null ? rarity.getReference() : null;
    }

    @JsonIgnore
    public String getSetName() {
        return cardSet != null ? cardSet.getName() : null;
    }

    @JsonIgnore
    public String getSetCode() {
        return cardSet != null ? cardSet.getCode() : null;
    }

    @JsonIgnore
    public String getCardTypeName() {
        return cardType != null ? cardType.getName().getEn_label() : null;
    }

    @JsonIgnore
    public boolean hasSubType(String subType) {
        if (cardSubTypes == null)
            return false;
        return cardSubTypes.stream()
                .anyMatch(st -> st.getName() != null && st.getName().getEn_label().equalsIgnoreCase(subType));
    }

    @JsonIgnore
    public String getElementValue(String elementKey) {
        if (elements == null)
            return null;
        return elements.get(elementKey);
    }

    @JsonIgnore
    public int getMainCost() {
        String val = getElementValue("MAIN_COST");
        if (val == null)
            return -1;
        try {
            return Integer.parseInt(val);
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    @Override
    public String toString() {
        return String.format("Card{ref='%s', name='%s', faction='%s', rarity='%s', set='%s'}",
                reference, name, getFactionName(), getRarityName(), getSetName());
    }
}