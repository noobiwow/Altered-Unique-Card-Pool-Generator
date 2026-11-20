package com.cardpool.backend.dto;

import java.util.List;

import com.cardpool.backend.model.Card;
import com.cardpool.backend.model.report.Replacement;

/**
 * @param title          report title
 * @param poolName       name of the analysed pool
 * @param notes          free-form notes for the team
 * @param cards          the whole pool (only findings are kept in the report)
 * @param replacements   swaps made in the pool before reporting
 * @param includeCleared also include flagged cards already approved
 */
public record CreateReportRequest(String title, String poolName, String notes, List<Card> cards,
        List<Replacement> replacements, boolean includeCleared) {
}
