package com.cardpool.backend.source.csv;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.cardpool.backend.enums.FactionEnum;
import com.cardpool.backend.model.card_elements.CardSet;
import com.cardpool.backend.model.card_elements.Faction;
import com.cardpool.backend.model.card_elements.LocalizedElement;
import com.cardpool.backend.model.card_elements.LocalizedNamedEntity;
import com.cardpool.backend.model.card_elements.NamedEntity;

/**
 * The small CardsData tables (factions, sets, families, types, sub types,
 * keywords, effect fragments), with the shared objects cards are built from.
 */
final class Referentials {

    static final short NO_FRAGMENT = Short.MIN_VALUE;
    private static final Pattern KEYWORD_REF = Pattern.compile("\\[([A-Z0-9_]+)\\]");

    /** Card family: what every print of a card shares. */
    record Family(String nameEn, String nameLower, LocalizedNamedEntity cardType, String cardTypeLower,
            List<LocalizedNamedEntity> subTypes, Set<String> subTypesLower) {
    }

    final List<String> factionCodes = new ArrayList<>();
    final List<Faction> factions = new ArrayList<>();
    final Map<String, CardSet> sets = new HashMap<>();
    /** English sub type names, in CardSubTypes.csv order. */
    final List<String> subTypeNames = new ArrayList<>();
    final NamedEntity uniqueRarity = new NamedEntity();
    Family[] families;

    private int fragmentOffset;
    private String[] fragmentText;
    private String[] fragmentLower;
    private boolean[] echoTrigger;
    /** Type column of EffectFragments.csv: Trigger, Condition or Output. */
    private String[] fragmentType;

    static Referentials load(Path dir) throws IOException {
        Referentials r = new Referentials();
        r.uniqueRarity.setReference("UNIQUE");
        r.uniqueRarity.setName("Unique");

        for (Map<String, String> row : CsvReader.readRows(dir.resolve("Factions.csv"))) {
            r.factionIndex(row.get("Reference"), row.get("Name_en_US"));
        }
        for (Map<String, String> row : CsvReader.readRows(dir.resolve("Sets.csv"))) {
            CardSet set = new CardSet();
            set.setCode(row.get("Reference"));
            set.setReference(row.get("Reference"));
            set.setName(row.get("Name_en_US"));
            r.sets.put(set.getCode(), set);
        }

        Map<String, LocalizedNamedEntity> types = new HashMap<>();
        for (Map<String, String> row : CsvReader.readRows(dir.resolve("CardTypes.csv"))) {
            types.put(row.get("Reference"), entity(row));
        }
        Map<String, LocalizedNamedEntity> subTypes = new HashMap<>();
        for (Map<String, String> row : CsvReader.readRows(dir.resolve("CardSubTypes.csv"))) {
            subTypes.put(row.get("Reference"), entity(row));
            r.subTypeNames.add(row.get("Name_en_US"));
        }
        Map<Integer, List<LocalizedNamedEntity>> familySubTypes = new HashMap<>();
        for (Map<String, String> row : CsvReader.readRows(dir.resolve("CardFamilySubTypes.csv"))) {
            LocalizedNamedEntity subType = subTypes.get(row.get("CardSubTypeReference"));
            if (subType != null) {
                familySubTypes.computeIfAbsent(Integer.parseInt(row.get("CardFamilyId")), k -> new ArrayList<>())
                        .add(subType);
            }
        }
        List<Map<String, String>> familyRows = CsvReader.readRows(dir.resolve("CardFamilies.csv"));
        int maxFamily = familyRows.stream().mapToInt(row -> Integer.parseInt(row.get("Id"))).max().orElse(0);
        r.families = new Family[maxFamily + 1];
        for (Map<String, String> row : familyRows) {
            int id = Integer.parseInt(row.get("Id"));
            String name = row.get("Name_en_US");
            LocalizedNamedEntity type = types.get(row.get("CardTypeReference"));
            List<LocalizedNamedEntity> subs = List.copyOf(familySubTypes.getOrDefault(id, List.of()));
            Set<String> subsLower = new HashSet<>();
            subs.forEach(s -> subsLower.add(lower(s.getName().getEn_label())));
            r.families[id] = new Family(name, lower(name), type,
                    type == null ? null : lower(type.getName().getEn_label()), subs, Set.copyOf(subsLower));
        }

        Map<String, String> keywordDisplay = new HashMap<>();
        for (Map<String, String> row : CsvReader.readRows(dir.resolve("Keywords.csv"))) {
            keywordDisplay.put(row.get("Reference"), row.get("DisplayWeb_en_US"));
        }
        List<Map<String, String>> fragments = CsvReader.readRows(dir.resolve("EffectFragments.csv"));
        int min = 0, max = 0;
        for (Map<String, String> row : fragments) {
            int id = Integer.parseInt(row.get("IdGd"));
            min = Math.min(min, id);
            max = Math.max(max, id);
        }
        r.fragmentOffset = -min;
        r.fragmentText = new String[max - min + 1];
        r.fragmentLower = new String[max - min + 1];
        r.echoTrigger = new boolean[max - min + 1];
        r.fragmentType = new String[max - min + 1];
        for (Map<String, String> row : fragments) {
            int index = Integer.parseInt(row.get("IdGd")) + r.fragmentOffset;
            String text = render(row.get("Text_en_US"), keywordDisplay);
            r.fragmentText[index] = text;
            r.fragmentLower[index] = lower(text);
            r.echoTrigger[index] = "Trigger".equals(row.get("Type")) && "ECHO_EFFECT".equals(row.get("Slot"));
            r.fragmentType[index] = row.get("Type");
        }
        return r;
    }

    /** Index of a faction code, adding unknown ones (cards may use codes absent from Factions.csv). */
    synchronized byte factionIndex(String code, String nameEn) {
        int index = factionCodes.indexOf(code);
        if (index < 0) {
            Faction faction = new Faction();
            faction.setCode(code);
            faction.setName(nameEn == null || nameEn.isBlank() ? code : nameEn);
            for (FactionEnum f : FactionEnum.values()) {
                if (f.getCode().equals(code)) {
                    faction.setId(f.getId());
                }
            }
            factionCodes.add(code);
            factions.add(faction);
            index = factionCodes.size() - 1;
        }
        return (byte) index;
    }

    CardSet set(String code) {
        return sets.computeIfAbsent(code, c -> {
            CardSet set = new CardSet();
            set.setCode(c);
            set.setReference(c);
            set.setName(c);
            return set;
        });
    }

    Family family(int id) {
        return id >= 0 && id < families.length ? families[id] : null;
    }

    String fragmentText(int id) {
        int index = id + fragmentOffset;
        return id == NO_FRAGMENT || index < 0 || index >= fragmentText.length ? null : fragmentText[index];
    }

    String fragmentLower(int id) {
        int index = id + fragmentOffset;
        return id == NO_FRAGMENT || index < 0 || index >= fragmentLower.length ? null : fragmentLower[index];
    }

    /** Size of the fragment tables (ids from the lowest to the highest). */
    int fragmentCount() {
        return fragmentText.length;
    }

    /** Table index of a fragment id, -1 when empty or unknown. */
    int fragmentIndex(int id) {
        int index = id + fragmentOffset;
        return id == NO_FRAGMENT || index < 0 || index >= fragmentText.length || fragmentText[index] == null ? -1
                : index;
    }

    /** Fragment id of a table index. */
    int fragmentIdAt(int index) {
        return index - fragmentOffset;
    }

    String fragmentTextAt(int index) {
        return fragmentText[index];
    }

    String fragmentTypeAt(int index) {
        return fragmentType[index];
    }

    boolean isEchoTrigger(int id) {
        int index = id + fragmentOffset;
        return id != NO_FRAGMENT && index >= 0 && index < echoTrigger.length && echoTrigger[index];
    }

    /** Fragment ids whose rendered (lower-case) text contains {@code search}, as a lookup table. */
    boolean[] fragmentsContaining(String search) {
        boolean[] result = new boolean[fragmentLower.length];
        for (int i = 0; i < fragmentLower.length; i++) {
            result[i] = fragmentLower[i] != null && fragmentLower[i].contains(search);
        }
        return result;
    }

    boolean in(boolean[] table, int id) {
        int index = id + fragmentOffset;
        return id != NO_FRAGMENT && index >= 0 && index < table.length && table[index];
    }

    /**
     * Fragment text as shown on cards: keyword references replaced by
     * their display name, {@code #} markers removed.
     */
    static String render(String text, Map<String, String> keywordDisplay) {
        if (text == null) {
            return "";
        }
        Matcher m = KEYWORD_REF.matcher(text.replace("#", ""));
        StringBuilder out = new StringBuilder();
        while (m.find()) {
            String display = keywordDisplay.get(m.group(1));
            m.appendReplacement(out, Matcher.quoteReplacement(
                    "[" + (display == null || display.isBlank() ? m.group(1) : display) + "]"));
        }
        m.appendTail(out);
        return out.toString().strip();
    }

    private static LocalizedNamedEntity entity(Map<String, String> row) {
        String en = row.get("Name_en_US");
        LocalizedElement name = new LocalizedElement();
        name.setEn_label(en);
        name.setFr_label(orElse(row.get("Name_fr_FR"), en));
        name.setEs_label(orElse(row.get("Name_es_ES"), en));
        name.setDe_label(orElse(row.get("Name_de_DE"), en));
        name.setIt_label(orElse(row.get("Name_it_IT"), en));
        LocalizedNamedEntity entity = new LocalizedNamedEntity();
        entity.setReference(row.get("Reference"));
        entity.setName(name);
        return entity;
    }

    private static String orElse(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    static String lower(String s) {
        return s == null ? null : s.toLowerCase(Locale.ROOT);
    }
}
