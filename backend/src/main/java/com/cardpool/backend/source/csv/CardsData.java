package com.cardpool.backend.source.csv;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.Stream;

import com.cardpool.backend.model.Ability;
import com.cardpool.backend.model.AbilitySlot;
import com.cardpool.backend.model.Card;
import com.cardpool.backend.source.csv.Referentials.Family;

/**
 * All unique prints of the CardsData CSV export ({@code data/csv}), held in
 * memory. Cards are rebuilt on demand (keywords shown with single brackets in
 * effect texts).
 */
public final class CardsData {

    final Referentials ref;
    final List<Segment> segments;

    private CardsData(Referentials ref, List<Segment> segments) {
        this.ref = ref;
        this.segments = List.copyOf(segments);
    }

    /** Loads {@code csvDir} (the folder holding {@code Unique/} and the referential CSVs). */
    public static CardsData load(Path csvDir) throws IOException {
        if (!Files.isDirectory(csvDir.resolve("Unique"))) {
            throw new IOException("No Unique/ folder in " + csvDir.toAbsolutePath()
                    + " (run ./update-cardsdata.sh at the root of the project)");
        }
        Referentials ref = Referentials.load(csvDir);
        List<Path> setDirs;
        try (Stream<Path> dirs = Files.list(csvDir.resolve("Unique"))) {
            setDirs = dirs.filter(d -> Files.isRegularFile(d.resolve("UniquePrints.csv"))).sorted().toList();
        }
        ExecutorService pool = Executors.newFixedThreadPool(
                Math.max(1, Math.min(setDirs.size(), Runtime.getRuntime().availableProcessors())));
        try {
            List<Future<Segment>> futures = new ArrayList<>();
            for (Path dir : setDirs) {
                futures.add(pool.submit(() -> Segment.load(dir, ref)));
            }
            List<Segment> segments = new ArrayList<>();
            for (Future<Segment> future : futures) {
                segments.add(future.get());
            }
            return new CardsData(ref, segments);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while loading CardsData", e);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof IOException io) {
                throw io;
            }
            if (cause instanceof UncheckedIOException io) {
                throw io.getCause();
            }
            throw new IOException("Failed to load CardsData: " + cause.getMessage(), cause);
        } finally {
            pool.shutdownNow();
        }
    }

    public int size() {
        return segments.stream().mapToInt(s -> s.size).sum();
    }

    public long memoryBytes() {
        return segments.stream().mapToLong(Segment::memoryBytes).sum();
    }

    public List<String> setCodes() {
        return segments.stream().map(s -> s.setCode).toList();
    }

    Segment segment(String setCode) {
        for (Segment s : segments) {
            if (s.setCode.equalsIgnoreCase(setCode)) {
                return s;
            }
        }
        return null;
    }

    String factionCode(Segment s, int row) {
        return ref.factionCodes.get(s.faction[row]);
    }

    Family family(Segment s, int row) {
        return ref.family(s.family[row]);
    }

    // ------------------------------------------------------------------ cards

    public Card card(Segment s, int row) {
        Card card = new Card();
        card.setReference(s.reference(row));
        Family family = family(s, row);
        if (family != null) {
            card.setName(family.nameEn());
            card.setCardType(family.cardType());
            card.setCardSubTypes(family.subTypes());
        }
        card.setCardSet(s.cardSet);
        card.setMainFaction(ref.factions.get(s.faction[row]));
        card.setRarity(ref.uniqueRarity);
        card.setBanned((s.flags[row] & Segment.FLAG_BANNED) != 0);
        card.setSuspended((s.flags[row] & Segment.FLAG_SUSPENDED) != 0);
        card.setExclusive((s.flags[row] & Segment.FLAG_EXCLUSIVE) != 0);

        List<Ability> abilities = abilities(s, row);
        String[] effects = effectTexts(abilities);
        Map<String, String> elements = new LinkedHashMap<>();
        putStat(elements, "MAIN_COST", s.mainCost[row]);
        putStat(elements, "RECALL_COST", s.recallCost[row]);
        putStat(elements, "MOUNTAIN_POWER", s.mountain[row]);
        putStat(elements, "OCEAN_POWER", s.ocean[row]);
        putStat(elements, "FOREST_POWER", s.forest[row]);
        if (!effects[0].isEmpty()) {
            elements.put("MAIN_EFFECT", effects[0]);
        }
        if (!effects[1].isEmpty()) {
            elements.put("ECHO_EFFECT", effects[1]);
        }
        card.setElements(elements);
        card.setMainEffect(effects[0]);
        card.setEchoEffect(effects[1]);
        card.setAbilities(abilities);
        return card;
    }

    private static void putStat(Map<String, String> elements, String key, byte value) {
        if (value != Segment.MISSING) {
            elements.put(key, Integer.toString(value));
        }
    }

    /**
     * The abilities of a print in {@code DisplayIndex} order, each made of its
     * trigger, condition and effect fragments.
     */
    List<Ability> abilities(Segment s, int row) {
        int from = s.effStart[row], to = s.effStart[row + 1];
        if (from == to) {
            return List.of();
        }
        List<Integer> order = new ArrayList<>(to - from);
        for (int e = from; e < to; e++) {
            order.add(e);
        }
        if (order.size() > 1) {
            Collections.sort(order, (a, b) -> Short.compare(s.effects[a * 4], s.effects[b * 4]));
        }
        List<Ability> abilities = new ArrayList<>(order.size());
        for (int e : order) {
            int base = e * 4;
            AbilitySlot slot = ref.isEchoTrigger(s.effects[base + 1]) ? AbilitySlot.ECHO : AbilitySlot.MAIN;
            short trigger = s.effects[base + 1], condition = s.effects[base + 2], effect = s.effects[base + 3];
            Ability ability = Ability.of(slot, ref.fragmentText(trigger), ref.fragmentText(condition),
                    ref.fragmentText(effect), fragmentId(trigger), fragmentId(condition), fragmentId(effect));
            if (!ability.text().isEmpty()) {
                abilities.add(ability);
            }
        }
        return List.copyOf(abilities);
    }

    /** The id of a fragment column, null when empty or unknown. */
    private Integer fragmentId(short id) {
        return ref.fragmentText(id) == null ? null : (int) id;
    }

    /**
     * Number of unique prints using each fragment, by position in the ability
     * (0 trigger, 1 condition, 2 effect), indexed like {@link Referentials#fragments()}.
     */
    int[][] fragmentUses() {
        int[][] uses = new int[3][ref.fragmentCount()];
        for (Segment s : segments) {
            int rows = s.effStart[s.size];
            for (int e = 0; e < rows; e++) {
                int base = e * 4;
                for (int k = 0; k < 3; k++) {
                    int index = ref.fragmentIndex(s.effects[base + 1 + k]);
                    if (index >= 0) {
                        uses[k][index]++;
                    }
                }
            }
        }
        return uses;
    }

    /**
     * Main and echo effect texts: abilities separated by
     * two spaces (see {@link Ability#text()} for one ability).
     */
    static String[] effectTexts(List<Ability> abilities) {
        StringBuilder main = new StringBuilder(), echo = new StringBuilder();
        for (Ability ability : abilities) {
            StringBuilder target = ability.slot() == AbilitySlot.ECHO ? echo : main;
            if (target.length() > 0) {
                target.append("  ");
            }
            target.append(ability.text());
        }
        return new String[] { main.toString(), echo.toString() };
    }

    /** Main and echo effect texts of a print. */
    String[] effects(Segment s, int row) {
        return effectTexts(abilities(s, row));
    }

    /** True when one main-effect fragment of the row is in {@code table}. */
    boolean mainFragmentIn(Segment s, int row, boolean[] table) {
        for (int e = s.effStart[row]; e < s.effStart[row + 1]; e++) {
            int base = e * 4;
            if (ref.isEchoTrigger(s.effects[base + 1])) {
                continue;
            }
            for (int k = 1; k <= 3; k++) {
                if (ref.in(table, s.effects[base + k])) {
                    return true;
                }
            }
        }
        return false;
    }
}
