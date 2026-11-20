package com.cardpool.backend.source;

import java.util.function.Supplier;

import com.cardpool.backend.model.Card;

/**
 * A card matching a filter, whose full {@link Card} is only built when needed
 * (sources may hold millions of cards; samplers keep a handful).
 */
public final class Candidate {

    private final String factionCode;
    private final String setCode;
    private Supplier<Card> loader;
    private Card card;

    public Candidate(String factionCode, String setCode, Supplier<Card> loader) {
        this.factionCode = factionCode;
        this.setCode = setCode;
        this.loader = loader;
    }

    public static Candidate of(Card card) {
        Candidate candidate = new Candidate(
                card.getMainFaction() == null ? null : card.getMainFaction().getCode(),
                card.getSetCode(), null);
        candidate.card = card;
        return candidate;
    }

    public String factionCode() {
        return factionCode;
    }

    public String setCode() {
        return setCode;
    }

    public Card card() {
        if (card == null) {
            card = loader.get();
            loader = null;
        }
        return card;
    }
}
