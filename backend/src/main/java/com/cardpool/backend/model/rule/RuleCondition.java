package com.cardpool.backend.model.rule;

/**
 * One test of an {@link EffectRule}. All conditions of a rule must hold for the
 * rule to flag a card.
 *
 * @param field   what part of the card is tested
 * @param mode    how text is compared (defaults to {@link MatchMode#CONTAINS})
 * @param value   the text, regex or cost expression to look for (unused for ABILITY)
 * @param negate  when true the condition holds if the test does NOT match
 * @param ability the combination looked for, for {@link RuleField#ABILITY} only
 */
public record RuleCondition(RuleField field, MatchMode mode, String value, boolean negate, AbilityPattern ability) {

    /** A condition on a single field. */
    public RuleCondition(RuleField field, MatchMode mode, String value, boolean negate) {
        this(field, mode, value, negate, null);
    }

    /** A condition looking for one ability combining the given parts. */
    public static RuleCondition ability(AbilityPattern pattern, boolean negate) {
        return new RuleCondition(RuleField.ABILITY, null, null, negate, pattern);
    }

    public MatchMode modeOrDefault() {
        return mode == null ? MatchMode.CONTAINS : mode;
    }
}
