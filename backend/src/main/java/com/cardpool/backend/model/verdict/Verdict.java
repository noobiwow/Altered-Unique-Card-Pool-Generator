package com.cardpool.backend.model.verdict;

import java.util.List;

/**
 * A team decision about a specific card (by reference).
 *
 * @param reference       card reference
 * @param name            card name, kept for readability of the data file
 * @param status          decision
 * @param note            free-form comment from the review
 * @param reviewedRuleIds rules that flagged the card when it was approved; a
 *                        rule not in this list flags the card again
 * @param decidedAt       ISO-8601 timestamp
 */
public record Verdict(
        String reference,
        String name,
        VerdictStatus status,
        String note,
        List<String> reviewedRuleIds,
        String decidedAt) {
}
