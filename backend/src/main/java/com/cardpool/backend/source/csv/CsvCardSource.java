package com.cardpool.backend.source.csv;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.function.Consumer;

import com.cardpool.backend.dto.Catalog;
import com.cardpool.backend.dto.EffectFragment;
import com.cardpool.backend.dto.FactionDTO;
import com.cardpool.backend.dto.SetDTO;
import com.cardpool.backend.dto.SubtypeDTO;
import com.cardpool.backend.enums.FactionEnum;
import com.cardpool.backend.exception.CardDataUnavailableException;
import com.cardpool.backend.model.Ability;
import com.cardpool.backend.model.Card;
import com.cardpool.backend.model.CardFilter;
import com.cardpool.backend.source.Candidate;
import com.cardpool.backend.source.CardSource;
import com.cardpool.backend.source.csv.Referentials.Family;

import lombok.extern.slf4j.Slf4j;

/**
 * Card source backed by the CardsData CSV export, loaded once in memory (in the
 * background at startup). Filters are evaluated on the columns; full cards are
 * only built for matching rows.
 */
@Slf4j
public class CsvCardSource implements CardSource {

    private final Path csvDir;
    private final CompletableFuture<CardsData> data;
    private volatile List<EffectFragment> fragments;

    public CsvCardSource(Path csvDir) {
        this.csvDir = csvDir;
        this.data = CompletableFuture.supplyAsync(this::load);
    }

    /** For tests: a source over already loaded data. */
    public CsvCardSource(CardsData loaded) {
        this.csvDir = null;
        this.data = CompletableFuture.completedFuture(loaded);
    }

    private CardsData load() {
        long start = System.nanoTime();
        try {
            CardsData loaded = CardsData.load(csvDir);
            log.info("Loaded {} unique cards from {} ({} sets) in {} ms, ~{} MB",
                    loaded.size(), csvDir.toAbsolutePath(), loaded.setCodes().size(),
                    (System.nanoTime() - start) / 1_000_000, loaded.memoryBytes() / 1_000_000);
            return loaded;
        } catch (IOException e) {
            log.error("Could not load CardsData from {}: {}", csvDir.toAbsolutePath(), e.getMessage());
            throw new CompletionException(e);
        }
    }

    /** Waits for the background load if it is still running. */
    public CardsData data() {
        try {
            return data.join();
        } catch (CompletionException e) {
            Throwable cause = e.getCause() == null ? e : e.getCause();
            throw new CardDataUnavailableException("Card data unavailable: " + cause.getMessage(), cause);
        }
    }

    @Override
    public String name() {
        return "csv";
    }

    @Override
    public void scan(CardFilter filter, String setCode, Consumer<Candidate> sink) {
        CardsData d = data();
        Matcher matcher = new Matcher(d, filter.getCriteria());
        if (matcher.impossible) {
            return;
        }
        boolean customs = filter.hasCustomPredicates();
        for (Segment s : d.segments) {
            if (setCode != null && !s.setCode.equalsIgnoreCase(setCode)) {
                continue;
            }
            if (!matcher.acceptsSegment(s)) {
                continue;
            }
            boolean[] excluded = matcher.excludedRows(s);
            for (int row = 0; row < s.size; row++) {
                if ((excluded != null && excluded[row]) || !matcher.accepts(s, row)) {
                    continue;
                }
                final int r = row;
                if (customs) {
                    Card card = d.card(s, r);
                    if (!filter.test(card)) {
                        continue;
                    }
                    Candidate candidate = Candidate.of(card);
                    sink.accept(candidate);
                } else {
                    sink.accept(new Candidate(d.factionCode(s, r), s.setCode, () -> d.card(s, r)));
                }
            }
        }
    }

    /** Playable factions, the sets holding unique cards, and every sub type of CardsData. */
    @Override
    public Catalog catalog() {
        CardsData d = data();
        List<FactionDTO> factions = java.util.Arrays.stream(FactionEnum.values())
                .map(f -> new FactionDTO(f.getId(), f.getCode(), f.getName())).toList();
        List<SetDTO> sets = d.segments.stream()
                .map(s -> new SetDTO(s.setCode, s.cardSet.getName(), 0)).toList();
        List<SubtypeDTO> subtypes = d.ref.subTypeNames.stream().sorted().map(SubtypeDTO::new).toList();
        return new Catalog(factions, sets, subtypes);
    }

    @Override
    public List<Card> findByReferences(Collection<String> references) {
        CardsData d = data();
        List<Card> result = new ArrayList<>(references.size());
        for (String reference : references) {
            if (reference == null) {
                continue;
            }
            Segment s = segmentOf(d, reference);
            int row = s == null ? -1 : s.find(reference);
            if (row < 0) {
                // reference not following ALT_<SET>_... : try every set
                for (Segment other : d.segments) {
                    row = other.find(reference);
                    if (row >= 0) {
                        s = other;
                        break;
                    }
                }
            }
            if (row >= 0) {
                result.add(d.card(s, row));
            }
        }
        return result;
    }

    /** Fragments used by unique cards, most used first within each part (computed once). */
    @Override
    public List<EffectFragment> fragments() {
        List<EffectFragment> result = fragments;
        if (result == null) {
            CardsData d = data();
            int[][] uses = d.fragmentUses();
            String[] parts = { "TRIGGER", "CONDITION", "EFFECT" };
            List<EffectFragment> list = new ArrayList<>();
            for (int k = 0; k < parts.length; k++) {
                for (int index = 0; index < uses[k].length; index++) {
                    String text = d.ref.fragmentTextAt(index);
                    // The static marker stored as a condition is not one (see Ability.of).
                    boolean marker = k == 1 && Ability.STATIC_MARKER.equals(text);
                    if (uses[k][index] > 0 && text != null && !text.isBlank() && !marker) {
                        list.add(new EffectFragment(d.ref.fragmentIdAt(index), parts[k], text, uses[k][index]));
                    }
                }
            }
            list.sort(java.util.Comparator.comparing(EffectFragment::part)
                    .thenComparing(java.util.Comparator.comparingInt(EffectFragment::uses).reversed()));
            result = fragments = List.copyOf(list);
        }
        return result;
    }

    /** References look like ALT_EOLE_B_AX_106_U_462: the set is the second token. */
    private static Segment segmentOf(CardsData d, String reference) {
        int first = reference.indexOf('_');
        int second = first < 0 ? -1 : reference.indexOf('_', first + 1);
        return second < 0 ? null : d.segment(reference.substring(first + 1, second));
    }

    // ------------------------------------------------------------------

    /**
     * Column-level evaluation of {@link CardFilter.Criteria}, equivalent to
     * {@link CardFilter#test(Card)} for everything but custom predicates.
     */
    static final class Matcher {
        private final CardsData d;
        private final CardFilter.Criteria c;
        private final int factionIndex; // -1 = any
        final boolean impossible;
        private final boolean[] familyOk; // null = any
        private final String search;
        private final byte[] searchBytes;
        private final boolean[] searchFragments;
        private final boolean searchMaySpanFragments;

        Matcher(CardsData d, CardFilter.Criteria c) {
            this.d = d;
            this.c = c;
            boolean impossibleFilter = false;

            if (c.faction() != null) {
                int index = -1;
                for (int i = 0; i < d.ref.factionCodes.size(); i++) {
                    if (d.ref.factionCodes.get(i).equalsIgnoreCase(c.faction())) {
                        index = i;
                    }
                }
                factionIndex = index;
                impossibleFilter |= index < 0;
            } else {
                factionIndex = -1;
            }

            boolean filterFamilies = c.cardType() != null || (c.subTypes() != null && !c.subTypes().isEmpty());
            boolean[] families = null;
            if (filterFamilies) {
                families = new boolean[d.ref.families.length];
                for (int id = 0; id < families.length; id++) {
                    Family f = d.ref.families[id];
                    families[id] = f != null && familyMatches(f);
                }
            }
            this.familyOk = families;

            if (c.textSearch() != null) {
                search = c.textSearch();
                searchBytes = search.getBytes(StandardCharsets.ISO_8859_1);
                searchFragments = d.ref.fragmentsContaining(search);
                // A search can only span two fragments if it contains a separator character.
                searchMaySpanFragments = search.chars().anyMatch(ch -> Character.isWhitespace(ch) || ch == '[' || ch == ']');
            } else {
                search = null;
                searchBytes = null;
                searchFragments = null;
                searchMaySpanFragments = false;
            }
            this.impossible = impossibleFilter;
        }

        private boolean familyMatches(Family f) {
            if (c.cardType() != null && (f.cardTypeLower() == null || !f.cardTypeLower().equals(lower(c.cardType())))) {
                return false;
            }
            if (c.subTypes() != null && !c.subTypes().isEmpty()) {
                return c.subTypes().stream().anyMatch(st -> f.subTypesLower().contains(lower(st)));
            }
            return true;
        }

        boolean acceptsSegment(Segment s) {
            if (c.set() != null && !c.set().equalsIgnoreCase(s.cardSet.getReference())) {
                return false;
            }
            return c.setCode() == null || c.setCode().equalsIgnoreCase(s.cardSet.getCode());
        }

        /** Rows of the segment whose reference is excluded, or null. */
        boolean[] excludedRows(Segment s) {
            if (c.excludedReferences().isEmpty()) {
                return null;
            }
            boolean[] excluded = new boolean[s.size];
            for (String reference : c.excludedReferences()) {
                int row = s.find(reference);
                if (row >= 0) {
                    excluded[row] = true;
                }
            }
            return excluded;
        }

        boolean accepts(Segment s, int row) {
            if (factionIndex >= 0 && s.faction[row] != factionIndex) {
                return false;
            }
            if (familyOk != null) {
                int f = s.family[row];
                if (f < 0 || f >= familyOk.length || !familyOk[f]) {
                    return false;
                }
            }
            byte flags = s.flags[row];
            if (c.excludeBanned() && (flags & Segment.FLAG_BANNED) != 0) {
                return false;
            }
            if (c.excludeSuspended() && (flags & Segment.FLAG_SUSPENDED) != 0) {
                return false;
            }
            if (!CardFilter.inRange(s.mainCost[row], c.minCost(), c.maxCost())
                    || !CardFilter.inRange(s.recallCost[row], c.minReserveCost(), c.maxReserveCost())) {
                return false;
            }
            return search == null || matchesSearch(s, row);
        }

        private boolean matchesSearch(Segment s, int row) {
            Family f = d.family(s, row);
            if (f != null && f.nameLower() != null && f.nameLower().contains(search)) {
                return true;
            }
            if (s.referenceContains(row, searchBytes)) {
                return true;
            }
            if (d.mainFragmentIn(s, row, searchFragments)) {
                return true;
            }
            return searchMaySpanFragments && lower(d.effects(s, row)[0]).contains(search);
        }

        private static String lower(String s) {
            return s.toLowerCase(Locale.ROOT);
        }
    }
}
