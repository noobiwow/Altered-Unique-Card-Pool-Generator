package com.cardpool.backend.model.rule;

/** The part of a card a {@link RuleCondition} is evaluated against. */
public enum RuleField {
    /** Card name. */
    NAME,
    /** Main effect text (abilities shown when the card is played). */
    MAIN_EFFECT,
    /** Echo effect text (the ability usable from the Reserve). */
    ECHO_EFFECT,
    /** Main or echo effect text. */
    ANY_EFFECT,
    /** A keyword of the main or echo effect, e.g. {@code Sabotage} or {@code Anchored}. */
    KEYWORD,
    /** An ability trigger, e.g. {@code When I'm played}, {@code At Dusk}, {@code {J}}. */
    TRIGGER,
    /** Hand cost, value is a cost expression such as {@code 3-}, {@code 4+} or {@code 2}. */
    HAND_COST,
    /** Reserve cost, value is a cost expression. */
    RESERVE_COST,
    /** Card type (English label), e.g. {@code Character}, {@code Spell}. */
    CARD_TYPE,
    /** Any sub type (English label), e.g. {@code Animal}. */
    SUBTYPE,
    /** Set code, e.g. {@code CYCLONE}. */
    SET,
    /**
     * One ability matching a combination of trigger, condition and effect, see
     * {@link AbilityPattern}. Uses {@code RuleCondition.ability}, not {@code value}.
     */
    ABILITY;

    public boolean isCost() {
        return this == HAND_COST || this == RESERVE_COST;
    }
}
