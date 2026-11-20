package com.cardpool.backend.dto;

import com.cardpool.backend.model.Ability;
import com.cardpool.backend.model.rule.Severity;

/**
 * A rule that flagged a card.
 *
 * @param ruleId      the rule that matched
 * @param label       rule label at analysis time
 * @param severity    rule severity
 * @param category    rule category
 * @param reason      why the rule exists
 * @param matchedText the ability (or name) that triggered the match, if any
 * @param source      where the match was found: NAME, MAIN, ECHO or CARD
 * @param reviewed    true when the team already approved the card with this
 *                    rule in place
 * @param ability     the ability matched by an ABILITY condition, split into its
 *                    parts (null for other conditions)
 */
public record RuleMatch(
        String ruleId,
        String label,
        Severity severity,
        String category,
        String reason,
        String matchedText,
        String source,
        boolean reviewed,
        Ability ability) {

    public RuleMatch withReviewed(boolean value) {
        return new RuleMatch(ruleId, label, severity, category, reason, matchedText, source, value, ability);
    }
}
