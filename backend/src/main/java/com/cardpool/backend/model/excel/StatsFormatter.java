package com.cardpool.backend.model.excel;

import java.util.Comparator;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

public class StatsFormatter {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    public String formatText(EffectInventory inventory) {
        StringBuilder sb = new StringBuilder();
        String line = "─".repeat(80);

        sb.append("\n").append(line).append("\n");
        sb.append("  CARD EFFECT INVENTORY\n");
        sb.append(line).append("\n\n");

        sb.append(String.format("  Total effects  : %,d%n", inventory.totalEffects()));
        sb.append(String.format("  Sets           : %,d%n", inventory.countBySet().size()));
        sb.append(String.format("  Unique structures: %,d%n", inventory.countByEffectStructure().size()));

        sb.append("\n").append(section("EFFECTS BY SET"));
        sb.append(String.format("  %-40s  %8s%n", "Set", "Count"));
        sb.append("  " + "─".repeat(52) + "\n");
        inventory.countBySet().entrySet().stream()
                .sorted(byValueDesc())
                .forEach(e -> sb.append(String.format("  %-40s  %8,d%n", e.getKey(), e.getValue())));

        sb.append("\n").append(section("EFFECTS BY TYPE"));
        sb.append(String.format("  %-20s  %8s  %8s%n", "Type", "Count", "Share"));
        sb.append("  " + "─".repeat(42) + "\n");
        inventory.countByType().entrySet().stream()
                .sorted(byValueDesc())
                .forEach(e -> sb.append(String.format("  %-20s  %8,d  %7.1f%%%n",
                        e.getKey(), e.getValue(),
                        pct(e.getValue(), inventory.totalEffects()))));

        sb.append("\n").append(section("EFFECTS BY TRIGGER"));
        sb.append(String.format("  %-20s  %8s%n", "Trigger", "Count"));
        sb.append("  " + "─".repeat(32) + "\n");
        inventory.countByTrigger().entrySet().stream()
                .sorted(byValueDesc())
                .limit(20)
                .forEach(e -> sb.append(String.format("  %-20s  %8,d%n", e.getKey(), e.getValue())));

        sb.append("\n").append(section("EFFECT STRUCTURES (Trigger + Condition + Effect Body) - by occurrence"));
        sb.append(String.format("  %-65s  %8s%n", "Effect Structure", "Count"));
        sb.append("  " + "─".repeat(77) + "\n");
        inventory.countByEffectStructure().entrySet().stream()
                .sorted(byValueDesc())
                .limit(40)
                .forEach(e -> {
                    String effectPreview = truncate(e.getKey(), 63);
                    sb.append(String.format("  %-65s  %8,d%n", effectPreview, e.getValue()));
                });

        sb.append("\n").append(section("EFFECT STRUCTURES BY SET"));
        for (Map.Entry<String, Map<String, Long>> setEntry : inventory.countByEffectStructureBySet().entrySet()) {
            String setName = setEntry.getKey();
            Map<String, Long> effectCounts = setEntry.getValue();

            sb.append(String.format("\n  ▸ %s  (%,d unique structures)%n", setName, effectCounts.size()));
            sb.append(String.format("    %-70s  %8s%n", "Effect Structure", "Count"));
            sb.append("    " + "─".repeat(82) + "\n");
            effectCounts.entrySet().stream()
                    .sorted(byValueDesc())
                    .limit(30)
                    .forEach(e -> {
                        String effectPreview = truncate(e.getKey(), 68);
                        sb.append(String.format("    %-70s  %8,d%n", effectPreview, e.getValue()));
                    });
        }

        sb.append("\n").append(section("EFFECT STRUCTURES BY TYPE"));
        for (Map.Entry<String, Map<String, Long>> typeEntry : inventory.countByEffectStructureByType().entrySet()) {
            String typeName = typeEntry.getKey();
            Map<String, Long> effectCounts = typeEntry.getValue();

            sb.append(String.format("\n  ▸ %s  (%,d unique structures)%n", typeName, effectCounts.size()));
            sb.append(String.format("    %-70s  %8s%n", "Effect Structure", "Count"));
            sb.append("    " + "─".repeat(82) + "\n");
            effectCounts.entrySet().stream()
                    .sorted(byValueDesc())
                    .limit(15)
                    .forEach(e -> {
                        String effectPreview = truncate(e.getKey(), 68);
                        sb.append(String.format("    %-70s  %8,d%n", effectPreview, e.getValue()));
                    });
        }

        sb.append("\n").append(line).append("\n");
        return sb.toString();
    }

    public String formatJson(EffectInventory inventory) {
        ObjectNode root = MAPPER.createObjectNode();
        root.put("totalEffects", inventory.totalEffects());
        root.put("sets", inventory.countBySet().size());
        root.put("uniqueStructures", inventory.countByEffectStructure().size());
        root.set("effectStructureBySet", toNestedJsonNode(inventory.countByEffectStructureBySet()));
        root.set("effectBodyBySet", toNestedJsonNode(inventory.countByEffectBodyBySet()));
        root.set("triggerBySet", toNestedJsonNode(inventory.countByTriggerBySet()));
        root.set("conditionBySet", toNestedJsonNode(inventory.countByConditionBySet()));
        root.set("countByType", toJsonNode(inventory.countByType()));
        root.set("countByTrigger", toJsonNode(inventory.countByTrigger()));
        root.set("countByCondition", toJsonNode(inventory.countByCondition()));
        root.set("countByEffectStructure", toJsonNode(inventory.countByEffectStructure()));
        root.set("effectStructureByType", toNestedJsonNode(inventory.countByEffectStructureByType()));
        root.set("effectEntries", toJsonArray(inventory.getEntries()));
        root.set("effectEntriesBySet", toJsonEntriesBySet(inventory));
        return root.toPrettyString();
    }

    private ObjectNode toJsonEntriesBySet(EffectInventory inventory) {
        ObjectNode root = MAPPER.createObjectNode();
        inventory.getEntries().stream()
                .collect(java.util.stream.Collectors.groupingBy(EffectEntry::getSet))
                .entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(setEntry -> {
                    ArrayNode arr = root.putArray(setEntry.getKey());
                    setEntry.getValue().stream()
                            .map(EffectEntry::getEffectStructureJson)
                            .forEach(arr::add);
                });
        return root;
    }

    private ArrayNode toJsonArray(List<EffectEntry> entries) {
        ArrayNode arr = MAPPER.createArrayNode();
        entries.stream()
                .map(EffectEntry::getEffectStructureJson)
                .forEach(arr::add);
        return arr;
    }

    public String formatEntriesBySet(EffectInventory inventory) {
        StringBuilder sb = new StringBuilder();
        String line = "═".repeat(80);

        for (Map.Entry<String, List<EffectEntry>> setGroup : inventory.getEntries().stream()
                .collect(java.util.stream.Collectors.groupingBy(EffectEntry::getSet)).entrySet()) {

            sb.append("\n").append(line).append("\n");
            sb.append(String.format("  SET: %s (%d effects)%n", setGroup.getKey(), setGroup.getValue().size()));
            sb.append(line).append("\n\n");

            for (EffectEntry entry : setGroup.getValue()) {
                sb.append(formatEntry(entry));
                sb.append("\n");
            }
        }

        return sb.toString();
    }

    public String formatEntry(EffectEntry entry) {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("[%s] %s%n", entry.getSet(), entry.getType()));
        if (entry.getTrigger() != null) {
            sb.append(String.format("  Trigger: %s%n", entry.getTrigger()));
        }
        if (entry.getCondition() != null) {
            sb.append(String.format("  Condition: %s%n", entry.getCondition()));
        }
        sb.append(String.format("  Effect: %s%n", entry.getEffectBody()));
        if (!entry.getNamedEffects().isEmpty()) {
            sb.append(String.format("  Keywords: %s%n", entry.getNamedEffects()));
        }
        return sb.toString();
    }

    private String section(String title) {
        return "  ── " + title + "\n";
    }

    private double pct(long part, long total) {
        return total == 0 ? 0 : 100.0 * part / total;
    }

    private <V> Comparator<Map.Entry<String, V>> byValueDesc() {
        return (a, b) -> {
            long av = (a.getValue() instanceof Long l) ? l : 0;
            long bv = (b.getValue() instanceof Long l) ? l : 0;
            return Long.compare(bv, av);
        };
    }

    private ObjectNode toJsonNode(Map<String, Long> map) {
        ObjectNode node = MAPPER.createObjectNode();
        map.entrySet().stream()
                .sorted(byValueDesc())
                .forEach(e -> node.put(e.getKey(), e.getValue()));
        return node;
    }

    private ObjectNode toNestedJsonNode(Map<String, Map<String, Long>> outer) {
        ObjectNode root = MAPPER.createObjectNode();
        outer.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> {
                    ObjectNode inner = root.putObject(entry.getKey());
                    entry.getValue().entrySet().stream()
                            .sorted(byValueDesc())
                            .forEach(ie -> inner.put(ie.getKey(), ie.getValue()));
                });
        return root;
    }

    private String truncate(String s, int maxLen) {
        if (s == null)
            return "";
        if (s.length() <= maxLen)
            return s;
        return s.substring(0, maxLen - 3) + "...";
    }

}
