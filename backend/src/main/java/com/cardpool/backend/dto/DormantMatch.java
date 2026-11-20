package com.cardpool.backend.dto;

import java.util.List;

import com.cardpool.backend.model.Ability;
import com.cardpool.backend.model.rule.Severity;

/**
 * A rule that would flag a card but does not apply to it: the rule is
 * disabled, or restricted to other factions. Shown for information only; it
 * never changes the card's status.
 *
 * @param ruleId      the rule
 * @param label       rule label at analysis time
 * @param severity    rule severity
 * @param matchedText the ability (or name) the rule found, if any
 * @param source      where it was found: NAME, MAIN, ECHO or CARD
 * @param ability     the ability matched by an ABILITY condition (null otherwise)
 * @param disabled    the rule is switched off
 * @param factions    the factions the rule is restricted to when the card is not
 *                    one of them (empty otherwise)
 */
public record DormantMatch(String ruleId, String label, Severity severity, String matchedText, String source,
        Ability ability, boolean disabled, List<String> factions) {
}
