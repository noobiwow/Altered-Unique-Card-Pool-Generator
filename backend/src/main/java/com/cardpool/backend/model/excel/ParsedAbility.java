package com.cardpool.backend.model.excel;

import java.util.List;
import java.util.Objects;

/**
 * Represents a single parsed ability from a card's effect text.
 * <p>
 * A card may have multiple abilities separated by two or more spaces.
 * Each ability is decomposed into:
 * <ul>
 * <li>{@link AbilityType} - the kind of ability (ACTIVATED, TRIGGERED,
 * STATIC)</li>
 * <li>trigger - what activates or fires the ability (e.g. "{H}", "When I
 * leave...")</li>
 * <li>condition - optional "If ..." clause</li>
 * <li>effectBody - the actual effect text</li>
 * <li>keywords - extracted named effects [{Sabotage}], statuses [[Asleep]],
 * symbols {J}</li>
 * </ul>
 */
public class ParsedAbility {

    public enum AbilityType {
        /** Uses an action cost: {H}, {J}, {R}, {F} or custom costs like {1}, {3} */
        ACTIVATED,
        /** Fires on an event: "When ...", "At Dusk", "At Night" */
        TRIGGERED,
        /** Always active, prefixed with [] or no trigger */
        STATIC
    }

    private final String rawText;
    private final AbilityType type;
    private final String trigger;
    private final String condition;
    private final String effectBody;
    private final List<String> namedEffects; // [Sabotage], [Resupply] …
    private final List<String> keywordStatuses; // [[Asleep]], [[Fleeting]] …
    private final List<String> costSymbols; // {H}, {J}, {1} …

    public ParsedAbility(
            String rawText,
            AbilityType type,
            String trigger,
            String condition,
            String effectBody,
            List<String> namedEffects,
            List<String> keywordStatuses,
            List<String> costSymbols) {
        this.rawText = rawText;
        this.type = type;
        this.trigger = trigger;
        this.condition = condition;
        this.effectBody = effectBody;
        this.namedEffects = List.copyOf(namedEffects);
        this.keywordStatuses = List.copyOf(keywordStatuses);
        this.costSymbols = List.copyOf(costSymbols);
    }

    public String getRawText() {
        return rawText;
    }

    public AbilityType getType() {
        return type;
    }

    public String getTrigger() {
        return trigger;
    }

    public String getCondition() {
        return condition;
    }

    public String getEffectBody() {
        return effectBody;
    }

    public List<String> getNamedEffects() {
        return namedEffects;
    }

    public List<String> getKeywordStatuses() {
        return keywordStatuses;
    }

    public List<String> getCostSymbols() {
        return costSymbols;
    }

    public boolean hasCondition() {
        return condition != null && !condition.isBlank();
    }

    @Override
    public String toString() {
        return String.format("ParsedAbility{type=%s, trigger='%s', condition='%s', effectBody='%s'}",
                type, trigger, condition, effectBody);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (!(o instanceof ParsedAbility))
            return false;
        ParsedAbility that = (ParsedAbility) o;
        return Objects.equals(rawText, that.rawText);
    }

    @Override
    public int hashCode() {
        return Objects.hash(rawText);
    }
}
