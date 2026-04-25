package com.cardpool.backend.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Predicate;

/**
 * Immutable filter descriptor.
 * The Builder accumulates criteria; FilterIndex uses getCriteria() for
 * fast BitSet intersection; CardRepository fallback uses apply() for
 * sequential stream filtering.
 */
public class CardFilter {

    private final Criteria criteria;

    private CardFilter(Builder builder) {
        this.criteria = new Criteria(
                builder.faction,
                builder.rarity,
                builder.set,
                builder.subType,
                builder.cardType,
                builder.minCost,
                builder.maxCost,
                builder.excludeBanned,
                builder.excludeSuspended,
                Collections.unmodifiableList(new ArrayList<>(builder.customs)));
    }

    public Criteria getCriteria() {
        return criteria;
    }

    /** Sequential fallback — use FilterIndex.query() for large datasets. */
    public List<Card> apply(List<Card> cards) {
        return cards.stream().filter(this::test).toList();
    }

    private boolean test(Card c) {
        Criteria cr = criteria;
        if (cr.faction() != null && !cr.faction().equalsIgnoreCase(c.getMainFaction().getCode()))
            return false;
        if (cr.rarity() != null && !cr.rarity().equalsIgnoreCase(c.getRarityName()))
            return false;
        if (cr.set() != null && !cr.set().equalsIgnoreCase(c.getCardSet().getReference()))
            return false;
        if (cr.subType() != null && !c.hasSubType(cr.subType()))
            return false;
        if (cr.cardType() != null && !cr.cardType().equalsIgnoreCase(c.getCardTypeName()))
            return false;
        if (cr.excludeBanned() && c.isBanned())
            return false;
        if (cr.excludeSuspended() && c.isSuspended())
            return false;
        if (cr.minCost() != null && c.getMainCost() < cr.minCost())
            return false;
        if (cr.maxCost() != null && (c.getMainCost() < 0 || c.getMainCost() > cr.maxCost()))
            return false;
        for (Predicate<Card> p : cr.customPredicates())
            if (!p.test(c))
                return false;
        return true;
    }

    public static Builder builder() {
        return new Builder();
    }

    // -------------------------------------------------------------------------
    // Criteria record — readable by FilterIndex
    // -------------------------------------------------------------------------

    public record Criteria(
            String faction,
            String rarity,
            String set,
            String subType,
            String cardType,
            Integer minCost,
            Integer maxCost,
            boolean excludeBanned,
            boolean excludeSuspended,
            List<Predicate<Card>> customPredicates) {
    }

    // -------------------------------------------------------------------------
    // Builder
    // -------------------------------------------------------------------------

    public static class Builder {
        private String faction, rarity, set, subType, cardType;
        private Integer minCost, maxCost;
        private boolean excludeBanned, excludeSuspended;
        private final List<Predicate<Card>> customs = new ArrayList<>();

        public Builder faction(String v) {
            if (notBlank(v))
                faction = v;
            return this;
        }

        public Builder rarity(String v) {
            if (notBlank(v))
                rarity = v;
            return this;
        }

        public Builder set(String v) {
            if (notBlank(v))
                set = v;
            return this;
        }

        public Builder setCode(String v) {
            if (notBlank(v))
                customs.add(c -> c.getCardSet() != null && v.equalsIgnoreCase(c.getCardSet().getCode()));
            return this;
        }

        public Builder subType(String v) {
            if (notBlank(v))
                subType = v;
            return this;
        }

        public Builder cardType(String v) {
            if (notBlank(v))
                cardType = v;
            return this;
        }

        public Builder minMainCost(int v) {
            minCost = v;
            return this;
        }

        public Builder maxMainCost(int v) {
            maxCost = v;
            return this;
        }

        public Builder excludeBanned() {
            excludeBanned = true;
            return this;
        }

        public Builder excludeSuspended() {
            excludeSuspended = true;
            return this;
        }

        public Builder custom(Predicate<Card> p) {
            if (p != null)
                customs.add(p);
            return this;
        }

        public Builder elementEquals(String key, String value) {
            if (key != null && value != null)
                customs.add(c -> value.equals(c.getElementValue(key)));
            return this;
        }

        public CardFilter build() {
            return new CardFilter(this);
        }

        private static boolean notBlank(String s) {
            return s != null && !s.isBlank();
        }
    }
}
