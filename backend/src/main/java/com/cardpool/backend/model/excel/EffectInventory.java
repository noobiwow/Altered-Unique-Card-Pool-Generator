package com.cardpool.backend.model.excel;

import java.util.*;
import java.util.stream.Collectors;

public class EffectInventory {

    private final List<EffectEntry> entries;
    private final Map<String, Long> countByEffectBody;
    private final Map<String, Map<String, Long>> countByEffectBodyBySet;
    private final Map<String, Long> countBySet;
    private final Map<String, Long> countByType;
    private final Map<String, Long> countByTrigger;
    private final Map<String, Long> countByCondition;
    private final Map<String, Long> countByEffectStructure;
    private final Map<String, Map<String, Long>> countByEffectStructureBySet;
    private final Map<String, Map<String, Long>> countByEffectStructureByType;
    private final Map<String, Map<String, Long>> countByTriggerBySet;
    private final Map<String, Map<String, Long>> countByConditionBySet;

    public EffectInventory(List<EffectEntry> entries) {
        this.entries = List.copyOf(entries);
        this.countByEffectBody = computeEffectBodyCounts(this.entries);
        this.countByEffectBodyBySet = computeEffectBodyBySetCounts(this.entries);
        this.countBySet = computeSetCounts(this.entries);
        this.countByType = computeTypeCounts(this.entries);
        this.countByTrigger = computeTriggerCounts(this.entries);
        this.countByCondition = computeConditionCounts(this.entries);
        this.countByEffectStructure = computeEffectStructureCounts(this.entries);
        this.countByEffectStructureBySet = computeEffectStructureBySetCounts(this.entries);
        this.countByEffectStructureByType = computeEffectStructureByTypeCounts(this.entries);
        this.countByTriggerBySet = computeTriggerBySetCounts(this.entries);
        this.countByConditionBySet = computeConditionBySetCounts(this.entries);
    }

    public List<EffectEntry> getEntries() {
        return entries;
    }

    public int totalEffects() {
        return entries.size();
    }

    public int totalCards() {
        return entries.stream()
                .map(EffectEntry::getSet)
                .collect(Collectors.toSet())
                .size();
    }

    public Map<String, Long> countByEffectBody() {
        return countByEffectBody;
    }

    public Map<String, Map<String, Long>> countByEffectBodyBySet() {
        return countByEffectBodyBySet;
    }

    public Map<String, Long> countBySet() {
        return countBySet;
    }

    public Map<String, Long> countByType() {
        return countByType;
    }

    public Map<String, Long> countByTrigger() {
        return countByTrigger;
    }

    public Map<String, Long> countByCondition() {
        return countByCondition;
    }

    public Map<String, Long> countByEffectStructure() {
        return countByEffectStructure;
    }

    public Map<String, Map<String, Long>> countByEffectStructureBySet() {
        return countByEffectStructureBySet;
    }

    public Map<String, Map<String, Long>> countByEffectStructureByType() {
        return countByEffectStructureByType;
    }

    public Map<String, Map<String, Long>> countByTriggerBySet() {
        return countByTriggerBySet;
    }

    public Map<String, Map<String, Long>> countByConditionBySet() {
        return countByConditionBySet;
    }

    public List<EffectEntry> getBySet(String set) {
        return entries.stream()
                .filter(e -> set.equals(e.getSet()))
                .toList();
    }

    public List<EffectEntry> getByType(ParsedAbility.AbilityType type) {
        return entries.stream()
                .filter(e -> e.getType() == type)
                .toList();
    }

    public List<EffectEntry> getByExactEffectBody(String effectBody) {
        return entries.stream()
                .filter(e -> effectBody.equals(e.getNormalizedEffectBody()))
                .toList();
    }

    public List<EffectEntry> getByTrigger(String trigger) {
        return entries.stream()
                .filter(e -> trigger.equals(e.getTrigger()))
                .toList();
    }

    public List<EffectEntry> searchByEffectBody(String query) {
        String lowerQuery = query.toLowerCase();
        return entries.stream()
                .filter(e -> e.getEffectBody() != null &&
                        e.getEffectBody().toLowerCase().contains(lowerQuery))
                .toList();
    }

    public List<EffectEntry> getByEffectStructure(String structure) {
        return entries.stream()
                .filter(e -> structure.equals(e.getEffectStructure()))
                .toList();
    }

    private Map<String, Long> computeEffectBodyCounts(List<EffectEntry> entries) {
        return entries.stream()
                .collect(Collectors.groupingBy(
                        e -> e.getNormalizedEffectBody(),
                        Collectors.counting()));
    }

    private Map<String, Map<String, Long>> computeEffectBodyBySetCounts(List<EffectEntry> entries) {
        Map<String, Map<String, Long>> result = new TreeMap<>();
        for (EffectEntry entry : entries) {
            result.computeIfAbsent(entry.getSet(), k -> new TreeMap<>())
                    .merge(entry.getNormalizedEffectBody(), 1L, Long::sum);
        }
        Map<String, Map<String, Long>> unmodifiable = new TreeMap<>();
        result.forEach((k, v) -> unmodifiable.put(k, Collections.unmodifiableMap(v)));
        return Collections.unmodifiableMap(unmodifiable);
    }

    private Map<String, Long> computeSetCounts(List<EffectEntry> entries) {
        return entries.stream()
                .collect(Collectors.groupingBy(
                        EffectEntry::getSet,
                        Collectors.counting()));
    }

    private Map<String, Long> computeTypeCounts(List<EffectEntry> entries) {
        return entries.stream()
                .collect(Collectors.groupingBy(
                        e -> e.getType().name(),
                        Collectors.counting()));
    }

    private Map<String, Long> computeTriggerCounts(List<EffectEntry> entries) {
        return entries.stream()
                .filter(e -> e.getTrigger() != null)
                .collect(Collectors.groupingBy(
                        EffectEntry::getTrigger,
                        Collectors.counting()));
    }

    private Map<String, Long> computeConditionCounts(List<EffectEntry> entries) {
        return entries.stream()
                .filter(e -> e.getCondition() != null)
                .collect(Collectors.groupingBy(
                        EffectEntry::getCondition,
                        Collectors.counting()));
    }

    private Map<String, Long> computeEffectStructureCounts(List<EffectEntry> entries) {
        return entries.stream()
                .collect(Collectors.groupingBy(
                        EffectEntry::getEffectStructure,
                        Collectors.counting()));
    }

    private Map<String, Map<String, Long>> computeEffectStructureBySetCounts(List<EffectEntry> entries) {
        Map<String, Map<String, Long>> result = new TreeMap<>();
        for (EffectEntry entry : entries) {
            result.computeIfAbsent(entry.getSet(), k -> new TreeMap<>())
                    .merge(entry.getEffectStructure(), 1L, Long::sum);
        }
        Map<String, Map<String, Long>> unmodifiable = new TreeMap<>();
        result.forEach((k, v) -> unmodifiable.put(k, Collections.unmodifiableMap(v)));
        return Collections.unmodifiableMap(unmodifiable);
    }

    private Map<String, Map<String, Long>> computeEffectStructureByTypeCounts(List<EffectEntry> entries) {
        Map<String, Map<String, Long>> result = new TreeMap<>();
        for (EffectEntry entry : entries) {
            result.computeIfAbsent(entry.getType().name(), k -> new TreeMap<>())
                    .merge(entry.getEffectStructure(), 1L, Long::sum);
        }
        Map<String, Map<String, Long>> unmodifiable = new TreeMap<>();
        result.forEach((k, v) -> unmodifiable.put(k, Collections.unmodifiableMap(v)));
        return Collections.unmodifiableMap(unmodifiable);
    }

    private Map<String, Map<String, Long>> computeTriggerBySetCounts(List<EffectEntry> entries) {
        Map<String, Map<String, Long>> result = new TreeMap<>();
        for (EffectEntry entry : entries) {
            if (entry.getTrigger() != null) {
                result.computeIfAbsent(entry.getSet(), k -> new TreeMap<>())
                        .merge(entry.getTrigger(), 1L, Long::sum);
            }
        }
        Map<String, Map<String, Long>> unmodifiable = new TreeMap<>();
        result.forEach((k, v) -> unmodifiable.put(k, Collections.unmodifiableMap(v)));
        return Collections.unmodifiableMap(unmodifiable);
    }

    private Map<String, Map<String, Long>> computeConditionBySetCounts(List<EffectEntry> entries) {
        Map<String, Map<String, Long>> result = new TreeMap<>();
        for (EffectEntry entry : entries) {
            if (entry.getCondition() != null) {
                result.computeIfAbsent(entry.getSet(), k -> new TreeMap<>())
                        .merge(entry.getCondition(), 1L, Long::sum);
            }
        }
        Map<String, Map<String, Long>> unmodifiable = new TreeMap<>();
        result.forEach((k, v) -> unmodifiable.put(k, Collections.unmodifiableMap(v)));
        return Collections.unmodifiableMap(unmodifiable);
    }

    public static EffectInventory empty() {
        return new EffectInventory(List.of());
    }
}
