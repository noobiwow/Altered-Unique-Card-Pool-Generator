package com.cardpool.backend.model.report;

import java.util.List;

import com.cardpool.backend.dto.AnalysisStatus;
import com.cardpool.backend.dto.RuleMatch;
import com.cardpool.backend.model.Ability;
import com.cardpool.backend.model.verdict.Verdict;

/** Snapshot of a reported card, independent of later rule/verdict changes. */
public record ReportCard(
        String reference,
        String name,
        String factionCode,
        String factionName,
        String setName,
        String cardType,
        String handCost,
        String reserveCost,
        String mainEffect,
        String echoEffect,
        /** Structured abilities when the card source provides them (may be null). */
        List<Ability> abilities,
        AnalysisStatus status,
        List<RuleMatch> warnings,
        Verdict verdict) {
}
