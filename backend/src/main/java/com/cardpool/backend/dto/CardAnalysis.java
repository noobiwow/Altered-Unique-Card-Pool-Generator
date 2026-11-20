package com.cardpool.backend.dto;

import java.util.List;

import com.cardpool.backend.model.verdict.Verdict;
import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Findings on one card.
 *
 * @param status   FLAGGED, CLEARED or REJECTED; null when the card only has
 *                 dormant matches (it is then clean)
 * @param warnings enabled rules matching the card
 * @param dormant  disabled rules, or rules restricted to other factions, that
 *                 would match the card (information only)
 */
public record CardAnalysis(
        String reference,
        String name,
        String factionCode,
        AnalysisStatus status,
        List<RuleMatch> warnings,
        Verdict verdict,
        @JsonInclude(JsonInclude.Include.NON_EMPTY) List<DormantMatch> dormant) {

    public CardAnalysis(String reference, String name, String factionCode, AnalysisStatus status,
            List<RuleMatch> warnings, Verdict verdict) {
        this(reference, name, factionCode, status, warnings, verdict, List.of());
    }

    public CardAnalysis withDormant(List<DormantMatch> matches) {
        return new CardAnalysis(reference, name, factionCode, status, warnings, verdict, List.copyOf(matches));
    }
}
