package com.cardpool.backend.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Immutable filter descriptor, built with {@link #builder()}.
 * <p>
 * Criteria are kept structured (not as opaque predicates) whenever possible so
 * that indexed card sources can evaluate them without building cards; see
 * {@code CsvCardSource}. {@link #test(Card)} is the reference implementation
 * every source must agree with.
 */
public final class CardFilter {

    /**
     * @param faction            faction code, compared with {@code mainFaction.code}
     * @param set                set reference, compared with {@code cardSet.reference}
     * @param subTypes           English sub type names; the card needs one of them
     * @param cardType           English card type name, e.g. {@code Character}
     * @param minCost            minimum hand cost
     * @param maxCost            maximum hand cost
     * @param minReserveCost     minimum reserve cost
     * @param maxReserveCost     maximum reserve cost
     * @param excludeBanned      drop banned cards
     * @param excludeSuspended   drop suspended cards
     * @param setWeights         share of each set code in a drawn pool (not a filter)
     * @param setCode            set code, compared with {@code cardSet.code}
     * @param excludedReferences references that must not be returned
     * @param textSearch         lower-cased text searched in name, reference and main effect
     * @param customPredicates   anything else; forces sources to build every card
     */
    public record Criteria(
            String faction,
            String set,
            List<String> subTypes,
            String cardType,
            Integer minCost,
            Integer maxCost,
            Integer minReserveCost,
            Integer maxReserveCost,
            boolean excludeBanned,
            boolean excludeSuspended,
            Map<String, Double> setWeights,
            String setCode,
            Set<String> excludedReferences,
            String textSearch,
            List<Predicate<Card>> customPredicates) {
    }

    private final Criteria criteria;

    private CardFilter(Builder b) {
        this.criteria = new Criteria(b.faction, b.set, b.subTypes, b.cardType,
                b.minCost, b.maxCost, b.minReserveCost, b.maxReserveCost,
                b.excludeBanned, b.excludeSuspended, b.setWeights, b.setCode,
                b.excludedReferences == null ? Set.of() : Set.copyOf(b.excludedReferences),
                b.textSearch, List.copyOf(b.customs));
    }

    public static Builder builder() {
        return new Builder();
    }

    public Criteria getCriteria() {
        return criteria;
    }

    /** True when some criteria can only be evaluated on a built card. */
    public boolean hasCustomPredicates() {
        return !criteria.customPredicates().isEmpty();
    }

    public boolean test(Card c) {
        Criteria cr = criteria;
        if (cr.faction() != null
                && (c.getMainFaction() == null || !cr.faction().equalsIgnoreCase(c.getMainFaction().getCode())))
            return false;
        if (cr.set() != null && (c.getCardSet() == null || !cr.set().equalsIgnoreCase(c.getCardSet().getReference())))
            return false;
        if (cr.setCode() != null && (c.getCardSet() == null || !cr.setCode().equalsIgnoreCase(c.getCardSet().getCode())))
            return false;
        if (!cr.excludedReferences().isEmpty() && cr.excludedReferences().contains(c.getReference()))
            return false;
        if (cr.subTypes() != null && !cr.subTypes().isEmpty() && cr.subTypes().stream().noneMatch(c::hasSubType))
            return false;
        if (cr.cardType() != null && !cr.cardType().equalsIgnoreCase(c.getCardTypeName()))
            return false;
        if (cr.excludeBanned() && c.isBanned())
            return false;
        if (cr.excludeSuspended() && c.isSuspended())
            return false;
        if (!inRange(c.getMainCost(), cr.minCost(), cr.maxCost()))
            return false;
        if (!inRange(c.getRecallCost(), cr.minReserveCost(), cr.maxReserveCost()))
            return false;
        if (cr.textSearch() != null && !matchesText(c, cr.textSearch()))
            return false;
        for (Predicate<Card> p : cr.customPredicates())
            if (!p.test(c))
                return false;
        return true;
    }

    /**
     * Cost range check shared by every source. A missing cost is -1: it fails
     * any maximum, and any minimum above -1.
     */
    public static boolean inRange(int cost, Integer min, Integer max) {
        if (min != null && cost < min)
            return false;
        return max == null || (cost >= 0 && cost <= max);
    }

    /** Name, reference or main effect contains the (lower-cased) search. */
    public static boolean matchesText(Card c, String search) {
        return contains(c.getName(), search) || contains(c.getReference(), search) || contains(c.getMainEffect(), search);
    }

    private static boolean contains(String text, String search) {
        return text != null && text.toLowerCase(Locale.ROOT).contains(search);
    }

    public static final class Builder {
        private String faction, set, cardType, setCode, textSearch;
        private List<String> subTypes;
        private Integer minCost, maxCost, minReserveCost, maxReserveCost;
        private boolean excludeBanned, excludeSuspended;
        private Map<String, Double> setWeights;
        private Set<String> excludedReferences;
        private final List<Predicate<Card>> customs = new ArrayList<>();

        private Builder() {
        }

        public Builder faction(String v) {
            if (notBlank(v))
                faction = v;
            return this;
        }

        /** Set reference, as sent by the generator form. */
        public Builder set(String v) {
            if (notBlank(v))
                set = v;
            return this;
        }

        public Builder setCode(String v) {
            if (notBlank(v))
                setCode = v;
            return this;
        }

        public Builder subTypes(List<String> v) {
            if (v != null) {
                List<String> nonBlank = v.stream().filter(Builder::notBlank).toList();
                if (!nonBlank.isEmpty())
                    subTypes = nonBlank;
            }
            return this;
        }

        public Builder cardType(String v) {
            if (notBlank(v))
                cardType = v;
            return this;
        }

        public Builder handCost(CostConstraint constraint) {
            if (constraint != null) {
                minCost = constraint.min();
                maxCost = constraint.max();
            }
            return this;
        }

        public Builder reserveCost(CostConstraint constraint) {
            if (constraint != null) {
                minReserveCost = constraint.min();
                maxReserveCost = constraint.max();
            }
            return this;
        }

        public Builder excludeBanned() {
            return excludeBanned(true);
        }

        public Builder excludeBanned(boolean exclude) {
            excludeBanned = exclude;
            return this;
        }

        public Builder excludeSuspended() {
            return excludeSuspended(true);
        }

        public Builder excludeSuspended(boolean exclude) {
            excludeSuspended = exclude;
            return this;
        }

        public Builder setWeights(Map<String, Double> v) {
            setWeights = v;
            return this;
        }

        public Builder excludeReferences(Set<String> references) {
            if (references != null && !references.isEmpty())
                excludedReferences = references;
            return this;
        }

        public Builder textSearch(String v) {
            if (notBlank(v))
                textSearch = v.strip().toLowerCase(Locale.ROOT);
            return this;
        }

        public Builder custom(Predicate<Card> p) {
            if (p != null)
                customs.add(p);
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
