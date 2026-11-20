package com.cardpool.backend.dto;

/** Outcome of the analysis of one card. */
public enum AnalysisStatus {
    /** At least one rule flags the card and the team has not cleared it. */
    FLAGGED,
    /** Every rule flagging the card was reviewed and the card approved. */
    CLEARED,
    /** The team rejected the card. */
    REJECTED
}
