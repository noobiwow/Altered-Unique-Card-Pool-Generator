package com.cardpool.backend.model.excel;

import java.util.Comparator;
import java.util.List;
import java.util.Map;

public class StatsFormatter {

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
        StringBuilder sb = new StringBuilder();
        sb.append("{\n");
        sb.append(String.format("  \"totalEffects\": %d,%n", inventory.totalEffects()));
        sb.append(String.format("  \"sets\": %d,%n", inventory.countBySet().size()));
        sb.append(String.format(" \"uniqueStructures\": %d,%n", inventory.countByEffectStructure().size()));
        sb.append("  \"effectStructureBySet\": ").append(jsonNestedMap(inventory.countByEffectStructureBySet()))
                .append(",\n");
        sb.append("  \"effectBodyBySet\": ").append(jsonNestedMap(inventory.countByEffectBodyBySet()))
                .append(",\n");
        sb.append("  \"triggerBySet\": ").append(jsonNestedMap(inventory.countByTriggerBySet()))
                .append(",\n");
        sb.append("  \"conditionBySet\": ").append(jsonNestedMap(inventory.countByConditionBySet()))
                .append(",\n");
        sb.append("  \"countByType\": ").append(jsonMap(inventory.countByType())).append(",\n");
        sb.append("  \"countByTrigger\": ").append(jsonMap(inventory.countByTrigger())).append(",\n");
        sb.append("  \"countByCondition\": ").append(jsonMap(inventory.countByCondition())).append(",\n");
        sb.append("  \"countByEffectStructure\": ").append(jsonMap(inventory.countByEffectStructure())).append(",\n");
        sb.append("  \"effectStructureByType\": ").append(jsonNestedMap(inventory.countByEffectStructureByType()))
                .append(",\n");
        sb.append("  \"effectEntries\": ").append(jsonEffectEntries(inventory.getEntries()))
                .append(",\n");
        sb.append("  \"effectEntriesBySet\": ").append(jsonEffectEntriesBySet(inventory))
                .append("\n");
        sb.append("}");
        return sb.toString();
    }

    private String jsonEffectEntriesBySet(EffectInventory inventory) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\n");
        var setGroups = inventory.getEntries().stream()
                .collect(java.util.stream.Collectors.groupingBy(EffectEntry::getSet))
                .entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .toList();
        for (int i = 0; i < setGroups.size(); i++) {
            var setEntry = setGroups.get(i);
            sb.append(String.format("    \"%s\": ", escape(setEntry.getKey())));
            sb.append("[\n");
            var entries = setEntry.getValue();
            for (int j = 0; j < entries.size(); j++) {
                sb.append("      ").append(entries.get(j).getEffectStructureJson());
                if (j < entries.size() - 1)
                    sb.append(",");
                sb.append("\n");
            }
            sb.append("    ]");
            if (i < setGroups.size() - 1)
                sb.append(",");
            sb.append("\n");
        }
        sb.append("  }");
        return sb.toString();
    }

    private String jsonEffectEntries(List<EffectEntry> entries) {
        StringBuilder sb = new StringBuilder();
        sb.append("[\n");
        for (int i = 0; i < entries.size(); i++) {
            sb.append("    ").append(entries.get(i).getEffectStructureJson());
            if (i < entries.size() - 1)
                sb.append(",");
            sb.append("\n");
        }
        sb.append("  ]");
        return sb.toString();
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

    private String jsonMap(Map<String, Long> map) {
        if (map.isEmpty())
            return "{}";
        StringBuilder sb = new StringBuilder("{\n");
        var entries = map.entrySet().stream().sorted(byValueDesc()).toList();
        for (int i = 0; i < entries.size(); i++) {
            var e = entries.get(i);
            sb.append(String.format("    \"%s\": %d", escape(e.getKey()), e.getValue()));
            if (i < entries.size() - 1)
                sb.append(",");
            sb.append("\n");
        }
        sb.append("  }");
        return sb.toString();
    }

    private String jsonNestedMap(Map<String, Map<String, Long>> outer) {
        if (outer.isEmpty())
            return "{}";
        StringBuilder sb = new StringBuilder("{\n");
        var sets = outer.entrySet().stream().toList();
        for (int i = 0; i < sets.size(); i++) {
            var entry = sets.get(i);
            sb.append(String.format("    \"%s\": ", escape(entry.getKey())));
            Map<String, Long> inner = entry.getValue();
            if (inner.isEmpty()) {
                sb.append("{}");
            } else {
                sb.append("{\n");
                var innerEntries = inner.entrySet().stream().sorted(byValueDesc()).toList();
                for (int j = 0; j < innerEntries.size(); j++) {
                    var ie = innerEntries.get(j);
                    sb.append(String.format("      \"%s\": %d", escape(ie.getKey()), ie.getValue()));
                    if (j < innerEntries.size() - 1)
                        sb.append(",");
                    sb.append("\n");
                }
                sb.append("    }");
            }
            if (i < sets.size() - 1)
                sb.append(",");
            sb.append("\n");
        }
        sb.append("  }");
        return sb.toString();
    }

    private String truncate(String s, int maxLen) {
        if (s == null)
            return "";
        if (s.length() <= maxLen)
            return s;
        return s.substring(0, maxLen - 3) + "...";
    }

    private String escape(String s) {
        if (s == null)
            return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
