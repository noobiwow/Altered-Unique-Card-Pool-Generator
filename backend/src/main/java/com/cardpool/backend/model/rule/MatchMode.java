package com.cardpool.backend.model.rule;

/**
 * How a {@link RuleCondition} value is compared with the card text. Text is
 * compared case-insensitively with whitespace collapsed. Ignored for cost
 * fields, whose value is always a cost expression.
 */
public enum MatchMode {
    /** The text contains the value. */
    CONTAINS,
    /** The text (or one whole ability of an effect) equals the value. */
    EXACT,
    /** The value is a (case-insensitive) regular expression found in the text. */
    REGEX,
    /** The part is empty (ability parts only, e.g. "no condition"). The value is ignored. */
    ABSENT,
    /**
     * The part is one of the given CardsData fragments (ability parts only, see
     * {@link PartMatch#fragments()}). Compared by id when the card has fragment
     * ids, by text otherwise.
     */
    FRAGMENTS
}
