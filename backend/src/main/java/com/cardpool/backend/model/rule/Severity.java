package com.cardpool.backend.model.rule;

/** How dangerous a flagged effect is considered for the pool. */
public enum Severity {
    LOW, MEDIUM, HIGH;

    public static Severity orDefault(Severity severity) {
        return severity == null ? MEDIUM : severity;
    }
}
