package com.cardpool.backend.model.report;

import java.util.Map;

/**
 * @param flagged    cards awaiting a team decision
 * @param cleared    flagged cards already approved
 * @param rejected   cards rejected by the team
 * @param bySeverity flagged cards by their highest severity
 * @param byFaction  reported cards by faction code
 */
public record ReportSummary(int flagged, int cleared, int rejected, Map<String, Integer> bySeverity,
        Map<String, Integer> byFaction) {
}
