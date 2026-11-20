package com.cardpool.backend.model.report;

import java.util.List;

public record Report(
        String id,
        String title,
        String poolName,
        String notes,
        String createdAt,
        int poolSize,
        ReportSummary summary,
        List<ReportCard> cards,
        List<Replacement> replacements) {
}
