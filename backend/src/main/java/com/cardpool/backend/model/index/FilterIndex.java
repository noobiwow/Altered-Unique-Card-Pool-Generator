package com.cardpool.backend.model.index;

import java.util.*;
import java.util.stream.Collectors;

import com.cardpool.backend.model.Card;
import com.cardpool.backend.model.CardFilter;

/**
 * Pre-bucketed inverted index over the full card list.
 *
 * Instead of scanning all N cards for every filter, we maintain sets
 * of card indices grouped by faction, rarity, set, subtype, and cost.
 * Filtering becomes set intersections, which for 1M cards is orders of
 * magnitude faster than linear scans.
 *
 * Build once after loading, reuse for every query.
 */
public class FilterIndex {

    private final List<Card> master;

    // Index maps: normalized key -> BitSet of positions in master
    private final Map<String, BitSet> byFaction = new HashMap<>();
    private final Map<String, BitSet> byRarity = new HashMap<>();
    private final Map<String, BitSet> bySet = new HashMap<>();
    private final Map<String, BitSet> bySubType = new HashMap<>();
    private final Map<String, BitSet> byCardType = new HashMap<>();
    private final Map<Integer, BitSet> byCost = new HashMap<>();

    private final BitSet notBanned;
    private final BitSet notSuspended;

    // -------------------------------------------------------------------------

    public FilterIndex(List<Card> cards) {
        this.master = cards;
        int n = cards.size();

        notBanned = new BitSet(n);
        notSuspended = new BitSet(n);

        for (int i = 0; i < n; i++) {
            final int idx = i; // 👈 make it effectively final
            Card c = cards.get(i);

            index(byFaction, key(c.getFactionName()), idx);
            index(byRarity, key(c.getRarityName()), idx);
            index(bySet, key(c.getSetCode()), idx);
            index(byCardType, key(c.getCardTypeName()), idx);

            if (c.getCardSubTypes() != null)
                c.getCardSubTypes().forEach(st -> index(bySubType, key(st.getName().getEn_label()), idx));

            int cost = c.getMainCost();
            if (cost >= 0)
                byCost.computeIfAbsent(cost, k -> new BitSet(n)).set(idx);

            if (!c.isBanned())
                notBanned.set(idx);
            if (!c.isSuspended())
                notSuspended.set(idx);
        }
    }

    // -------------------------------------------------------------------------
    // Query
    // -------------------------------------------------------------------------

    /**
     * Returns a list of cards matching the given filter,
     * using BitSet intersections instead of stream predicates.
     */
    public List<Card> query(CardFilter filter) {
        BitSet result = fullSet();

        CardFilter.Criteria c = filter.getCriteria();

        intersect(result, byFaction, c.faction());
        intersect(result, byRarity, c.rarity());
        intersect(result, bySet, c.set());
        intersect(result, bySubType, c.subType());
        intersect(result, byCardType, c.cardType());

        if (c.excludeBanned())
            result.and(notBanned);
        if (c.excludeSuspended())
            result.and(notSuspended);

        if (c.minCost() != null || c.maxCost() != null) {
            BitSet costBits = new BitSet(master.size());
            int min = c.minCost() != null ? c.minCost() : 0;
            int max = c.maxCost() != null ? c.maxCost() : Integer.MAX_VALUE;
            byCost.forEach((cost, bits) -> {
                if (cost >= min && cost <= max)
                    costBits.or(bits);
            });
            result.and(costBits);
        }

        // Custom predicates (lambdas) still run as a post-filter on the already-reduced
        // set
        List<Card> candidates = bitSetToList(result);
        List<java.util.function.Predicate<Card>> customs = c.customPredicates();
        if (customs.isEmpty())
            return candidates;

        return candidates.stream()
                .filter(card -> customs.stream().allMatch(p -> p.test(card)))
                .collect(Collectors.toCollection(ArrayList::new));
    }

    // -------------------------------------------------------------------------
    // Index helpers
    // -------------------------------------------------------------------------

    private void index(Map<String, BitSet> map, String key, int i) {
        if (key == null)
            return;
        map.computeIfAbsent(key, k -> new BitSet(master.size())).set(i);
    }

    private void intersect(BitSet result, Map<String, BitSet> index, String filterValue) {
        if (filterValue == null)
            return;
        BitSet bucket = index.get(key(filterValue));
        if (bucket == null) {
            result.clear();
            return;
        }
        result.and(bucket);
    }

    private BitSet fullSet() {
        BitSet bs = new BitSet(master.size());
        bs.set(0, master.size());
        return bs;
    }

    private List<Card> bitSetToList(BitSet bs) {
        List<Card> out = new ArrayList<>(bs.cardinality());
        for (int i = bs.nextSetBit(0); i >= 0; i = bs.nextSetBit(i + 1))
            out.add(master.get(i));
        return out;
    }

    private static String key(String s) {
        return s == null ? null : s.toLowerCase(Locale.ROOT);
    }

    // -------------------------------------------------------------------------
    // Distinct value accessors (for filter dropdowns)
    // -------------------------------------------------------------------------

    public Set<String> distinctFactions() {
        return displayKeys(byFaction);
    }

    public Set<String> distinctRarities() {
        return displayKeys(byRarity);
    }

    public Set<String> distinctSets() {
        return displayKeys(bySet);
    }

    public Set<String> distinctSubTypes() {
        return displayKeys(bySubType);
    }

    public Set<String> distinctCardTypes() {
        return displayKeys(byCardType);
    }

    private Set<String> displayKeys(Map<String, BitSet> index) {
        // Recover original casing from the master list — just return sorted keys
        // capitalised
        return index.keySet().stream()
                .map(k -> Character.toUpperCase(k.charAt(0)) + k.substring(1))
                .collect(Collectors.toCollection(TreeSet::new));
    }

    public int totalCards() {
        return master.size();
    }
}
