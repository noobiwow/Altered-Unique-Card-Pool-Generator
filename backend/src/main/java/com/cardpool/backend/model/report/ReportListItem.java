package com.cardpool.backend.model.report;

public record ReportListItem(String id, String title, String poolName, String createdAt, int poolSize,
        ReportSummary summary) {

    public static ReportListItem of(Report report) {
        return new ReportListItem(report.id(), report.title(), report.poolName(), report.createdAt(),
                report.poolSize(), report.summary());
    }
}
