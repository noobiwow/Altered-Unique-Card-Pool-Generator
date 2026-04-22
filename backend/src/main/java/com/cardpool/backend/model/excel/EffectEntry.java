package com.cardpool.backend.model.excel;

import java.util.List;
import java.util.Objects;

public class EffectEntry {

    private final String set;
    private final ParsedAbility ability;

    public EffectEntry(String set, ParsedAbility ability) {
        this.set = set;
        this.ability = ability;
    }

    public String getSet() {
        return set;
    }

    public ParsedAbility getAbility() {
        return ability;
    }

    public String getTrigger() {
        return ability.getTrigger();
    }

    public String getCondition() {
        return ability.getCondition();
    }

    public String getEffectBody() {
        return ability.getEffectBody();
    }

    public ParsedAbility.AbilityType getType() {
        return ability.getType();
    }

    public List<String> getNamedEffects() {
        return ability.getNamedEffects();
    }

    public List<String> getKeywordStatuses() {
        return ability.getKeywordStatuses();
    }

    public List<String> getCostSymbols() {
        return ability.getCostSymbols();
    }

    public String getFullEffectText() {
        return ability.getRawText();
    }

    public String getNormalizedEffectBody() {
        String body = getEffectBody();
        if (body == null)
            return "";
        return body.replaceAll("\\s+", " ").strip();
    }

    public String getEffectStructure() {
        StringBuilder sb = new StringBuilder();
        if (getTrigger() != null) {
            sb.append(getTrigger()).append(" ");
        }
        if (getCondition() != null) {
            sb.append(getCondition()).append(" ");
        }
        sb.append(getNormalizedEffectBody());
        return sb.toString().strip();
    }

    public String getEffectStructureJson() {
        StringBuilder sb = new StringBuilder();
        sb.append("{");
        sb.append(formatJsonField("trigger", getTrigger()));
        sb.append(formatJsonField("condition", getCondition()));
        sb.append(formatJsonField("effect", getNormalizedEffectBody()));
        if (sb.charAt(sb.length() - 1) == ',') {
            sb.setLength(sb.length() - 1);
        }
        sb.append("}");
        return sb.toString();
    }

    private String formatJsonField(String key, String value) {
        if (value == null || value.isEmpty())
            return "";
        return String.format(" \"%s\": \"%s\",", key, escape(value));
    }

    private String escape(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n");
    }

    public String getEffectKey() {
        return String.format("%s|%s|%s",
                getType().name(),
                getTrigger() != null ? getTrigger() : "",
                getCondition() != null ? getCondition() : "");
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (!(o instanceof EffectEntry))
            return false;
        EffectEntry that = (EffectEntry) o;
        return Objects.equals(set, that.set) && Objects.equals(ability, that.ability);
    }

    @Override
    public int hashCode() {
        return Objects.hash(set, ability);
    }

    @Override
    public String toString() {
        return String.format("[%s] %s | %s | %s",
                set,
                getType(),
                getTrigger() != null ? getTrigger() : "[]",
                getEffectBody());
    }
}
